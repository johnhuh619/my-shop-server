# 결제 완료 시 배송 자동 생성 — 비동기 설계 결정

> STAR 기법 기반 설계 결정 문서

---

## 📌 Situation (상황)

결제 완료(`confirmPayment`) 후 PREPARING 상태의 Delivery를 자동 생성하는 기능을 추가해야 했다.
기존에는 관리자가 수동으로 배송을 생성하는 구조였으나, 결제 완료 → 배송 준비가 자동으로 이어지도록 변경이 필요했다.

**기존 흐름:**
```
confirmPayment → [async] markAsPaid → 끝 (관리자가 수동으로 배송 생성)
```

**목표 흐름:**
```
confirmPayment → [async] markAsPaid → [async] createDelivery(PREPARING)
```

---

## 🎯 Task (과제)

배송 생성을 결제 확인 트랜잭션 안에서 동기로 처리할지, 기존 이벤트 체인에 붙여 비동기로 처리할지 결정해야 했다.

### 동기 처리 방식 (선택하지 않음)

```
confirmPayment 트랜잭션 {
    PG 승인
    Payment COMPLETED 저장
    Order PAID 변경
    Delivery PREPARING 생성   ← 여기에 추가
}
```

### 비동기 처리 방식 (선택)

```
confirmPayment 트랜잭션 {
    PG 승인
    Payment COMPLETED 저장
    PaymentCompletedEvent 발행
}
→ [AFTER_COMMIT, @Async] markAsPaid
→ [AFTER_COMMIT, @Async] createDeliveryFromOrder
```

---

## 🔧 Action (행동)

비동기 이벤트 방식을 선택한 이유:

### 1. 배송 생성은 즉각적일 필요가 없다

배송은 물리적 프로세스다. PREPARING 상태로 만들어 놓기만 하면 되고, 사용자에게 결제 응답을 돌려주는 시점에 배송이 이미 생성되어 있을 필요가 없다. 결제 확인 API의 응답 시간에 배송 생성 지연을 포함시킬 이유가 없다.

### 2. 동기 처리 시 위험 시나리오 — 왜 위험한가

동기 방식의 핵심 문제는 **이미 돌이킬 수 없는 외부 부수효과(PG 승인)와 아직 실패할 수 있는 내부 작업(배송 생성)을 하나의 트랜잭션에 묶는 것**이다.

#### 시나리오 A: 배송 테이블 쓰기 실패 → PG 보상 필요

```
confirmPayment 트랜잭션 {
  1. paymentGateway.confirmPayment()  → ✅ PG 승인 완료 (돌이킬 수 없음)
  2. payment.markAsCompleted()        → ✅ Payment COMPLETED
  3. order.markAsPaid()               → ✅ Order PAID
  4. deliveryRepository.save()        → ❌ DB 예외 (deadlock, constraint 등)
  → 트랜잭션 롤백
}
```

**결과:**
- PG사에는 결제가 승인된 상태 — 사용자 카드에서 돈이 빠져나감
- 우리 DB에는 Payment, Order 모두 롤백 — 결제 기록 자체가 없음
- **PG 승인은 됐는데 우리 시스템에 결제 기록이 없는 유령 결제 발생**
- 이를 복구하려면 PG 취소 API를 호출해야 하는데, 트랜잭션이 롤백되면서 tossOrderId 등 식별 정보도 사라짐
- 수동으로 PG 관리자 페이지에서 건별 취소해야 하는 운영 사고로 번짐

현재 코드에서 `finalizeConfirmSuccess`는 `REQUIRES_NEW` 트랜잭션으로 Payment 상태만 COMPLETED로 바꾸고 이벤트를 발행한다. 여기에 배송 생성을 넣으면 이 트랜잭션의 실패 반경이 배송 테이블까지 확장된다.

#### 시나리오 B: DB 커넥션 풀 고갈

```
[동기 방식 — 요청당 트랜잭션 보유 시간]
PG 외부 호출 (300~2000ms) + Payment 쓰기 + Order 쓰기 + Delivery 쓰기
= 트랜잭션 하나가 DB 커넥션을 수백 ms ~ 수 초 점유
```

