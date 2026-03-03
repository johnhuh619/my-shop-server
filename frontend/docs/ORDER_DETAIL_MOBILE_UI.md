# Order Detail Mobile UI

## Goal
- Align order detail with compact commerce-style mobile UI.
- Make key information scannable in this order:
  - order date/number/status
  - shipping info
  - ordered items
  - payment summary
  - actions

## Selected Layout Pattern
- Stacked section cards with tight spacing.
- Product list as repeated row cards:
  - thumbnail placeholder
  - product title/quantity/unit price/subtotal
  - two quick action buttons
- Payment summary as key/value rows with divider lines.
- Action block grouped at bottom for navigation and order operations.

## Status Mapping Rules
- Top area shows `OrderStatus` via shared status chip.
- Payment area shows latest payment status via shared status chip.
- No new enum values or API fields introduced.

## Loading / Error / Empty States
- Main order fetch:
  - loading: `LoadingView`
  - error: `ErrorView`
- Delivery/payment section fetch:
  - loading text in section
  - inline error text + retry button
  - empty informational text when data is not present

## Data Contract Notes
- Uses existing contract fields only:
  - order: id/status/totalAmount/items/createdAt
  - delivery: recipient/address/phone/status/timeline
  - payment: amount/status/createdAt
- Image-like fields are not in current order item contract, so a visual placeholder is used.

## Design References
- `frontend/docs/frontend_design_guide.txt`
  - Layout Rules
  - Color and State Rules
  - Interaction Rules
- seed_docs:
  - `react/components/list`
  - `react/components/action-button`
- context7:
  - React list rendering and stable key practices (`/websites/react_dev`)
