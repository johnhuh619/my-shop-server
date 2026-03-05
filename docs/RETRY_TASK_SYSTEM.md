# RetryTask 시스템 — Outbox 패턴 기반 재시도 메커니즘

## 개요

비동기 이벤트 처리(`@Async` + `@TransactionalEventListener`)에서 후속 작업이 실패할 경우,
DB 기반 Outbox 패턴으로 재시도를 보장하는 시스템이다.

### 왜 필요한가

결제 완료 후 Order 상태 변경, 배송 생성 등의 후속 작업은 `@Async`로 비동기 실행된다.
비동기 작업이 실패하면 호출자(HTTP 응답)는 이미 반환된 상태이므로, 실패를 감지하고
재시도할 별도의 메커니즘이 필요하다.

```
사용자 결제 → confirmPayment() → HTTP 200 반환 (여기서 끝)
                                    │
                                    └─ @Async: markAsPaid() ← 여기서 실패하면?
                                         사용자에게 알릴 방법 없음
                                         → RetryTask에 기록 → 스케줄러가 재시도
```

---

## 아키텍처

### 전체 흐름

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 1차 시도: @Async 이벤트 리스너                                          │
│                                                                         │
│  PaymentEventListener                                                   │
│    ├─ handlePaymentCompleted()                                          │
│    │    ├─ orderService.markAsPaid() ─────── 성공 → 끝                  │
│    │    │                                    실패 ─┐                    │
│    │    │                                          ▼                    │
│    │    │                              retryTaskService.register(       │
│    │    │                                "MARK_AS_PAID",               │
│    │    │                                "{orderId: 7}")               │
│    │    │                                          │                    │
│    │    └─ deliveryService.create() ──── 성공 → 끝 │                    │
│    │                                     실패 ─┐   │                    │
│    │                                           ▼   │                    │
│    │                              register("CREATE_DELIVERY", ...)      │
│    │                                               │                    │
│    └─ handlePaymentFailed()                        │                    │
│         └─ orderService.cancelOrderBySystem()      │                    │
│                                      실패 ─┐       │                    │
│                                            ▼       │                    │
│                              register("CANCEL_ORDER", ...)              │
└─────────────────────────────────────────────┼──────┘                    │
                                              │                           │
┌─────────────────────────────────────────────▼───────────────────────────┐
│ DB: retry_tasks 테이블                                                  │
│                                                                         │
│  id │ taskType      │ payload       │ status  │ retryCount │ nextRetryAt│
│  1  │ MARK_AS_PAID  │ {orderId: 7}  │ PENDING │ 0          │ now        │
└─────────────────────────────────────────────────────────────────────────┘
                                              │
┌─────────────────────────────────────────────▼───────────────────────────┐
│ 2차~ 시도: @Scheduled 스케줄러 (30초마다 폴링)                          │
│                                                                         │
│  RetryTaskScheduler                                                     │
│    └─ SELECT * FROM retry_tasks                                         │
│         WHERE status = 'PENDING' AND next_retry_at < now()              │
│                                                                         │
│    └─ RetryTaskService.processTask(task)                                │
│         └─ handlerMap.get("MARK_AS_PAID") → MarkAsPaidHandler           │
│              └─ orderService.markAsPaid(7) ── 성공 → COMPLETED          │
│                                               실패 → recordFailure()   │
└─────────────────────────────────────────────────────────────────────────┘
```

### 구성 요소

| 컴포넌트 | 역할 | 위치 |
|----------|------|------|
| `RetryTask` | 재시도 작업 엔티티 (상태, 횟수, 백오프 관리) | `outbox/domain/` |
| `RetryTaskStatus` | `PENDING` → `COMPLETED` / `EXHAUSTED` | `outbox/domain/` |
| `RetryTaskRepository` | PENDING + nextRetryAt 기반 조회 | `outbox/repository/` |
| `RetryTaskService` | 등록(register) + 처리(processTask) | `outbox/service/` |
| `RetryTaskScheduler` | 30초마다 PENDING task 폴링 | `outbox/scheduler/` |
| `RetryTaskHandler` | 핸들러 인터페이스 | `outbox/handler/` |
| `MarkAsPaidHandler` | markAsPaid + 배송 생성 재시도 | `outbox/handler/` |
| `CancelOrderHandler` | cancelOrderBySystem 재시도 | `outbox/handler/` |
| `CompleteOrderHandler` | completeOrder 재시도 | `outbox/handler/` |
| `CreateDeliveryHandler` | 배송 생성 재시도 | `outbox/handler/` |

---

## RetryTask 상태 머신

```
                          register()
                              │
                              ▼
                         ┌─────────┐
                         │ PENDING │ ◄──────────────────────┐
                         └────┬────┘                        │
                              │                             │
                    processTask() 실행                      │
                              │                             │
                    ┌─────────┴─────────┐                   │
                    │                   │                    │
                 성공                 실패                   │
                    │                   │                    │
                    ▼                   ▼                    │
             ┌───────────┐    retryCount < maxRetries?      │
             │ COMPLETED │           │                      │
             └───────────┘     ┌─────┴─────┐                │
                               │           │                │
                              Yes          No               │
                               │           │                │
                               │           ▼                │
                               │    ┌───────────┐           │
                               │    │ EXHAUSTED │           │
                               │    └───────────┘           │
                               │                            │
                               └─ nextRetryAt 갱신 ─────────┘
                                  (지수 백오프)
