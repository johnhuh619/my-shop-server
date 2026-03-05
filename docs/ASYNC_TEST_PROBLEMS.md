# Spring Async Event 테스트 문제 해결 가이드

## 개요

Spring Event 기반 비동기 처리를 도입하면서 테스트 코드 작성 중 발생한 4가지 핵심 문제와 해결 과정을 문서화합니다.

**목표**: 응답 시간 최적화 (3000ms → 100ms)
**방법**: PG 호출을 `@Async` 이벤트 리스너로 이동
**도전**: 비동기 처리 검증 테스트 작성

---

## 문제 1: @Transactional 테스트에서 이벤트 미발생

### 증상

```
expected: PAID
 but was: CREATED
```

테스트에서 `PaymentCreatedEvent` 발행 후 `Order` 상태가 `PAID`로 변경되지 않음.

### 에러 메시지

테스트는 실패하지만 명확한 에러 메시지는 없음. 단순히 기대값과 실제값 불일치.

### 원본 코드

```java
@SpringBootTest
@Transactional  // ❌ 문제의 원인
class PaymentServiceTest {

    @Test
    void processPayment_정상결제_성공() {
        // Given
        Order order = createTestOrder();

        // When
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key-123");

        // Then - 이벤트 처리 대기
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Order paidOrder = orderService.getOrder(order.getId(), testUserId);
            assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);  // FAILED
        });
    }
}
```

### 근본 원인

`@Transactional` 어노테이션이 테스트 클래스에 있으면:

1. **각 테스트 메서드가 하나의 트랜잭션 안에서 실행**됨
2. **테스트 종료 시 자동으로 롤백**됨 (기본 동작)
3. **트랜잭션이 커밋되지 않으므로** `@TransactionalEventListener(phase = AFTER_COMMIT)` 이벤트가 **발생하지 않음**

#### Spring Event 발행 조건

```java
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handlePaymentCreated(PaymentCreatedEvent event) {
    // 이 메서드는 트랜잭션 커밋 후에만 실행됨
}
```

- `AFTER_COMMIT`: 트랜잭션이 **실제로 커밋**된 후에만 이벤트 처리
- 테스트의 `@Transactional`은 **롤백**하므로 커밋이 없음
- 결과: 이벤트 리스너가 **절대 실행되지 않음**

### 시도한 해결 방법들

#### 시도 1: @Commit 추가 (실패)

```java
@Test
@Commit  // 롤백 대신 커밋 시도
void processPayment_정상결제_성공() {
    // ...
}
```

**문제점**:
- 다른 테스트에 영향을 줌 (DB 상태 오염)
- 테스트 격리 원칙 위반

#### 시도 2: 테스트마다 수동 롤백 (실패)

```java
@Test
void processPayment_정상결제_성공() {
    // 테스트 후 수동 정리
    orderRepository.deleteAll();
    paymentRepository.deleteAll();
}
```

**문제점**:
- 복잡하고 에러 발생 가능
- 외래 키 제약 조건 문제
- 테스트 실패 시 정리 코드 미실행

### 최종 해결 방법

```java
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)  // ✅
class PaymentServiceTest {

    @Test
    void processPayment_정상결제_성공() {
        // Given
        Order order = createTestOrder();

        // When
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key-123");

        // Then - 즉시 검증 (동기)
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);

        // 비동기 이벤트 처리 대기
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Payment updated = paymentService.getPayment(payment.getId(), testUserId);
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        });

        // Order 상태 변경 확인
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Order paidOrder = orderService.getOrder(order.getId(), testUserId);
            assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);  // ✅ SUCCESS
        });
    }
}
```

#### @DirtiesContext의 동작 원리

- **각 테스트 메서드 후** ApplicationContext를 재생성
- DB 상태도 초기화 (Spring Boot의 내장 DB 사용 시)
- **실제 트랜잭션 커밋** 발생 → 이벤트 정상 발행
- 테스트 격리 보장

#### 트레이드오프

| 방식 | 속도 | 격리 | 이벤트 발행 |
|------|------|------|-------------|
| `@Transactional` | 빠름 (롤백) | 완벽 | ❌ 불가 |
| `@DirtiesContext` | 느림 (컨텍스트 재생성) | 완벽 | ✅ 가능 |
| `@Commit` | 중간 | ❌ 오염 | ✅ 가능 |

