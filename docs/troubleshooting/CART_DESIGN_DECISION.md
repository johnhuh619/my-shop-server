# 장바구니 설계 결정: 서버 사이드 vs 프론트 로컬

## 배경

장바구니 기능 도입을 검토하면서, 서버 사이드 Cart 도메인을 새로 만들어야 하는지 고민함.

## 현재 구조

주문 생성 API가 이미 **복수 상품**을 지원하고 있음:

```java
// CreateOrderRequest
private List<OrderItemRequest> items;

// OrderItemRequest
private Long productId;
private Long quantity;
```

```json
POST /api/orders
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

## 결정: 서버 사이드 장바구니 도입하지 않음

### 이유

1. **이미 복수 아이템 주문이 가능** — 프론트에서 상품 목록을 모아서 `items` 배열로 보내면 됨.
2. **불필요한 복잡성 증가** — Cart/CartItem 엔티티, CRUD API, 재고 정합성 문제(담아둔 상품 품절 시 처리), 만료 정책, Cart→Order 전환 로직 등 관리 비용이 실질적 가치 대비 큼.
3. **프론트 로컬 스토리지가 자연스러움** — 장바구니는 본질적으로 "아직 주문하지 않은 임시 목록"이므로 클라이언트 상태로 관리하는 것이 적합. 로그인 전에도 담을 수 있는 장점.

### 서버 사이드 장바구니가 정당화되는 경우 (현재 해당 없음)

- 멀티 디바이스 동기화가 필수일 때 (모바일 ↔ PC)
- 장바구니 기반 마케팅이 필요할 때 (장바구니 할인, 리마인드 알림)
- 대규모 커머스에서 장바구니 분석 데이터가 필요할 때

## 채택한 방식

**프론트 로컬 장바구니 + 기존 주문 API** 조합

- 프론트엔드: 로컬 스토리지에서 장바구니 상태 관리
- 결제 시: 장바구니 아이템을 `CreateOrderRequest.items`에 매핑하여 주문 생성 API 호출
- 서버: 변경 없음
