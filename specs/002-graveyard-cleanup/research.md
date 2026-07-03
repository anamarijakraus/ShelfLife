# Phase 0 Research: Graveyard Page & Automatic Permanent Cleanup

The user's plan instructions already fixed the stack, the no-new-column constraint, and the
delete-then-read read pattern (see plan.md Technical Context), so no `NEEDS CLARIFICATION` markers
exist. This document resolves the remaining implementation-level design decisions needed before
Phase 1.

## 1. Deriving graveyard membership without a new column

**Decision**: Keep using the single existing `expires_at` column. A link is:
- **active** if `expiresAt > now`
- **in the graveyard** if `expiresAt <= now` and `expiresAt > now.minus(30, DAYS)`
- **due for deletion** if `expiresAt <= now.minus(30, DAYS)`

Because `expiresAt` already encodes `savedAt + 168h`, the 30-day graveyard deadline is simply
`expiresAt + 30 days` — equivalently, "due for deletion" is `expiresAt <= now - 30 days`. No new
column or index is needed; the existing `idx_link_expires_at` index serves all three predicates.

**Rationale**: Directly implements the spec's Key Entities note ("implicit lifecycle stage derived
from time") and the user's explicit plan instruction ("derive a link's lifecycle stage purely from
its existing saved timestamp and the fixed durations — no new status column"). Reusing one column
for three predicates keeps the schema and Constitution Principle I's "no speculative abstraction"
intact.

**Alternatives considered**: A separate `graveyardExpiresAt` persisted column, computed and stored
once the link leaves the active list — rejected as an unnecessary write (would require a mutation
the moment a link expires, contradicting the read-time-only enforcement model and adding a second
timestamp that must always agree with the first). A `status` enum column updated by a job —
explicitly rejected by the user's instruction and by Constitution Principle I (no state machine
column when the state is a pure, cheap function of an existing timestamp).

## 2. Permanent deletion mechanism: bulk `DELETE`, not a derived `deleteBy` query

**Decision**: Add a `@Modifying @Transactional @Query("DELETE FROM Link l WHERE l.expiresAt <= :threshold")`
method to `LinkRepository`, executed at the start of `LinkService.listGraveyardLinks()` with
`threshold = now.minus(30, DAYS)`, immediately followed by the range-query `SELECT` that returns
the remaining graveyard rows.

