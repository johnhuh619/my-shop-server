# Frontend Review Log

Purpose: persist code review findings and their status.
Detailed troubleshooting narratives are stored in `frontend/docs/TROUBLESHOOTING_LOG.md`.

## Status Rules
- `OPEN`: identified, not yet fixed
- `IN_PROGRESS`: fix is being implemented or validated
- `RESOLVED`: fixed and validated with evidence

## Entry Template
### [YYYY-MM-DD] Review <ID>
- Scope:
- Reviewer:
- Validation:

| Finding ID | Severity | Status | Summary | Evidence | Troubleshooting ID | Resolution |
|---|---|---|---|---|---|---|
| R-YYYYMMDD-01 | High/Medium/Low | OPEN |  | `path:line` | TS-YYYYMMDD-01 (optional) |  |

## Reviews
### [2026-02-26] Review R-20260226-01
- Scope: P0 ~ P2 implementation (`AppLayout`, `ProductListPage`, `ProductDetailPage`)
- Reviewer: Codex
- Validation:
  - `npm run lint` passed
  - `npm run build` passed

| Finding ID | Severity | Status | Summary | Evidence | Troubleshooting ID | Resolution |
|---|---|---|---|---|---|---|
| R-20260226-01 | Medium | RESOLVED | Non-authenticated users could see protected navigation menus. | `src/app/layouts/AppLayout.tsx:63` | TS-20260226-01 | 2026-02-26: Navigation render is now gated by `auth.isAuthenticated`. Non-auth state shows login action only. |
| R-20260226-02 | Medium | OPEN | `page` query value is not sanitized; invalid values can cause API errors. | `src/features/product/pages/ProductListPage.tsx:47` | - | Pending |
| R-20260226-03 | Low | OPEN | Sold-out product can force quantity input to `0`, creating UI inconsistency. | `src/features/product/pages/ProductDetailPage.tsx:53` | - | Pending |
| R-20260226-04 | Low | OPEN | Search input state can desync from URL query after browser navigation. | `src/features/product/pages/ProductListPage.tsx:49` | - | Pending |

### [2026-02-26] Review R-20260226-02
- Scope: P3 implementation (`CheckoutPage`, `OrderListPage`, `OrderDetailPage`, `PaymentListPage`)
- Reviewer: Codex
- Validation:
  - `npm run lint` passed
  - `npm run build` passed

| Finding ID | Severity | Status | Summary | Evidence | Troubleshooting ID | Resolution |
|---|---|---|---|---|---|---|
| R-20260226-05 | Medium | RESOLVED | Checkout page can still show/allow payment confirm action for non-payable order statuses (e.g. `CANCELED`, `REFUND_REQUESTED`, `REFUNDED`). | `src/features/order/pages/CheckoutPage.tsx:108` | TS-20260226-02 | 2026-02-26: Checkout flow is now rendered only for `CREATED` orders; non-payable statuses show blocking guidance and alternative actions. |
| R-20260226-06 | Medium | RESOLVED | Checkout result text always says payment is completed even though async/payment status can still be non-terminal (`PROCESSING`). | `src/features/order/pages/CheckoutPage.tsx:250` | - | 2026-02-26: Backend contract confirmed that `POST /api/payments/confirm` returns `COMPLETED` consistently, so this is not a defect. |
| R-20260226-07 | Low | RESOLVED | Checkout `paymentKey` is prefilled with test value, increasing accidental invalid confirm submissions in real flow. | `src/features/order/pages/CheckoutPage.tsx:48` | TS-20260226-03 | 2026-02-26: Removed test default value and auto-hydrated confirm fields (`paymentKey`, `orderId`, `amount`) from redirect query params when present. |

### [2026-02-26] Review R-20260226-03
- Scope: P4/P5 implementation (`RefundListPage`, `DeliveryListPage`, `MyPage`, `AdminPlaceholderPage`, `adminApi`)
- Reviewer: Codex
- Validation:
  - `npm run lint` passed
  - `npm run build` passed

| Finding ID | Severity | Status | Summary | Evidence | Troubleshooting ID | Resolution |
|---|---|---|---|---|---|---|
| R-20260226-08 | Medium | OPEN | Admin operations accept empty/zero IDs (`orderId`, `productId`) and can call invalid endpoints like `/api/admin/orders/0/complete` and `/api/admin/inventories/0/add-stock`. | `src/features/admin/pages/AdminPlaceholderPage.tsx:105`, `src/features/admin/pages/AdminPlaceholderPage.tsx:175`, `src/features/admin/pages/AdminPlaceholderPage.tsx:188` | - | Pending |
| R-20260226-09 | Medium | OPEN | Refund creation does not enforce selected order item max quantity in mutation validation; client can submit over-quantity refunds and fail server-side. | `src/features/refund/pages/RefundListPage.tsx:55`, `src/features/refund/pages/RefundListPage.tsx:203` | - | Pending |
| R-20260226-10 | Low | OPEN | P5 numeric-input semantics are partially applied: `unitPrice` input lacks `inputMode=\"numeric\"` unlike other admin numeric fields. | `src/features/admin/pages/AdminPlaceholderPage.tsx:630` | - | Pending |