**선택**: `@DirtiesContext` - 속도보다 정확성 우선

### 학습 포인트

1. **Spring 테스트의 기본 동작**
   - `@Transactional`은 테스트 격리를 위해 롤백함
   - 롤백은 커밋이 아니므로 `AFTER_COMMIT` 이벤트 미발생

2. **비동기 테스트는 실제 커밋이 필요**
   - 이벤트 기반 아키텍처 테스트 시 `@Transactional` 제거 필수
   - 대신 `@DirtiesContext`로 격리 보장

3. **Awaitility 필수**
   - 비동기 처리 완료를 기다리지 않으면 테스트 실패
   - `await().atMost(N, SECONDS).untilAsserted()`로 검증

---

## 문제 2: 비동기 메서드 내부에서 발행한 이벤트 미전파

### 증상

`handlePaymentCreated` 내부에서 발행한 `PaymentCompletedEvent`가 `handlePaymentCompleted`로 전달되지 않음.

### 원본 설계 (실패)

```java
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final ApplicationEventPublisher eventPublisher;
    private final OrderService orderService;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handlePaymentCreated(PaymentCreatedEvent event) {
        Payment payment = paymentRepository.findById(event.getPaymentId()).get();

        try {
            paymentGateway.processPayment(payment);  // PG 호출
            payment.markAsCompleted();
            paymentRepository.save(payment);

            // ❌ 이 이벤트가 전파되지 않음
            eventPublisher.publishEvent(PaymentCompletedEvent.from(payment));
        } catch (Exception e) {
            payment.markAsFailed();
            paymentRepository.save(payment);

            // ❌ 이 이벤트도 전파되지 않음
            eventPublisher.publishEvent(PaymentFailedEvent.from(payment, order));
        }
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        // ❌ 절대 호출되지 않음
        orderService.markAsPaid(event.getOrderId());
    }
}
```

### 근본 원인

#### 1. 트랜잭션 컨텍스트 격리

`@Async` 메서드는 **별도 스레드**에서 실행됨:

```
[main thread]
  PaymentService.processPayment()
    └─ eventPublisher.publishEvent(PaymentCreatedEvent)
         │
         │ 트랜잭션 커밋
         ▼
[event-1 thread]  ← 새로운 스레드
  handlePaymentCreated()
    └─ eventPublisher.publishEvent(PaymentCompletedEvent)  ← 다른 트랜잭션 컨텍스트
         │
         ▼ AFTER_COMMIT 대기?
         ❌ 상위 트랜잭션이 없음 → 이벤트 미발생
```

#### 2. @TransactionalEventListener의 조건

```java
@TransactionalEventListener(phase = AFTER_COMMIT)
```

이 어노테이션은:
- **현재 실행 중인 트랜잭션의 커밋을 기다림**
- `@Async` 메서드 내부에는 **트랜잭션이 없음** (별도 스레드)
- 트랜잭션이 없으면 `AFTER_COMMIT`이 의미 없음

#### 3. 이벤트 발행 스레드 불일치

```java
// PaymentService (main thread, 트랜잭션 O)
eventPublisher.publishEvent(PaymentCreatedEvent)  ✅

// PaymentEventListener (event-1 thread, 트랜잭션 X)
eventPublisher.publishEvent(PaymentCompletedEvent)  ❌
```

### 시도한 해결 방법들

#### 시도 1: @Transactional 추가 (실패 → 문제 4로 연결)

```java
@TransactionalEventListener(phase = AFTER_COMMIT)
@Async
@Transactional  // 트랜잭션 컨텍스트 생성 시도
public void handlePaymentCreated(PaymentCreatedEvent event) {
    // ...
    eventPublisher.publishEvent(PaymentCompletedEvent.from(payment));
}
```

**결과**: Spring Boot 4.x에서 금지된 조합 (문제 4 참조)

#### 시도 2: @EventListener로 변경 (실패)

```java
@EventListener  // @TransactionalEventListener 대신
@Async
public void handlePaymentCreated(PaymentCreatedEvent event) {
    // ...
}
```

**문제점**:
- `@EventListener`는 트랜잭션 커밋을 기다리지 않음
- 트랜잭션 롤백 시에도 이벤트 발생 → 데이터 불일치

