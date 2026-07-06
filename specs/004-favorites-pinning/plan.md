# Implementation Plan: Favorites Tab & Link Pinning

**Branch**: `004-favorites-pinning` | **Date**: 2026-07-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-favorites-pinning/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Add a pin/unpin mechanic that exempts a link from ShelfLife's existing time-derived active-expiry and
graveyard-deletion mechanics for as long as it stays pinned, plus a third "Favorites" tab (positioned
after Graveyard) showing every currently pinned link with the same card design/palette/title-favicon
conventions as the other two views, a "Pinned" indicator in place of a countdown, and its own third,
distinct empty-state illustration. This is the first feature to introduce genuine persisted, mutable
state on `Link` (`pinned`, `pinnedAt`) rather than deriving lifecycle purely from timestamps — every
existing timing query (active list, graveyard list, and critically the graveyard's automatic
permanent-deletion sweep) is extended with a `pinned = false` predicate so pinned links are excluded
at the read/write-query level, not merely hidden in the UI. Unpinning recomputes `expiresAt` fresh as
`now() + 168h` (the same formula `createLink` already uses), independent of the link's original save
time or however long it sat pinned. Three new endpoints are added (`GET /api/links/favorites`,
`POST /api/links/{id}/pin`, `POST /api/links/{id}/unpin`), all idempotent and following the existing
`DELETE /api/links/{id}` contract's shape (no body, `204`, no-op on a nonexistent/already-in-that-state
id) — critically, "no-op" means the underlying `pinnedAt`/`expiresAt` fields are left completely
untouched when the link is already in the requested state, not merely that the call avoids erroring;
`pinLink`/`unpinLink` check the link's current `pinned` value before mutating anything, the same
"verify, then act" shape `deleteLink` already uses. On the frontend, the existing `LinkListItem`/`LinkList` components are extended in place with a
`pinned` prop and a new `PinControl` (no arm/confirm — pinning is immediate and reversible per the
spec) rather than forked into a separate favorites-specific card; `DeleteControl` requires no changes
at all, since it already works identically regardless of pinned state. Stack is unchanged: Java
21/Spring Boot 3.5/Spring Data JPA/H2/Maven backend, React 19/Vite/TypeScript/Tailwind+DaisyUI/Vitest+RTL
frontend — no new dependency on either side.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.x with React 19 (frontend) — unchanged from
Features 1–3.

**Primary Dependencies**: Spring Boot 3.5.x (Spring Web, Spring Data JPA), Maven (backend); React 19,
Vite, Tailwind CSS 4 + DaisyUI 5 (frontend) — reused as-is. No new dependency on either side; the
`pin`/`unpin` endpoints use plain `@PostMapping` sub-paths (already-supported HTTP verb), and the
frontend's new `PinControl`/`PinnedBadge`/`EmptyFavoritesIllustration` are inline components/SVGs like
their Feature 3 counterparts.

**Storage**: The same H2 file-based `links` table, extended with two new columns (`pinned` boolean
`NOT NULL DEFAULT false`, `pinned_at` nullable `Instant`) applied via Hibernate `ddl-auto=update` — no
migration tooling needed at this scale, no new table. `expires_at`'s existing index and column
definition are unchanged; its *meaning* is now conditional on `pinned` (research.md §2).

**Testing**: JUnit 5 + Spring Boot Test (`@DataJpaTest`, `@WebMvcTest`/`@SpringBootTest`) for backend;
Vitest + React Testing Library for frontend — unchanged.

**Target Platform**: Self-hosted/local web server (backend) served to a modern browser (frontend);
single-user, no deployment-scale infrastructure — unchanged.

**Project Type**: Web application monorepo with `backend/` and `frontend/` as sibling top-level
folders — unchanged structure, extended in place.

