# Frontend Troubleshooting Log

Purpose: accumulate troubleshooting history with enough detail for fast re-understanding and recurrence prevention.

## Status Rules
- `OPEN`: issue identified, no validated fix yet
- `IN_PROGRESS`: fix work or validation in progress
- `RESOLVED`: fix validated and closed

## Writing Rules
- Record one troubleshooting case per entry.
- Link related review findings (`R-*`) from `frontend/docs/REVIEW_LOG.md`.
- STAR must be explicit in every entry:
  - `Situation`
  - `Task`
  - `Action`
  - `Result`
- Include root cause, changed files, validation evidence, and prevention actions.
- Keep old history; never delete resolved records.

## Entry Template
### [YYYY-MM-DD] TS-YYYYMMDD-XX
- Related Finding IDs:
- Status:
- Severity:
- Owner:
- Started At:
- Resolved At:
- Scope:

#### Summary
- Symptom:
- User Impact:

#### STAR
- Situation:
- Task:
- Action:
  1.
  2.
  3.
- Result:

#### Root Cause
- Direct cause:
- Why it escaped earlier checks:

#### Fix Details
- Changed files:
  - `path:line`
- Key changes:
  - 

#### Validation Evidence
- `command`: outcome
- Manual checks:
  - 

#### Prevention / Follow-up
- 

## Troubleshooting Cases
### [2026-02-26] TS-20260226-01
- Related Finding IDs: `R-20260226-01`
- Status: `RESOLVED`
- Severity: Medium
- Owner: Codex
- Started At: 2026-02-26
- Resolved At: 2026-02-26
- Scope: Header navigation visibility for non-authenticated users

#### Summary
- Symptom: Non-authenticated users could see user-protected navigation items (`/orders`, `/payments`, `/refunds`, `/deliveries`, `/me`) in the global header.
- User Impact: UX confusion and permission-boundary ambiguity; clicking items redirected to login but exposed protected IA prematurely.

#### STAR
- Situation: During post-implementation review of P1/P2 UI changes, protected menus were found to be visible before login.
- Task: Restrict global header navigation so non-authenticated state exposes only login action while preserving authenticated navigation behavior.
- Action:
  1. Reviewed layout rendering path in `AppLayout` and confirmed navigation was unconditionally rendered.
  2. Gated `<nav>` rendering with `auth.isAuthenticated`.
  3. Re-ran static checks/build to confirm no regression.
- Result: Non-authenticated users now see login action only, and protected navigation is shown only after authentication.

#### Root Cause
- Direct cause: `AppLayout` rendered navigation regardless of authentication state.
- Why it escaped earlier checks: initial validation focused on lint/build success and route guard behavior, not pre-auth header IA visibility.

#### Fix Details
- Changed files:
  - `src/app/layouts/AppLayout.tsx:63`
- Key changes:
  - Wrapped navigation block with `auth.isAuthenticated ? ... : null`.

#### Validation Evidence
- `npm run lint`: passed
- `npm run build`: passed
- Manual checks:
  - Logged-out header shows only login button.
  - Logged-in header shows domain navigation and role-based admin link.

#### Prevention / Follow-up
- Add header visibility checks to UI phase DoD for auth boundaries.
- Keep future auth-related review findings linked to troubleshooting entries via IDs.

### [2026-02-26] TS-20260226-02
- Related Finding IDs: `R-20260226-05`
- Status: `RESOLVED`
- Severity: Medium
- Owner: Codex
- Started At: 2026-02-26
- Resolved At: 2026-02-26
- Scope: Checkout payable-state gating

#### Summary
- Symptom: Checkout page exposed confirm flow for non-payable order states (`CANCELED`, `REFUND_REQUESTED`, `REFUNDED`).
- User Impact: Misleading UI path and potential invalid API attempts from states that should not enter payment confirm.

#### STAR
- Situation: P3 review identified that checkout flow rendering condition only excluded expired/already-paid states.
- Task: Restrict checkout flow to payable (`CREATED`) orders and provide clear blocking guidance for non-payable states.
- Action:
  1. Added explicit non-payable status handler for `EXPIRED`, `CANCELED`, `REFUND_REQUESTED`, `REFUNDED`.
  2. Changed render condition so prepare/confirm steps are shown only when order status is `CREATED`.
  3. Added alternative navigation actions (`order detail`, `product list`) in blocking section.
- Result: Non-payable orders cannot access confirm UI, and users are guided to valid next actions.

#### Root Cause
- Direct cause: Checkout step rendering condition was broader than payable-order boundary.
- Why it escaped earlier checks: Initial P3 validation emphasized build/lint and primary happy-path checkout execution.

#### Fix Details
- Changed files:
  - `src/features/order/pages/CheckoutPage.tsx:92`
- Key changes:
  - Added `isOrderNonPayable` and `getNonPayableStatusMessage`.
  - Restricted checkout steps to `isOrderCreatable`.

#### Validation Evidence
- `npm run lint`: passed
- `npm run build`: passed
- Manual checks:
  - Non-payable statuses render blocking section only.
  - `CREATED` status keeps full prepare/confirm flow.

#### Prevention / Follow-up
- Include payable-status gating checks in checkout DoD regression checklist.

### [2026-02-26] TS-20260226-03
- Related Finding IDs: `R-20260226-07`
- Status: `RESOLVED`
- Severity: Low
- Owner: Codex
- Started At: 2026-02-26
- Resolved At: 2026-02-26
- Scope: Checkout confirm form default inputs

#### Summary
- Symptom: `paymentKey` input had a test default value.
- User Impact: Increased chance of accidental invalid confirm request in real environment.

#### STAR
- Situation: P3 review flagged that hardcoded test `paymentKey` remained in checkout form.
- Task: Remove misleading default and auto-hydrate confirm inputs from redirect query when available.
- Action:
  1. Changed `paymentKeyInput` initial state from test string to empty string.
  2. Added query-param hydration for `paymentKey`, `orderId`, `amount` via `useSearchParams`.
  3. Re-validated confirm button enable condition for both redirected and manual input cases.
- Result: Redirect-based checkout now pre-fills confirm inputs automatically, while still allowing manual fallback input.

#### Root Cause
- Direct cause: Development convenience default was left in production-facing form.
- Why it escaped earlier checks: Functional checks focused on request success path, not form prefill safety.

#### Fix Details
- Changed files:
  - `src/features/order/pages/CheckoutPage.tsx:48`
- Key changes:
  - Removed hardcoded test default for `paymentKey`.
  - Auto-hydrated confirm inputs (`paymentKey`, `orderId`, `amount`) from redirect query params.

#### Validation Evidence
- `npm run lint`: passed
- `npm run build`: passed
- Manual checks:
  - Redirect query params auto-fill confirm inputs.
  - Confirm button remains disabled until `paymentKey/orderId/amount` are valid.

#### Prevention / Follow-up
- Disallow test fixture prefill in user-facing payment forms unless explicitly gated by development mode.