**Rationale**: Directly implements the spec's Clarifications (2026-07-03): deletion must be a real
row removal on read, not a query-level filter. A plain Spring Data derived `deleteByExpiresAt...`
method would internally `SELECT` then remove entities one at a time (N+1 individual `DELETE`
statements) — a single `@Modifying @Query` issues exactly one bulk `DELETE` statement, which is
what Constitution Principle IV requires ("indexed, set-based database operations rather than
per-row iteration").

**Alternatives considered**: A derived `deleteByExpiresAtLessThanEqual` method — rejected for the
per-row-iteration reason above. A scheduled `@Scheduled` job performing the same delete on a timer
— explicitly rejected by the spec's Clarifications answer (read-time evaluation only, no dedicated
background process, consistent with Feature 1's precedent and the "few minutes of staleness is
acceptable" tolerance in SC-002).

## 3. Graveyard list query

**Decision**: A second Spring Data JPA derived query method,
`findByExpiresAtLessThanEqualAndExpiresAtAfterOrderByExpiresAtAsc(Instant activeThreshold, Instant graveyardThreshold)`,
called with `activeThreshold = now` and `graveyardThreshold = now.minus(30, DAYS)` — the same two
values already computed for the bulk delete.

**Rationale**: A two-sided range comparison on the single indexed `expires_at` column matches
FR-003/FR-004 (show everything past active expiration but not yet past the graveyard deadline,
soonest-to-be-deleted first) in one indexed, set-based read — the same pattern Feature 1 used for
the active-list query, just with both bounds instead of one.

**Alternatives considered**: A `@Query`-annotated JPQL method — rejected as unnecessary; the
derived method name expresses the same predicate without extra syntax, consistent with Feature 1's
precedent.

## 4. Response shape for graveyard entries: reuse `LinkResponse`, reinterpret `expiresAt`

**Decision**: Add a small static factory, `LinkResponse.forGraveyard(Link link)`, that builds the
existing `LinkResponse(id, url, savedAt, expiresAt)` record but with `expiresAt` set to
`link.getExpiresAt().plus(30, ChronoUnit.DAYS)` — i.e., the permanent-deletion deadline — rather
than the original active-expiration instant. No new DTO class is introduced.

**Rationale**: Keeps the wire format identical between `GET /api/links` and
`GET /api/links/graveyard` (`{ id, url, savedAt, expiresAt }`), which lets the frontend reuse the
exact same `Link` TypeScript type and the exact same `LinkListItem`/`LinkList` rendering logic
(which reads `link.expiresAt` as "the timestamp this entry's countdown counts down to") for both
views, per the user's explicit instruction to reuse the list/list-item components so both views
behave identically. A field named `expiresAt` is a reasonable, honest label for "when this entry
leaves the current view" in both contexts.

**Alternatives considered**: A distinct `GraveyardLinkResponse(id, url, savedAt, expiresAt,
graveyardExpiresAt)` DTO — rejected as unnecessary duplication of an identical shape, and it would
force `LinkListItem` to branch on which field to read, undermining the reuse goal. Computing the
+30-day offset client-side from the original `expiresAt` — rejected: it would duplicate the
30-day constant on both sides of the wire and risk drift; computing it once, server-side, is the
single source of truth.

## 5. Frontend: reusing list/list-item/count components across two views

**Decision**: Extend three existing components with small, optional, backward-compatible props
instead of forking graveyard-specific copies:
- `LinkListItem`: add `openable?: boolean` (default `false`). When `true`, the URL label renders
  inside an `<a href={link.url} target="_blank" rel="noopener noreferrer">` instead of a plain
  `<span>`. Default behavior (active list) is byte-for-byte unchanged.
- `LinkList`: add `emptyMessage?: string` (default the current active-list copy). The graveyard
  view passes its own empty-state copy.
- `ActiveCount` → renamed `LinkCount`: add `label?: string` (default the current "saved" copy),
  used with a graveyard-appropriate label for the second view.

Two new thin wrapper components, `ActiveView` (existing `UrlCaptureForm` + `LinkCount` + `LinkList`,
driven by `useActiveLinks`) and `GraveyardView` (`LinkCount` + `LinkList` with `openable`/
`emptyMessage`, driven by a new `useGraveyardLinks` hook mirroring `useActiveLinks`'s ~60s poll
pattern), plus a `NavTabs` component (DaisyUI tab classes, plain `useState` in `App.tsx`) complete
the toggle.

**Rationale**: Directly implements the user's instruction to reuse the list/list-item components
and polling pattern "so both views behave identically," while satisfying Constitution Principle I
(a component is only generalized once a second real consumer exists — which is now true) without
touching the active list's default rendering or behavior (FR-016). The rename from `ActiveCount`
to `LinkCount` reflects that the component is no longer active-list-specific.

**Alternatives considered**: Duplicating `LinkListItem`/`LinkList`/count into graveyard-specific
copies — rejected as needless duplication the moment two consumers exist with only cosmetic
differences. A generic list-rendering library or a state-management library to share view state —
rejected as unjustified for two `useState`-driven client views, per Principle I and the "no new
dependencies" instruction. A routing library (e.g., react-router) for the tab navigation —
rejected; the user asked for something "lightweight," and a plain boolean/string view-state toggle
in `App.tsx` fully satisfies FR-008 without adding a dependency or URL-routing complexity this
single-page, single-user app doesn't need.

## 6. Countdown display granularity for the graveyard (FR-017)

**Decision**: Add a `granularity?: 'fine' | 'coarse'` prop to `LinkListItem` (forwarded through
`LinkList`), defaulting to `'fine'` — the existing day/hour/minute formatting, unchanged for the
active list. In `'coarse'` mode, used only by `GraveyardView`, the countdown shows whole-day
granularity while more than 1 day remains (e.g. "12d"), switching to hour-level (no minutes)
within the final day (e.g. "18h").

**Rationale**: FR-017 (added via a post-plan `/speckit-checklist` resolution, 2026-07-03)
explicitly requires the graveyard's 30-day countdown to use coarser precision than the active
list's minute-level final-hour display, since the graveyard is a lower-urgency grace period rather
than the core scarcity mechanic. A prop-driven mode on the existing formatting logic reuses the
same component (per the reuse strategy in §5) rather than forking a second countdown
implementation.

**Alternatives considered**: A wholly separate `GraveyardCountdown` formatting function/component —
rejected as unnecessary duplication of the day/hour/minute branching logic already in
`LinkListItem`; parameterizing the existing logic is a smaller, more maintainable diff. Always
using the active list's fine-grained format for both views — rejected, since it directly
contradicts FR-017's explicit requirement.

## Outcome

No `NEEDS CLARIFICATION` markers remain in the Technical Context. All decisions above are
consistent with the spec's functional requirements, its 2026-07-03 Clarifications, and the project
constitution.