**Performance Goals**: No fixed numeric SLA (personal-scale, per constitution). Pin/unpin actions MUST
feel immediate client-side, matching the existing delete action's feel — each is a single indexed
primary-key read+write (`findById` + `save`), not a scan. `GET /api/links/favorites` reuses the
existing Feature 3 concurrent metadata-backfill path for any never-fetched pinned link, so a favorites
read with several such links is bounded the same way active/graveyard reads already are (roughly one
fetch's worth of latency, not the sum).

**Constraints**: Every query that determines active-list or graveyard-list membership, *and* the
graveyard's automatic permanent-deletion sweep (`deleteByPinnedFalseAndExpiresAtLessThanEqual`), MUST
exclude `pinned = true` rows at the query level — this is the correctness-critical constraint flagged
by the user's plan instruction #1 and research.md §1: without it, a pinned link's stale `expiresAt`
could cause it to be permanently deleted by the sweep despite being pinned. Unpinning MUST compute a
brand-new `expiresAt = now() + 168h`, never resuming, extending, or otherwise deriving from the value
`expiresAt` held before or during pinning, and never from `savedAt` — and this recomputation MUST
happen only on an actual pinned → unpinned transition, never on a repeated `unpin` call against a link
that is already unpinned. `pin`/`unpin`/the favorites read MUST NOT introduce any scheduled/background
job — all three remain request-time operations, consistent with the constitution's established
read-time-computation precedent. `pin`/`unpin` MUST be idempotent (acting on an already-pinned/
-unpinned or nonexistent id is a successful no-op, matching the existing `DELETE` idempotency
contract) — critically, a true no-op that leaves `pinnedAt`/`expiresAt` completely untouched, not just
an operation that avoids erroring, since re-stamping either on a repeated call would silently reorder
the favorites list or re-arm a countdown with no corresponding state change having occurred. Neither
action may alter any other link's `expiresAt`, ordering, or count. The
existing `DELETE /api/links/{id}` endpoint MUST continue to work, unmodified, on pinned links. No new
priority/ordering concept, no limit on pinned-link count, and no change to non-pinned active-list or
graveyard behavior (FR-013–FR-015) may be introduced.

**Scale/Scope**: Single user, expected link volume in the hundreds (per constitution) — unchanged.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Code Quality** — PASS. No new abstraction layer, interface, or speculative seam is introduced.
  `LinkService` gains two focused, concrete methods (`pinLink`, `unpinLink`) alongside a renamed set of
  existing-shape repository query methods (adding a `pinned` predicate to each) and one new query
  (`findByPinnedTrueOrderByPinnedAtDesc`) — all plain Spring Data derived-query methods, no custom
  abstraction. `LinkController` gains three thin endpoints delegating directly to `LinkService`,
  matching the existing controller→service→repository shape exactly. Frontend changes stay inside the
  existing flat `components/` structure: one new small presentational component (`PinControl`), one new
  static badge (`PinnedBadge`), one new empty-state SVG (`EmptyFavoritesIllustration`), and one new view
  (`FavoritesView`) that mirrors `GraveyardView`'s existing shape rather than inventing a new pattern.
  `LinkListItem`/`LinkList` are extended with two optional props (`pinned`, `onPinToggled`) rather than
  forked — reuse, not duplication, per the user's explicit plan instruction #4.
- **II. Testing Standards** — PASS, with new obligations. This feature adds two new lifecycle
  transitions (active/graveyard → favorites via pin, favorites → active via unpin) alongside the
  pre-existing ones, all of which MUST have dedicated tests per the constitution: `LinkServiceTest`
  gains tests for both transitions (from each origin view), idempotency for both actions (already-in-
  target-state and nonexistent-id cases) — asserting `pinnedAt`/`expiresAt` are byte-for-byte unchanged
  by a repeated call, not merely that the call doesn't error (research.md §2's true-no-op guard) — and
  — critically — a test proving a pinned link survives past both the 168-hour and 30-day boundaries
  without being excluded from favorites or swept by `deleteByPinnedFalseAndExpiresAtLessThanEqual` (the
  correctness risk research.md §1 identifies).
  `LinkRepositoryTest` gains boundary/predicate tests for the three modified queries plus the new
  favorites query's ordering. The existing controller test
  `controllerExposesOnlyGetPostAndDeleteNoRescueResurrectOrPinEndpoint` is renamed and its assertions
  narrowed: it continues to prove no `PATCH`/`PUT` endpoint exists anywhere (still true), but no longer
  claims "no pin endpoint" — this feature intentionally and explicitly supersedes that narrower claim,
  exactly as Feature 3 did for the analogous "no DELETE endpoint" assertion. All existing 168-hour and
  30-day boundary tests, and all existing delete/metadata tests, MUST continue to pass unmodified for
  every link with the (default) `pinned = false`, proving zero regressions for non-pinned links
  (mirrors SC-006).
- **III. User Experience Consistency** — PASS. The Favorites tab reuses the established card design,
  the shelflife DaisyUI theme, and the tab-bar pattern verbatim — no new visual language is introduced.
  The pin control is small and understated, matching the existing delete control's visual weight, and
  does not compete with the capture input's prominence on the active view (unaffected by this feature).
  No new settings screen, toggle, or configuration is introduced.
- **IV. Performance Requirements** — PASS. `pinLink`/`unpinLink` are single indexed primary-key
  operations (`findById` + `save`), not scans. The three modified repository queries remain indexed,
  set-based derived queries (the existing `idx_link_expires_at` index still covers `expiresAt`; adding
  an equality predicate on a low-cardinality boolean column does not introduce an N+1 or per-row
  pattern). The favorites read reuses Feature 3's existing concurrent (virtual-thread) metadata-backfill
  path rather than introducing a second, divergent backfill implementation.

No violations identified; Complexity Tracking table is not needed.

