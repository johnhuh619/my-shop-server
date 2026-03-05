# 실패 시나리오 처리 구현 변경사항

## 개요

이번 커밋에서는 결제 실패 보상, Refund 도메인, 주문 만료 기능을 구현하여 시스템의 장애 대응 능력을 강화했습니다.

---

## 1. 주요 변경사항 요약

| 카테고리 | 변경 내용 |
|---------|----------|
| **결제 실패 보상** | 결제 실패 시 예약된 재고 자동 해제 |
| **Refund 도메인** | PAID 상태 주문 환불 처리 (8개 파일 신규 생성) |
| **주문 만료** | 30분 미결제 주문 자동 만료 + 재고 해제 |
| **상태 확장** | OrderStatus에 EXPIRED, REFUND_REQUESTED, REFUNDED 추가 |

---

## 2. 수정된 파일 (7개)

### 2.1 ErrorCode.java

**위치**: `common/exception/ErrorCode.java`

**추가된 에러 코드**:
```java
// Order
ORDER_ALREADY_PAID("O003", "Order is already paid"),
ORDER_EXPIRED("O004", "Order has expired"),

// Refund
REFUND_NOT_ALLOWED("R003", "Refund not allowed for this order"),
REFUND_AMOUNT_EXCEEDED("R004", "Refund amount exceeds payment")
```

**용도**:
- `O003`: 이미 결제된 주문에 대한 중복 결제 시도 방지
- `O004`: 만료된 주문에 대한 작업 시도 방지
- `R003`: 환불 불가능한 상태(PAID 외)에서 환불 시도 방지
- `R004`: 결제 금액 초과 환불 방지

---

### 2.2 OrderStatus.java

**위치**: `order/domain/OrderStatus.java`

**변경 전**:
```java
public enum OrderStatus {
    CREATED, PAID, COMPLETED, CANCELED
}
```

**변경 후**:
```java
public enum OrderStatus {
    CREATED,
    PAID,
    COMPLETED,
    CANCELED,
    EXPIRED,           // 신규: 주문 만료
    REFUND_REQUESTED,  // 신규: 환불 요청됨
    REFUNDED           // 신규: 환불 완료
}
```

**상태 전이 다이어그램**:
```
                    ┌──────────────┐
                    │   CREATED    │
                    └──────┬───────┘
           ┌───────────────┼───────────────┐
           ↓               ↓               ↓
      ┌─────────┐    ┌──────────┐    ┌──────────┐
      │ EXPIRED │    │ CANCELED │    │   PAID   │
      └─────────┘    └──────────┘    └────┬─────┘
                                          │
                    ┌─────────────────────┼─────────────────────┐
                    ↓                     ↓                     ↓
           ┌────────────────┐    ┌─────────────┐    ┌────────────────┐
           │REFUND_REQUESTED│    │  COMPLETED  │    │  (환불 불가)   │
           └───────┬────────┘    └─────────────┘    └────────────────┘
                   ↓
           ┌───────────────┐
           │   REFUNDED    │
           └───────────────┘
```

---

### 2.3 Order.java

**위치**: `order/domain/Order.java`

**추가된 상태 전이 메서드**:

#### expire()
```java
public void expire() {
    if (this.status != OrderStatus.CREATED) {
        throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                "Order can only be expired when status is CREATED");
    }
    this.status = OrderStatus.EXPIRED;
    this.updatedAt = Instant.now();
}
```
- **목적**: CREATED 상태 주문을 만료 처리
- **트리거**: OrderExpirationScheduler에서 30분 경과 주문 대상으로 호출
- **검증**: CREATED 상태에서만 전이 허용

#### requestRefund()
```java
public void requestRefund() {
    if (this.status != OrderStatus.PAID) {
        throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                "Refund can only be requested when order status is PAID");
    }
    this.status = OrderStatus.REFUND_REQUESTED;
    this.updatedAt = Instant.now();
}
```
- **목적**: 환불 요청 상태로 전이
- **트리거**: RefundService.processRefund()에서 호출
- **검증**: PAID 상태에서만 전이 허용

#### markAsRefunded()
```java
public void markAsRefunded() {
    if (this.status != OrderStatus.REFUND_REQUESTED) {
        throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                "Order can only be marked as refunded when status is REFUND_REQUESTED");
    }
    this.status = OrderStatus.REFUNDED;
    this.updatedAt = Instant.now();
}
```
- **목적**: 환불 완료 상태로 전이
- **트리거**: RefundService.onRefundCompleted()에서 호출
- **검증**: REFUND_REQUESTED 상태에서만 전이 허용

