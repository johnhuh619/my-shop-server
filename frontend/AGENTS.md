# Mini-Shop Frontend Agent Rules (Short)

This file is intentionally short. Keep only high-impact guardrails here.
Detailed implementation rules live in `frontend/docs/IMPLEMENTATION_GUIDE.md`.

## Source of Truth
- UI phase SoT: `./docs/FRONTEND_UI_SOT.md`
- Repo rules: `../AGENTS.md`
- API contract: `../docs/API_SPEC.md`
- Frontend detail rules: `./docs/IMPLEMENTATION_GUIDE.md`
- Frontend design rules: `./docs/frontend_design_guide.txt`
- Review log: `./docs/REVIEW_LOG.md`
- Troubleshooting log (STAR): `./docs/TROUBLESHOOTING_LOG.md`

## Priority
1. Contract correctness (`API_SPEC` exact match)
2. SoT phase execution (`FRONTEND_UI_SOT.md`)
3. Auth/error reliability
4. UI polish

## Non-Negotiable Rules
- Use domain-first structure: `src/app`, `src/features/*`, `src/shared/*`.
- Separate public/user/admin routes and enforce role guards.
- Attach `Authorization: Bearer <accessToken>` to authenticated APIs.
- Use `X-Idempotency-Key` for `POST /api/payments`.
- Keep session strategy aligned with current backend contract:
  - `accessToken` in memory
  - `refreshToken` in `localStorage`
  - on 401: refresh then retry once
  - refresh failure: logout + clear session + redirect
- In `POST /api/payments/confirm`, request `orderId` must be `tossOrderId` (string), not DB order id.
- Treat payment/refund/delivery as async status flows; do not assume immediate terminal state.
- Parse timestamps carefully (`UserResponse.createdAt` format differs from other timestamp fields).
- For UI implementation, read `./docs/frontend_design_guide.txt` first.
- If design/component details are unclear, use `seed_docs` MCP to fetch official docs and then apply project rules.

## Deployment Basics
- Deploy frontend on Vercel with Root Directory `frontend`.
- Require `VITE_API_BASE_URL`.
- Keep SPA rewrite and backend CORS aligned with deployed frontend origin.

## Prohibitions
- Do not rename API fields or enum values from spec.
- Do not expose admin actions on customer screens.
- Do not skip server logout in normal logout flow.

