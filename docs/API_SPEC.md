# Mini-Shop API 명세서

> Base URL: `https://<cloud-run-url>`
> Content-Type: `application/json`
> 인증: JWT Bearer Token (`Authorization: Bearer <accessToken>`)

---

## 공통 응답 형식

### 성공 응답
```json
{
  "success": true,
  "data": { ... },
  "errorCode": null,
  "errorMessage": null
}
```

### 에러 응답
```json
{
  "success": false,
  "data": null,
  "errorCode": "P001",
  "errorMessage": "Product not found"
}
```

### 페이징 응답 (data 내부)
```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5,
  "hasNext": true
}
```

---

## 인증 불필요 (Public)

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/auth/login` | 로그인 |
| POST | `/api/auth/refresh` | 토큰 갱신 |
| POST | `/api/users/register` | 회원가입 |
| GET | `/api/products` | 상품 목록 조회 |
| GET | `/api/products/{id}` | 상품 상세 조회 |
| GET | `/actuator/health` | 헬스체크 |

---

## 파라미터 전달 방식 요약

> 테스트(curl, Postman 등) 시 파라미터 위치를 틀리면 서버가 인식하지 못합니다.

| 전달 방식 | 설명 | 예시 |
|-----------|------|------|
| **Path** | URL 경로에 포함 | `/api/products/1` |
| **Query** | URL `?key=value` | `/api/products?page=0&size=20` |
| **Body** | JSON request body | `{"email":"...","password":"..."}` |
| **Header** | HTTP 헤더 | `Authorization: Bearer xxx` |

### 전체 엔드포인트 파라미터 전달 방식

| Method | Endpoint | Auth | 파라미터 | 전달 방식 |
|--------|----------|------|----------|-----------|
| POST | `/api/auth/login` | X | email, password | Body |
| POST | `/api/auth/refresh` | X | refreshToken | Body |
| POST | `/api/auth/logout` | O | accessToken, refreshToken | Header(access) + Body(refresh) |
| POST | `/api/users/register` | X | email, password, name | Body |
| GET | `/api/users/me` | O | - | - |
| PATCH | `/api/users/me/deactivate` | O | - | - |
| GET | `/api/products` | X | page, size, keyword | Query |
| GET | `/api/products/{id}` | X | id | Path |
| POST | `/api/orders` | O | items[], recipientName, recipientPhone, address, addressDetail, zipCode | Body |
| GET | `/api/orders` | O | - | - |
| GET | `/api/orders/{id}` | O | id | Path |
| PATCH | `/api/orders/{id}/cancel` | O | id | Path |
| POST | `/api/payments` | O | X-Idempotency-Key, orderId | Header + Body |
| POST | `/api/payments/confirm` | O | paymentKey, orderId, amount | Body |
| GET | `/api/payments/{id}` | O | id | Path |
| GET | `/api/payments` | O | - | - |
| POST | `/api/refunds` | O | paymentId, items[], reason | Body |
| GET | `/api/refunds/{id}` | O | id | Path |
| GET | `/api/refunds` | O | - | - |
| GET | `/api/deliveries` | O | - | - |
| GET | `/api/deliveries/{id}` | O | id | Path |
| GET | `/api/deliveries/order/{orderId}` | O | orderId | Path |
| POST | `/api/admin/products` | ADMIN | name, description, unitPrice | Body |
| PATCH | `/api/admin/products/{id}` | ADMIN | id + name, description, unitPrice | Path + Body |
| POST | `/api/admin/products/{id}/deactivate` | ADMIN | id | Path |
| GET | `/api/admin/inventories/{productId}` | ADMIN | productId | Path |
| PATCH | `/api/admin/inventories/{productId}/add-stock` | ADMIN | productId + quantity | Path + Body |
| POST | `/api/admin/orders/{id}/complete` | ADMIN | id | Path |
| GET | `/api/admin/refunds` | ADMIN | status | Query |
| GET | `/api/admin/refunds/{id}` | ADMIN | id | Path |
| POST | `/api/admin/refunds/{id}/approve` | ADMIN | id + comment | Path + Body(선택) |
| POST | `/api/admin/refunds/{id}/reject` | ADMIN | id + comment | Path + Body(선택) |
| GET | `/api/admin/deliveries` | ADMIN | status | Query |
| GET | `/api/admin/deliveries/{id}` | ADMIN | id | Path |
| POST | `/api/admin/deliveries` | ADMIN | orderId, recipientName 등 | Body |
| POST | `/api/admin/deliveries/{id}/ship` | ADMIN | id + carrier, trackingNumber | Path + Body |
| POST | `/api/admin/deliveries/{id}/in-transit` | ADMIN | id | Path |
| POST | `/api/admin/deliveries/{id}/deliver` | ADMIN | id | Path |
| POST | `/api/admin/deliveries/{id}/cancel` | ADMIN | id | Path |

---

## 1. 인증 (Auth)

### POST `/api/auth/login`
로그인하여 JWT 토큰 발급.

**Request** `Body (JSON)`
```json
{
  "email": "user@example.com",    // required
  "password": "password123"       // required
}
```

**Response**
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "userId": 1,
  "email": "user@example.com",
  "name": "홍길동"
}
```

