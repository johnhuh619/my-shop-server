# 주문 목록 조회 N+1 문제 해결 — Facade 패턴 + 배치 쿼리

> STAR 기법 기반 트러블슈팅 문서

---

## 📌 Situation (상황)

`OrderQueryService.getOrderList()`에서 사용자의 주문 목록을 조회할 때 심각한 N+1 쿼리 문제가 존재했다.

**기존 흐름:**
```
1. findByUserId(userId)           → 주문 N건 조회 (쿼리 1)
2. for each order:
   2a. order.getOrderItems()      → Lazy Loading (쿼리 N) 
   2b. findCompletedRefundItemIdsByOrderId(orderId) → 환불 조회 (쿼리 N)
```

**총 쿼리 수:** `2N + 1` (주문 10건이면 21 쿼리)

추가로, 프론트엔드에서 주문 목록 화면에 배송 상태를 표시할 수 없었다.
`OrderResponse`에 `deliveryStatus` 필드가 없었기 때문이다.

---

## 🎯 Task (과제)

1. **N+1 쿼리를 상수 쿼리로 최적화**: 주문 수에 관계없이 고정된 수의 쿼리만 실행
2. **아이템별 환불 여부 + 주문별 배송 상태를 목록에서 표시**: 무신사 스타일의 주문 목록 UX
3. **크로스 도메인 집계를 Facade 패턴으로 명확히 분리**: 도메인 경계 준수

### 설계 제약

- `OrderQueryService`가 이미 `RefundRepository`를 직접 참조하는 크로스 도메인 읽기 집계 역할을 하고 있었음
- 새 패키지 생성 없이 기존 `order.service` 패키지 내에서 해결해야 함
- Domain 엔티티 스키마 변경 없이 DTO 레벨에서만 확장

---

## 🔧 Action (해결)

### 전략: Facade 패턴 + IN 배치 쿼리

`OrderQueryService`를 Facade 역할로 확장하여, 각 도메인 서비스가 자기 경계 내 배치 조회만 제공하고, Facade가 결과를 조합한다.

### 변경 사항

#### 1. OrderRepository — Fetch Join 쿼리 추가
```java
@Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.userId = :userId")
List<Order> findByUserIdWithItems(@Param("userId") Long userId);
```
- `LEFT JOIN FETCH`로 Orders + OrderItems를 단일 쿼리로 즉시 로딩
- `DISTINCT`로 1:N 조인에 의한 중복 제거

#### 2. 각 도메인에 배치 조회 메서드 추가

| 도메인 | Repository 메서드 | Service 메서드 |
|--------|-------------------|----------------|
| Delivery | `findByOrderIdIn(List<Long>)` | `getStatusMapByOrderIds()` → `Map<Long, DeliveryStatus>` |
| Refund | `findCompletedRefundItemIdsByOrderIds(List<Long>)` | (Repository 직접 사용) → `Map<Long, Set<Long>>` |

각 도메인 서비스는 `@Transactional(readOnly = true)` 배치 조회만 제공하므로 도메인 경계를 침범하지 않는다.

#### 3. OrderResponse DTO 확장
```java
// 새 필드
private final DeliveryStatus deliveryStatus;  // nullable

// 새 팩토리 메서드 (목록용)
public static OrderResponse from(Order order, Set<Long> refundedItemIds,
                                  DeliveryStatus deliveryStatus)

// 기존 팩토리 메서드 유지 (상세 조회 하위호환)
public static OrderResponse from(Order order, Set<Long> refundedItemIds)
```

#### 4. OrderQueryService — Facade 리팩터링
```java
// DI 추가
private final DeliveryService deliveryService;

public List<OrderResponse> getOrderList(Long userId) {
    List<Order> orders = orderService.getOrdersByUserWithItems(userId);  // 쿼리 1
    List<Long> orderIds = orders.stream().map(Order::getId).toList();

    Map<Long, Set<Long>> refundMap = ...;    // 쿼리 2 (IN)
    Map<Long, DeliveryStatus> deliveryMap = ...;  // 쿼리 3 (IN)

    return orders.stream()
            .map(order -> OrderResponse.from(order, refundMap, deliveryMap))
            .toList();
}
```

### 왜 Fetch Join + IN 배치를 함께 썼는가

핵심은 **조회 대상의 성격이 다르기 때문**이다.

- `Order` ↔ `OrderItem`은 같은 Aggregate 내부 연관(`@OneToMany`)이므로 `LEFT JOIN FETCH`가 가장 자연스럽다.
- `Refund`, `Delivery`는 `orderId` 스칼라 참조 기반의 **별도 도메인**이므로, 주문 목록 쿼리에 무리하게 합치기보다 `IN (:orderIds)` 배치 조회가 단순하고 안전하다.
- 따라서 `주문+아이템(fetch join)` + `환불/배송(IN 배치)`로 역할을 분리해, 쿼리 수는 상수로 유지하면서 도메인 경계도 지킨다.

### 대안 비교: 거대 조인(1방 쿼리) vs 현재 방식