```

---

## 지수 백오프 (Exponential Backoff)

### 왜 지수 백오프인가

고정 간격 재시도의 문제:

```
고정 30초 간격:
  실패 → 30초 후 재시도 → 실패 → 30초 후 재시도 → 실패 → 30초 후 재시도
  → 외부 서비스 장애 시 30초마다 계속 때림
  → 장애 상황을 악화시킴 (thundering herd)
```

지수 백오프:

```
실패 → 60초 후 → 실패 → 120초 후 → 실패 → 240초 후 → 실패 → 480초 후 → 실패 → EXHAUSTED
  → 간격이 점점 넓어져 장애 회복 시간을 줌
  → 외부 서비스에 부하를 주지 않음
```

### 구현

```java
// RetryTask.java
private static final int DEFAULT_MAX_RETRIES = 5;
private static final long BASE_BACKOFF_SECONDS = 30;

public void recordFailure(String errorMessage) {
    this.retryCount++;
    this.lastError = errorMessage;
    this.updatedAt = Instant.now();

    if (this.retryCount >= this.maxRetries) {
        this.status = RetryTaskStatus.EXHAUSTED;
    } else {
        long backoffSeconds = BASE_BACKOFF_SECONDS * (1L << this.retryCount);
        this.nextRetryAt = Instant.now().plusSeconds(backoffSeconds);
    }
}
```

`1L << this.retryCount`는 비트 시프트로 2의 거듭제곱을 계산한다:

```
retryCount=1:  1L << 1 = 2   → 30 * 2  =   60초 (1분)
retryCount=2:  1L << 2 = 4   → 30 * 4  =  120초 (2분)
retryCount=3:  1L << 3 = 8   → 30 * 8  =  240초 (4분)
retryCount=4:  1L << 4 = 16  → 30 * 16 =  480초 (8분)
retryCount=5:  maxRetries 도달 → EXHAUSTED
```

### 타임라인 예시

```
t=0s      1차 시도 (@Async 이벤트 리스너) → 실패 → RetryTask 등록 (nextRetryAt=now)

t=30s     스케줄러 폴링 → task 발견 → 2차 시도 → 실패
          recordFailure: retryCount=1, nextRetryAt = now + 60초

t=60s     스케줄러 폴링 → nextRetryAt 안 됨 → 스킵
t=90s     스케줄러 폴링 → nextRetryAt 지남 → 3차 시도 → 실패
          recordFailure: retryCount=2, nextRetryAt = now + 120초

t=120s    스케줄러 폴링 → 스킵
t=150s    스케줄러 폴링 → 스킵
...
t=210s    스케줄러 폴링 → nextRetryAt 지남 → 4차 시도 → 실패
          recordFailure: retryCount=3, nextRetryAt = now + 240초

...
t=450s    5차 시도 → 실패
          recordFailure: retryCount=4, nextRetryAt = now + 480초

...
t=930s    6차 시도 → 실패
          recordFailure: retryCount=5 >= maxRetries=5 → EXHAUSTED
          → 더 이상 폴링에 안 잡힘 → 수동 개입 필요