---

### POST `/api/auth/refresh`
Access Token 갱신.

**Request** `Body (JSON)`
```json
{
  "refreshToken": "eyJhbGci..."
}
```

**Response**
```json
{
  "accessToken": "eyJhbGci..."
}
```

---

### POST `/api/auth/logout` (인증 필요)
로그아웃. Authorization 헤더에 access token 필요.

**Request** `Header (Authorization: Bearer) + Body (JSON, 선택)`
```json
{
  "refreshToken": "eyJhbGci..."
}
```

**Response**
```json
{
  "success": true,
  "data": null
}
```

---

## 2. 사용자 (User)

### POST `/api/users/register`
회원가입.

**Request** `Body (JSON)`
```json
{
  "email": "user@example.com",    // required
  "password": "password123",      // required
  "name": "홍길동"                 // required
}
```

**Response**
```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "status": "ACTIVE",
  "createdAt": "2026-02-25T10:00:00"
}
```

---

### GET `/api/users/me` (인증 필요)
내 정보 조회. 파라미터 없음 (JWT에서 사용자 식별).

**Response**: 위와 동일한 UserResponse

---

### PATCH `/api/users/me/deactivate` (인증 필요)
계정 비활성화. 파라미터 없음 (JWT에서 사용자 식별).

**Response**: UserResponse (status: `INACTIVE`)

---

## 3. 상품 (Product)

### GET `/api/products`
상품 목록 조회 (페이징).

**Query Parameters**
| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `page` | int | 0 | 페이지 번호 |
| `size` | int | 20 | 페이지 크기 (최대 50) |
| `keyword` | string | - | 검색어 (선택) |

