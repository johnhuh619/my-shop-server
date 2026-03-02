# Order UI Design

## Goal
- Align order experience with commerce best practices:
  - `Order List` supports fast scan and clear next actions.
  - `Order Detail` groups information into clear sections: date/order items/payment/delivery.

## Selected Layout Pattern
- Order list card structure:
  - date + status
  - order number + summary
  - amount + item count
  - action button row
- Order detail section structure:
  - order summary block
  - order item list block
  - payment information block
  - delivery information block
  - action block
- For responsive button groups and overflow-prone rows:
  - use native `div` with CSS grid/flex (`grid-cols-*`) instead of forcing one-axis layout primitives.

## Routing Pattern
- Canonical order routes:
  - `/me/orders`
  - `/me/orders/:orderId`
- Action routes from list/detail:
  - 배송 조회 -> `/me/deliveries?orderId={orderId}`
  - 환불 페이지 -> `/me/refunds?orderId={orderId}`
  - 결제 진행 (if created) -> `/checkout/:orderId`

## Data Mapping Rules
- Do not invent contract fields.
- Order section uses `OrderResponse`.
- Payment section uses `PaymentResponse[]` filtered by `orderId`.
- Delivery section uses `/api/deliveries/order/{orderId}` (single-order query).

## Status Mapping Rules
- Keep enum visual mapping in shared chip:
  - `src/shared/ui/StatusChip.tsx`
- Reuse the same chip for `OrderStatus`, `PaymentStatus`, and `DeliveryStatus`.

## Loading / Error / Empty States
- Order detail top-level fetch:
  - loading -> `LoadingView`
  - error -> `ErrorView` with retry
- Payment/Delivery subsection fetches:
  - loading text in section
  - inline error with retry button
  - empty informational text

## Design Sources
- Project policy:
  - `frontend/docs/frontend_design_guide.txt`
    - Layout Rules
    - Interaction Rules
    - Color and State Rules
    - Output Expectations for Implementation
- SEED references:
  - `react/components/list`
  - `react/components/action-button`
  - `react/components/divider`
