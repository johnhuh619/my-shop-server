# Order 비관적 락 최적화 분석

## 배경

Supabase Query Performance 분석 결과, `SELECT ... FROM orders WHERE id=$1 FOR NO KEY UPDATE` 쿼리가
전체 DB 시간의 8.83%를 차지하며 max_time 2,301ms(2.3초)를 기록했다.

12회 호출에 mean 193ms는 PK 조회치고 비정상적으로 높으며, 이는 **락 대기(lock contention)** 때문이다.

**중요: 이 수치는 유저 1명(개발자 본인)의 테스트 결과다.** 다중 사용자 환경에서는 훨씬 악화된다.

---

## 기존 설계 의도: 왜 비관적 락을 걸었는가

### 1. Order 락 — 상태 전이 경쟁 방지

Order의 `CREATED` 상태에서 3개의 플로우가 동시에 경쟁한다:

```
                    ┌─ markAsPaid()          → PAID      (결제 완료 이벤트, @Async)
CREATED 상태 ───────┼─ expireOrder()         → EXPIRED   (스케줄러, 1분 주기)
                    └─ cancelOrderBySystem() → CANCELED  (결제 실패 이벤트, @Async)
```

**락 없이 동시 실행되면:**

```
Thread A: markAsPaid()                Thread B: expireOrder()
──────────────────────                ──────────────────────
SELECT order (status=CREATED)
                                      SELECT order (status=CREATED)
                                      → CREATED이니까 만료 진행
                                      inventory.release()  ← 예약분 환불
                                      order.expire()
                                      UPDATE status=EXPIRED

→ CREATED이니까 결제 진행
inventory.confirm()  ← 이미 release된 예약분을 confirm 시도!
order.markAsPaid()
UPDATE status=PAID   ← Lost Update: EXPIRED를 PAID로 덮어씀
```

**결과:** release된 재고를 confirm → `quantityReserved` 부정합, Order 상태 Lost Update.

**락의 의도:** `SELECT ... FOR UPDATE`로 한 번에 하나의 플로우만 Order 상태를 읽고 변경할 수 있게 직렬화.

### 2. Inventory 락 — 수량 정합성 보호

Inventory에는 두 개의 카운터가 있다:

```java
quantityAvailable  // 판매 가능 재고
quantityReserved   // 예약된 재고
```

연산들:

```java
reserve(qty):  available -= qty,  reserved += qty   // 주문 생성
release(qty):  reserved  -= qty,  available += qty  // 주문 취소/만료
confirm(qty):  reserved  -= qty                     // 결제 완료 (확정)
```

Inventory는 **경쟁 자원**(DOMAIN_RULES.md 3절)이다. 같은 상품에 대해 동시에
reserve/release/confirm이 실행되면 read-modify-write에서 Lost Update가 발생한다.

```
락 없이:
Thread A: available=10, reserved=0
Thread B: available=10, reserved=0   ← 같은 값 읽음

Thread A: available=10-3=7, reserved=0+3=3 → UPDATE
Thread B: available=10-2=8, reserved=0+2=2 → UPDATE (A 결과 덮어씀!)

결과: available=8, reserved=2 (정확한 값: available=5, reserved=5)
```

**락의 의도:** `SELECT inventory FOR UPDATE`로 읽기-수정-쓰기를 원자적으로 보장.

### 3. 같은 트랜잭션에 묶은 이유 — Order ↔ Inventory 원자성

`markAsPaid()`가 하나의 트랜잭션에서 Order 락 + Inventory 락을 모두 잡는 이유:

```java
@Transactional
public Order markAsPaid(Long orderId) {
    Order order = orderRepository.findByIdWithLock(orderId);       // Order 락
    inventoryService.confirm(productId, quantity);                  // Inventory 락
    order.markAsPaid();                                            // 상태 변경
}
// 커밋: 둘 다 반영되거나, 둘 다 롤백
```

만약 Inventory confirm은 성공했는데 Order 상태 변경에서 실패하면?
- 같은 트랜잭션이므로 **전부 롤백** → 정합성 유지
- 분리하면 Inventory는 커밋됐으므로 **보상 트랜잭션**이 필요해진다

### 요약: 각 락이 방어하는 위험

| 락 | 보호 대상 | 방어하는 위험 |
|-----|----------|-------------|
| Order `FOR UPDATE` | 상태 전이 직렬화 | 결제+만료 동시 진행 → 재고 이중 처리 (confirm + release 동시 발생) |
| Inventory `FOR UPDATE` | `quantityAvailable` / `quantityReserved` | 동시 reserve/release/confirm → Lost Update |
| 같은 트랜잭션에 묶음 | Order ↔ Inventory 원자성 | Inventory 변경됐는데 Order는 안 변경되는 불일치 |

---

## 현재 구조 분석

### 문제 쿼리의 출처

`OrderRepository.findByIdWithLock()` — `PESSIMISTIC_WRITE` 락

6개 메서드에서 호출:

| 메서드 | 호출 경로 | Inventory 처리 | 락 보유 시간 |
|--------|-----------|:-------------:|:-----------:|
| `markAsPaid()` | PaymentEventListener(@Async) / MarkAsPaidHandler(retry) | confirm × N | **길다** |
| `expireOrder()` | OrderExpirationScheduler (1분 배치) | release × N | **길다** |
| `cancelOrderBySystem()` | PaymentEventListener(@Async) / CancelOrderHandler(retry) | release × N | **길다** |
| `completeOrder()` | CompleteOrderHandler(retry) | 없음 | 짧다 |
| `requestRefund()` | RefundService | 없음 | 짧다 |
| `markAsRefunded()` | RefundEventListener | 없음 | 짧다 |

### 핵심 원인: 이중 비관적 락 중첩

`markAsPaid()` / `expireOrder()` / `cancelOrderBySystem()`는 하나의 트랜잭션 안에서:
1. `orders` 테이블에 `FOR UPDATE` 락
2. 그 안에서 `inventory` 테이블에도 아이템 수만큼 `FOR UPDATE` 락

