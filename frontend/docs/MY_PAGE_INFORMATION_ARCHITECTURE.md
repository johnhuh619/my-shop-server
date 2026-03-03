# My Page Information Architecture

## Goal
- Place user self-service features under one hub route: `/me/*`.
- Keep account and post-purchase operations (orders, payments, refunds, deliveries) in one predictable navigation model.

## Canonical Routes
- `/me/account`
- `/me/orders`
- `/me/orders/:orderId`
- `/me/payments`
- `/me/refunds`
- `/me/deliveries`

## Supporting Routes
- Keep checkout as a dedicated payment flow route:
  - `/checkout/:orderId`
- Keep cart as a pre-order editable workspace:
  - `/cart`

## Legacy Compatibility
- Legacy routes are redirected to canonical routes:
  - `/orders` -> `/me/orders`
  - `/orders/:orderId` -> `/me/orders/:orderId`
  - `/payments` -> `/me/payments`
  - `/refunds` -> `/me/refunds`
  - `/deliveries` -> `/me/deliveries`
- Redirect preserves `search` and `hash` so bookmarked filter URLs do not break.

## Selected Layout Pattern
- Parent layout route: `MyPageLayout`
- Responsibilities:
  - section-level navigation (account/orders/payments/refunds/deliveries)
  - contextual section description
  - nested rendering via `Outlet`
- Navigation UI:
  - use `NavLink` for active-state semantics
  - use native `div + flex + overflow-x-auto` for horizontal scrolling on small screens

## Status Mapping Rules
- Keep one status presentation source in `src/shared/ui/StatusChip.tsx`.
- `MyPageLayout` does not redefine status colors or labels.

## Loading / Error / Empty States
- Domain pages keep their own async states:
  - `OrderListPage`
  - `OrderDetailPage`
  - `PaymentListPage`
  - `RefundListPage`
  - `DeliveryListPage`
- `MyPageLayout` is structural only and does not own data fetching.

## Design Sources
- `frontend/docs/frontend_design_guide.txt`
  - clear section boundaries
  - responsive layout
  - predictable interactions and async visibility
  - allow native `div` for responsive axis/overflow cases
- seed_docs MCP references:
  - `react/components/tabs`
  - `react/components/segmented-control`
  - `react/components/list`