**Response**: `PageResponse<ProductResponse>`
```json
{
  "content": [
    {
      "id": 1,
      "name": "상품명",
      "description": "상품 설명",
      "unitPrice": 10000,
      "status": "ACTIVE",
      "quantityAvailable": 50,
      "createdAt": "2026-02-25T10:00:00Z",
      "updatedAt": "2026-02-25T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

---

### GET `/api/products/{id}`
상품 상세 조회. `Path: id`

**Response**: `ProductResponse` (위와 동일)

---

## 4. 주문 (Order) - 인증 필요

### POST `/api/orders`
주문 생성. 재고가 즉시 예약(reserve)됨.

**Request** `Body (JSON)`
```json
{
  "items": [                       // required, 1개 이상
    {
      "productId": 1,              // required
      "quantity": 2                // required, 양수
    }
  ],
  "recipientName": "홍길동",         // required
  "recipientPhone": "010-1234-5678", // required
  "address": "서울시 강남구",         // required
  "addressDetail": "101호",          // optional
  "zipCode": "06000"                // required
}
```

**Response**
```json
{
  "id": 1,
  "userId": 1,
  "status": "CREATED",
  "totalAmount": 20000,
  "items": [
    {
      "id": 1,
      "productId": 1,
      "productName": "상품명",
      "unitPrice": 10000,
      "quantity": 2,
      "subtotal": 20000,
      "refunded": false
    }
  ],
  "deliveryStatus": null,
  "createdAt": "2026-02-25T10:00:00Z",
  "updatedAt": "2026-02-25T10:00:00Z"
}
```

---

### GET `/api/orders`
내 주문 목록 조회. 파라미터 없음 (JWT에서 사용자 식별).

**Response**: `List<OrderResponse>`  
(`items[].refunded`, `deliveryStatus` 필드 포함)

---

### GET `/api/orders/{id}`
주문 상세 조회. `Path: id`

**Response**: `OrderResponse`  
(`items[].refunded` 포함, 현재 구현에서는 `deliveryStatus`는 `null`)

---

### PATCH `/api/orders/{id}/cancel`
주문 취소. 예약된 재고가 해제됨. `Path: id`, Body 없음.

**Response**: `OrderResponse` (status: `CANCELED`)

---

## 5. 결제 (Payment) - 인증 필요

### POST `/api/payments`
결제 준비 (Toss Payments 연동 1단계).

**Headers**
| 헤더 | 필수 | 설명 |
|------|------|------|
| `X-Idempotency-Key` | O | 멱등성 키 (UUID 권장) |

**Request** `Header (X-Idempotency-Key) + Body (JSON)`
```json
{
  "orderId": 1                     // required
}
```

**Response**
```json
{
  "paymentId": 1,
  "tossOrderId": "MINISHOP_1_a1b2c3d4",
  "amount": 20000,
  "orderName": "상품명 외 1건"
}
```

---

### POST `/api/payments/confirm`
결제 승인 (Toss Payments 연동 2단계). Toss 결제창에서 리다이렉트된 후 호출.

> **응답 계약**: 성공(200) 시 `status`는 항상 `COMPLETED`. PG 실패 시 예외(502)를 던지므로 `PROCESSING`이나 `FAILED`가 응답으로 내려오는 경로는 없음. 프론트에서 성공 응답을 받았다면 `data.status === "COMPLETED"`를 확정적으로 가정 가능.

**Request** `Body (JSON)`
```json
{
  "paymentKey": "toss_payment_key", // required, Toss에서 발급
  "orderId": "MINISHOP_1_a1b2c3d4", // required, tossOrderId
  "amount": 20000                    // required, 양수
}
```

**Response**
```json
{
  "id": 1,
  "userId": 1,
  "orderId": 1,
  "status": "COMPLETED",
  "amount": 20000,
  "tossOrderId": "MINISHOP_1_a1b2c3d4",
  "paymentKey": "toss_payment_key",
  "createdAt": "2026-02-25T10:00:00Z",
  "updatedAt": "2026-02-25T10:00:00Z"
}
```

---

### GET `/api/payments/{id}`
결제 상세 조회. `Path: id`

**Response**: `PaymentResponse`

---

### GET `/api/payments`
내 결제 목록 조회. 파라미터 없음.

**Response**: `List<PaymentResponse>`

---

## 6. 환불 (Refund) - 인증 필요

### POST `/api/refunds`
환불 요청. 부분 환불 가능 (상품 단위).

**Request** `Body (JSON)`
```json
{
  "paymentId": 1,                  // required
  "items": [                       // required, 1개 이상
    {
      "orderItemId": 1,            // required
      "quantity": 1                // required, 양수
    }
  ],
  "reason": "상품 불량"             // 선택
}
```

**Response**
```json
{
  "id": 1,
  "userId": 1,
  "paymentId": 1,
  "orderId": 1,
  "status": "REQUESTED",
  "amount": 10000,
  "reason": "상품 불량",
  "adminComment": null,
  "items": [
    {
      "id": 1,
      "orderItemId": 1,
      "productId": 1,
      "productName": "상품명",
      "unitPrice": 10000,
      "quantity": 1,
      "subtotal": 10000
    }
  ],
  "createdAt": "2026-02-25T10:00:00Z",
  "updatedAt": "2026-02-25T10:00:00Z"
}
```

---

### GET `/api/refunds/{id}`
환불 상세 조회. `Path: id`

**Response**: `RefundResponse`

---

### GET `/api/refunds`
내 환불 목록 조회. 파라미터 없음.

**Response**: `List<RefundResponse>`

---

## 7. 배송 (Delivery) - 인증 필요

### GET `/api/deliveries`
내 배송 목록 조회. 파라미터 없음.

**Response**: `List<DeliveryResponse>`
```json
{
  "id": 1,
  "orderId": 1,
  "userId": 1,
  "status": "PREPARING",
  "recipientName": "홍길동",
  "recipientPhone": "010-1234-5678",
  "address": "서울시 강남구",
  "addressDetail": "101호",
  "zipCode": "06000",
  "carrier": null,
  "trackingNumber": null,
  "createdAt": "2026-02-25T10:00:00Z",
  "updatedAt": "2026-02-25T10:00:00Z",
  "shippedAt": null,
  "deliveredAt": null
}
```

---

### GET `/api/deliveries/{id}`
배송 상세 조회. `Path: id`

**Response**: `DeliveryResponse`

---

### GET `/api/deliveries/order/{orderId}`
주문 ID로 배송 조회. `Path: orderId`

**Response**: `DeliveryResponse`

---

## 8. 관리자 API (ADMIN 권한 필요)

관리자 계정의 JWT 토큰으로 호출. 일반 사용자는 `403 Forbidden`.

### 상품 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/admin/products` | 상품 등록 |
| PATCH | `/api/admin/products/{id}` | 상품 수정 |
| POST | `/api/admin/products/{id}/deactivate` | 상품 비활성화 (`Path: id`, Body 없음) |

