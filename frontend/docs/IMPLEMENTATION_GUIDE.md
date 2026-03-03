# Mini-Shop Frontend Implementation Guide

Detailed frontend implementation rules for this repository.
Use with `frontend/AGENTS.md`, `frontend/docs/FRONTEND_UI_SOT.md`, and `docs/API_SPEC.md`.

## Execution Baseline (SoT)
- Execute UI work by phase from `frontend/docs/FRONTEND_UI_SOT.md`.
- Mark phase completion only after its DoD is satisfied.
- Report completion to user at each phase boundary.

## Review Log Operation
- Store each code review result in `frontend/docs/REVIEW_LOG.md`.
- Every finding must have an ID and one of these statuses: `OPEN`, `IN_PROGRESS`, `RESOLVED`.
- When troubleshooting is completed, keep the original finding and append a resolution note with date, changed files, validation command results, and linked troubleshooting ID (`TS-*`).
- Do not silently remove old findings; close them with explicit `RESOLVED` history.

## Troubleshooting Log Operation
- Store every troubleshooting case in `frontend/docs/TROUBLESHOOTING_LOG.md`.
- One issue per entry, and each entry must include explicit STAR sections:
  - `Situation`
  - `Task`
  - `Action`
  - `Result`
- Use ID format `TS-YYYYMMDD-XX` and link related review finding IDs (`R-*`).
- On resolution, mark status `RESOLVED` and record:
  - root cause
  - changed files
  - validation commands and outcomes
  - prevention/follow-up actions

## Design Reference Flow (Project Guide + seed_docs MCP)
UI implementation follows this order:
1. Confirm current phase and DoD in `frontend/docs/FRONTEND_UI_SOT.md`.
2. Read `frontend/docs/frontend_design_guide.txt` for project-specific design direction.
3. If component behavior, tokens, or usage patterns are unclear, query `seed_docs` MCP.
4. Prefer `docs` section for foundation/guidelines and `react` section for component APIs.
5. Resolve conflicts in this order:
   - `docs/API_SPEC.md` (API contract)
   - `frontend/docs/FRONTEND_UI_SOT.md` (phase scope and completion criteria)
   - `frontend/docs/frontend_design_guide.txt` (project design policy)
   - seed_docs official reference (implementation details)

## Scope (Phase 1 MVP)
Implement customer + admin core flows with behavior-first priority.

### Customer
- Auth: login, refresh, logout, register
- Product: list, detail
- Order: create, list, detail, cancel
- Payment: prepare, confirm, list, detail
- Refund: create, list, detail
- Delivery: list, detail, by order
- User: me, deactivate

### Admin
- Product: create, update, deactivate
- Inventory: get, add stock
- Order: complete
- Refund: list, detail, approve, reject
- Delivery: list, detail, create, ship, in-transit, deliver, cancel

## Recommended Frontend Structure
- `src/app/`: app bootstrap, providers, router, guards
- `src/features/auth/`
- `src/features/user/`
- `src/features/product/`
- `src/features/order/`
- `src/features/payment/`
- `src/features/refund/`
- `src/features/delivery/`
- `src/features/admin/`
- `src/shared/api/`: HTTP client, interceptors, response parser
- `src/shared/types/`: shared DTOs/enums
- `src/shared/ui/`: reusable components
- `src/shared/lib/`: utility helpers

## Routing and Guards
- Public: login, register, product list/detail
- User-protected: user/order/payment/refund/delivery
- Admin-protected: `/admin/*`

Guard behavior:
- No token: block protected routes
- Non-admin: block admin routes
- 401: run refresh flow, then fail to logout path

## API Contract Rules
Always match `docs/API_SPEC.md`.

### Response Type Standards
```ts
export type ApiResponse<T> = {
  success: boolean
  data: T | null
  errorCode?: string
  errorMessage?: string
}

export type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}
```

### Request Standards
- Path values stay in path.
- Query values are serialized explicitly.
- Header values only when required by endpoint.
- Auth-required endpoints must include Bearer access token.
- `POST /api/payments` requires `X-Idempotency-Key`.

## Auth and Session Strategy
Backend contract is token-in-body, not cookie session.

- `accessToken`: memory-only
- `refreshToken`: `localStorage`

401 handling:
1. call `/api/auth/refresh` with refresh token
2. update access token in memory
3. retry original request once

If refresh fails:
1. attempt `/api/auth/logout`
2. clear session state
3. redirect to login

Logout:
- Prefer server logout call before local token cleanup.

## Critical Domain Flows
### Payment
1. Create order
2. `POST /api/payments` (idempotency key required)
3. Receive `tossOrderId` + amount
4. After PG redirect, `POST /api/payments/confirm`

Important:
- Confirm payload `orderId` must be `tossOrderId` (string).
- Do not send DB order id in confirm `orderId`.

### Order -> Payment (Current Contract)
- `POST /api/orders` must include delivery snapshot fields:
  - `recipientName` (required)
  - `recipientPhone` (required)
  - `address` (required)
  - `addressDetail` (optional)
  - `zipCode` (required)
- Checkout UI must treat payment confirm as phase completion, not whole flow completion.
- After confirm success, surface async follow-up state (`markAsPaid` / delivery creation) with polling + manual refresh path.

### Async Status Flows
Payment/refund/delivery can update asynchronously.
Use refetch or polling and keep UI states non-terminal until confirmed.

### Expired Order
`EXPIRED` disables payment/cancel actions and shows explicit expired state.

## Enum and Timestamp Handling
- Enum values must match API spec exactly.
- Most timestamps are UTC ISO-8601.
- `UserResponse.createdAt` uses local datetime format and needs separate parsing.

## MVP Screen Checklist
Customer:
- Login/Register
- Product list/detail
- Order create/list/detail/cancel
- Payment prepare/confirm/history
- Refund request/history
- Delivery tracking
- My profile

Admin:
- Product management
- Inventory management
- Order completion
- Refund approval/rejection
- Delivery status management

## Acceptance Scenarios
1. Login -> product -> order -> payment prepare -> payment confirm
2. Forced 401 -> refresh success -> request retry success
3. Refresh failure -> session clear -> login redirect
4. Non-admin blocked from admin routes/APIs
5. Required header missing -> error UI handling
6. Confirm uses `tossOrderId` correctly
7. Pagination uses `PageResponse` fields

## Deployment Rules
- Vercel Root Directory: `frontend`
- Required env: `VITE_API_BASE_URL`
- SPA rewrite required
- Backend CORS must allow deployed frontend origin

## Prohibitions
- Do not change API field/enum names without backend contract change.
- Do not mix DB order id and `tossOrderId`.
- Do not hide auth failures as success states.
- Do not expose admin-only actions on customer UI.
- Do not skip server logout in normal logout flow.