Spring Boot의 기본 HikariCP 커넥션 풀은 10개다. 동시에 11명이 결제하면 1명은 커넥션을 기다린다. 여기에 배송 쓰기까지 추가되면:

- 트랜잭션당 커넥션 점유 시간 증가 → 동시 처리량 감소
- 배송 테이블에 lock contention이 생기면 다른 결제 트랜잭션까지 대기
- 최악의 경우 커넥션 풀 전체가 배송 쓰기 대기로 막히면서 **결제뿐 아니라 주문 조회, 상품 조회 등 모든 API가 먹통**

```
[비동기 방식 — 요청당 트랜잭션 보유 시간]
PG 외부 호출은 트랜잭션 밖에서 수행
finalizeConfirmSuccess: Payment 쓰기만 (수 ms)
→ AFTER_COMMIT 후 별도 스레드에서 Order 쓰기, Delivery 쓰기
= 결제 API의 커넥션 점유 시간 최소화
```

#### 시나리오 C: 배송 도메인 장애가 결제를 마비시킴

동기 방식에서는 deliveries 테이블에 문제가 생기면(디스크 풀, 인덱스 손상, DDL 락 등) **결제 자체가 불가능**해진다. 배송과 결제는 비즈니스적으로 독립된 도메인인데, 구현 수준에서 강결합되면 한쪽의 장애가 다른 쪽으로 전파된다.

비동기 방식에서는 배송 생성이 실패해도 결제는 정상 완료되고, 배송은 RetryTask가 나중에 재시도한다. 배송 테이블이 1시간 동안 죽어 있어도 그 동안 쌓인 `CREATE_DELIVERY` retry 태스크들이 복구 후 순차 처리된다.

### 3. 기존 이벤트 체인 패턴과 일관성 유지

`markAsPaid`도 이미 `PaymentCompletedEvent` → `@Async` 리스너로 처리하고 있다. 배송 생성도 같은 패턴으로 처리하면:
- 실패 시 `RetryTask`로 보상하는 동일한 안전망 적용
- 코드 구조가 일관적이라 유지보수가 쉬움

### 4. 실패 격리와 단계별 재시도

비동기로 분리하면 각 단계가 독립적으로 실패하고, 독립적으로 재시도할 수 있다:

| 실패 지점 | 동기 방식 결과 | 비동기 방식 결과 |
|---|---|---|
| markAsPaid 실패 | 전체 롤백, PG 보상 필요 | `MARK_AS_PAID` retry 등록, 배송 시도 안 함 |
| 배송 생성 실패 | 전체 롤백, PG 보상 필요 | `CREATE_DELIVERY` retry 등록, 결제는 정상 |
| 둘 다 실패 | 전체 롤백, PG 보상 필요 | 각각 retry 등록, 스케줄러가 순차 복구 |

### 구현 상세

**PaymentEventListener.handlePaymentCompleted:**
```java
try {
    orderService.markAsPaid(event.getOrderId());
} catch (Exception e) {
    retryTaskService.register("MARK_AS_PAID", ...);
    return;  // 배송 시도 안 함 — Order가 PAID가 아니면 배송 생성 불가
}

try {
    deliveryService.createDeliveryFromOrder(event.getOrderId());
} catch (Exception e) {
    retryTaskService.register("CREATE_DELIVERY", ...);
}
```

**MarkAsPaidHandler (retry):**
markAsPaid 재시도 성공 후 `createDeliveryFromOrder`도 호출한다. 리스너에서 markAsPaid 실패로 배송 단계를 건너뛴 경우를 보상하기 위함이다.

**createDeliveryFromOrder 멱등성:**
`findByOrderId`로 이미 배송이 존재하면 기존 것을 반환한다. retry나 중복 이벤트에 안전하다.

---

## 📊 Result (결과)

