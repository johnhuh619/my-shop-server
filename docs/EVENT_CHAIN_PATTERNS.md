# 이벤트 체인 패턴 가이드

## 개요

Spring Event를 사용할 때 **이벤트 체인**(Event Chain)이 적절한 패턴인지, 아니면 지양해야 할 패턴인지에 대한 가이드입니다.

**결론 먼저**: 이벤트 체인은 **절대적으로 나쁜 패턴이 아니지만**, **비동기 처리에서는 문제가 됩니다**.

---

## 이벤트 체인이란?

### 정의

이벤트 리스너 내부에서 또 다른 이벤트를 발행하는 패턴:

```java
Service
  └─ Event A 발행
       └─ Listener A
            └─ Event B 발행  ← 이벤트 체인
                 └─ Listener B
                      └─ Event C 발행  ← 다단계 체인
                           └─ Listener C
```

### 예시

```java
@Service
public class OrderService {
    public Order createOrder(...) {
        Order order = Order.create(...);
        orderRepository.save(order);

        // 1단계: OrderCreatedEvent 발행
        eventPublisher.publishEvent(OrderCreatedEvent.from(order));

        return order;
    }
}

@Component
public class OrderEventListener {
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 주문 처리 로직
        Order order = orderRepository.findById(event.getOrderId()).get();
        order.markAsConfirmed();

        // 2단계: OrderConfirmedEvent 발행 (이벤트 체인)
        eventPublisher.publishEvent(OrderConfirmedEvent.from(order));
    }
}

@Component
public class PaymentEventListener {
    @EventListener
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        // 결제 시작
        Payment payment = paymentService.createPayment(event.getOrderId());

        // 3단계: PaymentCreatedEvent 발행 (이벤트 체인)
        eventPublisher.publishEvent(PaymentCreatedEvent.from(payment));
    }
}
```

---

## 우리 프로젝트에서 겪은 문제

### 실패한 비동기 이벤트 체인

#### 시도한 패턴

```java
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final ApplicationEventPublisher eventPublisher;
    private final OrderService orderService;
    private final InventoryService inventoryService;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handlePaymentCreated(PaymentCreatedEvent event) {
        try {
            // PG 호출 (3초 소요)
            Payment payment = paymentRepository.findById(event.getPaymentId()).get();
            paymentGateway.processPayment(payment);
            payment.markAsCompleted();
            paymentRepository.save(payment);

            // ❌ 이벤트 체인 시도 - 전파되지 않음!
            eventPublisher.publishEvent(PaymentCompletedEvent.from(payment));

        } catch (Exception e) {
            payment.markAsFailed();
            paymentRepository.save(payment);

            // ❌ 이 이벤트도 전파되지 않음!
            eventPublisher.publishEvent(PaymentFailedEvent.from(payment));
        }
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        // ❌ 절대 실행되지 않음
        orderService.markAsPaid(event.getOrderId());
        log.info("Order marked as PAID: {}", event.getOrderId());
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handlePaymentFailed(PaymentFailedEvent event) {
        // ❌ 절대 실행되지 않음
        for (OrderItemSnapshot item : event.getOrderItems()) {
            inventoryService.release(item.productId(), item.quantity());
        }
    }
}
```

#### 증상

- `PaymentCompletedEvent` 발행 후 `handlePaymentCompleted()` 실행 안됨
- `PaymentFailedEvent` 발행 후 `handlePaymentFailed()` 실행 안됨
- 로그에 에러 없음 (조용히 실패)
- Order 상태가 `CREATED`에서 `PAID`로 변경 안됨

#### 실행 흐름

```
[main thread]
  PaymentService.processPayment()
    └─ eventPublisher.publishEvent(PaymentCreatedEvent)
         │
         │ 트랜잭션 커밋
         ▼
[event-1 thread]  ← @Async로 별도 스레드
  handlePaymentCreated()
    ├─ paymentGateway.processPayment()  ✅ 정상 실행
    ├─ payment.markAsCompleted()  ✅ 정상 실행
    └─ eventPublisher.publishEvent(PaymentCompletedEvent)  ❌ 전파 안됨
         │
         ▼ AFTER_COMMIT 대기?
         ❌ 트랜잭션 컨텍스트 없음
         ❌ handlePaymentCompleted() 실행 안됨
```