2개 테이블의 비관적 락이 중첩되고, 특히 `expireOrder()`는 스케줄러에서 1분마다 배치로 호출되므로
동시에 여러 주문의 Inventory 락이 경합할 수 있다.

---

## 1인 테스트에서 2.3초가 발생하는 원인 — 로우 레벨 분석

### 현재 리소스 설정

```
DB 커넥션 풀 (HikariCP):  maximum-pool-size = 5  (application-prod.properties)
Async 스레드 풀:          core = 5, max = 10, queue = 25  (AsyncConfig.java)
스케줄러 스레드:           Spring 기본 = 1  (@Scheduled 전용)
PostgreSQL:              Supabase (PgBouncer 경유)
```

### 경합 시나리오: 결제 완료 vs 스케줄러

유저가 결제를 완료하면:

```
[Tomcat 스레드] confirmPayment()
    ├─ prepareConfirm()               ← HikariCP 커넥션 #1 획득, Payment 락, 커밋, 커넥션 반환
    ├─ paymentGateway.confirm()       ← 토스 외부 API (커넥션 없음, HTTP만)
    └─ finalizeConfirmSuccess()       ← HikariCP 커넥션 #2 획득, Payment 락, 커밋, 커넥션 반환
        └─ PaymentCompletedEvent 발행
        └─ HTTP 응답 반환 (Tomcat 스레드 해제)
            │
            └─ [event- 스레드] @Async handlePaymentCompleted()
                └─ orderService.markAsPaid()
                    ├─ HikariCP 커넥션 #3 획득 (트랜잭션 시작)
                    ├─ Order FOR UPDATE
                    ├─ inventoryService.confirm() × N  ← Inventory FOR UPDATE
                    └─ 커밋, 커넥션 반환
```

동시에 항상 돌고 있는 것:

```
[scheduling-1 스레드] 매 60초
    └─ findExpiredOrderIds()          ← HikariCP 커넥션 #4 획득, readOnly, 커넥션 반환
    └─ for each orderId:
        └─ orderService.expireOrder(orderId)
            ├─ HikariCP 커넥션 #4 획득 (트랜잭션 시작)
            ├─ Order FOR UPDATE
            ├─ inventoryService.release() × N  ← Inventory FOR UPDATE
            └─ 커밋, 커넥션 반환
```

### 경합 시 리소스 상태 상세

```
시간축 ──────────────────────────────────────────────────────────────────►

[scheduling-1 스레드]
    expireOrder(orderId=5)
    ├─ HikariCP conn#4 획득 (잔여: 4→3)
    ├─ BEGIN
    ├─ SELECT orders FOR UPDATE (Order#5 row lock 획득)
    ├─ SELECT inventory FOR UPDATE (Inventory#1 row lock 획득)
    │   └─ UPDATE inventory  ← 처리 중...
    │
    │   [event-1 스레드]  ← @Async 결제 완료 이벤트
    │       markAsPaid(orderId=7)
    │       ├─ HikariCP conn#3 획득 (잔여: 3→2)
    │       ├─ BEGIN
    │       ├─ SELECT orders FOR UPDATE (Order#7 row lock 획득, 다른 row라 OK)
    │       ├─ SELECT inventory FOR UPDATE WHERE productId=1
    │       │   ╔════════════════════════════════════════════════════════════╗
    │       │   ║ PostgreSQL: 이 쿼리가 Inventory#1 row lock 대기에 진입     ║
    │       │   ║                                                            ║
    │       │   ║ event-1 스레드 상태: BLOCKED (JDBC 소켓 read 대기)          ║
    │       │   ║ HikariCP conn#3 상태: active (반환 불가, 트랜잭션 진행중)    ║
    │       │   ║ Order#7 row lock 상태: 유지됨 (트랜잭션 안 끝남)            ║
    │       │   ║                                                            ║
    │       │   ║ → 이 시간 동안 conn#3 + event-1 스레드 모두 점유           ║
    │       │   ╚════════════════════════════════════════════════════════════╝
    │
    ├─ SELECT inventory FOR UPDATE (Inventory#2 처리)
    ├─ SELECT inventory FOR UPDATE (Inventory#3 처리)
    ├─ UPDATE orders SET status='EXPIRED'
    ├─ COMMIT                    ← Inventory#1 row lock 해제
    └─ HikariCP conn#4 반환 (잔여: 2→3)
                    │
                    │   (Inventory#1 row lock 획득!)
                    ├─ UPDATE inventory
                    ├─ SELECT inventory FOR UPDATE (Inventory#2, 이미 해제됨)
                    ├─ UPDATE orders SET status='PAID'
                    ├─ COMMIT
                    └─ HikariCP conn#3 반환 (잔여: 3→4)
```

### 경합 시 리소스 점유 요약

```
                        경합 발생 시점 (1,500ms 동안)
                        ┌──────────────────────────────────────┐
리소스                   │ scheduling-1       │ event-1          │
─────────────────────────┼────────────────────┼──────────────────┤
HikariCP 커넥션 (5개중)  │ conn#4 점유       │ conn#3 점유      │ → 잔여 3개
스레드                   │ scheduling-1 활성  │ event-1 BLOCKED  │
PostgreSQL 백엔드        │ 쿼리 실행 중      │ lock 대기 중     │ → 백엔드 2개 점유
Row locks               │ Order#5 + Inv#1,2,3│ Order#7 (Inv#1대기)│
                        └──────────────────────────────────────┘
```

**1,500ms 동안 5개 중 2개의 DB 커넥션이 묶여 있다. 잔여 3개.**

### 왜 1명인데도 경합이 발생하나

| 경합 지점 | 원인 | 빈도 |
|-----------|------|------|
| Order 락 vs Order 락 | 같은 주문에 markAsPaid + expire 동시 | 낮음 |
| **Inventory 락 vs Inventory 락** | **다른 주문이라도 같은 상품이면 경합** | **높음** |