**POST `/api/admin/products`** Request `Body (JSON)`
```json
{
  "name": "상품명",
  "description": "설명",
  "unitPrice": 10000
}
```

**PATCH `/api/admin/products/{id}`** Request `Path: id + Body (JSON, 모든 필드 선택)`
```json
{
  "name": "수정된 이름",
  "description": "수정된 설명",
  "unitPrice": 15000
}
```

---

### 재고 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/admin/inventories/{productId}` | 재고 조회 (`Path: productId`) |
| PATCH | `/api/admin/inventories/{productId}/add-stock` | 재고 추가 |

**PATCH `/api/admin/inventories/{productId}/add-stock`** Request `Path: productId + Body (JSON)`
```json
{
  "quantity": 100
}
```

**Response**
```json
{
  "id": 1,
  "productId": 1,
  "quantityAvailable": 150,
  "quantityReserved": 10,
  "createdAt": "2026-02-25T10:00:00Z",
  "updatedAt": "2026-02-25T10:00:00Z"
}
```

---

### 주문 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/admin/orders/{id}/complete` | 주문 완료 처리 (`Path: id`, Body 없음) |

---

### 환불 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/admin/refunds` | 환불 요청 목록 (status 필터) |
| GET | `/api/admin/refunds/{id}` | 환불 상세 |
| POST | `/api/admin/refunds/{id}/approve` | 환불 승인 |
| POST | `/api/admin/refunds/{id}/reject` | 환불 거절 |

**GET `/api/admin/refunds`** Query Parameters
| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `status` | RefundStatus | REQUESTED | 필터링할 상태 |

**POST `/api/admin/refunds/{id}/approve`** Request `Path: id + Body (JSON, 선택)`
```json
{
  "comment": "승인합니다"
}
```