```

### 왜 retryCount=0에서는 백오프를 안 거는가

```java
public static RetryTask create(String taskType, String payload) {
    return RetryTask.builder()
            .retryCount(0)
            .nextRetryAt(now)    // ← 즉시 실행 가능
            .build();
}
```

1차 시도는 이미 `@Async` 이벤트 리스너가 실패한 직후이므로, RetryTask 등록 시
`nextRetryAt = now`로 설정하여 다음 스케줄러 폴링(최대 30초 이내)에 바로 재시도한다.

첫 재시도가 실패해야 비로소 `recordFailure()`가 호출되어 `retryCount=1`이 되고,
이때부터 지수 백오프가 적용된다.

---

## 핸들러 설계

### RetryTaskHandler 인터페이스

```java
public interface RetryTaskHandler {
    String taskType();          // "MARK_AS_PAID", "CANCEL_ORDER" 등
    void handle(String payload); // 실제 재시도 로직
}
```

### 핸들러 ↔ taskType 매핑

```java
// RetryTaskService 생성자에서 자동 매핑
public RetryTaskService(RetryTaskRepository retryTaskRepository,
                        List<RetryTaskHandler> handlers) {
    this.handlerMap = handlers.stream()
            .collect(Collectors.toMap(RetryTaskHandler::taskType, Function.identity()));
}
```

Spring이 `RetryTaskHandler` 구현체를 모두 주입 → `taskType()`을 키로 Map 구성.
새 핸들러를 추가하면 자동으로 등록된다.

### 등록된 핸들러

| taskType | 핸들러 | 재시도하는 작업 |
|----------|--------|----------------|
| `MARK_AS_PAID` | `MarkAsPaidHandler` | `orderService.markAsPaid()` + `deliveryService.createDeliveryFromOrder()` |
| `CANCEL_ORDER` | `CancelOrderHandler` | `orderService.cancelOrderBySystem()` |
| `COMPLETE_ORDER` | `CompleteOrderHandler` | `orderService.completeOrder()` |
| `CREATE_DELIVERY` | `CreateDeliveryHandler` | `deliveryService.createDeliveryFromOrder()` |

---

## 중복 등록 방지

```java
// RetryTaskService.register()
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void register(String taskType, String payload) {
    boolean exists = retryTaskRepository
            .findByTaskTypeAndPayloadAndStatus(taskType, payload, RetryTaskStatus.PENDING)
            .isPresent();

    if (exists) {
        log.debug("RetryTask already exists: taskType={}, payload={}", taskType, payload);
        return;  // 이미 PENDING인 동일 task가 있으면 스킵
    }

    RetryTask task = RetryTask.create(taskType, payload);
    retryTaskRepository.save(task);
}
```

같은 `(taskType, payload, PENDING)` 조합이 이미 있으면 등록하지 않는다.
이벤트 리스너가 여러 번 실패해도 RetryTask가 중복 생성되지 않는다.

`REQUIRES_NEW`를 사용하는 이유: 이벤트 리스너의 예외 처리 과정에서 호출되므로,
리스너의 트랜잭션 상태와 무관하게 독립적으로 커밋되어야 한다.

---

## 스케줄러 폴링 비용

```java
@Scheduled(fixedRate = 30000)
public void processRetryTasks() {
    List<RetryTask> tasks = retryTaskRepository
            .findByStatusAndNextRetryAtBefore(RetryTaskStatus.PENDING, Instant.now());
    // ...
}
```

30초마다 실행되는 SELECT:

```sql
SELECT * FROM retry_tasks
WHERE status = 'PENDING' AND next_retry_at < now()
```

- `status`와 `next_retry_at`에 인덱스가 걸려 있으면 O(log N)
- 처리할 task가 없으면 빈 리스트 반환 → 아무것도 안 함
- 정상 운영 시 PENDING task는 거의 없으므로 실질적 부하 무시 가능

```
정상 상태: 30초마다 빈 SELECT 1회 → 무시 가능
장애 상태: PENDING task 수만큼 처리 → 지수 백오프로 부하 제한
```

---

## 멱등성과의 관계

RetryTask는 실패한 작업을 **통째로 다시 호출**한다. `MarkAsPaidHandler`가
`orderService.markAsPaid(orderId)`를 다시 호출하면, 1차에서 부분 성공한 재고 처리가
다시 실행될 수 있다.

```
1차: markAsPaid(order=7)
  TX1: CREATED → PAID ✓
  TX2: confirmForOrder(prod=1) ✓ (커밋됨)
  TX3: confirmForOrder(prod=2) ✗ (장애!)
  → RetryTask 등록

2차: MarkAsPaidHandler → markAsPaid(order=7)
  TX1: status=PAID → null (스킵)
  TX2: confirmForOrder(prod=1) → 멱등성 로그 있음 → skip ✓
  TX3: confirmForOrder(prod=2) → 멱등성 로그 없음 → confirm ✓
```

G1+A 트랜잭션 분리 이후에는 `markAsPaid`가 non-transactional 오케스트레이터이므로
부분 커밋이 발생할 수 있고, RetryTask의 재호출 시 `InventoryOperationLog` 기반
멱등성 체크가 이중 처리를 방지한다.

| 구성 요소 | 역할 |
|-----------|------|
| RetryTask | 실패한 작업을 **다시 호출**하는 메커니즘 |
| 멱등성 로그 | 다시 호출되었을 때 **이미 완료된 부분을 스킵**하는 안전장치 |

둘은 독립적이지만 상호 보완적이다. RetryTask 없이 멱등성만 있으면 재시도가 안 되고,
멱등성 없이 RetryTask만 있으면 이중 처리가 발생한다.
