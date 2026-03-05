# Outbox 패턴 적용 시 예상 변화 및 효과 분석

## 1. 현재 구조 (동기 처리)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        현재: 동기 처리 구조                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  PaymentService.processPayment()                                        │
│  ┌─────────────────────────────────────────────────────────────┐       │
│  │  @Transactional (하나의 긴 트랜잭션)                         │       │
│  │  ├─ 1. 멱등성 체크                                          │       │
│  │  ├─ 2. Payment 생성/저장                                    │       │
│  │  ├─ 3. 외부 PG 호출 (느림, 1-3초)          ← 문제점         │       │
│  │  ├─ 4. Order 상태 변경 (markAsPaid)                         │       │
│  │  └─ 5. 실패 시 재고 해제 (Inventory.release)                │       │
│  └─────────────────────────────────────────────────────────────┘       │
│                                                                         │
│  문제점:                                                                │
│  - 외부 API 호출이 트랜잭션 내에 포함 → DB 커넥션 장시간 점유           │
│  - 후처리 실패 시 전체 롤백                                             │
│  - 단일 장애점 (Single Point of Failure)                               │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Outbox 패턴 적용 후 구조

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      Outbox 패턴 적용 후 구조                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  [Phase 1: 짧은 트랜잭션]                                               │
│  PaymentService.processPayment()                                        │
│  ┌─────────────────────────────────────────────────────────────┐       │
│  │  @Transactional (짧고 빠른 트랜잭션)                         │       │
│  │  ├─ 1. 멱등성 체크                                          │       │
│  │  ├─ 2. Payment 생성/저장 (REQUESTED)                        │       │
│  │  └─ 3. Outbox 레코드 저장 (PaymentCreatedEvent)             │       │
│  └─────────────────────────────────────────────────────────────┘       │
│         │                                                               │
│         ▼                                                               │
│  [Phase 2: 이벤트 발행]                                                 │
│  ┌─────────────────────────────────────────────────────────────┐       │
│  │  OutboxPublisher (스케줄러)                                  │       │
│  │  └─ Outbox 읽기 → Redis Stream 발행 → Outbox 삭제           │       │
│  └─────────────────────────────────────────────────────────────┘       │
│         │                                                               │
│         ▼                                                               │
│  [Phase 3: 비동기 처리]                                                 │
│  ┌─────────────────────────────────────────────────────────────┐       │
│  │  PaymentWorker (별도 스레드/프로세스)                        │       │
│  │  ├─ 외부 PG 호출 (트랜잭션 외부)                            │       │
│  │  ├─ Payment 상태 변경 (COMPLETED/FAILED)                    │       │
│  │  └─ 후속 이벤트 발행 (PaymentCompletedEvent)                │       │
│  └─────────────────────────────────────────────────────────────┘       │
│         │                                                               │
│         ▼                                                               │
│  [Phase 4: 후처리]                                                      │
│  ┌─────────────────────────────────────────────────────────────┐       │
│  │  OrderWorker                                                 │       │
│  │  └─ Order 상태 변경 (markAsPaid)                            │       │
│  └─────────────────────────────────────────────────────────────┘       │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. 예상되는 코드 변화

### 3.1 새로 생성되는 파일

| 파일 | 역할 |
|------|------|
| `outbox/domain/Outbox.java` | 이벤트 저장 엔티티 |
| `outbox/domain/OutboxStatus.java` | PENDING, PUBLISHED, FAILED |
| `outbox/repository/OutboxRepository.java` | Outbox CRUD |
| `outbox/service/OutboxService.java` | 이벤트 저장/조회 |
| `outbox/publisher/OutboxPublisher.java` | Redis Stream 발행 |
| `worker/PaymentWorker.java` | 결제 처리 Worker |
| `worker/OrderWorker.java` | 주문 상태 변경 Worker |
| `worker/InventoryWorker.java` | 재고 처리 Worker |

### 3.2 기존 서비스 변화

**PaymentService.java 변화:**

```java
// 변경 전 (현재)
@Transactional
public Payment processPayment(...) {
    Payment payment = Payment.create(...);
    paymentRepository.save(payment);

    paymentGateway.processPayment(payment);  // 외부 호출
    payment.markAsCompleted();

    onPaymentCompleted(payment);  // Order 상태 변경
    return payment;
}

// 변경 후 (Outbox 적용)
@Transactional
public Payment processPayment(...) {
    Payment payment = Payment.create(...);
    paymentRepository.save(payment);

    // 이벤트만 저장하고 종료 (외부 호출 X)
    outboxService.save(PaymentCreatedEvent.of(payment));

    return payment;  // REQUESTED 상태로 즉시 반환
}
```

**RefundService.java 변화:**