**POST `/api/admin/refunds/{id}/reject`** Request `Path: id + Body (JSON, 선택)`
```json
{
  "comment": "사유 불충분"
}
```

---

### 배송 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/admin/deliveries` | 배송 목록 (`Query: status`, 선택) |
| GET | `/api/admin/deliveries/{id}` | 배송 상세 (`Path: id`) |
| POST | `/api/admin/deliveries` | 배송 생성 |
| POST | `/api/admin/deliveries/{id}/ship` | 발송 처리 |
| POST | `/api/admin/deliveries/{id}/in-transit` | 배송중 처리 (`Path: id`, Body 없음) |
| POST | `/api/admin/deliveries/{id}/deliver` | 배송 완료 (`Path: id`, Body 없음) |
| POST | `/api/admin/deliveries/{id}/cancel` | 배송 취소 (`Path: id`, Body 없음) |

**POST `/api/admin/deliveries`** Request `Body (JSON)`
```json
{
  "orderId": 1,                    // required
  "recipientName": "홍길동",        // required
  "recipientPhone": "010-1234-5678", // required
  "address": "서울시 강남구",        // required
  "addressDetail": "101호",         // 선택
  "zipCode": "06000"               // required
}
```

**POST `/api/admin/deliveries/{id}/ship`** Request `Path: id + Body (JSON)`
```json
{
  "carrier": "CJ대한통운",          // required
  "trackingNumber": "1234567890"   // required
}
```

---

## Enum 값 참조

| Enum | 값 |
|------|----|
| **OrderStatus** | `CREATED`, `PAID`, `COMPLETED`, `CANCELED`, `EXPIRED`, `REFUND_REQUESTED`, `REFUNDED` |
| **PaymentStatus** | `REQUESTED`, `PROCESSING`, `COMPLETED`, `FAILED` |
| **RefundStatus** | `REQUESTED`, `APPROVED`, `REJECTED`, `COMPLETED`, `FAILED` |
| **DeliveryStatus** | `PREPARING`, `SHIPPED`, `IN_TRANSIT`, `DELIVERED`, `CANCELED` |
| **ProductStatus** | `ACTIVE`, `INACTIVE`, `DELETED` |
| **UserStatus** | `ACTIVE`, `INACTIVE`, `DELETED` |

---

## 에러 코드 참조

| 코드 | HTTP | 설명 |
|------|------|------|
| `C001` | 500 | Internal server error |
| `C002` | 400 | Validation error (필드별 메시지 포함) |
| `C003` | 403 | Access denied |
| `C004` | 401 | Authentication required |
| `USER_NOT_FOUND` | 404 | 사용자 없음 |
| `DUPLICATE_EMAIL` | 409 | 이메일 중복 |
| `INVALID_CREDENTIALS` | 401 | 로그인 실패 |
| `INVALID_TOKEN` | 401 | 유효하지 않은 토큰 |
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 |
| `INSUFFICIENT_INVENTORY` | 409 | 재고 부족 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `INVALID_ORDER_STATUS` | 400 | 잘못된 주문 상태 전이 |
| `ORDER_ALREADY_PAID` | 400 | 이미 결제된 주문 |
| `ORDER_EXPIRED` | 400 | 만료된 주문 |
| `PAYMENT_NOT_FOUND` | 404 | 결제 없음 |
| `DUPLICATE_PAYMENT` | 409 | 중복 결제 |
| `PAYMENT_AMOUNT_MISMATCH` | 400 | 결제 금액 불일치 |
| `PG_CONFIRM_FAILED` | 502 | PG 승인 실패 |
| `PG_REFUND_FAILED` | 502 | PG 환불 실패 |
| `REFUND_NOT_FOUND` | 404 | 환불 없음 |
| `REFUND_NOT_ALLOWED` | 400 | 환불 불가 상태 |
| `REFUND_AMOUNT_EXCEEDED` | 400 | 환불 금액 초과 |
| `REFUND_QUANTITY_EXCEEDED` | 400 | 환불 수량 초과 |
| `DELIVERY_NOT_FOUND` | 404 | 배송 없음 |
| `INVALID_DELIVERY_STATUS` | 400 | 잘못된 배송 상태 전이 |