---

### 2.4 OrderService.java

**위치**: `order/service/OrderService.java`

**추가된 메서드**:

#### getOrderById(Long orderId)
```java
@Transactional(readOnly = true)
public Order getOrderById(Long orderId) {
    return orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
}
```
- **목적**: userId 검증 없이 Order 조회 (내부용)
- **사용처**: PaymentService, RefundService에서 사용
- **주의**: 외부 API에 노출하지 않음

#### expireOrder(Long orderId)
```java
@Transactional
public void expireOrder(Long orderId) {
    Order order = orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    if (order.getStatus() != OrderStatus.CREATED) {
        return; // 이미 처리됨 (멱등성)
    }

    // 재고 해제
    for (OrderItem item : order.getOrderItems()) {
        inventoryService.release(item.getProductId(), item.getQuantity());
    }

    order.expire();
    orderRepository.save(order);
}
```
- **목적**: 만료 주문 처리 + 재고 해제
- **특징**:
  - PESSIMISTIC_WRITE 락으로 동시성 보장
  - 멱등성 처리 (이미 다른 상태면 무시)

#### requestRefund(Long orderId)
```java
@Transactional
public Order requestRefund(Long orderId) {
    Order order = orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    order.requestRefund();
    return orderRepository.save(order);
}
```
- **목적**: 환불 요청 상태로 변경
- **트랜잭션**: Order 저장 포함

#### markAsRefunded(Long orderId)
```java
@Transactional
public Order markAsRefunded(Long orderId) {
    Order order = orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    order.markAsRefunded();
    return orderRepository.save(order);
}
```
- **목적**: 환불 완료 상태로 변경
- **트랜잭션**: Order 저장 포함

---

### 2.5 PaymentService.java

**위치**: `payment/service/PaymentService.java`

**주요 변경**:

#### 의존성 추가
```java
private final InventoryService inventoryService;  // 신규 추가
```

#### processPayment() 수정
```java
catch (Exception e) {
    payment.markAsFailed();
    // 결제 실패 시 재고 보상 (추후 이벤트로 전환 가능)
    onPaymentFailed(payment, orderId);  // 신규 추가
}
```

#### onPaymentFailed() 추가
```java
// 추후 이벤트 발행으로 전환 가능한 메서드
// 결제 실패 시 예약된 재고 해제
private void onPaymentFailed(Payment payment, Long orderId) {
    Order order = orderService.getOrderById(orderId);
    for (OrderItem item : order.getOrderItems()) {
        inventoryService.release(item.getProductId(), item.getQuantity());
    }
}
```

**동작 원리**:
1. 결제 처리 중 예외 발생
2. Payment 상태를 FAILED로 변경
3. onPaymentFailed() 호출 → 예약된 재고 해제
4. Order 상태는 CREATED 유지 (재결제 가능)

**Outbox 전환 시**:
```java
// 현재 (동기)
private void onPaymentFailed(Payment payment, Long orderId) {
    Order order = orderService.getOrderById(orderId);
    // 재고 해제...
}

// 추후 (비동기 이벤트)
private void onPaymentFailed(Payment payment, Long orderId) {
    PaymentFailedEvent event = PaymentFailedEvent.from(payment);
    outboxService.save(event);  // Worker가 비동기로 처리
}
```

---

### 2.6 OrderRepository.java

**위치**: `order/repository/OrderRepository.java`

**추가된 쿼리 메서드**:
```java
/**
 * 주문 만료 조회용
 * 특정 상태이고 생성 시간이 특정 시간 이전인 주문 조회
 */
List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, Instant createdAt);
```

**사용 예시** (OrderExpirationScheduler):
```java
Instant expirationTime = Instant.now().minus(30, ChronoUnit.MINUTES);
List<Order> expiredOrders = orderRepository
    .findByStatusAndCreatedAtBefore(OrderStatus.CREATED, expirationTime);
```

---

### 2.7 MiniShopApplication.java

**위치**: `MiniShopApplication.java`

**변경 내용**:
```java
@SpringBootApplication
@EnableScheduling  // 신규 추가
public class MiniShopApplication {
    // ...
}
```

**목적**: Spring의 @Scheduled 어노테이션 활성화