**Post-Phase 1 re-check**: Re-evaluated after producing `data-model.md`,
`contracts/favorites-pin-api.md`, and `quickstart.md`. The design introduces no new dependency, no new
package/layer, and confirms the one correctness-critical query change (the graveyard sweep's `pinned =
false` predicate) is captured in both the data model and the test plan. All four principles still PASS
with no changes to this section.

## Project Structure

### Documentation (this feature)

```text
specs/004-favorites-pinning/
├── plan.md                          # This file (/speckit-plan command output)
├── research.md                      # Phase 0 output (/speckit-plan command)
├── data-model.md                    # Phase 1 output (/speckit-plan command)
├── quickstart.md                    # Phase 1 output (/speckit-plan command)
├── contracts/                       # Phase 1 output (/speckit-plan command)
│   └── favorites-pin-api.md
└── tasks.md                         # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/com/shelflife/backend/
│   │   │   └── link/
│   │   │       ├── Link.java                      # MODIFIED: + pinned (boolean, default false),
│   │   │       │                                   #           + pinnedAt (Instant, nullable)
│   │   │       ├── LinkRepository.java             # MODIFIED: findByExpiresAtAfterOrderByExpiresAtAsc
│   │   │       │                                   #           → findByPinnedFalseAndExpiresAtAfter...;
│   │   │       │                                   #           findByExpiresAtLessThanEqualAnd... →
│   │   │       │                                   #           findByPinnedFalseAndExpiresAtLessThan...;
│   │   │       │                                   #           deleteByExpiresAtLessThanEqual →
│   │   │       │                                   #           deleteByPinnedFalseAndExpiresAtLessThan...
│   │   │       │                                   #           (correctness-critical, research.md §1);
│   │   │       │                                   #           + findByPinnedTrueOrderByPinnedAtDesc
│   │   │       ├── LinkService.java                # MODIFIED: + pinLink(Long id) — true no-op if
│   │   │       │                                   #           already pinned (pinnedAt untouched),
│   │   │       │                                   #           + unpinLink(Long id) — true no-op if
│   │   │       │                                   #           already unpinned (expiresAt untouched);
│   │   │       │                                   #           both check current pinned state before
│   │   │       │                                   #           mutating, same shape as deleteLink's
│   │   │       │                                   #           existsById check; + listFavoriteLinks()
│   │   │       │                                   #           (reuses backfillMetadata); existing
│   │   │       │                                   #           methods call the renamed repository
│   │   │       │                                   #           queries above
│   │   │       ├── LinkController.java             # MODIFIED: + GET /api/links/favorites,
│   │   │       │                                   #           + POST /api/links/{id}/pin,
│   │   │       │                                   #           + POST /api/links/{id}/unpin (all 3
│   │   │       │                                   #           idempotent, 204/200 per contract)
│   │   │       └── LinkResponse.java               # MODIFIED: + forFavorites(Link) factory
│   │   │                                           #           (expiresAt sent as null; research.md §5)
│   │   └── resources/                              # unchanged (ddl-auto=update picks up new columns)
│   └── test/
│       └── java/com/shelflife/backend/link/
│           ├── LinkServiceTest.java                # MODIFIED: + pin/unpin transition tests (from active,
│           │                                       #           from graveyard, back to active),
│           │                                       #           + true-no-op idempotency tests asserting
│           │                                       #           pinnedAt/expiresAt are byte-for-byte
│           │                                       #           unchanged on a repeated call
│           │                                       #           (already-pinned, already-unpinned,
│           │                                       #           nonexistent id — not just "no error"),
│           │                                       #           + fresh-168h-on-unpin test,
│           │                                       #           + pinned-link-survives-168h-and-30d-
│           │                                       #           boundaries-without-being-swept test
│           ├── LinkRepositoryTest.java              # MODIFIED: + pinned-exclusion tests for the 3
│           │                                       #           modified queries, + favorites ordering
│           │                                       #           test (pinnedAt desc)
│           └── LinkControllerTest.java              # MODIFIED: + GET /favorites, POST /pin, POST /unpin
│                                                    #           contract tests; existing
│                                                    #           "...NoRescueResurrectOrPinEndpoint" test
│                                                    #           renamed/narrowed (PATCH/PUT still
│                                                    #           unsupported; "or Pin" claim removed,
│                                                    #           mirroring Feature 3's DELETE precedent)

frontend/
├── src/
│   ├── types/
│   │   └── link.ts                        # MODIFIED: expiresAt: string → string | null
│   ├── api/
│   │   └── linksApi.ts                    # MODIFIED: + fetchFavoriteLinks(), + pinLink(id),
│   │                                       #           + unpinLink(id)
│   ├── components/
│   │   ├── LinkListItem.tsx               # MODIFIED: + pinned?/onPinToggled? props; renders PinControl
│   │   │                                   #           unconditionally; when pinned, renders
│   │   │                                   #           PinnedBadge instead of the countdown pill/
│   │   │                                   #           HourglassMotif/progress bar
│   │   ├── LinkList.tsx                    # MODIFIED: + pinned?/onPinToggled? passthrough props
│   │   ├── PinControl.tsx                  # NEW: small icon toggle button (no arm/confirm — immediate,
│   │   │                                   #      reversible); calls pinLink/unpinLink based on current
│   │   │                                   #      pinned state, then onToggled
│   │   ├── PinnedBadge.tsx                 # NEW: static "Pinned" indicator occupying the countdown
│   │   │                                   #      pill's slot on a pinned card
│   │   ├── EmptyFavoritesIllustration.tsx   # NEW: third distinct hand-drawn-style SVG for the
│   │   │                                   #      Favorites empty state (matches Active/Graveyard
│   │   │                                   #      illustrations' style/palette, distinct subject)
│   │   ├── FavoritesView.tsx                # NEW: mirrors GraveyardView's shape — useFavoriteLinks,
│   │   │                                   #      LinkCount, LinkList(pinned, openable,
│   │   │                                   #      emptyIllustration=EmptyFavoritesIllustration,
│   │   │                                   #      onDeleted={refresh}, onPinToggled={refresh}) — the
│   │   │                                   #      same refresh-on-toggle wiring ActiveView/
│   │   │                                   #      GraveyardView get below, built in from the start
│   │   │                                   #      since Favorites must reflect an unpin immediately
│   │   │                                   #      (quickstart.md Scenario 4)
│   │   ├── ActiveView.tsx                  # MODIFIED: + onPinToggled={refresh} wiring
│   │   ├── GraveyardView.tsx               # MODIFIED: + onPinToggled={refresh} wiring
│   │   ├── NavTabs.tsx                     # MODIFIED: View = 'active' | 'graveyard' | 'favorites';
│   │   │                                   #           + third tab ("Favorites"), positioned after
│   │   │                                   #           Graveyard
│   │   ├── DeleteControl.tsx               # unchanged — already works identically on pinned cards
│   │   ├── HourglassMotif.tsx               # unchanged
│   │   ├── EmptyActiveIllustration.tsx      # unchanged
│   │   ├── EmptyGraveyardIllustration.tsx   # unchanged
│   │   ├── LinkCount.tsx                   # unchanged
│   │   └── UrlCaptureForm.tsx               # unchanged
│   ├── App.tsx                             # MODIFIED: + FavoritesView branch for view === 'favorites'
│   └── hooks/
│       ├── useActiveLinks.ts               # unchanged
│       ├── useGraveyardLinks.ts            # unchanged
│       └── useFavoriteLinks.ts              # NEW: mirrors useActiveLinks/useGraveyardLinks, calling
│                                           #      fetchFavoriteLinks on the same 60s poll cadence
└── tests/
    ├── LinkListItem.test.tsx               # MODIFIED: + pinned-state rendering (PinnedBadge instead of
    │                                       #           countdown), + PinControl presence/toggle wiring
    ├── PinControl.test.tsx                  # NEW: renders unpinned/pinned states, calls pinLink/
    │                                       #      unpinLink appropriately, calls onToggled
    ├── FavoritesView.test.tsx               # NEW: empty state, renders pinned links via LinkList
    ├── NavTabs.test.tsx                     # MODIFIED: + third "Favorites" tab assertions, tab order
    ├── LinkList.test.tsx                    # unchanged (existing empty-state/rendering tests still
    │                                       #           apply; new pinned passthrough covered via
    │                                       #           LinkListItem.test.tsx and FavoritesView.test.tsx)
    ├── DeleteControl.test.tsx               # unchanged
    ├── LinkCount.test.tsx                   # unchanged
    ├── UrlCaptureForm.test.tsx              # unchanged
    ├── GraveyardView.test.tsx               # unchanged
    ├── useActiveLinks.test.ts               # unchanged
    └── useGraveyardLinks.test.ts            # unchanged
```

**Structure Decision**: Same monorepo layout as Features 1–3 (`backend/` + `frontend/` siblings, no new
top-level folders, no new package). All backend changes stay inside the existing `link` package — no
new class is introduced, only new methods/endpoints and two new entity columns. All frontend additions
stay inside the existing flat `components/`/`hooks/` structure — no routing library, no state-management
library, no new npm dependency; `FavoritesView` and `useFavoriteLinks` mirror `GraveyardView`/
`useGraveyardLinks`'s exact existing shape, and `LinkListItem`/`LinkList` are extended in place rather
than forked, consistent with how this codebase has grown across Features 1–3.

## Complexity Tracking

*No Constitution Check violations — this section is intentionally empty.*