#### 근본 원인

1. **트랜잭션 컨텍스트 부재**
   ```java
   @TransactionalEventListener(phase = AFTER_COMMIT)
   ```
   - `AFTER_COMMIT`은 **트랜잭션 커밋 후** 이벤트 처리
   - `@Async` 메서드는 **별도 스레드**에서 실행
   - 별도 스레드에는 **트랜잭션 컨텍스트가 없음**
   - 커밋을 기다릴 트랜잭션이 없음 → 이벤트 미발생

2. **스레드 격리**
   ```
   [main thread - TX O]
     └─ Event A 발행 ✅

   [event-1 thread - TX X]  ← @Async
     └─ Event B 발행 ❌ (TX 없어서 AFTER_COMMIT 조건 불충족)
   ```

---

## 이벤트 체인이 문제가 되는 경우

### 1. 비동기 + @TransactionalEventListener 조합

```java
// ❌ 안티패턴
@TransactionalEventListener(phase = AFTER_COMMIT)
@Async
public void handleEventA(EventA event) {
    // 비즈니스 로직

    // 다음 이벤트 발행 - 전파 안됨!
    eventPublisher.publishEvent(EventB);
}
```

**문제점**:
- 트랜잭션 컨텍스트 손실
- 이벤트 전파 실패
- 조용히 실패 (로그 없음)
- 디버깅 매우 어려움

**데이터 흐름**:
```
Service (TX O)
  └─ Event A 발행 ✅
       └─ Listener A (@Async, TX X)
            └─ Event B 발행 ❌  ← 전파 안됨
                 └─ Listener B  ❌  ← 실행 안됨
```

### 2. 순환 참조 위험

```java
// ❌ 위험한 패턴
@Component
public class OrderEventListener {
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        // Payment 이벤트 발행
        eventPublisher.publishEvent(PaymentRequestedEvent.from(event));
    }
}

@Component
public class PaymentEventListener {
    @EventListener
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        // Order 이벤트 발행
        eventPublisher.publishEvent(OrderStatusChangedEvent.from(event));
    }
}

@Component
public class OrderStatusEventListener {
    @EventListener
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        // 또 다른 Payment 이벤트 발행?
        if (event.getStatus() == OrderStatus.PAID) {
            eventPublisher.publishEvent(PaymentVerificationEvent.from(event));
        }
    }
}
```

**문제점**:
- 이벤트 체인이 길어질수록 순환 참조 위험 증가
- 실행 흐름 추적 어려움
- 예상치 못한 무한 루프 가능성

### 3. 실행 순서 보장 안됨

```java
// ❌ 순서에 의존하는 로직
@Async
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    // 두 이벤트의 처리 순서를 보장할 수 없음
    eventPublisher.publishEvent(OrderStatusChangedEvent.from(event));   // 먼저?
    eventPublisher.publishEvent(InventoryReleasedEvent.from(event));    // 나중?
}

// 두 리스너 중 어느 것이 먼저 실행될지 불확실
@Async
public void handleOrderStatusChanged(...) { }

@Async
public void handleInventoryReleased(...) { }
```

**문제점**:
- `@Async` 이벤트는 처리 순서 보장 안됨
- 순서 의존적 로직에서 버그 발생 위험
- 경합 조건(race condition) 가능성

### 4. 디버깅 복잡성

```java
// ❌ 3단계 이벤트 체인
OrderService
  └─ OrderCreatedEvent
       └─ PaymentEventListener
            └─ PaymentRequestedEvent
                 └─ PGEventListener
                      └─ PGCallCompletedEvent
                           └─ OrderEventListener
```

**문제점**:
- 로그를 여러 스레드에 걸쳐 추적해야 함
- 스택 트레이스가 끊김 (비동기)
- 어느 단계에서 실패했는지 파악 어려움

---

## 이벤트 체인이 괜찮은 경우

### 1. 동기 이벤트 체인 (같은 트랜잭션)

