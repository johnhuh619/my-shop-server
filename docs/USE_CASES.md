# Use Cases

## UC-01 회원가입
- 흐름: `POST /api/users/register` -> 이메일 중복 체크 -> 비밀번호 암호화 -> CUSTOMER 생성 -> 저장
- 결과: 활성 사용자 생성

## UC-02 로그인/토큰 발급
- 흐름: `POST /api/auth/login` -> 이메일/비밀번호 검증 -> 사용자 활성 상태 검증 -> Access/Refresh JWT 발급
- 결과: `{accessToken, refreshToken}` 반환

## UC-03 로그아웃
- 흐름: `POST /api/auth/logout` -> `Authorization` 헤더 access token 파싱 -> (선택) refresh token 파싱 -> Redis blacklist 저장(TTL)
- 결과: 토큰 재사용 차단

## UC-04 상품 등록/조회
- 흐름(등록): `POST /api/products` -> 상품 저장 -> 재고 엔티티 초기화
- 흐름(조회): `GET /api/products`, `GET /api/products/{id}`
- 결과: 상품 카탈로그 제공

## UC-05 주문 생성(재고 예약)
- 흐름: `POST /api/orders` -> 요청 상품을 `productId` 순 정렬 -> 각 상품 재고 `reserve` -> `OrderItem` 스냅샷 생성 -> 주문 저장(CREATED)
- 예외: 재고 부족 시 주문 생성 전체 롤백
- 결과: 주문 생성 + 재고 예약 완료

## UC-06 주문 취소(사용자)
- 흐름: `PATCH /api/orders/{id}/cancel` -> 본인 주문 조회 -> 주문 아이템 순회 `release` -> 주문 CANCELED
- 조건: CREATED 상태여야 함
- 결과: 예약 재고 복구

## UC-07 결제 준비(멱등)
- 흐름: `POST /api/payments` + `X-Idempotency-Key` -> `(userId,idempotencyKey)` 기존 결제 조회 -> 없으면 REQUESTED 결제 생성
- 조건: 주문 상태 CREATED
- 결과: 결제 준비 완료, 중복 요청은 동일 결제 반환

## UC-08 결제 확정 성공
- 흐름: `POST /api/payments/confirm` -> 결제 상태 잠금/검증(REQUESTED/PROCESSING) -> PG confirm 호출 -> Payment COMPLETED -> `PaymentCompletedEvent` 발행
- 비동기 후속: 이벤트 리스너 -> `orderService.markAsPaid(orderId)` -> 주문 PAID + 재고 confirm(실재고 확정)
- 결과: 결제 완료 + 주문 유료화 + 재고 확정

## UC-09 결제 확정 실패
- 흐름: PG confirm 실패 -> Payment FAILED -> `PaymentFailedEvent` 발행
- 비동기 후속: 이벤트 리스너 -> `cancelOrderBySystem` -> 주문 CANCELED + 재고 release
- 보상: 5분 주기 스케줄러가 FAILED 지연 건 재처리
- 결과: 결제 실패 시 재고 묶임 방지

## UC-10 주문 자동 만료
- 흐름: 1분 주기 스케줄러 -> CREATED + 30분 경과 주문 조회 -> `expireOrder` -> 재고 release + 주문 EXPIRED
- 결과: 미결제 장기 점유 해소

## UC-11 환불 요청(사용자)
- 흐름: `POST /api/refunds` -> 본인 Payment 조회 -> Order 상태 검증(PAID/REFUND_REQUESTED) -> 환불 수량 중복 검증 -> Refund REQUESTED 생성
- 보조 상태: 첫 환불 요청이면 주문 REFUND_REQUESTED 전이
- 결과: 관리자 승인 대기

## UC-12 환불 승인/거절(관리자)
- 흐름(승인): `POST /api/admin/refunds/{id}/approve` -> Refund APPROVED -> PG cancel -> 성공 시 COMPLETED, 실패 시 FAILED
- 비동기 후속: `RefundCompletedEvent` -> 전액 환불이면 주문 REFUNDED, 재고 `addStock` 복구
- 흐름(거절): `POST /api/admin/refunds/{id}/reject` -> REJECTED
- 결과: 환불 처리 종료

## UC-13 배송 완료 처리(관리자)
- 흐름: `POST /api/admin/orders/{id}/complete` -> `completeOrder` 호출
- 상태 규칙: PAID면 COMPLETED 전이, 이미 COMPLETED면 멱등 성공 반환, 그 외 상태는 오류
- 결과: 배송 완료 상태 확정