### 최종 해결 방법: 이벤트 체인 제거

```java
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    // eventPublisher 제거 ✅
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handlePaymentCreated(PaymentCreatedEvent event) {
        try {
            // 1. Payment 재조회
            Payment payment = paymentRepository.findById(event.getPaymentId())
                    .orElseThrow(() -> new RuntimeException("Payment not found"));

            // 2. PG 호출
            paymentGateway.processPayment(payment);

            // 3. 결제 성공 처리
            payment.markAsCompleted();
            paymentRepository.save(payment);

            // 4. Order 상태 직접 업데이트 ✅ (이벤트 대신 직접 호출)
            orderService.markAsPaid(event.getOrderId());

        } catch (Exception e) {
            // 1. Payment 재조회
            Payment payment = paymentRepository.findById(event.getPaymentId())
                    .orElseThrow(() -> new RuntimeException("Payment not found"));

            // 2. 결제 실패 처리
            payment.markAsFailed();
            paymentRepository.save(payment);

            // 3. 재고 해제 직접 처리 ✅ (이벤트 대신 직접 호출)
            Order order = orderRepository.findByIdWithItems(payment.getOrderId())
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            for (OrderItem item : order.getOrderItems()) {
                inventoryService.release(item.getProductId(), item.getQuantity());
            }
        }
    }

    // handlePaymentCompleted(), handlePaymentFailed() 제거 ✅
}
```

#### 아키텍처 변경

**Before (3단계 체인)**:
```
PaymentService
  └─ PaymentCreatedEvent 발행
       └─ handlePaymentCreated()
            └─ PaymentCompletedEvent 발행  ❌ 전파 안됨
                 └─ handlePaymentCompleted()  ❌ 실행 안됨
```

**After (2단계 + 직접 호출)**:
```
PaymentService
  └─ PaymentCreatedEvent 발행
       └─ handlePaymentCreated()
            ├─ orderService.markAsPaid()  ✅ 직접 호출
            └─ inventoryService.release()  ✅ 직접 호출
```

### 학습 포인트

1. **이벤트 체인의 한계**
   - `@Async` 메서드에서 발행한 이벤트는 트랜잭션 컨텍스트가 없음
   - `@TransactionalEventListener`는 트랜잭션 경계에서만 동작

2. **설계 원칙**
   - 이벤트는 **1단계 깊이**만 사용 (Service → Listener)
   - 다단계 이벤트 체인 지양 (Listener → Listener ❌)
   - 복잡한 로직은 **직접 호출**이 명확함

3. **이벤트 vs 직접 호출**
   - 이벤트: 도메인 간 결합도 감소, 비동기 처리
   - 직접 호출: 명확한 실행 흐름, 디버깅 용이
   - **같은 비동기 컨텍스트 내부**에서는 직접 호출 선호

---

## 문제 3: Lazy Loading Exception (No Hibernate Session)

### 증상

```
org.hibernate.LazyInitializationException:
failed to lazily initialize a collection of role: com.minishop.project.minishop.order.domain.Order.orderItems,
could not initialize proxy - no Session
```

### 에러 발생 코드

```java
@TransactionalEventListener(phase = AFTER_COMMIT)
@Async
public void handlePaymentCreated(PaymentCreatedEvent event) {
    try {
        // ... PG 호출 및 결제 성공 처리
    } catch (Exception e) {
        Payment payment = paymentRepository.findById(event.getPaymentId()).get();
        payment.markAsFailed();
        paymentRepository.save(payment);

        // ❌ 여기서 LazyInitializationException 발생
        Order order = orderRepository.findById(payment.getOrderId()).get();
        for (OrderItem item : order.getOrderItems()) {  // ← Exception!
            inventoryService.release(item.getProductId(), item.getQuantity());
        }
    }
}
```

### 근본 원인

#### 1. Hibernate Lazy Loading 기본 동작

```java
@Entity
public class Order {
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)  // 기본값
    private List<OrderItem> orderItems;
}
```

- `@OneToMany`의 기본 fetch 전략은 `LAZY` (지연 로딩)
- `order.getOrderItems()`를 호출할 때 **실제 DB 쿼리 발생**
- 쿼리를 실행하려면 **Hibernate Session**이 필요함