```java
// ✅ 괜찮은 패턴: 동기 처리
@Service
@Transactional
public class OrderService {
    public Order createOrder(...) {
        Order order = Order.create(...);
        orderRepository.save(order);

        // 동기 이벤트 발행
        eventPublisher.publishEvent(OrderCreatedEvent.from(order));

        return order;
    }
}

@Component
@RequiredArgsConstructor
public class OrderEventListener {

    @TransactionalEventListener(phase = BEFORE_COMMIT)  // 동기
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 같은 트랜잭션 내부
        Order order = orderRepository.findById(event.getOrderId()).get();
        order.markAsConfirmed();

        // ✅ 이벤트 체인 가능 (같은 트랜잭션 컨텍스트)
        eventPublisher.publishEvent(OrderConfirmedEvent.from(order));
    }

    @TransactionalEventListener(phase = BEFORE_COMMIT)  // 동기
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        // ✅ 정상 실행됨
        log.info("Order confirmed: {}", event.getOrderId());
        notificationService.sendOrderConfirmation(event.getOrderId());
    }
}
```

**장점**:
- 같은 트랜잭션 컨텍스트 공유
- 이벤트 전파 정상 동작
- 롤백 시 모든 이벤트 처리 취소 (일관성)
- 실행 순서 보장

**단점**:
- 응답 시간 증가 (모든 처리가 동기)
- 트랜잭션이 길어짐
- 성능 최적화 제한적

**적용 시나리오**:
- 트랜잭션 일관성이 중요한 경우
- 모든 처리가 빠르게 완료되는 경우
- 응답 시간보다 정확성이 중요한 경우

### 2. 도메인 간 결합도 감소 (1단계만)

```java
// ✅ 좋은 패턴: 1단계 이벤트 + 여러 독립적 리스너
@Service
@Transactional
public class OrderService {
    public Order createOrder(...) {
        Order order = Order.create(...);
        orderRepository.save(order);

        // 1단계 이벤트 발행
        eventPublisher.publishEvent(OrderCreatedEvent.from(order));

        return order;
    }
}

// 여러 도메인에서 독립적으로 처리 (추가 이벤트 발행 안함)
@Component
public class InventoryEventListener {
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 재고 예약 (이벤트 체인 없음)
        for (OrderItemSnapshot item : event.getOrderItems()) {
            inventoryService.reserve(item.productId(), item.quantity());
        }
    }
}

@Component
public class NotificationEventListener {
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 알림 전송 (이벤트 체인 없음)
        notificationService.sendOrderCreatedNotification(event.getUserId());
    }
}

@Component
public class AnalyticsEventListener {
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 분석 데이터 수집 (이벤트 체인 없음)
        analyticsService.trackOrderCreated(event);
    }
}
```

**구조**:
```
OrderService
  └─ OrderCreatedEvent
       ├─ InventoryEventListener  (독립적)
       ├─ NotificationEventListener  (독립적)
       └─ AnalyticsEventListener  (독립적)

각 리스너는 추가 이벤트 발행 안함 (1단계만 유지)
```

**장점**:
- OrderService는 Inventory, Notification, Analytics를 모름 (낮은 결합도)
- 새로운 리스너 추가 시 OrderService 수정 불필요
- 각 리스너의 실패가 다른 리스너에 영향 없음
- 관심사의 분리(Separation of Concerns)

**핵심**: 이벤트 깊이를 **1단계**로 제한

### 3. 명확한 인과관계 (비즈니스 흐름)

```java
// ✅ 괜찮은 패턴: 명확한 비즈니스 흐름
@Component
public class OrderEventListener {

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 주문 생성 → 결제 시작 (명확한 인과관계)
        Payment payment = paymentService.createPayment(
            event.getUserId(),
            event.getOrderId(),
            event.getTotalAmount()
        );

        // 다음 단계 이벤트 발행
        eventPublisher.publishEvent(PaymentCreatedEvent.from(payment));
    }
}

@Component
public class PaymentEventListener {

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    public void handlePaymentCreated(PaymentCreatedEvent event) {
        // 결제 생성 → PG 호출 준비 (명확한 인과관계)
        log.info("Payment created, ready for PG processing: {}", event.getPaymentId());

        // 다음 단계는 AFTER_COMMIT에서 비동기 처리
        // (여기서는 이벤트 체인 종료)
    }
}
```

**조건**:
- 비즈니스 흐름이 명확함 (주문 → 결제 → PG 호출)
- 각 단계가 논리적으로 순차적
- 트랜잭션 일관성 필요

---

## 우리 프로젝트의 해결책

### 비동기 처리 + 직접 호출

