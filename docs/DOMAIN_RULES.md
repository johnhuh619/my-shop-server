# Domain Rules

> **mini-shop Domain Invariants**

이 문서는 비즈니스 정합성을 보장하기 위해 **절대 깨지면 안 되는 규칙**을 정의한다.
**모든 코드 변경은 이 규칙을 우선으로 한다.**

---

## 1. 전역 도메인 불변 규칙 (Global Invariants)

### 1.1 과거 데이터 불변성

`Order`, `OrderItem`, `Payment`, `Refund`는 생성 이후 **비즈니스 의미가 바뀌는 UPDATE를 금지**한다.

- 상태 변경은 허용
- **스냅샷 데이터는 절대 수정 금지**

```java
// ❌ 금지 예시
orderItem.setUnitPrice(newPrice);
```

### 1.2 시간 개념 혼합 금지

| 도메인 | 시간 의미 |
|--------|----------|
| Product | 현재 |
| Inventory | 현재 |
| Order / OrderItem | 과거 |
| Payment / Refund | 돈의 흐름 |

**원칙:**
- 현재 도메인은 과거 도메인을 변경할 수 없다
- 과거 도메인은 현재 도메인을 참조하지 않는다

---

## 2. Product Domain Rules

### 규칙

- Product는 주문(`Order` / `OrderItem`)을 **절대 참조하지 않는다**
- Product는 가격, 이름, 설명 **변경 가능**
- Product 변경은 **과거 주문에 영향을 주지 않는다**

### 금지

```
❌ Product → OrderItem FK
❌ Product 가격을 기반으로 과거 주문 금액 계산
```

---

## 3. Inventory Domain Rules (경쟁 자원)

### 규칙

- Inventory는 **동시성 관리의 유일한 책임자**
- Inventory는 **예약(reserve)** 개념을 사용한다
- 재고 차감은 반드시 **조건부 UPDATE**로 수행한다

### 허용 흐름

```
Inventory.reserve() → Order 생성
Inventory.release() → Order 취소 / 만료
```

### 금지

```
❌ Order 생성 없이 재고 차감
❌ Payment 단계에서 재고 직접 조작
```

---

## 4. Order Domain Rules (구매 의사)

### 규칙

- Order는 **구매 의사 표현**이다
- Order 생성 시:
  - Inventory 예약 **필수**
  - OrderItem 스냅샷 생성 **필수**
- Order는 결제를 **직접 수행하지 않는다**

### 상태 전이

```
CREATED → PAID → COMPLETED
CREATED → CANCELED
```

### 금지

```
❌ Order에서 Payment API 호출
❌ Order 없이 Payment 생성
```

---

## 5. OrderItem Domain Rules (스냅샷 핵심)

### 규칙

- OrderItem은 **Product의 스냅샷**
- 반드시 아래 필드를 **값 복사**로 저장해야 한다:
  - `productName`
  - `unitPrice`
  - `quantity`

### Product FK 정책

- OrderItem은 Product FK를 **선택적으로만 허용**
  - 조회용: **가능**
  - 비즈니스 로직 사용: **금지**

### 금지

```
❌ Product 가격을 다시 조회해 금액 계산
❌ OrderItem에서 Product 변경 로직 수행
```

---

## 6. Payment Domain Rules (금전 거래)

### 규칙

- Payment는 **멱등성 보장 필수**
- `(user_id, idempotency_key)`는 **UNIQUE**
- Payment는 Order 상태를 **직접 변경하지 않는다**

### 상태 전이

```
REQUESTED → COMPLETED
REQUESTED → FAILED
```

### 금지

```
❌ Payment 중복 생성
❌ Payment 재시도 시 새로운 Payment 생성
```

---

## 7. Refund Domain Rules

### 규칙

- Refund는 반드시 **Payment 기준**으로 생성
- Refund는 **부분 환불 가능**
- Refund는 재고 복구 여부를 **직접 결정하지 않는다**

### 금지

```
❌ Order 기준 Refund 생성
❌ Refund 단독 생성
```

---

## 8. Event / Outbox Rules

### 규칙

- **DB가 Source of Truth**
- Outbox는 **트랜잭션 내부에서만** 생성
- Worker는 **중복 이벤트 처리 가능**해야 함

### 금지

```
❌ DB 없이 Redis 이벤트 발행
❌ 이벤트 1회 처리 가정
```

---

## 9. AI / 개발자 공통 강제 규칙

| 상황 | 조치 |
|------|------|
| 새로운 도메인 추가 시 | **반드시 이 문서에 규칙 추가** |
| 기존 규칙 위반 코드 | **구현 금지** |
| 해석이 애매할 경우 | **구현 중단 후 질문** |

---

## 부록: 규칙 위반 체크리스트

코드 리뷰 시 아래 항목을 확인한다:

### A. 스냅샷 데이터 불변성 체크

- [ ] OrderItem의 `unitPrice`, `productName` 수정 코드가 없는가?
- [ ] Payment, Refund의 금액 필드 수정 코드가 없는가?

### B. 도메인 참조 방향 체크

- [ ] Product가 Order/OrderItem을 참조하지 않는가?
- [ ] OrderItem이 Product를 비즈니스 로직에 사용하지 않는가?

### C. 재고 관리 체크

- [ ] 재고 차감이 Inventory 도메인 내에서만 수행되는가?
- [ ] Order 생성 전에 재고 예약이 수행되는가?
- [ ] Payment에서 재고를 직접 조작하지 않는가?

### D. 멱등성 체크

- [ ] Payment에 `(user_id, idempotency_key)` UNIQUE 제약이 있는가?
- [ ] 동일한 요청에 대해 새로운 Payment가 생성되지 않는가?

### E. 이벤트 처리 체크

- [ ] Outbox가 DB 트랜잭션 내에서 생성되는가?
- [ ] Worker가 중복 이벤트를 안전하게 처리하는가?
