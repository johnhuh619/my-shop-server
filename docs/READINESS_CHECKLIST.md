# 개발 서버 배포 준비도 체크리스트

> 최소 기능 쇼핑몰(MVP) 기준. 완성도 체크 요청 시 이 문서를 기준으로 점검한다.
>
> **마지막 점검일: 2026-02-24**

---

## 사용법

1. 완성도 체크 요청 시 이 문서의 체크리스트를 기준으로 코드베이스를 점검한다.
2. 각 항목의 상태를 아래 기호로 갱신한다:
   - `[x]` 완료 — 코드 확인 완료, 동작 검증됨
   - `[ ]` 미완료 — 구현 필요
   - `[!]` 문제 — 구현은 있으나 이슈 존재 (비고에 상세 기술)
   - `[-]` 해당없음 — 현재 스코프에서 제외
3. **배포 차단 이슈** 섹션이 모두 해결되면 개발 서버 배포 가능으로 판단한다.

---

## 1. 회원 (Auth / User)

### API 엔드포인트
- [x] `POST /api/users/register` — 회원가입 (email, password, name)
- [x] `POST /api/auth/login` — 로그인 (JWT access + refresh)
- [x] `POST /api/auth/logout` — 로그아웃 (Redis 블랙리스트)
- [x] `POST /api/auth/refresh` — 토큰 갱신
- [x] `GET /api/users/me` — 내 정보 조회
- [x] `PATCH /api/users/me/deactivate` — 회원 비활성화 (soft delete)
- [ ] 비밀번호 변경
- [ ] 프로필 수정 (이름 등)

### 검증 항목
- [x] BCrypt 패스워드 인코딩
- [x] 비활성 계정 로그인 차단
- [x] JWT jti + tokenType 클레임 포함
- [x] Refresh 토큰 블랙리스트 검증

---

## 2. 상품 (Product)

### API 엔드포인트
- [x] `POST /api/admin/products` — 상품 등록 (ADMIN)
- [x] `PATCH /api/admin/products/{id}` — 상품 수정 (ADMIN)
- [x] `POST /api/admin/products/{id}/deactivate` — 상품 비활성화 (ADMIN)
- [x] `GET /api/products/{id}` — 상품 상세 (재고 포함)
- [x] `GET /api/products` — 상품 목록 (페이징, 키워드 검색, 재고 포함)
- [ ] 상품 이미지 URL 필드
- [ ] 카테고리

### 검증 항목
- [x] 상품명/설명/가격 입력 검증
- [x] ACTIVE 상태 상품만 목록 노출
- [x] 상품 생성 시 Inventory 자동 초기화

---

## 3. 재고 (Inventory)

### API 엔드포인트
- [x] `PATCH /api/admin/inventories/{productId}/add-stock` — 재고 추가 (ADMIN)
- [x] `GET /api/admin/inventories/{productId}` — 재고 조회 (ADMIN)

### 검증 항목
- [x] `PESSIMISTIC_WRITE` 락으로 동시성 보호 (reserve/release/confirm/addStock)
- [x] reserve → confirm 2단계 재고 관리
- [x] 수량 양수 검증

---

## 4. 주문 (Order)

### API 엔드포인트
- [x] `POST /api/orders` — 주문 생성 (재고 예약)
- [x] `GET /api/orders` — 내 주문 목록
- [x] `GET /api/orders/{id}` — 주문 상세 (소유권 검증)
- [x] `PATCH /api/orders/{id}/cancel` — 주문 취소 (재고 해제)
- [x] `POST /api/admin/orders/{id}/complete` — 주문 완료 (ADMIN)

### 검증 항목
- [x] productId 정렬로 데드락 방지
- [x] OrderItem에 Product 스냅샷 복사 (productName, unitPrice)
- [x] 주문 만료 스케줄러 (30분, L1 캐시 안전)
- [x] `findByIdWithLock` 사용으로 상태 전이 동시성 보호
- [x] 멱등 가드 (이미 PAID/COMPLETED/EXPIRED이면 스킵)

---

## 5. 결제 (Payment)

### API 엔드포인트
- [x] `POST /api/payments` — 결제 준비 (멱등성 키, tossOrderId)
- [x] `POST /api/payments/confirm` — 결제 확인 (Toss PG)
- [x] `GET /api/payments/{id}` — 결제 조회
- [x] `GET /api/payments` — 내 결제 목록

### 검증 항목
- [x] `(userId, idempotencyKey)` UNIQUE 제약
- [x] REQUESTED → PROCESSING → COMPLETED 상태 머신
- [x] 금액 변조 방지 (amount validation)
- [x] PG 호출은 트랜잭션 밖 (락 보유 시간 최소화)
- [x] `REQUIRES_NEW`로 finalize 트랜잭션 분리
- [x] `entityManager.clear()` 폴링 시 L1 캐시 초기화
- [x] 결제 성공 이벤트 → Order PAID + Inventory confirm (비동기)
- [x] 결제 실패 이벤트 → Order 취소 + Inventory release (비동기)
- [x] 이벤트 실패 시 RetryTask 등록