#### 2. @Async 메서드의 세션 부재

```
[main thread - PaymentService]
  @Transactional
  processPayment()
    └─ Hibernate Session 존재 ✅
         │
         │ 트랜잭션 커밋 및 Session 종료
         ▼
[event-1 thread - PaymentEventListener]
  @Async
  handlePaymentCreated()
    └─ Hibernate Session 없음 ❌
         │
         ▼
    order.getOrderItems()  ← Session 필요!
         │
         ▼
    LazyInitializationException
```

#### 3. 스레드 격리

- Hibernate Session은 **Thread Local**에 저장됨
- `@Async`는 **다른 스레드**에서 실행
- 원본 스레드의 Session에 접근 불가

### 시도한 해결 방법들

#### 시도 1: @Transactional 추가 (실패 → 문제 4로 연결)

```java
@TransactionalEventListener(phase = AFTER_COMMIT)
@Async
@Transactional  // 새로운 Session 생성 시도
public void handlePaymentCreated(PaymentCreatedEvent event) {
    Order order = orderRepository.findById(orderId).get();
    order.getOrderItems();  // Session이 있어서 동작할 것으로 기대
}
```

**결과**: Spring Boot 4.x에서 금지된 조합
```
java.lang.IllegalStateException:
Spring Boot does not allow @TransactionalEventListener + @Async + @Transactional
```

#### 시도 2: FetchType.EAGER 변경 (지양)

```java
@Entity
public class Order {
    @OneToMany(mappedBy = "order", fetch = FetchType.EAGER)
    private List<OrderItem> orderItems;
}
```

**문제점**:
- **모든 Order 조회 시** OrderItems도 항상 로딩 (불필요한 쿼리)
- N+1 문제 발생 가능
- 도메인 전체 성능에 영향

#### 시도 3: @EntityGraph (부분 실패)

```java
@EntityGraph(attributePaths = {"orderItems"})
Optional<Order> findById(Long id);
```

**문제점**:
- 기존 `findById()` 메서드 오버라이드 필요
- 다른 곳에서 `orderItems` 불필요한 경우에도 로딩됨

### 최종 해결 방법: JOIN FETCH 쿼리 메서드 추가

```java
// OrderRepository.java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // 기존 메서드 (Lazy Loading)
    Optional<Order> findById(Long id);

    /**
     * OrderItems를 즉시 로딩하여 조회
     * 비동기 이벤트 처리에서 사용 (Lazy Loading 방지)
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);  // ✅
}
```

```java
// PaymentEventListener.java
@TransactionalEventListener(phase = AFTER_COMMIT)
@Async
public void handlePaymentCreated(PaymentCreatedEvent event) {
    try {
        // ... 성공 처리
    } catch (Exception e) {
        Payment payment = paymentRepository.findById(event.getPaymentId()).get();
        payment.markAsFailed();
        paymentRepository.save(payment);

        // ✅ JOIN FETCH로 OrderItems 즉시 로딩
        Order order = orderRepository.findByIdWithItems(payment.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // ✅ Session 없어도 동작 (이미 메모리에 로드됨)
        for (OrderItem item : order.getOrderItems()) {
            inventoryService.release(item.getProductId(), item.getQuantity());
        }
    }
}
```

#### JOIN FETCH의 동작 원리

**일반 쿼리 (Lazy Loading)**:
```sql
-- 1. Order 조회
SELECT * FROM orders WHERE id = 1;

-- 2. getOrderItems() 호출 시 (Session 필요!)
SELECT * FROM order_items WHERE order_id = 1;
```

**JOIN FETCH 쿼리**:
```sql
-- 한 번에 조회 (Session 불필요)
SELECT o.*, oi.*
FROM orders o
LEFT JOIN order_items oi ON o.id = oi.order_id
WHERE o.id = 1;
```

#### 장점

1. **필요한 곳에서만 Eager Loading**
   - `findById()`: Lazy (기본)
   - `findByIdWithItems()`: Eager (명시적)

2. **Session 독립적**
   - 데이터가 쿼리 시점에 모두 로드됨
   - `@Async` 메서드에서도 안전