---

## DB / Redis 부수효과 (Side Effects)

> 각 API 호출 시 서버에서 발생하는 데이터 변경 사항. 테스트 시 이 변경이 실제로 일어나는지 확인할 수 있습니다.

### 범례
- **DB INSERT/UPDATE/DELETE**: 해당 테이블에 데이터 생성/수정/삭제
- **Redis SET**: 키 생성 (TTL 포함)
- **Redis GET**: 키 조회 (읽기만)
- **Event → Async**: 트랜잭션 커밋 후 비동기로 실행되는 후속 작업
- **없음**: 조회만 (SELECT), 상태 변경 없음

---

### Auth

| API | DB 변경 | Redis 변경 | 비동기 이벤트 |
|-----|---------|-----------|--------------|
| `POST /api/auth/login` | 없음 (SELECT users) | 없음 | 없음 |
| `POST /api/auth/logout` | 없음 | SET `blacklist:{jti}` (access + refresh, TTL=토큰 만료시간) | 없음 |
| `POST /api/auth/refresh` | 없음 | GET `blacklist:{jti}` (블랙리스트 체크, 읽기만) | 없음 |

### User

| API | DB 변경 | Redis 변경 | 비동기 이벤트 |
|-----|---------|-----------|--------------|
| `POST /api/users/register` | INSERT `users` | 없음 | 없음 |
| `GET /api/users/me` | 없음 | 없음 | 없음 |
| `PATCH /api/users/me/deactivate` | UPDATE `users` (status→INACTIVE) | 없음 | 없음 |

### Product (Admin)

| API | DB 변경 | Redis 변경 | 비동기 이벤트 |
|-----|---------|-----------|--------------|
| `GET /api/products` | 없음 | 없음 | 없음 |
| `GET /api/products/{id}` | 없음 | 없음 | 없음 |
| `POST /api/admin/products` | INSERT `products` + INSERT `inventory` (초기 재고 0) | 없음 | 없음 |
| `PATCH /api/admin/products/{id}` | UPDATE `products` (name, description, unitPrice) | 없음 | 없음 |
| `POST /api/admin/products/{id}/deactivate` | UPDATE `products` (status→INACTIVE) | 없음 | 없음 |

### Inventory (Admin)

| API | DB 변경 | Redis 변경 | 비동기 이벤트 |
|-----|---------|-----------|--------------|
| `GET /api/admin/inventories/{productId}` | 없음 | 없음 | 없음 |
| `PATCH /api/admin/inventories/{productId}/add-stock` | UPDATE `inventory` (quantityAvailable 증가) | 없음 | 없음 |

### Order

| API | DB 변경 | Redis 변경 | 비동기 이벤트 |
|-----|---------|-----------|--------------|
| `POST /api/orders` | INSERT `orders` + INSERT `order_items` + UPDATE `inventory` (available→reserved) | 없음 | 없음 |
| `GET /api/orders` | 없음 | 없음 | 없음 |
| `GET /api/orders/{id}` | 없음 | 없음 | 없음 |
| `PATCH /api/orders/{id}/cancel` | UPDATE `orders` (status→CANCELED) + UPDATE `inventory` (reserved→available) | 없음 | 없음 |
| `POST /api/admin/orders/{id}/complete` | UPDATE `orders` (status→COMPLETED) | 없음 | 없음 |

### Payment

| API | DB 변경 | Redis 변경 | 비동기 이벤트 |
|-----|---------|-----------|--------------|
| `POST /api/payments` | INSERT `payments` (status=REQUESTED) | 없음 | 없음 |
| `POST /api/payments/confirm` | UPDATE `payments` (→PROCESSING→COMPLETED 또는 FAILED) | 없음 | **아래 참조** |
| `GET /api/payments/{id}` | 없음 | 없음 | 없음 |
| `GET /api/payments` | 없음 | 없음 | 없음 |