**핵심:** Order 간 경합이 아니라 **Inventory 경합**이다.
테스트 중 상품 종류가 적으면(예: 1~3개 상품으로 반복 주문),
**거의 모든 주문이 같은 Inventory row를 놓고 경합**하게 된다.

### 2.3초 분해 추정

```
markAsPaid() 진입                                              ~1ms
+ HikariCP 커넥션 획득                                         ~1ms (풀 여유 있으면)
+ BEGIN                                                        ~0.5ms
+ Order FOR UPDATE (PK lookup)                                 ~1ms
+ inventory.confirm(product=1)
  ├─ SELECT inventory FOR UPDATE WHERE productId=1
  │   └─ row lock 대기 (scheduling-1이 잡고 있음)             ~1,500ms
  └─ UPDATE inventory                                          ~1ms
+ inventory.confirm(product=2)
  ├─ SELECT inventory FOR UPDATE                               ~1ms
  └─ UPDATE inventory                                          ~1ms
+ order.markAsPaid()                                           ~0ms (메모리)
+ UPDATE orders (dirty checking flush)                         ~1ms
+ COMMIT                                                       ~2ms
+ HikariCP 커넥션 반환                                         ~0ms
──────────────────────────────────────────────────────────────
총 트랜잭션 시간                                                ~1,510ms
총 커넥션 점유 시간                                             ~1,510ms (= 트랜잭션 시간)
총 event-1 스레드 점유 시간                                     ~1,510ms
```

- **mean 193ms** = 스케줄러와 안 겹쳤을 때 (lock 대기 없음)
- **max 2,301ms** = 스케줄러와 겹쳤을 때 (lock 대기 ~1,500ms + 나머지 처리)

1분마다 스케줄러가 도니까 12번 중 1~2번은 겹칠 수 있다.

### 다중 유저 시 리소스 고갈 시나리오

```
유저 3명이 동시에 같은 상품(product=1) 포함 주문 결제 + 스케줄러 실행

HikariCP 커넥션 풀 (maximum-pool-size=5):
  conn#1: scheduling-1 → expireOrder (Inventory#1 row lock 보유)
  conn#2: event-1 → markAsPaid(주문A) → Inventory#1 대기 (BLOCKED)
  conn#3: event-2 → markAsPaid(주문B) → Inventory#1 대기 (BLOCKED)
  conn#4: event-3 → markAsPaid(주문C) → Inventory#1 대기 (BLOCKED)
  conn#5: 잔여 1개
  ────────────────────────────────────────────────────────────────
  → 다음 요청이 커넥션을 못 잡으면 HikariPool-1 - Connection is not available 발생
  → 기본 connectionTimeout = 30초 후 예외

Async 스레드 풀 (core=5, max=10, queue=25):
  event-1: BLOCKED (JDBC 대기)
  event-2: BLOCKED (JDBC 대기)
  event-3: BLOCKED (JDBC 대기)
  → 3/5 core 스레드가 블로킹됨
  → 다른 이벤트(DeliveryCompleted, RefundApproved 등) 처리 지연

PostgreSQL 백엔드:
  4개 백엔드가 동일 Inventory row에 대해 lock queue 형성
  → conn#1 커밋 후 conn#2 획득 → conn#2 커밋 후 conn#3 획득 → 순차 처리
  → 총 처리 시간: 스케줄러 트랜잭션 + (유저 수 × 개별 트랜잭션) → 직렬화
```

**인기 상품 1개면 동시 결제가 사실상 직렬 처리된다.**
커넥션 풀 5개 중 4개가 lock 대기로 묶이면 나머지 API(상품 조회, 로그인 등)도 영향받는다.

---

## 문제 1: markAsPaid / expireOrder / cancelOrderBySystem — Order 락 안에서 Inventory 락 N번

### 방안 A: Inventory 처리 먼저, Order 락 범위 축소

Inventory confirm/release를 먼저 수행하고, Order 상태 변경만 락 범위에 포함.

```java
// 비트랜잭션 오케스트레이터
public void markAsPaid(Long orderId) {
    Order order = orderRepository.findById(orderId).orElseThrow(...);
    if (order.getStatus() != OrderStatus.CREATED) return;

    // 1) Inventory confirm (각각 독립 트랜잭션)
    order.getOrderItems().stream()
        .sorted(Comparator.comparing(OrderItem::getProductId))
        .forEach(item -> inventoryService.confirm(...));

    // 2) Order 상태 변경만 락 (최소 범위)
    updateOrderStatusWithLock(orderId, OrderStatus.PAID);
}
```

| 항목 | 평가 |
|------|------|
| 락 보유 시간 | Order 락: ~1ms (상태 변경만), Inventory 락: 개별 ~1ms |
| 장점 | 락 경합 대폭 감소. 가장 큰 성능 개선 |
| 단점 | **원자성 깨짐** — Inventory confirm 후 Order 락에서 상태 이미 변경 시 불일치 가능 |
| 복구 비용 | 보상 트랜잭션 필요. 또는 재시도로 최종 일관성 보장 |
| 복잡도 | 중~높음 |
| 적합성 | 이미 @Async + RetryTask 최종 일관성 모델 사용 중이므로 방향은 맞지만, 보상 로직 없으면 위험 |

### 방안 B: Optimistic Lock으로 전환

> **참고:** 이 방안은 Order row의 동시 수정을 방지하지만, 실제 병목은 Inventory row 경합이다.
> Order에 낙관락을 걸어도 Inventory `FOR UPDATE`는 그대로 필요하므로 커넥션 점유 시간에 큰 변화가 없다.
> Order 상태 전이 충돌 빈도가 높을 때만 의미 있고, 현재 문제의 핵심 해결책은 아니다.

```java
@Version
private Long version;

@Transactional
public Order markAsPaid(Long orderId) {
    Order order = orderRepository.findById(orderId).orElseThrow(...);
    // inventory confirm ... ← 여전히 Inventory FOR UPDATE 필요
    order.markAsPaid();
    return orderRepository.save(order); // UPDATE WHERE id=? AND version=?
}
```