3. **명확한 의도 표현**
   - 메서드 이름으로 "Items 포함" 명시
   - 코드 가독성 향상

### 학습 포인트

1. **Lazy Loading의 전제 조건**
   - Hibernate Session이 열려 있어야 함
   - `@Transactional` 범위 내에서만 동작
   - 비동기 메서드는 별도 스레드 → Session 없음

2. **비동기 처리 시 데이터 로딩 전략**
   - **이벤트 발행 전**: 필요한 데이터를 DTO로 변환 (스냅샷)
   - **이벤트 처리 중**: JOIN FETCH로 Eager Loading
   - **절대 금지**: 비동기 메서드에서 Lazy Loading 의존

3. **설계 원칙**
   - 도메인 기본 fetch는 LAZY 유지
   - 특수한 경우만 명시적 쿼리 메서드 추가
   - 메서드 이름으로 로딩 전략 표현

---

## 문제 4: Spring Boot 4.x 어노테이션 조합 제한

### 증상

```
java.lang.IllegalStateException: @TransactionalEventListener method must not be annotated with @Async and @Transactional at the same time
```

### 에러 발생 코드

```java
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    @Transactional  // ❌ 금지된 조합
    public void handlePaymentCreated(PaymentCreatedEvent event) {
        // ...
    }
}
```

### 상세 에러 메시지

```
java.lang.IllegalStateException: @TransactionalEventListener method must not be annotated with @Async and @Transactional at the same time
    at org.springframework.transaction.event.RestrictedTransactionalEventListenerFactory.createApplicationListener(RestrictedTransactionalEventListenerFactory.java:52)
    at org.springframework.context.event.EventListenerMethodProcessor.processBean(EventListenerMethodProcessor.java:157)
    ...
```

### 근본 원인

#### Spring Boot 4.x의 명시적 제한

Spring Boot 4.0부터 다음 조합이 **명시적으로 금지**됨:

```java
@TransactionalEventListener + @Async + @Transactional
```

#### 왜 금지했는가?

##### 1. 트랜잭션 전파 모호성

```java
@TransactionalEventListener(phase = AFTER_COMMIT)  // 트랜잭션 A의 커밋 후 실행
@Async                                              // 별도 스레드에서 실행
@Transactional                                      // 새로운 트랜잭션 B 시작
public void handle(Event event) {
    // 어느 트랜잭션에 속하는가?
    // - 트랜잭션 A는 이미 커밋됨
    // - 트랜잭션 B는 새로 시작됨
    // - 혼란 발생!
}
```

##### 2. AFTER_COMMIT의 의미 왜곡

```
@TransactionalEventListener(phase = AFTER_COMMIT)
→ "트랜잭션 커밋 후 실행"

@Transactional
→ "새로운 트랜잭션 시작"

모순: "커밋 후 실행"인데 "새 트랜잭션 시작"?
```

##### 3. 실행 순서 불명확

```
옵션 1:
  트랜잭션 A 커밋 → @Async 스레드 시작 → 트랜잭션 B 시작 → 이벤트 처리

옵션 2:
  트랜잭션 A 커밋 → 트랜잭션 B 시작 → @Async 스레드 시작 → 이벤트 처리

어느 것이 정답?
```

##### 4. 예외 처리 복잡성

```java
@TransactionalEventListener(phase = AFTER_COMMIT)
@Async
@Transactional
public void handle(Event event) {
    // 예외 발생 시:
    // - 트랜잭션 B는 롤백?
    // - 트랜잭션 A는 이미 커밋됨
    // - 데이터 불일치 발생!
}
```

### Spring Boot 3.x vs 4.x

#### Spring Boot 3.x (경고만 발생)

```
WARN: @TransactionalEventListener with @Async and @Transactional may cause unexpected behavior
```

- 런타임 경고만 로깅
- 애플리케이션은 정상 실행
- 개발자 판단에 맡김

#### Spring Boot 4.x (애플리케이션 실행 불가)

```
ERROR: IllegalStateException
```

- 애플리케이션 구동 실패
- 명시적으로 금지
- 안전한 설계 강제

### 시도한 해결 방법들

#### 시도 1: @Transactional만 제거 (실패)