**`/api/payments/confirm` 비동기 후속 작업:**

- **PG 성공 시** → `PaymentCompletedEvent`:
  - UPDATE `orders` (status→PAID)
  - UPDATE `inventory` (reserved→consumed, 각 상품별)
  - INSERT `deliveries` (status=PREPARING, 주문 배송지 스냅샷 기반 자동 생성)
  - 실패 시 `retry_tasks` INSERT (MARK_AS_PAID)
  - 배송 생성 실패 시 `retry_tasks` INSERT (CREATE_DELIVERY)

- **PG 실패 시** → `PaymentFailedEvent`:
  - UPDATE `orders` (status→CANCELED)
  - UPDATE `inventory` (reserved→available, 재고 해제)
  - 실패 시 `retry_tasks` INSERT (CANCEL_ORDER)

### Refund

| API | DB 변경 | Redis 변경 | 비동기 이벤트 |
|-----|---------|-----------|--------------|
| `POST /api/refunds` | INSERT `refunds` + INSERT `refund_items` + UPDATE `orders` (status→REFUND_REQUESTED, 첫 환불 시) | 없음 | 없음 |
| `GET /api/refunds/{id}` | 없음 | 없음 | 없음 |
| `GET /api/refunds` | 없음 | 없음 | 없음 |
| `GET /api/admin/refunds` | 없음 | 없음 | 없음 |
| `GET /api/admin/refunds/{id}` | 없음 | 없음 | 없음 |
| `POST /api/admin/refunds/{id}/approve` | UPDATE `refunds` (→APPROVED→COMPLETED) | 없음 | **아래 참조** |
| `POST /api/admin/refunds/{id}/reject` | UPDATE `refunds` (status→REJECTED) | 없음 | 없음 |

**`/api/admin/refunds/{id}/approve` 비동기 후속 작업:**

- `RefundCompletedEvent`:
  - 전액 환불 시: UPDATE `orders` (status→REFUNDED)
  - UPDATE `inventory` (quantityAvailable 복원, 각 환불 상품별)

### Delivery

| API | DB 변경 | Redis 변경 | 비동기 이벤트 |
|-----|---------|-----------|--------------|
| `POST /api/admin/deliveries` | INSERT `deliveries` (status=PREPARING) | 없음 | 없음 |
| `POST /api/admin/deliveries/{id}/ship` | UPDATE `deliveries` (status→SHIPPED, carrier, trackingNumber) | 없음 | 없음 |
| `POST /api/admin/deliveries/{id}/in-transit` | UPDATE `deliveries` (status→IN_TRANSIT) | 없음 | 없음 |
| `POST /api/admin/deliveries/{id}/deliver` | UPDATE `deliveries` (status→DELIVERED) | 없음 | **아래 참조** |
| `POST /api/admin/deliveries/{id}/cancel` | UPDATE `deliveries` (status→CANCELED) | 없음 | 없음 |
| `GET /api/deliveries` | 없음 | 없음 | 없음 |
| `GET /api/deliveries/{id}` | 없음 | 없음 | 없음 |
| `GET /api/deliveries/order/{orderId}` | 없음 | 없음 | 없음 |
| `GET /api/admin/deliveries` | 없음 | 없음 | 없음 |
| `GET /api/admin/deliveries/{id}` | 없음 | 없음 | 없음 |

**`/api/admin/deliveries/{id}/deliver` 비동기 후속 작업:**

- `DeliveryCompletedEvent`:
  - UPDATE `orders` (status→COMPLETED)
  - 실패 시 `retry_tasks` INSERT (COMPLETE_ORDER)

---

### Inventory 상태 흐름 요약