- 결제 확인 API 응답 시간에 배송 생성 지연이 포함되지 않음
- 배송 생성 실패가 결제 성공을 롤백하지 않음
- `RetryTask` 기반 보상으로 최종적 일관성(Eventual Consistency) 보장
- 기존 `markAsPaid` 이벤트 패턴과 동일한 구조로 코드 일관성 유지
- 전체 테스트 통과 확인

---

## Current Limitations (2026-03)

This document describes why async delivery auto-creation + RetryTask dispatch was selected.
The following are known limitations in the current implementation and the practical next steps.

### 1) String-based `taskType` dispatch
- Current state: `taskType` is a raw string (e.g. `MARK_AS_PAID`, `CREATE_DELIVERY`).
- Limitation: typo or renamed value is not compile-time safe; runtime fallback becomes `No handler found`.
- Impact: task can move to repeated failure or `EXHAUSTED` if mapping is broken.
- Next step: introduce a shared enum/constant set + startup validation for registered handlers.

### 2) String payload parsing (`PayloadParser`)
- Current state: payload is a JSON-like string and parsed by regex.
- Limitation: schema change and nested payload structures are fragile.
- Impact: parse failures can create retry loops.
- Next step: move to typed payload DTO + `ObjectMapper` deserialization + version field.

### 3) At-least-once retry semantics
- Current state: scheduler retries pending tasks with exponential backoff.
- Limitation: the same business action may be attempted more than once.
- Impact: handlers must be idempotent; non-idempotent writes can duplicate side effects.
- Next step: keep idempotent handler design and enforce unique constraints where possible.

### 4) Observability and alerting gap
- Current state: logs exist, but operational metrics are limited.
- Limitation: no first-class alarm on growing `PENDING` or `EXHAUSTED` counts.
- Impact: delayed discovery of stuck compensation tasks.
- Next step: add metrics (`pending_count`, `exhausted_count`, retry latency) and dashboard/alerts.

### 5) Manual recovery workflow is implicit
- Current state: `EXHAUSTED` indicates manual intervention required.
- Limitation: there is no explicit admin API or runbook command in one place.
- Impact: recovery speed depends on engineer familiarity.
- Next step: define standard requeue procedure and add operator endpoint/script.

### 6) `retry_tasks` retention policy is missing
- Current state: task history accumulates in DB.
- Limitation: cleanup/archival strategy is not formalized.
- Impact: table growth can affect query performance and operational cost.
- Next step: periodic purge/archival job by status + age.

### 7) Test coverage gap for new dispatch path
- Current state: core payment/refund flows are tested, but dedicated tests for `CREATE_DELIVERY` retry chain are limited.
- Limitation: regressions in handler registration/dispatch may be detected late.
- Impact: production-only failure mode risk.
- Next step: add integration tests for:
  - payment completed -> delivery creation failure -> `CREATE_DELIVERY` registration
  - scheduler retry -> delivery creation success
  - duplicate task registration dedupe behavior

## Troubleshooting Runbook (RetryTask Dispatch)

### Symptom A: Order is PAID but delivery was not created
1. Check whether a pending/exhausted `CREATE_DELIVERY` task exists.
2. Verify delivery uniqueness constraint and order status.
3. Re-run/requeue task after root cause is fixed.

Example SQL:
```sql
SELECT status, task_type, COUNT(*)
FROM retry_tasks
GROUP BY status, task_type;

SELECT id, task_type, status, retry_count, next_retry_at, last_error, updated_at
FROM retry_tasks
WHERE task_type IN ('MARK_AS_PAID', 'CREATE_DELIVERY')
ORDER BY updated_at DESC
LIMIT 50;
```

### Symptom B: Repeated retries without recovery
1. Inspect `last_error` and classify as transient vs permanent.
2. If permanent business-rule mismatch, avoid blind retries.
3. If transient infra issue, fix dependency first, then requeue.

### Symptom C: Unknown task type appears
1. Confirm handler bean exists and `taskType()` value matches exactly.
2. Confirm no stale value is being registered in listeners/services.
3. Add startup validation to fail fast when task type has no handler.
