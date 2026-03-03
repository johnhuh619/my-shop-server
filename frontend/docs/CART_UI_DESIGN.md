# Cart UI Design (2026-02-27)

## Scope
- Product detail purchase action split:
  - `Buy Now`: create order -> checkout
  - `Add to Cart`: local cart add -> modal choice (`Go to Cart` / `Go to Products`)
- New `/cart` page for multi-item local cart checkout.

## Design Inputs
- Project policy: `frontend/docs/frontend_design_guide.txt`
- Decision source: `docs/troubleshooting/CART_DESIGN_DECISION.md`
- SEED references (MCP):
  - `react/components/alert-dialog` (modal decision pattern)
  - `react/components/checkbox` (multi-select pattern)
  - `react/components/list` (row/list interaction pattern)

## Layout Pattern
- Product detail:
  - desktop: left image, right information and purchase controls
  - right column order: product info -> quantity -> estimated amount -> action buttons
- Cart page:
  - desktop: left item list, right payment summary
  - mobile: stacked list then summary

## Status and Interaction Rules
- Non-purchasable items (`status != ACTIVE` or `quantityAvailable < 1`) are shown but not selectable for checkout.
- Quantity is editable per item with plus/minus and numeric input.
- Selected item subtotal and cart total are both visible before checkout.

## Loading / Error / Empty States
- Empty: dedicated empty-state section with `Browse Products` CTA.
- Checkout pending: button loading state.
- API errors: inline error text from `getErrorMessage`.
- Dangerous actions:
  - remove item / clear all require explicit confirmation.

## Data and Flow
- Cart state: localStorage (`minishop.cart.v1`)
- Checkout from cart:
  - selected cart items -> `CreateOrderRequest.items` -> `POST /api/orders`
  - success -> remove selected items from local cart -> navigate `/checkout/{orderId}`
