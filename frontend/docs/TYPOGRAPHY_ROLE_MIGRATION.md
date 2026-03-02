# Typography Role Migration

## Goal
- Re-define frontend text hierarchy with role-based typography.
- Align all `Text` scale usage with SEED typography guidance.

## Source
- `seed_docs`:
  - `docs/foundation/typography/overview`

## Role Mapping
- `pageTitle`: `t7Bold`
- `sectionTitle`: `t6Bold`
- `cardTitle`, `keyValueStrong`: `t5Bold` / `t5Medium`
- `body`: `t4Regular` (`t4Medium` for emphasized lines)
- `helper`, `meta`, `chip`: `t3Regular`

## Applied Scope
- Frontend `*.tsx` files under `frontend/src` using `Text textStyle`.
- Replaced oversized body text (`t6Regular`) and undersized headings (`t2Bold`, `t3Bold`, `t4Bold`) to role-appropriate scales.
- Removed ad-hoc large class typography from product title card (`text-lg`) and moved to tokenized `Text`.
- Status chip typography normalized to `t3Regular`.

## Verification
- `npx tsc -b`
- `npm run lint`
