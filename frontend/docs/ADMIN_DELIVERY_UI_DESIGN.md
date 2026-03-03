# Admin Delivery UI Design

## Goal
- Make admin delivery operations queue-first so operators can process all pending deliveries in one place.
- Keep filtering and status transitions fast without extra round-trips per filter click.

## Selected Layout Pattern
- Queue summary block at top:
  - action-required count
  - delivered count
  - canceled count
- Filter/action row:
  - status filter chips with counts
  - explicit refresh button
- Delivery card list:
  - recipient/order/address/timeline info
  - stale warning line for delayed work
  - action buttons for valid next transitions
  - critical action warning before cancel
- Delivery create block is placed after queue list so processing flow stays primary.

## Status Mapping Rules
- `ACTION_REQUIRED` (UI-only aggregate filter):
  - includes `PREPARING`, `SHIPPED`, `IN_TRANSIT`
- Per-status transitions:
  - `PREPARING` -> `ship`
  - `SHIPPED` -> `in-transit`
  - `IN_TRANSIT` -> `deliver`
  - non-terminal states -> `cancel` (with confirmation)
- Display uses shared enum chip mapping in:
  - `frontend/src/shared/ui/StatusChip.tsx`

## Loading / Error / Empty States
- Initial delivery fetch:
  - loading -> `LoadingView`
  - error -> `ErrorView` with retry
- Refresh:
  - uses button loading state and inline "syncing" text
- Empty:
  - if filtered results are empty, show contextual empty message

## Query and Performance Decision
- Fetch all admin deliveries once per panel query key:
  - query key: `['admin', 'deliveries']`
  - API: `GET /api/admin/deliveries` (no status param)
- Apply filtering/sorting client-side for immediate operator response:
  - no network call on every filter toggle
  - action-required statuses are prioritized in list ordering

## Risk Visibility Rules
- Show stale warning messages:
  - `PREPARING` older than 24h
  - `SHIPPED` older than 72h
- `cancel` action requires explicit confirmation via dialog.

## References
- Project guide:
  - `frontend/docs/frontend_design_guide.txt`
    - Layout Rules
    - Interaction Rules
    - Admin UX Rules
    - Output Expectations for Implementation
- seed_docs references:
  - `react/components/list`
  - `react/components/action-button`
  - `react/components/segmented-control` (pattern reference for quick exclusive filtering)