```java
// ✅ 현재 채택한 패턴
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    // eventPublisher 제거 (이벤트 체인 사용 안함)

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handlePaymentCreated(PaymentCreatedEvent event) {
        log.info("[{}] Processing PaymentCreatedEvent: paymentId={}, orderId={}",
                Thread.currentThread().getName(), event.getPaymentId(), event.getOrderId());

        try {
            // 1. Payment 재조회
            Payment payment = paymentRepository.findById(event.getPaymentId())
                    .orElseThrow(() -> new RuntimeException("Payment not found"));

            // 2. 외부 PG 호출 (3초 소요)
            paymentGateway.processPayment(payment);
            log.info("PG payment processed successfully: paymentId={}", event.getPaymentId());

            // 3. 결제 성공 처리
            payment.markAsCompleted();
            paymentRepository.save(payment);

            // 4. Order 상태 직접 업데이트 ✅ (이벤트 대신 직접 호출)
            orderService.markAsPaid(event.getOrderId());
            log.info("Order marked as PAID: orderId={}", event.getOrderId());

        } catch (Exception e) {
            log.error("PG payment failed: paymentId={}, error={}",
                    event.getPaymentId(), e.getMessage(), e);

            try {
                // 1. Payment 재조회
                Payment payment = paymentRepository.findById(event.getPaymentId())
                        .orElseThrow(() -> new RuntimeException("Payment not found"));

                // 2. 결제 실패 처리
                payment.markAsFailed();
                paymentRepository.save(payment);

                // 3. OrderItems 조회 및 재고 해제 ✅ (이벤트 대신 직접 호출)
                Order order = orderRepository.findByIdWithItems(payment.getOrderId())
                        .orElseThrow(() -> new RuntimeException("Order not found"));

                for (OrderItem item : order.getOrderItems()) {
                    inventoryService.release(item.getProductId(), item.getQuantity());
                    log.info("Inventory released: productId={}, quantity={}",
                            item.getProductId(), item.getQuantity());
                }

            } catch (Exception failureHandlingError) {
                log.error("Failed to handle payment failure: paymentId={}, error={}",
                        event.getPaymentId(), failureHandlingError.getMessage(), failureHandlingError);
            }
        }
    }
}
```

### 아키텍처 변경

**Before (이벤트 체인 - 실패)**:
```
PaymentService
  └─ PaymentCreatedEvent 발행
       └─ handlePaymentCreated() [@Async]
            ├─ PaymentCompletedEvent 발행  ❌ 전파 안됨
            │    └─ handlePaymentCompleted()  ❌ 실행 안됨
            │         └─ orderService.markAsPaid()
            │
            └─ PaymentFailedEvent 발행  ❌ 전파 안됨
                 └─ handlePaymentFailed()  ❌ 실행 안됨
                      └─ inventoryService.release()
```

**After (직접 호출 - 성공)**:
```
PaymentService
  └─ PaymentCreatedEvent 발행
       └─ handlePaymentCreated() [@Async]
            ├─ orderService.markAsPaid()  ✅ 직접 호출
            └─ inventoryService.release()  ✅ 직접 호출
```

### 장점

1. **명확한 실행 흐름**
   - 코드를 읽으면 실행 순서가 명확함
   - 디버깅 용이 (단일 메서드 내부)
   - 스택 트레이스 추적 가능

2. **비동기 처리 가능**
   - 응답 시간 최적화 (3000ms → 100ms)
   - PG 호출을 백그라운드 처리

3. **트랜잭션 독립성**
   - 각 Service 호출이 독립적 트랜잭션
   - 부분 실패 처리 가능

4. **안정성**
   - 이벤트 전파 실패 위험 없음
   - Spring Boot 4.x 제약 회피

### 단점

1. **결합도 증가**
   - PaymentEventListener가 OrderService, InventoryService에 의존
   - 도메인 경계가 약간 흐려짐

2. **확장성 제한**
   - 새로운 처리 추가 시 리스너 수정 필요
   - 이벤트 방식보다 유연성 낮음

### 트레이드오프 결정

**우리의 선택**: 결합도 증가 < 명확성 + 안정성

---

## 설계 원칙

### 1. 이벤트는 1단계 깊이 유지