```
                  주문 생성           결제 확인           환불 완료
available ──────→ reserved ──────→ consumed ──────→ available
    ↑                │
    └────────────────┘
       주문 취소 / 결제 실패
```

---

## 프론트엔드 연동 시 주의사항

### 1. 인증 처리

- **토큰 저장**: `accessToken`은 메모리(변수/상태), `refreshToken`은 httpOnly cookie 또는 안전한 저장소에 보관. localStorage 사용 시 XSS 주의.
- **요청 헤더**: 모든 인증 필요 API에 `Authorization: Bearer <accessToken>` 헤더 추가.
- **토큰 만료**: accessToken은 24시간, refreshToken은 7일. 401 응답 시 `/api/auth/refresh`로 갱신 후 원래 요청 재시도. refresh도 실패하면 로그인 페이지로 이동.
- **로그아웃**: 서버에 `/api/auth/logout` 호출 필수 (토큰 블랙리스트 등록됨). 클라이언트 토큰만 삭제하면 토큰이 만료까지 유효한 상태로 남음.

### 2. 결제 플로우 (Toss Payments)

결제는 반드시 **2단계**로 진행:

```
1. POST /api/payments (멱등성 키 필수)
   → tossOrderId, amount 획득

2. Toss 결제 위젯으로 결제 진행
   → paymentKey 획득

3. POST /api/payments/confirm
   → paymentKey + orderId(tossOrderId) + amount 전송
```

- **`X-Idempotency-Key`**: 결제 준비 시 반드시 고유 UUID 전송. 같은 키로 재요청하면 중복 결제 방지됨.
- **금액 검증**: confirm 시 amount는 prepare에서 받은 금액과 정확히 일치해야 함. 클라이언트에서 금액 조작 시 `PAYMENT_AMOUNT_MISMATCH` 에러.
- **orderId 주의**: confirm의 `orderId`는 DB 주문 ID가 아니라 `tossOrderId` (문자열). prepare 응답의 `tossOrderId` 값을 그대로 전달.

### 3. 비동기 처리 인지

- 결제 승인(`confirm`) 성공 후 **주문 상태 변경, 재고 확정, 배송 자동 생성은 비동기**로 처리됨. confirm 응답이 200이어도 주문 상태가 즉시 `PAID`가 아닐 수 있음.
- 환불 승인 후 **PG 환불과 재고 복원도 비동기**. 승인 즉시 환불 완료가 아님.
- 프론트에서 상태 변경을 확인하려면 **폴링** 또는 일정 딜레이 후 재조회 필요.

### 4. 응답 구조 파싱

- 모든 응답은 `ApiResponse` 래퍼. 항상 `success` 필드를 먼저 확인.
- 에러 시 HTTP 상태코드와 `errorCode`를 함께 활용. `errorCode`가 더 구체적.
- 타임스탬프 필드: `createdAt` 등은 `Instant`(UTC ISO-8601, `"2026-02-25T10:00:00Z"`) 형식. UserResponse의 `createdAt`만 `LocalDateTime`(`"2026-02-25T10:00:00"`) 형식이므로 주의.

### 5. 페이징

- `page`는 0부터 시작 (0-indexed).
- `size`는 서버에서 최대 50으로 제한됨. 50 초과 전송해도 50으로 잘림.
- `hasNext`로 다음 페이지 존재 여부 판단.

### 6. CORS

- 개발 서버 기준 `cors.allowed-origins=*` 설정. 프로덕션 전환 시 도메인 제한될 수 있음.
- `credentials: true` 설정이므로, fetch 시 `credentials: 'include'` 필요할 수 있음.
- 허용 메서드: `GET`, `POST`, `PATCH`, `DELETE`, `OPTIONS`.

### 7. 주문 만료

- 주문 생성 후 일정 시간 내 결제하지 않으면 자동 만료(`EXPIRED`)되고 예약 재고가 해제됨.
- 만료된 주문에 결제 시도 시 `ORDER_EXPIRED` 에러. 새 주문을 생성해야 함.