```java
@TransactionalEventListener(phase = AFTER_COMMIT)
@Async
// @Transactional 제거
public void handlePaymentCreated(PaymentCreatedEvent event) {
    Order order = orderRepository.findById(orderId).get();
    order.getOrderItems();  // ❌ LazyInitializationException (문제 3)
}
```

**결과**: 문제 3 발생 (Lazy Loading 실패)

#### 시도 2: @Async만 제거 (요구사항 불충족)

```java
@TransactionalEventListener(phase = AFTER_COMMIT)
// @Async 제거
@Transactional
public void handlePaymentCreated(PaymentCreatedEvent event) {
    paymentGateway.processPayment(payment);  // 3초 소요
}
```

**문제점**:
- 응답 시간 최적화 목표 달성 불가
- PG 호출이 동기적으로 실행됨
- 사용자 대기 시간 증가 (3000ms)

#### 시도 3: @TransactionalEventListener만 제거 (안전성 문제)

```java
@EventListener  // @TransactionalEventListener 대신
@Async
@Transactional
public void handlePaymentCreated(PaymentCreatedEvent event) {
    // ...
}
```

**문제점**:
- 트랜잭션 롤백 시에도 이벤트 발생
- 데이터 불일치 위험

### 최종 해결 방법: @Transactional 제거 + JOIN FETCH

```java
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final OrderRepository orderRepository;  // JOIN FETCH용
    private final InventoryService inventoryService;

    @TransactionalEventListener(phase = AFTER_COMMIT)  // ✅ 유지
    @Async                                              // ✅ 유지
    // @Transactional 제거 ✅
    public void handlePaymentCreated(PaymentCreatedEvent event) {
        try {
            // PG 호출 및 결제 성공 처리
            Payment payment = paymentRepository.findById(event.getPaymentId()).get();
            paymentGateway.processPayment(payment);
            payment.markAsCompleted();
            paymentRepository.save(payment);  // ✅ @Transactional 없어도 save() 동작

            orderService.markAsPaid(event.getOrderId());

        } catch (Exception e) {
            Payment payment = paymentRepository.findById(event.getPaymentId()).get();
            payment.markAsFailed();
            paymentRepository.save(payment);

            // ✅ JOIN FETCH로 Lazy Loading 문제 해결 (문제 3 해결)
            Order order = orderRepository.findByIdWithItems(payment.getOrderId())
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            for (OrderItem item : order.getOrderItems()) {
                inventoryService.release(item.getProductId(), item.getQuantity());
            }
        }
    }
}
```

#### @Transactional 없이도 동작하는 이유

```java
// OrderService.markAsPaid()
@Service
@Transactional  // ← 여기에 트랜잭션 있음
public class OrderService {
    public void markAsPaid(Long orderId) {
        Order order = orderRepository.findById(orderId).get();
        order.markAsPaid();
        orderRepository.save(order);  // ✅ 트랜잭션 내부
    }
}
```

```java
// InventoryService.release()
@Service
@Transactional  // ← 여기에도 트랜잭션 있음
public class InventoryService {
    public void release(Long productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId).get();
        inventory.release(quantity);
        inventoryRepository.save(inventory);  // ✅ 트랜잭션 내부
    }
}
```

**핵심**:
- **EventListener 자체는 트랜잭션 불필요**
- **호출하는 Service 메서드**에 `@Transactional` 있음
- 각 Service 호출마다 **독립적인 트랜잭션** 생성

### 허용되는 조합

| @TransactionalEventListener | @Async | @Transactional | 결과 |
|----------------------------|--------|----------------|------|
| ✅ | ✅ | ❌ | ✅ 허용 (현재 사용) |
| ✅ | ❌ | ✅ | ✅ 허용 (동기 처리) |
| ✅ | ❌ | ❌ | ✅ 허용 |
| ❌ (EventListener) | ✅ | ✅ | ✅ 허용 |
| ✅ | ✅ | ✅ | ❌ **금지** |

### 학습 포인트

1. **Spring Boot 4.x의 엄격한 검증**
   - 모호한 설정 조합을 명시적으로 금지
   - 런타임 오류 대신 구동 시점 검증
   - "안전하지 않으면 실행하지 않음" 철학

2. **트랜잭션 책임 분리**
   - EventListener: 조정(orchestration) 역할
   - Service: 비즈니스 로직 + 트랜잭션 관리
   - 각 계층의 책임 명확히 구분

