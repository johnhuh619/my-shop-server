# Mini-Shop Frontend UI SoT

This document is the Source of Truth for frontend UI implementation phases.
All redesign work is executed by phase, and phase completion is reported to the user immediately.

## Scope and Goal
- Target: customer-first commerce UI refinement with clear information hierarchy.
- Style target: fashion-commerce tone (clean, bold, practical), not decorative-heavy.
- Constraint: API behavior and field names must match `../docs/API_SPEC.md`.

## Primary References
1. `../docs/API_SPEC.md`
2. `./frontend_design_guide.txt`
3. `./IMPLEMENTATION_GUIDE.md`
4. `seed_docs` MCP (only when project docs are insufficient)

## Operating Rules
- Work only one phase as `IN_PROGRESS` at a time.
- A phase is marked `DONE` only when its DoD is satisfied.
- After each phase completion:
  - update this document (status + completion log)
  - report completion summary to user

## Phase Board
| Phase | Status | Objective | Main Targets |
| --- | --- | --- | --- |
| P0 | DONE | SoT setup and document wiring | `docs/*`, `AGENTS.md`, `README.md` |
| P1 | DONE | Global frame and navigation polish (desktop/mobile) | `src/app/layouts/AppLayout.tsx`, shared style tokens |
| P2 | DONE | Product list/detail redesign for fashion-commerce browsing | `src/features/product/pages/*` |
| P3 | DONE | Order/checkout/payment conversion flow clarity | `src/features/order/pages/*`, `src/features/payment/pages/*` |
| P4 | DONE | Refund/delivery/my/admin screens operational UX | feature pages under `refund`, `delivery`, `user`, `admin` |
| P5 | IN_PROGRESS | UI QA, responsive/accessibility pass, API integration re-check | all touched UI files |

## Definition of Done by Phase
### P0
- SoT file created and linked from frontend docs.
- Execution/reporting rules are explicit.

### P1
- Header/navigation works on desktop and mobile without overflow.
- Main layout spacing and section hierarchy are consistent.
- Shared UI tone (surface/border/emphasis) is applied consistently.

### P2
- Product list supports scan-first browsing (clear card hierarchy).
- Product detail has clear purchase section and stock/price emphasis.
- Loading/empty/error states are explicit and consistent.

### P3
- Checkout/payment path exposes next action clearly.
- Async/payment pending states are visible and understandable.
- Critical actions (cancel/confirm) have clear affordance and feedback.

### P4
- Refund/delivery/my/admin screens are optimized for operational tasks.
- State/status chips and action placement remain consistent across pages.
- Role separation (customer vs admin) remains clear in UI.

### P5
- `npm run lint` and `npm run build` pass.
- Representative mobile and desktop checks complete.
- API integration spot-check passes with configured `VITE_API_BASE_URL`.

## Completion Log
- 2026-02-26: P0 DONE
  - Added this SoT and linked it from `frontend/AGENTS.md`, `frontend/README.md`, and `frontend/docs/IMPLEMENTATION_GUIDE.md`.
  - Established phase board + DoD + reporting process.
- 2026-02-26: P1 DONE
  - Reworked global header and nav in `src/app/layouts/AppLayout.tsx` for responsive overflow-safe navigation.
  - Added authenticated user action area (login/logout) and admin route entry.
  - Updated main content frame spacing and top gradient accent for clearer section hierarchy.
  - Validation: `npm run lint`, `npm run build`.
- 2026-02-26: P2 DONE
  - Redesigned `src/features/product/pages/ProductListPage.tsx` for scan-first product browsing (clear stock/price emphasis and card hierarchy).
  - Redesigned `src/features/product/pages/ProductDetailPage.tsx` with purchase-focused sidebar, quantity controls, and explicit stock states.
  - Preserved API behavior and route flow (`detail -> order create -> checkout`).
  - Validation: `npm run lint`, `npm run build`.
- 2026-02-26: P3 DONE
  - Reworked checkout flow in `src/features/order/pages/CheckoutPage.tsx` into explicit step-based UX (prepare -> confirm -> result) with order-state specific actions.
  - Refined `OrderListPage.tsx` and `OrderDetailPage.tsx` to surface status-driven next actions (pay/cancel/delivery/refund) and improve conversion clarity.
  - Enhanced `PaymentListPage.tsx` with status filters and async-processing guidance for REQUESTED/PROCESSING states.
  - Validation: `npm run lint`, `npm run build`.
  - Follow-up (review fixes): closed `R-20260226-05`, `R-20260226-07` with payable-status gating and paymentKey prefill removal.
- 2026-02-26: P4 DONE
  - Redesigned `RefundListPage.tsx` with payment/item selection-driven refund creation and operational refund history filters.
  - Redesigned `DeliveryListPage.tsx` with status filters, manual refresh, and action-oriented delivery cards.
  - Upgraded `MyPage.tsx` with profile/account management sections, refresh action, and guarded deactivate/logout UX.
  - Replaced admin placeholder with operational admin center (`AdminPlaceholderPage.tsx`) for refunds, deliveries, and operations.
  - Added `adminApi.ts` for admin products/inventory/orders/refunds/deliveries endpoints.
  - Validation: `npm run lint`, `npm run build`.
- 2026-02-26: P5 IN_PROGRESS
  - Accessibility/responsive pass applied on operational screens:
    - Added `aria-pressed` to status/panel toggle buttons for delivery/refund/admin filters.
    - Added numeric input semantics (`type="number"`, `inputMode="numeric"`) to admin operational numeric fields.
    - Added mobile-safe wrapping on status header rows (`flex-wrap` + spacing).
  - Validation: `npm run lint`, `npm run build`.
  - Blocker: API spot-check could not be completed in current sandbox due network connection errors to configured `VITE_API_BASE_URL` and `localhost:8080`.

## Phase Completion Report Template
Use this template when reporting phase completion:

```
[Phase Complete] Px - <title>
- What changed:
- Files touched:
- Validation:
- Next phase:
```