---

## 6. 환불 (Refund)

### API 엔드포인트
- [x] `POST /api/refunds` — 환불 요청 (부분 환불 지원)
- [x] `GET /api/refunds/{id}` — 환불 조회
- [x] `GET /api/refunds` — 내 환불 목록
- [x] `GET /api/admin/refunds` — 관리자 환불 목록 (ADMIN)
- [x] `POST /api/admin/refunds/{id}/approve` — 관리자 승인 (ADMIN)
- [x] `POST /api/admin/refunds/{id}/reject` — 관리자 거절 (ADMIN)

### 검증 항목
- [x] PAID 또는 REFUND_REQUESTED 상태에서만 환불 가능
- [x] OrderItem별 환불 수량 초과 검증
- [x] 중복 환불 방지 (기존 APPROVED/COMPLETED/REQUESTED 합산)
- [x] 환불 완료 이벤트 → 재고 복구 (addStock) + 전액 시 Order REFUNDED

---

## 7. 배송 (Delivery)

### API 엔드포인트
- [x] `POST /api/admin/deliveries` — 배송 생성 (ADMIN, 멱등)
- [x] `POST /api/admin/deliveries/{id}/ship` — 발송 (운송장 등록)
- [x] `POST /api/admin/deliveries/{id}/in-transit` — 배송중
- [x] `POST /api/admin/deliveries/{id}/deliver` — 배송완료 → Order COMPLETED
- [x] `POST /api/admin/deliveries/{id}/cancel` — 배송취소
- [x] `GET /api/deliveries` — 내 배송 목록
- [x] `GET /api/deliveries/{id}` — 배송 상세
- [x] `GET /api/deliveries/order/{orderId}` — 주문별 배송 조회

### 검증 항목
- [x] PAID 상태 주문에만 배송 생성 가능
- [x] 상태 머신: PREPARING → SHIPPED → IN_TRANSIT → DELIVERED
- [x] 배송완료 이벤트 → Order completeOrder

---

## 8. 인프라 / 공통

### 인증·보안
- [x] JWT 인증 필터 (`JwtAuthenticationFilter`)
- [x] Spring Security STATELESS + CORS
- [x] `@PreAuthorize("hasRole('ADMIN')")` — AdminProduct, AdminInventory, AdminRefund, AdminOrder, AdminDelivery
- [x] 글로벌 예외 처리 (`GlobalExceptionHandler`)
- [x] API 응답 형식 통일 (`ApiResponse<T>`)

### 비동기·보상
- [x] `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`
- [x] RetryTask 스케줄러 (30초 간격, 지수 백오프, 최대 5회)
- [x] RetryTaskHandler 구현체 (MARK_AS_PAID, CANCEL_ORDER, COMPLETE_ORDER)

### DB·설정
- [!] JWT secret 하드코딩 → 환경 변수 필요
- [!] `ddl-auto=create-drop` → 개발 서버에서는 `update` 또는 Flyway
- [x] `application-dev.properties` 존재 여부 확인 필요
- [x] Redis 필수 (로그아웃 블랙리스트)

---

## 배포 차단 이슈

> 이 섹션의 모든 항목이 해결되어야 개발 서버 배포 가능.

| # | 이슈 | 상태 | 비고 |
|---|------|------|------|
| 1 | ~~상품 등록 API 권한 체크 없음~~ | **해결** | `AdminProductController`로 이동, `@PreAuthorize("hasRole('ADMIN')")` 적용 |
| 2 | ~~재고 추가 API 권한 체크 없음~~ | **해결** | `AdminInventoryController`로 이동, `@PreAuthorize("hasRole('ADMIN')")` 적용 |
| 3 | JWT secret 하드코딩 | **미해결** | `${JWT_SECRET:default}` 패턴으로 교체 필요 |
| 4 | DB 설정 개발 서버 미분리 | **확인 필요** | `application-dev.properties` 내용 검증 |

---

## 기능 갭 (배포 차단은 아님)

> MVP 이후 우선 구현 대상

| 기능 | 우선도 | 설명 |
|------|--------|------|
| 비밀번호 변경 | 중간 | |
| 주문 목록 페이징 | 낮음 | 현재 `List<Order>` 전체 반환 |
| 상품 이미지 URL | 낮음 | Product 엔티티에 필드 추가 |
| 배송지 관리 (저장된 주소) | 낮음 | |

---

## 변경 이력

| 날짜 | 변경 내용 |
|------|-----------|
| 2026-02-24 | 최초 작성. 전체 도메인 기능별 점검 완료. |
| 2026-02-24 | 배포 차단 이슈 #1, #2 해결. AdminProductController, AdminInventoryController 추가. InventoryController 삭제. |
