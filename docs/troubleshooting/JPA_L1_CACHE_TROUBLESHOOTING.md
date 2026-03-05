# JPA L1 캐시(영속성 컨텍스트) 트러블슈팅

## 배경: L1 캐시란?

JPA의 영속성 컨텍스트(Persistence Context)는 **1차 캐시(L1 Cache)** 를 내장하고 있다.
한 트랜잭션 안에서 같은 엔티티를 두 번 조회하면, 두 번째 조회는 DB를 거치지 않고 1차 캐시에서 반환된다.

이것은 **엔티티 동일성 보장(Identity Guarantee)** 이라는 JPA 명세에 의한 동작이다.

```
@Transactional
void someMethod() {
    Order a = orderRepository.findById(1L);  // DB 조회 → L1 캐시 저장
    Order b = orderRepository.findById(1L);  // L1 캐시에서 반환
    assert a == b;  // true (동일 객체)
}
```

### 핵심 문제

`PESSIMISTIC_WRITE` (SELECT FOR UPDATE) 쿼리도 L1 캐시 동일성 보장을 깨지 못한다.

```
@Transactional
void dangerous() {
    Order order = orderRepository.findById(1L);       // L1 캐시: status=CREATED
    
    // 다른 스레드가 order를 PAID로 변경하고 커밋
    
    Order locked = orderRepository.findByIdWithLock(1L); // SQL FOR UPDATE 실행,
                                                          // DB 락 획득됨.
                                                          // BUT L1 캐시에서 반환!
    locked.getStatus();  // CREATED (stale!) ← DB에는 PAID
}
```

FOR UPDATE 쿼리는 **DB 레벨의 락은 정상 획득**하지만, Hibernate는 이미 영속성 컨텍스트에
같은 ID의 엔티티가 있으면 **DB에서 읽은 최신 데이터를 버리고 캐시된 엔티티를 반환**한다.

---

## Case 1: OrderExpirationScheduler (수정 완료)

### 증상

결제 완료된 주문(`PAID`)이 만료 스케줄러에 의해 `EXPIRED`로 변경되고,
`confirm()`으로 확정된 재고가 `release()`로 잘못 해제됨.

### 원인

**수정 전 코드** (`OrderExpirationScheduler.java`):
```java
@Scheduled(fixedRate = 60000)
@Transactional                          // ← 문제: 외부 트랜잭션
public void expireOrders() {
    List<Order> expiredOrders = orderRepository
            .findByStatusAndCreatedAtBefore(OrderStatus.CREATED, expirationTime);
    //      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    //      Order(id=1, CREATED) → L1 캐시에 로드

    for (Order order : expiredOrders) {
        orderService.expireOrder(order.getId());
        //          ^^^^^^^^^^^
        //          REQUIRED 전파 → 외부 트랜잭션에 합류 → 같은 L1 캐시 공유
    }
}
```

**`OrderService.expireOrder()`** 내부:
```java
@Transactional  // REQUIRED(기본) → 외부 트랜잭션에 합류
public void expireOrder(Long orderId) {
    Order order = orderRepository.findByIdWithLock(orderId);
    //            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    //            SELECT FOR UPDATE 실행, DB 락 획득
    //            BUT L1 캐시에서 CREATED 상태 반환! (실제 DB는 PAID)

    if (order.getStatus() != OrderStatus.CREATED) {
        return;     // ← CREATED로 보이므로 이 가드를 통과!
    }

    // 아래 로직이 실행되어 PAID 주문을 잘못 만료시킴
    inventoryService.release(...);  // confirm된 재고를 해제 → 재고 정합성 파괴
    order.expire();
}
```

**재현 타임라인**:
```
시간  스케줄러 스레드                    결제 완료 스레드
─────────────────────────────────────────────────────────────
T1    findByStatusAndCreatedAtBefore()
      → Order(1, CREATED) L1 캐시 로드
T2                                       markAsPaid(1)
                                         → Order(1) PAID로 변경, 커밋
T3    expireOrder(1)
      → findByIdWithLock(1)
      → DB에서 PAID 읽음, FOR UPDATE 락 획득
      → BUT L1 캐시에서 CREATED 반환
T4    status != CREATED? → false (stale!)
      → expire() 실행 → PAID 주문이 EXPIRED 됨
      → release() 실행 → 확정된 재고 해제됨
```

### 수정

`expireOrders()`에서 `@Transactional` 제거. 목록 조회를 별도 `readOnly` 트랜잭션으로 분리하고,
ID 목록만 추출하여 각 `expireOrder()`가 독립 트랜잭션(= 독립 영속성 컨텍스트)에서 실행되도록 변경.

```java
@Scheduled(fixedRate = 60000)
// @Transactional 없음! → expireOrder()가 자체 트랜잭션 사용
public void expireOrders() {
    List<Long> expiredOrderIds = findExpiredOrderIds();

    for (Long orderId : expiredOrderIds) {
        try {
            orderService.expireOrder(orderId);
            // expireOrder()의 @Transactional → 새 영속성 컨텍스트 생성
            // → findByIdWithLock()이 DB에서 최신 상태를 읽음
        } catch (Exception e) {
            log.error("Failed to expire order: orderId={}, error={}",
                    orderId, e.getMessage(), e);
        }
    }
}

@Transactional(readOnly = true)
public List<Long> findExpiredOrderIds() {
    Instant expirationTime = Instant.now()
            .minus(EXPIRATION_MINUTES, ChronoUnit.MINUTES);
    return orderRepository
            .findByStatusAndCreatedAtBefore(OrderStatus.CREATED, expirationTime)
            .stream()
            .map(Order::getId)
            .toList();
}
```