3. **비동기 + 트랜잭션 설계 원칙**
   - Listener 자체는 가볍게 유지
   - 트랜잭션은 Service 계층에서 관리
   - 데이터 로딩은 JOIN FETCH로 해결

---

## 종합 해결 전략

### 1. 테스트 계층

```java
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentServiceTest {

    @Test
    void processPayment_정상결제_성공() {
        // 1. 즉시 검증 (동기)
        Payment payment = paymentService.processPayment(...);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);

        // 2. 비동기 완료 대기 (Awaitility)
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Payment updated = paymentService.getPayment(payment.getId(), testUserId);
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        });

        await().atMost(5, SECONDS).untilAsserted(() -> {
            Order order = orderService.getOrder(orderId, testUserId);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        });
    }
}
```

**핵심**:
- `@DirtiesContext`로 실제 커밋 보장
- Awaitility로 비동기 완료 대기
- 즉시 검증과 지연 검증 분리

### 2. EventListener 계층

```java
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final InventoryService inventoryService;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handlePaymentCreated(PaymentCreatedEvent event) {
        try {
            // 성공 처리
            Payment payment = paymentRepository.findById(event.getPaymentId()).get();
            paymentGateway.processPayment(payment);
            payment.markAsCompleted();
            paymentRepository.save(payment);

            // 직접 호출 (이벤트 체인 X)
            orderService.markAsPaid(event.getOrderId());

        } catch (Exception e) {
            // 실패 처리
            Payment payment = paymentRepository.findById(event.getPaymentId()).get();
            payment.markAsFailed();
            paymentRepository.save(payment);

            // JOIN FETCH로 Lazy Loading 방지
            Order order = orderRepository.findByIdWithItems(payment.getOrderId()).get();
            for (OrderItem item : order.getOrderItems()) {
                inventoryService.release(item.getProductId(), item.getQuantity());
            }
        }
    }
}
```

**핵심**:
- `@Transactional` 제거 (Spring Boot 4.x 제약)
- 이벤트 체인 제거 (직접 Service 호출)
- JOIN FETCH로 세션 독립성 확보

### 3. Repository 계층

```java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // 기본 조회 (Lazy)
    Optional<Order> findById(Long id);

    // 비동기용 조회 (Eager)
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);
}
```

**핵심**:
- 기본 LAZY 유지
- 비동기 컨텍스트용 명시적 메서드 추가
- 메서드 이름으로 의도 표현

---

## 테스트 결과

### 최종 성공

```
PaymentServiceTest: 20개 테스트 모두 통과 ✅
RefundServiceTest: 18개 테스트 모두 통과 ✅

총 38개 테스트 성공
실행 시간: 약 1분 4초
```

### 성능 개선

| 지표 | Before | After | 개선율 |
|------|--------|-------|--------|
| 응답 시간 | 3000ms | 100ms | **96.7%** |
| PG 호출 | 동기 | 비동기 | - |
| 사용자 체감 | 3초 대기 | 즉시 응답 | **30배** |

---

## 참고 자료

### Spring 공식 문서

- [Transaction Management - Event Listeners](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
- [Async Method Execution](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-async)

### 관련 문서

- `docs/ASYNC_CONFIG.md` - ThreadPoolTaskExecutor 설정 가이드
- `docs/ARCHITECTURE.md` - 시스템 아키텍처 및 이벤트 설계
- `CLAUDE.md` - Awaitility 의존성 추가 기록

---

## 요약

| 문제 | 원인 | 해결 |
|------|------|------|
| 1. 이벤트 미발생 | `@Transactional` 롤백 | `@DirtiesContext` |
| 2. 이벤트 미전파 | `@Async` 스레드 격리 | 이벤트 체인 제거 |
| 3. Lazy Loading | Hibernate Session 부재 | JOIN FETCH |
| 4. 어노테이션 충돌 | Spring Boot 4.x 제약 | `@Transactional` 제거 |

**핵심 교훈**:
- 비동기 처리는 **간단한 설계**가 안전함
- 트랜잭션은 **Service 계층**에서 관리
- 테스트는 **실제 커밋**이 필요함
- Spring Boot 4.x는 **모호한 설정을 거부**함