```java
// ✅ Good: 1단계 이벤트
Service
  └─ Event A
       ├─ Listener 1 (독립적, 추가 이벤트 발행 안함)
       ├─ Listener 2 (독립적, 추가 이벤트 발행 안함)
       └─ Listener 3 (독립적, 추가 이벤트 발행 안함)

// ❌ Bad: 다단계 이벤트 체인
Service
  └─ Event A
       └─ Listener A
            └─ Event B
                 └─ Listener B
                      └─ Event C
```

**이유**:
- 디버깅 복잡도 급증
- 실행 흐름 추적 어려움
- 순환 참조 위험

### 2. 비동기면 직접 호출, 동기면 이벤트 고려

```java
// 비동기 처리 (성능 중요)
@TransactionalEventListener(phase = AFTER_COMMIT)
@Async
public void handleEvent(Event event) {
    // ✅ 직접 호출 (명확함)
    service1.doSomething();
    service2.doSomething();

    // ❌ 이벤트 체인 (전파 안됨)
    // eventPublisher.publishEvent(NextEvent);
}

// 동기 처리 (결합도 감소 중요)
@TransactionalEventListener(phase = BEFORE_COMMIT)
public void handleEvent(Event event) {
    // ✅ 이벤트 체인 가능 (같은 트랜잭션)
    eventPublisher.publishEvent(NextEvent);

    // ✅ 직접 호출도 가능
    // service.doSomething();
}
```

**판단 기준**:
- **비동기**: 성능 > 결합도 → 직접 호출
- **동기**: 결합도 > 성능 → 이벤트 고려

### 3. 명확성 > 이론적 순수성

```java
// ❌ 이론적으로 "아름다운" but 실무에서 "지옥"
@Async
public void handleEventA(EventA event) {
    eventPublisher.publishEvent(EventB);  // 전파 안됨 (버그)
}

// ✅ 단순하고 "명확한" (동작 보장)
@Async
public void handleEventA(EventA event) {
    service.doSomething();  // 직접 호출
}
```

**원칙**: "작동하지 않는 우아한 코드"보다 "작동하는 단순한 코드"

### 4. 트랜잭션 경계 명확히

```java
// ✅ 각 Service가 트랜잭션 관리
@Service
@Transactional
public class OrderService {
    public void markAsPaid(Long orderId) {
        // 독립적 트랜잭션
    }
}

@Service
@Transactional
public class InventoryService {
    public void release(Long productId, int quantity) {
        // 독립적 트랜잭션
    }
}

// EventListener는 조정(orchestration)만
@Component
public class PaymentEventListener {

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    // @Transactional 없음 (Service 계층에 위임)
    public void handlePaymentCreated(PaymentCreatedEvent event) {
        orderService.markAsPaid(...);      // 트랜잭션 1
        inventoryService.release(...);      // 트랜잭션 2
    }
}
```

**책임 분리**:
- EventListener: 조정 (어떤 작업을 할지 결정)
- Service: 비즈니스 로직 + 트랜잭션 관리

---

## 실제 사례 비교

### 시나리오: 결제 성공 시 Order 상태 변경 + 알림 전송

#### 방식 1: 이벤트 체인 (동기)

```java
@Component
public class PaymentEventListener {

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        Order order = orderService.markAsPaid(event.getOrderId());

        // 이벤트 체인
        eventPublisher.publishEvent(OrderPaidEvent.from(order));
    }
}

@Component
public class OrderEventListener {

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    public void handleOrderPaid(OrderPaidEvent event) {
        notificationService.sendOrderPaidNotification(event.getOrderId());
    }
}
```

**장점**:
- 도메인 분리 (Payment → Order → Notification)
- 트랜잭션 일관성 (롤백 시 모두 취소)

**단점**:
- 응답 시간 증가 (모든 처리가 동기)
- 트랜잭션이 길어짐 (DB 락 유지 시간 증가)
- 알림 전송 실패 시 전체 트랜잭션 롤백

**측정**:
```
응답 시간: 약 3.5초 (PG 3초 + 알림 0.5초)
사용자 대기: 3.5초 (동기 처리)
```

#### 방식 2: 직접 호출 (비동기)

```java
@Component
public class PaymentEventListener {

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        // 직접 호출 (이벤트 체인 없음)
        orderService.markAsPaid(event.getOrderId());
        notificationService.sendOrderPaidNotification(event.getOrderId());
    }
}
```