---

## 3. 생성된 파일 (10개)

### 3.1 PaymentFailedEvent.java

**위치**: `payment/event/PaymentFailedEvent.java`

```java
@Getter
public class PaymentFailedEvent {
    private final Long paymentId;
    private final Long userId;
    private final Long orderId;
    private final Long amount;
    private final Instant failedAt;

    public static PaymentFailedEvent from(Payment payment) {
        // Payment → Event 변환
    }
}
```

**목적**: 추후 Outbox 패턴 전환 시 사용할 이벤트 클래스

---

### 3.2 Refund 도메인 (8개 파일)

#### RefundStatus.java
```java
public enum RefundStatus {
    REQUESTED,   // 환불 요청됨
    COMPLETED,   // 환불 완료
    FAILED       // 환불 실패
}
```

#### Refund.java
```java
@Entity
@Table(name = "refunds")
public class Refund {
    @Id @GeneratedValue
    private Long id;
    private Long userId;
    private Long paymentId;    // Payment 기준 환불
    private Long orderId;
    @Enumerated(EnumType.STRING)
    private RefundStatus status;
    private Long amount;       // 부분 환불 가능
    private String reason;
    private Instant createdAt;
    private Instant updatedAt;

    // Factory method
    public static Refund create(...);

    // State transitions
    public void markAsCompleted();
    public void markAsFailed();
}
```

#### RefundRepository.java
```java
public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByUserId(Long userId);
    Optional<Refund> findByIdAndUserId(Long refundId, Long userId);
    List<Refund> findByPaymentId(Long paymentId);
}
```

#### CreateRefundRequest.java
```java
@Getter
public class CreateRefundRequest {
    private Long paymentId;
    private Long amount;      // null이면 전액 환불
    private String reason;
}
```

#### RefundResponse.java
```java
@Getter
public class RefundResponse {
    // 모든 필드 포함
    public static RefundResponse from(Refund refund);
}
```

#### RefundService.java
```java
@Service
public class RefundService {
    @Transactional
    public Refund processRefund(Long userId, Long paymentId, Long amount, String reason) {
        // 1. Payment 조회
        // 2. Order 상태 확인 (PAID만 환불 가능)
        // 3. 환불 금액 검증
        // 4. Refund 생성
        // 5. Order 상태 변경 (PAID → REFUND_REQUESTED)
        // 6. 외부 환불 처리
        // 7. 성공 시: onRefundCompleted() 호출
        // 8. 실패 시: markAsFailed()
    }

    private void onRefundCompleted(Refund refund, Long orderId) {
        // Order 상태 변경 (→ REFUNDED)
        // 재고 복구
    }
}
```

#### RefundController.java
```java
@RestController
@RequestMapping("/api/refunds")
public class RefundController {
    @PostMapping
    public ApiResponse<RefundResponse> processRefund(@RequestBody CreateRefundRequest request);

    @GetMapping("/{id}")
    public ApiResponse<RefundResponse> getRefund(@PathVariable Long id);

    @GetMapping
    public ApiResponse<List<RefundResponse>> getMyRefunds();
}
```

#### RefundCompletedEvent.java
```java
@Getter
public class RefundCompletedEvent {
    private final Long refundId;
    private final Long userId;
    private final Long paymentId;
    private final Long orderId;
    private final Long amount;
    private final Instant completedAt;

    public static RefundCompletedEvent from(Refund refund);
}
```

---

### 3.3 OrderExpirationScheduler.java

**위치**: `order/scheduler/OrderExpirationScheduler.java`

```java
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private static final long EXPIRATION_MINUTES = 30;

    @Scheduled(fixedRate = 60000) // 1분마다 실행
    @Transactional
    public void expireOrders() {
        Instant expirationTime = Instant.now().minus(EXPIRATION_MINUTES, ChronoUnit.MINUTES);

        List<Order> expiredOrders = orderRepository
            .findByStatusAndCreatedAtBefore(OrderStatus.CREATED, expirationTime);

        for (Order order : expiredOrders) {
            try {
                orderService.expireOrder(order.getId());
            } catch (Exception e) {
                // 로그 기록 후 계속 진행
            }
        }
    }
}
```

**동작 원리**:
1. 1분마다 스케줄러 실행
2. CREATED 상태 + 생성 시간 30분 이상 지난 주문 조회
3. 각 주문에 대해 expireOrder() 호출
4. 개별 주문 실패해도 다른 주문 처리 계속