| 항목 | 평가 |
|------|------|
| 락 보유 시간 | Order 락만 없어짐. **Inventory 락은 그대로** — 커넥션 점유 시간 변화 미미 |
| 장점 | Order 상태 전이 충돌 감지 |
| 단점 | 충돌 시 Inventory 연산 포함 전체 재시도. 멱등성 필요 |
| 적합성 | 이 문제의 핵심(Inventory 경합)을 해결하지 못함 |

### 방안 C: 현재 구조 유지 + Inventory 호출을 단일 배치로 최적화

```java
@Transactional
public Order markAsPaid(Long orderId) {
    Order order = orderRepository.findByIdWithLock(orderId).orElseThrow(...);
    if (order.getStatus() != OrderStatus.CREATED) return order;

    // N번 개별 락 → 1번 배치 락
    List<Long> productIds = order.getOrderItems().stream()
        .map(OrderItem::getProductId).sorted().toList();
    inventoryRepository.findByProductIdsWithLock(productIds);  // IN 절 + FOR UPDATE
    // ... confirm 처리
    order.markAsPaid();
    return orderRepository.save(order);
}
```

| 항목 | 평가 |
|------|------|
| 락 보유 시간 | Inventory 락 획득이 1회 쿼리로 줄어서 전체 시간 감소 |
| 장점 | **원자성 완전 보존**. 기존 구조 변경 최소. 데드락 방지 (정렬된 순서로 락) |
| 단점 | Order 락 보유 중 Inventory 락을 잡는 구조 자체는 그대로 — 근본 해결은 아님 |
| 복잡도 | 낮음 |
| 적합성 | 가장 안전한 점진적 개선. 아이템 수가 많을 때 효과 큼 |

---

## 문제 2: expireOrder() 스케줄러 배치 — 락 경합

1분마다 만료 대상 ID 조회 → for 루프로 개별 expireOrder() 호출 (각각 Order + Inventory 락)

### 방안 D: SKIP LOCKED 적용

```java
@Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt < :createdAt")
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
List<Order> findExpiredForUpdate(...);
```

| 항목 | 평가 |
|------|------|
| 효과 | 결제 진행 중인 주문은 자동 스킵 → 스케줄러가 결제 플로우를 블로킹하지 않음 |
| 장점 | 구현 간단. 건너뛴 주문은 다음 주기(1분 뒤)에 재시도 |
| 단점 | 현재 ID만 조회하는 설계(L1 캐시 오염 방지)와 충돌 — 구조 변경 필요 |
| 적합성 | 스케줄러 ↔ 결제 플로우 간 경합에 매우 효과적 |

### 방안 E: NOWAIT + 예외 처리

```java
// expireOrder()에서 사용
@Query("SELECT o FROM Order o WHERE o.id = :id")
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
Optional<Order> findByIdWithLockNowait(@Param("id") Long id);
```

스케줄러에서:
```java
try {
    orderService.expireOrderNowait(orderId);
} catch (PessimisticLockingFailureException e) {
    log.info("Order {} locked, will retry next cycle", orderId);
}
```

| 항목 | 평가 |
|------|------|
| 효과 | 락 못 잡으면 즉시 실패 → 대기 시간 0 |
| 장점 | 현재 ID 조회 + 개별 처리 구조 유지 가능. L1 캐시 설계 깨지지 않음 |
| 단점 | 매번 예외 발생/처리 비용. 로그 노이즈 |
| 적합성 | D보다 기존 구조 유지에 유리. 만료 지연 허용 가능하면 좋은 선택 |

### 방안 F: 스케줄러 주기 조정 + 배치 크기 제한

```java
@Scheduled(fixedRate = 60000)
public void expireOrders() {
    List<Long> ids = findExpiredOrderIds();
    int batchSize = Math.min(ids.size(), 10);
    for (Long orderId : ids.subList(0, batchSize)) { ... }
}
```

| 항목 | 평가 |
|------|------|
| 효과 | 동시 락 경합 줄임 |
| 장점 | 코드 변경 최소 |
| 단점 | 근본 해결 아님. 만료 대상 많으면 처리 지연 |
| 적합성 | 임시 완화용. 다른 방안과 조합 가능 |

---

## 방안 A~F 요약 비교 (원자성 유지 전제)

| 방안 | 대상 | 락 시간 감소 | 원자성 | 복잡도 | 기존 구조 변경 |
|------|------|:----------:|:------:|:------:|:------------:|
| **A** 락 범위 분리 | markAsPaid/expire/cancel | ★★★ | 깨짐 (보상 필요) | 높음 | 큼 |
| **B** Optimistic Lock | 전체 | ★★★ | 유지 (재시도 시) | 중간 | 중간 |
| **C** 배치 락 최적화 | markAsPaid/expire/cancel | ★★ | **완전 유지** | **낮음** | **최소** |
| **D** SKIP LOCKED | 스케줄러 | ★★★ | 유지 | 낮음~중간 | 중간 |
| **E** NOWAIT | 스케줄러 | ★★★ | 유지 | **낮음** | **최소** |
| **F** 배치 크기 제한 | 스케줄러 | ★ | 유지 | **낮음** | **최소** |

---

## 원자성을 유지해야 하는가? — 멱등성 확보 방향과의 비교

### 전제: 현재 시스템은 이미 원자성이 아니다

`markAsPaid()`가 호출되는 전체 경로를 보면:

```
[사용자 요청] confirmPayment()
    ├─ prepareConfirm()             ← 트랜잭션 1: Payment REQUESTED → PROCESSING
    ├─ paymentGateway.confirm()     ← 외부 API (트랜잭션 밖)
    └─ finalizeConfirmSuccess()     ← 트랜잭션 2 (REQUIRES_NEW): Payment → COMPLETED
        └─ PaymentCompletedEvent
            └─ @Async
                └─ markAsPaid()     ← 트랜잭션 3: Order + Inventory
```