**장점**:
- 명확한 실행 흐름
- 비동기 처리 (응답 시간 단축)
- 디버깅 용이

**단점**:
- 결합도 증가 (PaymentEventListener가 OrderService, NotificationService 의존)
- 도메인 경계 약간 흐려짐

**측정**:
```
응답 시간: 약 100ms (비동기 처리)
사용자 대기: 100ms
백그라운드 처리: PG 3초 + Order 업데이트 + 알림 0.5초
```

#### 방식 3: 하이브리드 (1단계 이벤트 + 여러 리스너)

```java
@Component
public class PaymentEventListener {

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        // Order 상태 변경만 처리
        orderService.markAsPaid(event.getOrderId());
    }
}

@Component
public class NotificationEventListener {

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        // 알림 전송만 처리 (원본 이벤트 직접 처리)
        notificationService.sendPaymentCompletedNotification(event.getOrderId());
    }
}
```

**장점**:
- 결합도 감소 (각 리스너 독립적)
- 비동기 처리 (성능 최적화)
- 확장 용이 (새 리스너 추가 시 기존 코드 수정 불필요)

**단점**:
- 같은 이벤트를 여러 리스너가 처리 (중복 코드 가능성)
- 리스너 간 실행 순서 보장 안됨

**측정**:
```
응답 시간: 약 100ms
사용자 대기: 100ms
백그라운드 처리: Order 업데이트 + 알림 전송 (병렬)
```

#### 비교표

| 구분 | 이벤트 체인 (동기) | 직접 호출 (비동기) | 하이브리드 (1단계) |
|------|-------------------|-------------------|-------------------|
| **응답 시간** | 3500ms | 100ms | 100ms |
| **결합도** | 낮음 | 높음 | 낮음 |
| **디버깅** | 어려움 | 쉬움 | 중간 |
| **확장성** | 높음 | 낮음 | 높음 |
| **트랜잭션 일관성** | 높음 | 중간 | 중간 |
| **코드 명확성** | 낮음 | 높음 | 중간 |

---

## 이벤트 체인 사용 가이드

### ✅ 사용해도 좋은 경우

#### 조건 1: 동기 처리 + 같은 트랜잭션

```java
@TransactionalEventListener(phase = BEFORE_COMMIT)  // 동기
public void handleEvent(Event event) {
    // 비즈니스 로직

    eventPublisher.publishEvent(NextEvent);  // ✅ OK
}
```

**체크리스트**:
- [ ] `@Async` 없음 (동기 처리)
- [ ] `phase = BEFORE_COMMIT` (같은 트랜잭션)
- [ ] 응답 시간 요구사항 충족
- [ ] 트랜잭션 일관성 중요

#### 조건 2: 1단계 깊이 + 독립적 리스너들

```java
// ✅ OK
Service
  └─ Event A
       ├─ Listener 1 (추가 이벤트 발행 안함)
       ├─ Listener 2 (추가 이벤트 발행 안함)
       └─ Listener 3 (추가 이벤트 발행 안함)
```

**체크리스트**:
- [ ] 이벤트 깊이 1단계만
- [ ] 각 리스너 독립적 (서로 의존 없음)
- [ ] 리스너가 추가 이벤트 발행 안함

#### 조건 3: 명확한 비즈니스 흐름

```java
// ✅ OK: 주문 생성 → 결제 시작 (명확한 인과관계)
@TransactionalEventListener(phase = BEFORE_COMMIT)
public void handleOrderCreated(OrderCreatedEvent event) {
    Payment payment = paymentService.createPayment(...);
    eventPublisher.publishEvent(PaymentCreatedEvent.from(payment));
}
```

**체크리스트**:
- [ ] 비즈니스 흐름이 명확함
- [ ] 각 단계가 논리적으로 순차적
- [ ] 도메인 경계 명확
- [ ] 트랜잭션 일관성 필요

### ❌ 피해야 하는 경우

#### 상황 1: 비동기 + @TransactionalEventListener

```java
// ❌ 금지
@TransactionalEventListener(phase = AFTER_COMMIT)
@Async
public void handleEvent(Event event) {
    eventPublisher.publishEvent(NextEvent);  // 전파 안됨!
}
```

**이유**: 트랜잭션 컨텍스트 부재로 이벤트 전파 실패

#### 상황 2: 3단계 이상 깊이