---

## 4. API 변경사항

### 신규 Refund API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/refunds` | 환불 요청 |
| GET | `/api/refunds/{id}` | 환불 상세 조회 |
| GET | `/api/refunds` | 내 환불 목록 |

### Request Example
```http
POST /api/refunds
Content-Type: application/json

{
  "paymentId": 1,
  "amount": 35000,      // null이면 전액 환불
  "reason": "단순 변심"
}
```

### Response Example
```json
{
  "success": true,
  "data": {
    "id": 1,
    "userId": 100,
    "paymentId": 1,
    "orderId": 1,
    "status": "COMPLETED",
    "amount": 35000,
    "reason": "단순 변심",
    "createdAt": "2025-01-15T10:00:00Z",
    "updatedAt": "2025-01-15T10:00:05Z"
  }
}
```

---

## 5. 트랜잭션 흐름

### 5.1 결제 실패 보상
```
@Transactional (PaymentService.processPayment)
├── Payment 생성 (REQUESTED)
├── 외부 결제 실패 → Exception
├── payment.markAsFailed()
├── onPaymentFailed()
│   ├── Order 조회
│   └── 각 OrderItem에 대해 inventory.release()
└── Payment 저장 (FAILED)

※ Order 상태: CREATED 유지 (재결제 가능)
※ Inventory: reserved → available 복원
```

### 5.2 환불 처리
```
@Transactional (RefundService.processRefund)
├── Payment 조회
├── Order 조회 및 검증 (PAID 상태)
├── Refund 생성 (REQUESTED)
├── orderService.requestRefund() → Order: PAID → REFUND_REQUESTED
├── 외부 환불 처리
├── refund.markAsCompleted()
├── onRefundCompleted()
│   ├── orderService.markAsRefunded() → Order: REFUND_REQUESTED → REFUNDED
│   └── 각 OrderItem에 대해 inventory.release()
└── Refund 저장 (COMPLETED)

※ Inventory: reserved → available 복원
```

### 5.3 주문 만료
```
@Scheduled (OrderExpirationScheduler.expireOrders)
└── 각 만료 대상 주문에 대해:
    @Transactional (OrderService.expireOrder)
    ├── Order 조회 (WITH LOCK)
    ├── 상태 확인 (CREATED가 아니면 무시)
    ├── 각 OrderItem에 대해 inventory.release()
    ├── order.expire()
    └── Order 저장 (EXPIRED)

※ 1분 주기 실행
※ 30분 이상 미결제 주문 대상
```

---

## 6. 데이터베이스 스키마 변경

### orders 테이블
```sql
-- status 컬럼에 새로운 값 추가
-- 기존: CREATED, PAID, COMPLETED, CANCELED
-- 추가: EXPIRED, REFUND_REQUESTED, REFUNDED
```

### refunds 테이블 (신규)
```sql
CREATE TABLE refunds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,  -- REQUESTED, COMPLETED, FAILED
    amount BIGINT NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

---

## 7. DOMAIN_RULES 준수 확인

| 규칙 | 준수 여부 | 구현 내용 |
|-----|---------|---------|
| Payment는 Order 직접 변경 금지 | ✅ | OrderService 통해서만 변경 |
| Payment는 Inventory 직접 조작 금지 | ✅ | InventoryService 통해 release |
| Refund는 Payment 기준 생성 | ✅ | paymentId 기반으로 생성 |
| 상태 전이 검증 | ✅ | 각 메서드에서 현재 상태 확인 |
| 트랜잭션 경계 명확 | ✅ | @Transactional 적용 |
| 이벤트 발행 구조 | ✅ | onXXX() 메서드 분리 |

---

## 8. 향후 개선 계획 (Phase 2)

1. **Outbox 패턴 전환**
   - onPaymentFailed(), onRefundCompleted() → 이벤트 발행으로 변경
   - Worker 기반 비동기 처리

2. **부분 환불 고도화**
   - 여러 번 부분 환불 지원
   - Payment에 refundedAmount 필드 추가

3. **재고 복구 정책**
   - 상품 상태에 따른 복구 결정
   - 정책 객체로 분리

4. **알림 시스템**
   - 결제 실패/환불 완료 알림
   - Email/SMS/Push 채널

5. **관리자 API**
   - 수동 환불 처리
   - 주문 상태 강제 변경