Payment → Order → Inventory가 **이미 3개의 별도 트랜잭션**이다.
`finalizeConfirmSuccess()`가 성공하고 `markAsPaid()`가 실패하면
Payment는 COMPLETED인데 Order는 CREATED — **불일치 상태가 이미 가능**하며,
그래서 RetryTask가 존재한다.

### 그러면 markAsPaid() 안의 원자성이 지키는 것은?

**"Inventory confirm은 됐는데 Order는 PAID가 안 되는 상황" 방지.**

이것이 실제로 위험한 이유는 `confirm()/release()`가 **멱등하지 않기 때문**이다:

```java
// Inventory.confirm() — 현재 코드
public void confirm(Long quantity) {
    if (quantityReserved < quantity) {
        throw new BusinessException("Cannot confirm more than reserved");  // ← 멱등 아님!
    }
    this.quantityReserved -= quantity;
}
```

원자성을 깨면 발생하는 시나리오:

```
markAsPaid() — 원자성 없는 경우
├─ inventory.confirm(product=1)  ← 독립 트랜잭션, 커밋됨 ✓
├─ inventory.confirm(product=2)  ← 실패 ✗
└─ updateOrderStatus(PAID)       ← 실행 안 됨

→ RetryTask가 markAsPaid() 재시도
→ inventory.confirm(product=1) 다시 호출
→ "Cannot confirm more than reserved" 예외!  ← 이미 confirm된 걸 또 하려고 함
```

**원자성을 유지해야 하는 실질적 이유: Inventory 연산이 멱등하지 않다.**
역으로, **멱등성을 확보하면 원자성 없이도 안전하다.**

---

## 멱등성 확보 방식 3가지 변형

### 변형 G1: 별도 처리 로그 테이블

```java
// 새 테이블: inventory_operation_log
// (order_id, product_id, operation_type) UNIQUE

@Transactional
public void confirmForOrder(Long orderId, Long productId, Long quantity) {
    if (operationLogRepository.exists(orderId, productId, "CONFIRM")) {
        return;  // 이미 처리됨
    }
    Inventory inventory = inventoryRepository.findByProductIdWithLock(productId);
    inventory.confirm(quantity);
    inventoryRepository.save(inventory);
    operationLogRepository.save(new OperationLog(orderId, productId, "CONFIRM"));
    // 같은 트랜잭션: confirm + 로그 기록이 원자적
}
```

| 항목 | 평가 |
|------|------|
| 장점 | 도메인 엔티티 변경 없음. 관심사 분리 깔끔 |
| 단점 | 신규 테이블 + Repository 추가. 로그 데이터 증가 |
| 도메인 규칙 영향 | DOMAIN_RULES.md 위반 없음 |

### 변형 G2: OrderItem에 처리 상태 플래그

```java
// OrderItem에 필드 추가
@Column(nullable = false)
private boolean inventoryConfirmed = false;

@Transactional
public void confirmForOrder(OrderItem item) {
    if (item.isInventoryConfirmed()) return;  // 멱등
    Inventory inventory = inventoryRepository.findByProductIdWithLock(item.getProductId());
    inventory.confirm(item.getQuantity());
    item.markInventoryConfirmed();
}
```

| 항목 | 평가 |
|------|------|
| 장점 | 별도 테이블 불필요. 구현 간단 |
| 단점 | **DOMAIN_RULES.md 1.1절 위반** — "OrderItem은 생성 이후 UPDATE 금지 (스냅샷 불변성)" |
| 도메인 규칙 영향 | **위반** |

### 변형 G3: Inventory에 주문별 예약 추적

```java
// 새 테이블: inventory_reservation
// (order_id, product_id, quantity, status: RESERVED/CONFIRMED/RELEASED)

@Transactional
public void confirmForOrder(Long orderId, Long productId, Long quantity) {
    InventoryReservation reservation = reservationRepository
        .findByOrderIdAndProductId(orderId, productId);
    if (reservation.getStatus() == CONFIRMED) return;  // 멱등

    Inventory inventory = inventoryRepository.findByProductIdWithLock(productId);
    inventory.confirm(quantity);
    reservation.markConfirmed();
}
```

| 항목 | 평가 |
|------|------|
| 장점 | 예약 상태 전체 추적 가능. 감사(audit) 용이. MSA 분리 시 자연스러운 구조 |
| 단점 | 테이블 + 엔티티 + 기존 reserve/release 플로우 전체 변경 필요 |
| 도메인 규칙 영향 | Inventory 도메인 확장 필요 (DOMAIN_RULES.md 3절 개정) |

---

## 기존 방안 A to F vs 멱등성 방안 G1 to G3 전체 비교

### 비교 축

| 축 | 설명 |
|-----|------|
| **락 보유 시간** | Order/Inventory 락을 얼마나 오래 잡는가 |
| **원자성** | Order + Inventory가 all-or-nothing인가 |
| **재시도 안전성** | 부분 실패 후 RetryTask 재시도가 안전한가 |
| **Inventory 경합** | 인기 상품에 동시 주문이 몰릴 때의 병목 |
| **도메인 규칙 영향** | DOMAIN_RULES.md 변경이 필요한가 |
| **구현 비용** | 코드/테이블/테스트 변경량 |

### 기존 방안 (원자성 유지 계열)

| | A 락범위분리 | B Optimistic | C 배치락 | D SKIP LOCKED | E NOWAIT | F 배치제한 |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| **락 보유 시간** | ★★★ | ★★★ | ★★ | ★★★ | ★★★ | ★ |
| **원자성** | 깨짐 | 유지 | **유지** | 유지 | 유지 | 유지 |
| **재시도 안전성** | **위험** | 재시도 필요 | **안전** | **안전** | **안전** | **안전** |
| **Inventory 경합** | 해소 | 해소 | 줄어듦 | Order만 해소 | Order만 해소 | 약간 줄어듦 |
| **도메인 규칙** | 변경 없음 | Order에 @Version | 변경 없음 | 변경 없음 | 변경 없음 | 변경 없음 |
| **구현 비용** | 중간 | 낮음 | **낮음** | 낮음~중간 | **낮음** | **낮음** |