```java
// ❌ 지양
Service
  └─ Event A
       └─ Listener A
            └─ Event B
                 └─ Listener B
                      └─ Event C  ← 너무 깊음
```

**이유**: 디버깅 복잡, 실행 흐름 추적 어려움

#### 상황 3: 순환 참조 가능성

```java
// ❌ 위험
OrderEventListener → PaymentEvent
PaymentEventListener → OrderEvent  ← 순환 위험
```

**이유**: 무한 루프, 예측 불가능한 동작

#### 상황 4: 순서 의존적 로직

```java
// ❌ 지양
@Async
public void handleEvent(Event event) {
    eventPublisher.publishEvent(Event1);  // 먼저?
    eventPublisher.publishEvent(Event2);  // 나중?
}
```

**이유**: 비동기 처리는 순서 보장 안됨

---

## 의사결정 플로우차트

```
이벤트 체인 사용 검토
    │
    ├─ 비동기 처리가 필요한가?
    │   ├─ Yes → ❌ 이벤트 체인 지양, 직접 호출 사용
    │   └─ No → 다음 단계
    │
    ├─ 트랜잭션 일관성이 중요한가?
    │   ├─ Yes → ✅ 이벤트 체인 고려 (동기, BEFORE_COMMIT)
    │   └─ No → 다음 단계
    │
    ├─ 도메인 분리가 중요한가?
    │   ├─ Yes → ✅ 1단계 이벤트 사용 (여러 독립 리스너)
    │   └─ No → 직접 호출 고려
    │
    └─ 이벤트 체인 깊이가 1단계인가?
        ├─ Yes → ✅ 이벤트 체인 사용 가능
        └─ No → ❌ 이벤트 체인 지양, 리팩토링 필요
```

---

## 결론

### 핵심 원칙

1. **비동기 처리에서는 이벤트 체인 지양**
   - 트랜잭션 컨텍스트 부재로 전파 실패
   - 직접 호출이 명확하고 안전

2. **이벤트 깊이는 1단계로 제한**
   - Service → Event → [독립적 리스너들]
   - 리스너에서 추가 이벤트 발행 최소화

3. **명확성과 단순성 우선**
   - "작동하지 않는 우아한 코드" < "작동하는 단순한 코드"
   - 디버깅 가능성 중요

4. **트랜잭션 경계 명확히**
   - EventListener: 조정(orchestration)
   - Service: 비즈니스 로직 + 트랜잭션

### 우리 프로젝트 선택

| 요구사항 | 선택 | 이유 |
|---------|------|------|
| 응답 시간 최적화 | 비동기 처리 | 3000ms → 100ms |
| 명확한 실행 흐름 | 직접 호출 | 디버깅 용이 |
| 안정성 | 이벤트 체인 제거 | 전파 실패 방지 |

**결론**: **1단계 이벤트 + 직접 호출** 패턴 채택

### 참고 문서

- `docs/ASYNC_TEST_PROBLEMS.md` - 비동기 이벤트 테스트 문제 해결
- `docs/ASYNC_CONFIG.md` - ThreadPoolTaskExecutor 설정 가이드
- `docs/ARCHITECTURE.md` - 시스템 아키텍처 및 이벤트 설계
- `CLAUDE.md` - 프로젝트 개발 가이드

---

## 요약표

| 패턴 | 동기/비동기 | 이벤트 체인 | 권장 여부 |
|------|------------|-----------|----------|
| 동기 + 이벤트 체인 (1단계) | 동기 | 1단계 | ✅ 상황에 따라 OK |
| 동기 + 이벤트 체인 (다단계) | 동기 | 2단계 이상 | ⚠️ 복잡도 증가 |
| 비동기 + 이벤트 체인 | 비동기 | 임의 | ❌ 전파 실패 |
| 비동기 + 직접 호출 | 비동기 | 없음 | ✅ 권장 |
| 1단계 이벤트 + 독립 리스너 | 비동기 | 1단계만 | ✅ 권장 |

**최종 권장사항**:
- 비동기 처리가 필요하면 **이벤트 체인을 사용하지 말고 직접 호출**
- 도메인 분리가 중요하면 **1단계 이벤트 + 여러 독립적 리스너**
- 트랜잭션 일관성이 중요하면 **동기 처리 + 이벤트 체인** 고려