**수정 후 타임라인**:
```
시간  스케줄러 스레드                    결제 완료 스레드
─────────────────────────────────────────────────────────────
T1    findExpiredOrderIds() [TX-A]
      → Order(1) ID만 추출, TX-A 커밋
      → 영속성 컨텍스트 종료, L1 캐시 소멸
T2                                       markAsPaid(1)
                                         → Order(1) PAID로 변경, 커밋
T3    expireOrder(1) [TX-B: 새 트랜잭션]
      → findByIdWithLock(1)
      → 새 영속성 컨텍스트 → DB에서 PAID 읽음
T4    status != CREATED? → true
      → return (안전하게 스킵)
```

---

## Case 2: PaymentConfirmHandler.waitForCompletion (올바른 해결)

### 패턴

결제 확인 대기 중 폴링할 때, 같은 `EntityManager`의 L1 캐시가 DB 변경사항을 가린다.

**해결 코드** (`PaymentConfirmHandler.java:142-172`):
```java
Payment waitForCompletion(Long userId, String tossOrderId) {
    long deadline = System.currentTimeMillis() + CONFIRM_WAIT_TIMEOUT_MILLIS;

    while (System.currentTimeMillis() < deadline) {
        entityManager.clear();  // ← L1 캐시 전체 초기화
        Payment payment = paymentRepository.findByTossOrderId(tossOrderId)
                .orElseThrow(...);

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return payment;
        }
        // ...
    }
}
```

### 왜 올바른가

- `entityManager.clear()`는 영속성 컨텍스트의 모든 관리 엔티티를 분리(detach)시킴
- 다음 조회는 반드시 DB에서 최신 데이터를 읽음
- 폴링 루프에서 매 반복마다 호출하여 항상 fresh 데이터를 보장

### 주의

`clear()`는 **영속성 컨텍스트 전체**를 날리므로, 같은 트랜잭션에서 다른 관리 엔티티가
있으면 의도치 않은 데이터 소실이 발생할 수 있다.
이 케이스에서는 `waitForCompletion()`이 트랜잭션 밖에서 호출되므로 안전하다.

---

## Case 3: PaymentConfirmHandler.finalizeConfirmSuccess/Failure (올바른 해결)

### 패턴

`PaymentService.confirmPayment()`에서 `prepareConfirm()` → 외부 PG 호출 → `finalizeConfirmSuccess()` 순서로 호출.
`prepareConfirm()`과 `finalizeConfirmSuccess()`가 같은 Payment 엔티티를 조회하지만,
L1 캐시 오염 없이 동작한다.

**해결 코드** (`PaymentConfirmHandler.java:95-117`):
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)  // ← 새 트랜잭션!
Payment finalizeConfirmSuccess(Long userId, String tossOrderId) {
    Payment payment = paymentRepository.findByTossOrderIdWithLock(tossOrderId)...;
    // REQUIRES_NEW → 새 영속성 컨텍스트 → DB에서 최신 상태 읽음
    payment.markAsCompleted();
    eventPublisher.publishEvent(PaymentCompletedEvent.from(saved));
    return saved;
}
```

### 왜 올바른가

- `Propagation.REQUIRES_NEW`는 기존 트랜잭션을 일시 중단하고 **새 트랜잭션 + 새 영속성 컨텍스트** 생성
- `prepareConfirm()`의 L1 캐시가 `finalizeConfirmSuccess()`에 전파되지 않음
- `finalizeConfirmFailure()`도 동일한 패턴으로 보호됨

---

## L1 캐시 문제 방지 체크리스트

### 위험 패턴

| 패턴 | 위험도 | 설명 |
|------|--------|------|
| 외부 `@Transactional` + 내부 `@Transactional(REQUIRED)` | **HIGH** | 내부 메서드가 외부의 L1 캐시를 상속 |
| 목록 조회 → 개별 건 락 조회 (같은 트랜잭션) | **HIGH** | 목록 조회가 L1 캐시를 오염시킴 |
| 락 없는 조회 → 락 있는 조회 (같은 트랜잭션) | **MEDIUM** | 첫 조회가 L1 캐시에 stale 엔티티 로드 |
| 폴링 루프에서 같은 엔티티 반복 조회 | **MEDIUM** | L1 캐시가 DB 변경을 가림 |

### 안전 패턴

| 패턴 | 사용 시점 |
|------|-----------|
| `Propagation.REQUIRES_NEW` | 별도 트랜잭션이 필요할 때 (가장 안전) |
| `entityManager.clear()` | 영속성 컨텍스트 전체 초기화 (폴링 등) |
| `entityManager.refresh(entity)` | 특정 엔티티만 DB에서 다시 읽을 때 |
| 외부 메서드에서 `@Transactional` 제거 | 배치/스케줄러에서 개별 건을 독립 트랜잭션으로 처리 |
| ID만 추출 후 개별 처리 | 목록 조회 결과의 엔티티가 L1 캐시를 오염시키는 것을 방지 |

### 코드 리뷰 시 확인할 것

1. **스케줄러/배치 메서드**에 `@Transactional`이 걸려있으면서 내부에서 서비스 메서드를 호출하는가?
2. 같은 트랜잭션 내에서 **같은 엔티티를 락 없이 조회한 후, 락이 걸린 조회를 다시 하는가?**
3. `@Transactional` 전파 설정이 `REQUIRED`(기본값)인 메서드가 외부 트랜잭션의 L1 캐시를 상속받는가?
4. 폴링이나 재시도 루프에서 `entityManager.clear()`나 `refresh()` 없이 같은 엔티티를 반복 조회하는가?