**A가 위험한 이유:** 멱등성 없이 원자성을 깨면 부분 confirm 후 재시도가 예외를 던진다.

**D/E의 한계:** Order 락 경합은 해소하지만 **같은 상품 Inventory 경합은 여전히 존재** —
이건 Order 레벨 문제가 아니라 Inventory 레벨 문제이기 때문.

### 멱등성 확보 방안

| | G1 로그테이블 | G2 OrderItem 플래그 | G3 예약 추적 |
|---|:---:|:---:|:---:|
| **락 보유 시간** | ★★★ | ★★★ | ★★★ |
| **원자성** | 불필요 (멱등) | 불필요 (멱등) | 불필요 (멱등) |
| **재시도 안전성** | **안전** | **안전** | **안전** |
| **Inventory 경합** | **해소** | **해소** | **해소** |
| **도메인 규칙** | 신규 테이블 | **위반** (스냅샷 불변성) | 신규 테이블 + 도메인 확장 |
| **구현 비용** | 중간 | 낮음 | 높음 |

---

## 멱등성이 기존 방안의 약점을 어떻게 바꾸는가

### 방안 A + 멱등성(G1) = A의 약점이 사라짐

| | A 단독 | A + G1 |
|---|---|---|
| 원자성 깨짐 | **위험** — 부분 confirm 후 재시도 불가 | **안전** — 처리된 건 스킵 |
| 복구 비용 | 보상 트랜잭션 필요 | 불필요 — 재시도만 하면 됨 |
| 결론 | 보상 로직 없으면 쓸 수 없음 | **가장 큰 성능 개선 + 안전** |

### 방안 B + 멱등성(G1) = B의 약점이 줄어듦

| | B 단독 | B + G1 |
|---|---|---|
| 충돌 시 재시도 | Inventory confirm/release 전부 다시 | 처리된 건 스킵, 남은 것만 처리 |
| 재시도 비용 | 높음 (전체 반복) | **낮음** (이미 처리된 건 O(1) 스킵) |
| 결론 | 충돌 빈도 높으면 비효율 | 충돌 빈도 높아도 괜찮음 |

### 방안 C/D/E + 멱등성(G1) = 과도한 조합

| | C/D/E 단독 | C/D/E + G1 |
|---|---|---|
| 이미 원자성 유지 | 안전 | 안전 (중복 보호) |
| 추가 이점 | — | 향후 원자성 분리 시 안전망 |
| 결론 | **충분히 효과적** | 필요 이상의 복잡도 |

---

## 최종 비교: 현실적 선택지 4가지

| 선택지 | 구성 | 락 시간 | 안전성 | 비용 | 적합한 시점 |
|--------|------|:-------:|:------:|:----:|:-----------:|
| **1. 보수적** | C + E | ★★ | ★★★ | **낮음** | **지금 당장** |
| **2. 중간** | C + D | ★★☆ | ★★★ | 낮음~중간 | 스케줄러 경합이 주 병목일 때 |
| **3. 적극적** | A + G1 | ★★★ | ★★★ | **중간** | 인기 상품 Inventory 경합이 심할 때 |
| **4. 풀 리디자인** | A + G3 | ★★★ | ★★★ | 높음 | MSA 분리 / 대규모 확장 시 |

### 점진적 적용 경로

```
1단계 (C + E): 원자성 유지, 배치 락 + NOWAIT
  → Inventory 경합이 여전히 문제면

2단계 (+ G1): inventory_operation_log 테이블 추가
  → 멱등성 확보됨, 아직 원자성 유지 상태 (안전망)

3단계 (A 적용): 원자성 분리 — Inventory를 트랜잭션 밖으로
  → G1 덕분에 안전하게 분리 가능
```

**G1 로그 테이블은 2→3단계의 다리 역할**을 한다.
멱등성을 먼저 확보해두면 원자성 분리가 안전해지는 구조이다.

---

## 구현 완료: G1 + A (멱등성 로그 + 트랜잭션 분리)

### 변경 전후

```
BEFORE (하나의 긴 트랜잭션):
  BEGIN
    Order FOR UPDATE            ← 락 시작
    Inventory FOR UPDATE × N   ← 경합 대기 (~1,500ms)
    Order 상태 변경
  COMMIT                        ← 락 해제

AFTER (1 + N개의 짧은 트랜잭션):
  TX1: Order FOR UPDATE + 상태 변경 + orderItems 로딩  (~2ms)
  TX2: Inventory[product=1] FOR UPDATE + confirm/release + 로그 기록  (~2ms)
  TX3: Inventory[product=2] FOR UPDATE + confirm/release + 로그 기록  (~2ms)
```

### 수정 파일

| # | 파일 | 동작 |
|---|------|------|
| 1 | `inventory/domain/InventoryOperationLog.java` | **신규** — `UNIQUE(orderId, productId, operationType)` |
| 2 | `inventory/repository/InventoryOperationLogRepository.java` | **신규** — `existsBy...` 멱등성 체크 |
| 3 | `inventory/service/InventoryService.java` | **수정** — `confirmForOrder`, `releaseForOrder` 추가 (`REQUIRES_NEW`) |
| 4 | `order/service/OrderStatusTransitioner.java` | **신규** — 짧은 Order 전용 트랜잭션 |
| 5 | `order/repository/OrderRepository.java` | **수정** — `findByIdWithItemsAndLock` 추가 |
| 6 | `order/service/OrderService.java` | **수정** — 3개 메서드 비트랜잭션 오케스트레이터로 변경 |

---

## G1+A 구현 시 발생한 테스트 문제

### 문제 1: `REQUIRES_NEW` + 테스트 `@Transactional` 격리 충돌

#### 증상

```
com.minishop.project.minishop.common.exception.BusinessException: Inventory not found
    at InventoryService.confirmForOrder(InventoryService.java:90)
```

#### 근본 원인: `REQUIRES_NEW`가 테스트의 미커밋 데이터를 볼 수 없음