```java
// 변경 전 (현재)
@Transactional
public Refund approveRefund(...) {
    refund.approve(comment);
    processExternalRefund(refund);  // 외부 호출
    refund.markAsCompleted();
    onRefundCompleted(refund);  // Order + Inventory 처리
    return refund;
}

// 변경 후 (Outbox 적용)
@Transactional
public Refund approveRefund(...) {
    refund.approve(comment);
    outboxService.save(RefundApprovedEvent.of(refund));
    return refund;  // APPROVED 상태로 즉시 반환
}
```

---

## 4. 예상되는 효과

### 4.1 성능 개선

| 항목 | 현재 | Outbox 적용 후 |
|------|------|----------------|
| **트랜잭션 시간** | 1-3초 (PG 호출 포함) | 50-100ms |
| **DB 커넥션 점유** | 장시간 | 최소화 |
| **동시 처리량** | 제한적 | 대폭 증가 |
| **응답 시간** | PG 응답에 의존 | 즉시 응답 |

### 4.2 안정성 개선

| 상황 | 현재 | Outbox 적용 후 |
|------|------|----------------|
| **PG 장애** | 전체 결제 실패 | 재시도로 복구 |
| **서버 재시작** | 진행 중 결제 유실 | Outbox에서 복구 |
| **부분 실패** | 전체 롤백 | 개별 재시도 |
| **중복 요청** | 멱등성 키로 방어 | 멱등성 키 + Worker 멱등성 |

### 4.3 장애 복구 시나리오

```
[시나리오: 결제 중 서버 장애]

현재 (동기):
1. Payment 생성 → 2. PG 호출 중 서버 다운 → 3. 트랜잭션 롤백
결과: 사용자에게 에러, 재시도 필요

Outbox 적용 후:
1. Payment 생성 + Outbox 저장 → 2. 서버 다운
3. 서버 재시작 → 4. Outbox에서 미처리 이벤트 발견
5. Worker가 PG 호출 재시도 → 6. 결제 완료
결과: 자동 복구, 사용자 재시도 불필요
```

---

## 5. 아키텍처 변화

### 5.1 데이터 흐름 변화

```
현재:
  API Request → Service → External API → Response
  (동기, 블로킹)

Outbox 적용 후:
  API Request → Service → Outbox → Response (즉시)
                              ↓
                         Redis Stream
                              ↓
                           Worker → External API
  (비동기, 논블로킹)
```

### 5.2 컴포넌트 의존성 변화

```
현재:
  PaymentService ──직접호출──→ PaymentGateway
                 ──직접호출──→ OrderService
                 ──직접호출──→ InventoryService

Outbox 적용 후:
  PaymentService ──저장──→ Outbox
                              ↓ (이벤트)
  PaymentWorker  ──호출──→ PaymentGateway
  OrderWorker    ──호출──→ OrderService
  InventoryWorker ──호출──→ InventoryService

  (서비스 간 결합도 감소)
```

---

## 6. 고려해야 할 트레이드오프

### 6.1 복잡도 증가

| 항목 | 설명 |
|------|------|
| **코드량 증가** | Outbox, Worker, Event 클래스 추가 |
| **디버깅 어려움** | 비동기 흐름 추적 필요 |
| **테스트 복잡도** | Worker 테스트, 이벤트 테스트 추가 |
| **운영 복잡도** | Redis, Worker 모니터링 필요 |

### 6.2 최종 일관성 (Eventual Consistency)

```
현재 (강한 일관성):
  결제 API 응답 시점 = Order가 PAID 상태

Outbox 적용 후 (최종 일관성):
  결제 API 응답 시점 = Payment가 REQUESTED 상태
  몇 초 후               = Order가 PAID 상태

  → 클라이언트가 폴링하거나 웹소켓으로 상태 변화 알림 필요
```

### 6.3 추가 인프라 필요

| 인프라 | 용도 |
|--------|------|
| **Redis** | Stream 메시지 큐 |
| **스케줄러** | Outbox 폴링, 재처리 |
| **모니터링** | 이벤트 처리 상태 추적 |

---

## 7. 결론

### Outbox 패턴 적용 시:

**장점:**
- 트랜잭션 시간 단축 (1-3초 → 100ms)
- 외부 시스템 장애 격리
- 자동 복구 가능
- 서비스 간 결합도 감소

**단점:**
- 시스템 복잡도 증가
- 최종 일관성 모델로 전환
- 추가 인프라 필요 (Redis)

**적용 권장 시점:**
- 외부 API 호출이 빈번할 때
- 트래픽이 증가할 때
- 장애 복구 자동화가 필요할 때

### 현재 프로젝트 상태:
- 이벤트 클래스 이미 정의됨 (`PaymentCompletedEvent` 등)
- Outbox 패키지 구조 준비됨
- 이벤트 분리 지점 명확함 (`onPaymentCompleted`, `onRefundCompleted`)
- **Outbox 패턴 적용 준비도: 90%**