#### 대안(거대 조인) 구현 시 형태

```java
// 예시 (개념 코드): 주문/아이템/환불/배송을 한 번에 평탄화 조회
@Query("""
SELECT o.id, oi.id, d.status, ri.orderItemId
FROM Order o
LEFT JOIN o.orderItems oi
LEFT JOIN Delivery d ON d.orderId = o.id
LEFT JOIN Refund r ON r.orderId = o.id AND r.status = COMPLETED
LEFT JOIN r.refundItems ri
WHERE o.userId = :userId
""")
List<Object[]> findOrderListFlat(Long userId);
```

```java
// 서비스에서 수동 재조립 (중복 제거/그룹핑 필수)
for (Object[] row : rows) {
    // orderId 기준으로 aggregate 생성
    // item/refund item 중복 제거
    // deliveryStatus 병합
}
```

#### 거대 조인의 한계

- 1:N(`Order`-`OrderItem`) + 1:N(`Refund`-`RefundItem`) 조합으로 row 수가 곱으로 증가한다.
- 결과가 평탄화되므로 서비스에서 수동 그룹핑/중복 제거 코드가 커진다.
- 조회 로직이 여러 도메인 테이블을 강하게 결합해 유지보수가 어려워진다.

#### row 수 증가 예시 (직관 비교)

가정:
- 주문당 평균 `OrderItem = 4개`
- 주문당 완료 `RefundItem = 3개` (환불 없는 주문 제외한 단순 평균 가정)
- 배송은 주문당 1건

| 주문 수 | 현재 방식 row (대략) | 거대 조인 row (대략) | 비고 |
|--------|----------------------|----------------------|------|
| 1 | 주문+아이템 4 + 환불 3 + 배송 1 = **8** | 4 × 3 = **12** | 곱 증가 시작 |
| 10 | 40 + 30 + 10 = **80** | 10 × (4 × 3) = **120** | 조인 결과 1.5배 |
| 100 | 400 + 300 + 100 = **800** | 100 × (4 × 3) = **1,200** | 규모 커질수록 부담 확대 |

설명:
- 현재 방식은 각 쿼리 결과가 선형적으로 증가(`aN + bN + cN`).
- 거대 조인은 다중 1:N 조인으로 곱 형태(`(a×b)N`)가 되어, DB 전송량/정렬/중복제거 비용이 커지기 쉽다.

#### 현재 방식의 코드 구조

- Query 1: `Order + OrderItems` fetch join
- Query 2: `Refund` 배치 조회 (`WHERE orderId IN (...)`)
- Query 3: `Delivery` 배치 조회 (`WHERE orderId IN (...)`)
- Facade(`OrderQueryService`)가 `Map<Long, ...>`로 결과를 조합

즉, 거대 조인의 "SQL 1회" 이점보다, 현재 방식의 **상수 쿼리 + 단순 조합 + 도메인 경계 유지**가 이 시나리오에서 더 실용적이었다.

---

## ✅ Result (결과)

### 쿼리 최적화
```
Before: 2N + 1 쿼리 (주문 10건 → 21 쿼리)
After:  3 상수 쿼리 (주문 수 무관)
```

| 쿼리 | 설명 |
|-------|------|
| 1 | `SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.userId = ?` |
| 2 | `SELECT r.orderId, ri.orderItemId FROM Refund r JOIN r.refundItems ri WHERE r.orderId IN (...)` |
| 3 | `SELECT d FROM Delivery d WHERE d.orderId IN (...)` |

### API 응답 개선

주문 목록 응답에 `deliveryStatus` 필드가 추가되어, 프론트엔드에서 주문별 배송 상태를 바로 표시할 수 있다.

### 아키텍처 정합성

- 기존 `getOrderDetail()` 단건 조회는 변경 없이 하위호환 유지
- Facade 패턴으로 크로스 도메인 읽기 집계가 명확하게 한 곳에서 관리됨
- 각 도메인 서비스는 자기 경계 내 배치 조회만 노출 → 의존 방향 단방향 유지

---

## 💡 Lessons Learned

1. **N+1은 목록 조회에서 가장 먼저 점검할 성능 이슈**: 단건 조회에서는 문제없던 패턴이 목록에서 증폭된다
2. **Fetch Join + IN 배치 조합이 가장 실용적**: JPA 환경에서 연관 엔티티 즉시 로딩(fetch join)과 크로스 도메인 IN 쿼리를 함께 쓰면 상수 쿼리 달성 가능
3. **Facade는 이미 존재하는 크로스 도메인 의존을 명시적으로 만든다**: `OrderQueryService`가 이미 `RefundRepository`를 참조하고 있었으므로, Facade 역할을 공식화한 것이 구조를 더 명확하게 만들었다
4. **DTO 팩토리 메서드 오버로딩으로 하위호환 유지**: 기존 `from(Order, Set<Long>)`을 그대로 두고 새 팩토리를 추가하여 상세 조회 코드는 변경 없음