기존 테스트는 `@Transactional`로 모든 코드가 하나의 트랜잭션(TX-A) 안에서 실행되었다.
데이터 생성 → 비즈니스 로직 → 검증이 모두 TX-A 안이므로 데이터가 전부 보이고,
테스트 끝나면 롤백으로 정리되었다.

```
BEFORE (기존 — 모든 것이 TX-A):
  @Transactional (테스트)
    └─ TX-A: Product/Inventory/Order 생성
    └─ TX-A: orderService.markAsPaid() → inventoryService.confirm() (TX-A 합류)
    └─ TX-A 롤백 → 깔끔하게 정리

AFTER (G1+A — REQUIRES_NEW가 별도 커넥션):
  @Transactional (테스트)
    └─ TX-A: Product/Inventory/Order 생성 (미커밋)
    └─ TX-A: orderStatusTransitioner.markAsPaidStatus() (REQUIRED → TX-A 합류, Order 보임 ✓)
    └─ TX-B: inventoryService.confirmForOrder() (REQUIRES_NEW → 새 커넥션)
         └─ TX-A의 미커밋 Inventory 데이터 안 보임 ✗
         └─ "Inventory not found" 💥
```

`REQUIRES_NEW`는 완전히 독립된 DB 커넥션을 열기 때문에,
TX-A에서 생성했지만 아직 커밋하지 않은 데이터를 볼 수 없다.

#### 해결: `@Transactional` → `@DirtiesContext(BEFORE_EACH_TEST_METHOD)`

```java
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class OrderServiceTest {
    // ...
}
```

- `@Transactional` 제거 → 데이터 생성이 즉시 커밋됨 → `REQUIRES_NEW` 트랜잭션에서도 보임
- `@DirtiesContext`가 매 테스트 전 컨텍스트 + DB 재생성으로 격리 보장

**적용 대상**: `OrderServiceTest`, `AdminOrderControllerTest`

### 문제 2: `LazyInitializationException` — 세션 없이 `orderItems` 접근

#### 증상

```
org.hibernate.LazyInitializationException:
  Cannot lazily initialize collection of role '...Order.orderItems' (no session)
```

#### 근본 원인

`@Transactional` 제거 후 테스트 메서드에 Hibernate 세션이 열려 있지 않다.
`orderService.getOrder()`가 반환한 Order 객체의 `orderItems`는 LAZY 로딩이므로
접근 시점에 세션이 필요한데, 이미 닫혀 있다.

#### 해결

```java
// BEFORE — 세션 밖에서 lazy loading 시도
Order retrievedOrder = orderService.getOrder(order.getId(), testUserId);
retrievedOrder.getOrderItems().get(0).getUnitPrice();  // 💥 LazyInitializationException

// AFTER — JOIN FETCH로 즉시 로딩
Order retrievedOrder = orderRepository.findByIdWithItems(order.getId()).orElseThrow();
retrievedOrder.getOrderItems().get(0).getUnitPrice();  // ✅
```

### 문제 3: `@DirtiesContext(AFTER_EACH_TEST_METHOD)` — "Table not found"

#### 증상

```
org.springframework.dao.InvalidDataAccessResourceUsageException:
  Table "PRODUCTS" not found (this database is empty)
```

#### 근본 원인

`AFTER_EACH_TEST_METHOD`는 테스트 **후**에 컨텍스트를 파괴한다.
클래스의 **첫 번째 테스트**는 다른 테스트 클래스가 남긴 이미 파괴된 컨텍스트를 받을 수 있다.

```
다른 테스트 클래스가 @DirtiesContext로 컨텍스트 파괴
  → H2 create-drop이 테이블 삭제
  → OrderServiceTest 첫 테스트가 기존 컨텍스트 재사용 시도
  → 테이블 없음 💥
```

#### 해결: `BEFORE_EACH_TEST_METHOD`로 변경

```java
// BEFORE — 첫 테스트에 stale 컨텍스트 가능
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)

// AFTER — 매 테스트 전 신선한 컨텍스트 보장
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
```

추가로 H2 URL에 `DB_CLOSE_DELAY=-1`을 설정하여 컨텍스트 재생성 간 H2 인스턴스가
유지되도록 했다.

```properties
# src/test/resources/application.properties
spring.datasource.url=jdbc:h2:mem:minishop;DB_CLOSE_DELAY=-1
```

### 문제 4: `markAsPaid` 반환 타입 `Order` → `void` 변경

#### 영향받은 테스트

| 테스트 | 수정 내용 |
|--------|-----------|
| `OrderServiceTest.markAsPaid_중복호출_멱등처리됨` | 반환값 사용 대신 `orderRepository.findById()`로 검증 |
| `AdminOrderControllerTest.createPaidOrder` | 반환값 사용 대신 `orderRepository.findById()`로 재조회 |

### 테스트 문제 요약

| 문제 | 원인 | 해결 |
|------|------|------|
| `REQUIRES_NEW` + `@Transactional` | 새 커넥션이 미커밋 데이터 못 봄 | `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` |
| `LazyInitializationException` | 세션 밖 lazy loading | `findByIdWithItems()` (JOIN FETCH) |
| "Table not found" | `AFTER_EACH` → stale 컨텍스트 | `BEFORE_EACH` + `DB_CLOSE_DELAY=-1` |
| 반환 타입 변경 | `markAsPaid` void화 | `orderRepository.findById()`로 재조회 |

**핵심 교훈**: `REQUIRES_NEW` 전파는 프로덕션에서 트랜잭션 분리에 필수적이지만,
테스트의 롤백 기반 격리(`@Transactional`)와 근본적으로 호환되지 않는다.
`@DirtiesContext`로 전환하면 테스트 속도가 느려지지만(컨텍스트 재생성),
실제 커밋 환경에서의 동작을 정확히 검증할 수 있다.

---

## 운영 DB 전/후 (소표본) 검증 업데이트 (2026-03-04)

### 데이터 소스
1. Before: `docs/Supabase Query Performance Statements (pbntbykdovzrrauuzwwd).csv`
2. After: `docs/after_slowquery.md` ( `pg_stat_statements_reset()` 직후 단기 수집 )

### 코드 경로 기준 매핑 (markAsPaid)
- `PaymentEventListener.handlePaymentCompleted()` -> `orderService.markAsPaid()`
- `OrderStatusTransitioner.markAsPaidStatus()` -> `OrderRepository.findByIdWithItemsAndLock()` (`PESSIMISTIC_WRITE`)
- 따라서 `orders ... FOR NO KEY UPDATE` 계열 쿼리가 핵심 검증 대상

### 전/후 비교 (핵심 쿼리)
| Query Family | calls | mean_time | max_time | total_time |
|---|---:|---:|---:|---:|
| Before: `orders ... where id=$1 ... for no key update` | 12 | 193.37ms | 2301.78ms | 2320.48ms |
| After: `orders ... left join order_items ... where id=$1 for no key update` | 1 | 1.86ms | 1.86ms | 1.86ms |

추가 관측:
1. `inventories ... where product_id=$1 for no key update`도 After에서 `mean 0.439ms`, `max 1.769ms`.
2. After 상위 시간 점유 쿼리 1위는 dashboard 쿼리(`prop_total_time 90.35%`)이므로 앱 비즈니스 쿼리와 분리 해석 필요.

### 해석
- 현재 데이터 기준으로는 `markAsPaid` 경로의 lock contention 증상이 **유의미하게 완화**된 것으로 판단.
- 단, After 표본이 작기 때문에 결론은 **잠정(working conclusion)** 으로 유지.

### 표본 한계
1. After의 calls가 작음(다수 1~10대).
2. 수집 구간이 짧음(문서 timestamp 기준 약 2분 내외).
3. reset 직후 데이터라 트래픽 대표성이 낮을 수 있음.

### 확정 기준 (재검증)
1. 실제 트래픽 30~60분 구간 재수집.
2. `markAsPaid` 계열 `FOR NO KEY UPDATE`의 `max_time`이 1000ms+로 재상승하지 않는지 확인.
3. dashboard/admin/meta 쿼리를 제외한 앱 쿼리 전용 리포트로 별도 판단.

---

## Operational DB Before/After Validation (Small Sample, 2026-03-04)

### Sources
1. Before: `docs/Supabase Query Performance Statements (pbntbykdovzrrauuzwwd).csv`
2. After: `docs/after_slowquery.md` (captured shortly after `pg_stat_statements_reset()`)

### Code-path mapping (`markAsPaid`)
- `PaymentEventListener.handlePaymentCompleted()` -> `orderService.markAsPaid()`
- `OrderStatusTransitioner.markAsPaidStatus()` -> `OrderRepository.findByIdWithItemsAndLock()` (`PESSIMISTIC_WRITE`)
- So `orders ... FOR NO KEY UPDATE` is the primary verification target.

### Before/After (key query)
| Query Family | calls | mean_time | max_time | total_time |
|---|---:|---:|---:|---:|
| Before: `orders ... where id=$1 ... for no key update` | 12 | 193.37ms | 2301.78ms | 2320.48ms |
| After: `orders ... left join order_items ... where id=$1 for no key update` | 1 | 1.86ms | 1.86ms | 1.86ms |

Additional observations:
1. `inventories ... where product_id=$1 for no key update` in After: `mean 0.439ms`, `max 1.769ms`.
2. Top time share in After is a dashboard query (`prop_total_time 90.35%`), so separate business query analysis from dashboard/admin/meta queries.

### Interpretation
- Based on current data, lock contention in `markAsPaid` path is materially improved.
- Because After sample size is small, this is a **working conclusion**, not a final confirmation.

### Sample limitations
1. After calls are small (many rows are in 1-10 range).
2. Collection window is short (about ~2 minutes by timestamps in the file).
3. Reset-adjacent data may not represent normal traffic distribution.

### Confirmation criteria
1. Re-collect after 30-60 minutes of real traffic.
2. Verify `max_time` for `markAsPaid`-related `FOR NO KEY UPDATE` queries does not jump back to 1000ms+.
3. Keep a separate report that excludes dashboard/admin/meta queries.

---

## Operational DB Re-check Update (Latest Snapshot, 2026-03-04)

This section supersedes the previous small-sample note with the refreshed `docs/after_slowquery.md` snapshot.

### Data Compared
1. Before: `docs/Supabase Query Performance Statements (pbntbykdovzrrauuzwwd).csv`
2. After (refreshed): `docs/after_slowquery.md`

### Key Path Mapping
- `PaymentEventListener.handlePaymentCompleted()` -> `orderService.markAsPaid()`
- `OrderStatusTransitioner.markAsPaidStatus()` -> `OrderRepository.findByIdWithItemsAndLock()` (`PESSIMISTIC_WRITE`)

### Before/After Highlights
1. Before major bottleneck:
   - `orders ... for no key update`: `calls=12`, `mean=193.37ms`, `max=2301.78ms`, `total=2320.48ms`
2. Refreshed snapshot:
   - `orders ... for no key update`: not observed in top statements
   - `inventories ... for no key update`: `calls=6`, `mean=0.439ms`, `max=1.769ms`, `total=2.634ms`
   - `orders ... status and created_at`: `calls=17`, `mean=0.702ms`, `max=8.685ms`, `total=11.940ms`

### App-query subset summary (refreshed snapshot)
- Subset total time (`orders/payments/inventories/order_items/products/refunds`): `~25.319ms`
- App-query rows with `max_time > 100ms`: `0`

### Notes
1. Top total-time share remains dashboard/meta query (`prop_total_time ~87.32%`), not app business path.
2. Conclusion remains a working conclusion because this is still a short collection window.

### Final confirmation criteria
1. Measure again after 30-60 minutes of real traffic.
2. Confirm no re-spike (`max_time >= 1000ms`) on app lock-related queries.
3. Evaluate with dashboard/admin/meta excluded.
