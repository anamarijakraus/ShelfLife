# Implementation Plan: Graveyard Page & Automatic Permanent Cleanup

**Branch**: `002-graveyard-cleanup` | **Date**: 2026-07-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-graveyard-cleanup/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Extend ShelfLife's link lifecycle past active expiration: instead of an expired link simply
vanishing from view, it becomes visible on a new graveyard page for 30 more days, with its own
soonest-to-be-permanently-deleted-first ordering, remaining-time countdown, and count — then its
underlying data is hard-deleted. No new "status" column is introduced: active, graveyard, and
due-for-deletion are all derived at read time from the existing `expiresAt` column plus the fixed
168-hour and 30-day durations. Permanent deletion is enforced by a single bulk `DELETE` statement
executed immediately before every graveyard read (no scheduled job), guaranteeing overdue rows are
actually removed from storage rather than merely filtered out. The frontend adds a graveyard view
and a lightweight tab toggle, reusing the active list's list/list-item/count components and
60-second polling pattern via small, additive, backward-compatible props rather than duplicating
them. Stack is unchanged from Feature 1: Spring Boot 3.5/Java 21/H2 backend, React 19/Vite/
Tailwind+DaisyUI frontend — no new dependencies on either side.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.x with React 19 (frontend) — unchanged from
Feature 1.

**Primary Dependencies**: Spring Boot 3.5.x (Spring Web, Spring Data JPA), Maven (backend); React
19, Vite, Tailwind CSS + DaisyUI plugin (frontend) — reused as-is; no new dependencies on either
side.

**Storage**: The same H2 file-based (persistent) database and `links` table from Feature 1, with
**no schema change**. Graveyard membership and the permanent-deletion deadline are derived at read
time from the existing indexed `expires_at` column plus the fixed 168-hour (already baked into
`expiresAt`) and 30-day durations — no new column, no new index, no new table.

**Testing**: JUnit 5 + Spring Boot Test (`@DataJpaTest`, `@WebMvcTest`) for backend; Vitest + React
Testing Library for frontend — unchanged from Feature 1.

**Target Platform**: Self-hosted/local web server (backend) served to a modern browser (frontend);
single-user, no deployment-scale infrastructure — unchanged.

**Project Type**: Web application monorepo with `backend/` and `frontend/` as sibling top-level
folders — unchanged structure, extended in place.

**Performance Goals**: No fixed numeric SLA (personal-scale, per constitution). The graveyard read
MUST remain a single indexed, set-based bulk `DELETE` followed by a single indexed, set-based
`SELECT` (reusing the existing `expires_at` index) — never per-row iteration for either the delete
or the read.

**Constraints**: No new "status"/lifecycle column — active vs. graveyard vs. due-for-deletion is
always computed from `expiresAt` plus fixed offsets (per spec Clarifications, 2026-07-03).
Permanent deletion MUST be an actual row removal (bulk `DELETE`), executed on every graveyard read
before the corresponding `SELECT`, not a query-level filter — no dedicated background/scheduled
process. No manual rescue, early-deletion, or "clear now" action may be exposed anywhere in the UI
or API. The existing active-list countdown, ordering, and capture flow (Feature 1) MUST NOT change.
The graveyard's remaining-time countdown MUST use coarser display granularity than the active
list's (whole days beyond 1 day remaining, hour-level with no minutes within the final day) per
FR-017, added via a post-plan spec amendment (2026-07-03).

**Scale/Scope**: Single user, expected link volume in the hundreds (per constitution) across both
active and graveyard views combined — unchanged from Feature 1.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Code Quality** — PASS. Backend adds one repository method pair (bulk delete + range query)
  and one service method to the existing `link` package — no new layer, no new interface, no
  speculative abstraction. Frontend reuses `LinkList`/`LinkListItem`/the count component across
  both views via small, additive, optional props (`openable` on `LinkListItem`, `emptyMessage` and
  `granularity` on `LinkList`/`LinkListItem` for FR-017's coarser graveyard countdown, a generalized
  `label` on the count component, now renamed `LinkCount` since it is used by two consumers) rather
  than forking duplicate components — consistent with "extract only once genuinely shared by two
  or more," which is now true. Two new thin wrapper components (`ActiveView`, `GraveyardView`) and
  one new `NavTabs` component keep `App.tsx` a simple shell; no state-management or routing library
  is introduced.
- **II. Testing Standards** — PASS. This feature adds a new lifecycle transition (graveyard →
  deleted) that MUST have dedicated boundary tests at 29d23h59m / exactly 30d00h00m / 30d00h01m,
  mirroring the rigor already established for the 168-hour boundary. It also requires tests for
  the delete-then-read graveyard logic against zero, one, and many eligible (overdue) rows, per
  the constitution's explicit requirement for lifecycle-enforcement logic. The active→graveyard
  boundary (168h mark) is unaffected by this feature (already covered by Feature 1's tests) but is
  re-asserted implicitly by the graveyard's own range query tests. An architectural/API-surface
  test (tasks.md T036) also covers the constitution's "attempted invalid transitions" requirement,
  confirming no endpoint exists to rescue, resurrect, early-delete, or pin a link.
- **III. User Experience Consistency** — PASS. The graveyard view reuses the active list's exact
  styling, empty-state pattern, remaining-time formatting, and refresh cadence, so switching views
  feels like the same application. The tab toggle uses DaisyUI's existing utility classes (no new
  library). The capture input remains the single most prominent element on the active view; the
  graveyard view has no competing prominent element (no manual actions to place).
- **IV. Performance Requirements** — PASS. The permanent-deletion sweep is a single `@Modifying
  @Query` bulk `DELETE ... WHERE expires_at <= :threshold` statement (one SQL statement, indexed,
  set-based) — not a derived `deleteBy` method, which would delete row-by-row. The subsequent
  graveyard read reuses the same indexed `expires_at` column with a two-sided range comparison,
  still a single indexed scan.

No violations identified; Complexity Tracking table is not needed.

**Post-Phase 1 re-check**: Re-evaluated after producing `data-model.md`,
`contracts/graveyard-api.md`, and `quickstart.md`. The design introduces no new dependencies,
layers, columns, or state beyond what was assessed above (one new endpoint, one new repository
query pair, four small/reused frontend components). All four principles still PASS with no
changes to this section.

## Project Structure

### Documentation (this feature)

```text
specs/002-graveyard-cleanup/
├── plan.md                  # This file (/speckit-plan command output)
├── research.md               # Phase 0 output (/speckit-plan command)
├── data-model.md              # Phase 1 output (/speckit-plan command)
├── quickstart.md               # Phase 1 output (/speckit-plan command)
├── contracts/                   # Phase 1 output (/speckit-plan command)
│   └── graveyard-api.md
└── tasks.md                      # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/com/shelflife/backend/
│   │   │   └── link/
│   │   │       ├── Link.java                 # unchanged — no schema change
│   │   │       ├── LinkRepository.java       # MODIFIED: + deleteByExpiresAtLessThanEqual (bulk @Modifying delete),
│   │   │       │                             #           + findByExpiresAtLessThanEqualAndExpiresAtAfterOrderByExpiresAtAsc
│   │   │       ├── LinkService.java          # MODIFIED: + GRAVEYARD_DAYS constant, + listGraveyardLinks() (delete-then-read)
│   │   │       ├── LinkController.java       # MODIFIED: + GET /api/links/graveyard (shared list envelope renamed ActiveLinksResponse → LinksResponse)
│   │   │       └── LinkResponse.java         # MODIFIED: + forGraveyard(Link) factory (expiresAt reinterpreted as deletion deadline)
│   │   └── resources/                        # unchanged
│   └── test/
│       └── java/com/shelflife/backend/link/
│           ├── LinkServiceTest.java           # MODIFIED: + graveyard boundary tests (29d23h59m/30d00h00m/30d00h01m),
│           │                                  #           + delete-then-read tests (zero/one/many eligible)
│           ├── LinkRepositoryTest.java        # MODIFIED: + bulk-delete query tests, + graveyard range-query tests
│           └── LinkControllerTest.java        # MODIFIED: + GET /api/links/graveyard contract tests,
│                                              #           + architectural test proving no rescue/resurrect/early-delete/pin endpoint exists (FR-010–FR-013)

frontend/
├── src/
│   ├── App.tsx                        # MODIFIED: thin shell — holds active/graveyard tab state, renders NavTabs + the selected view
│   ├── api/
│   │   └── linksApi.ts                # MODIFIED: + fetchGraveyardLinks()
│   ├── hooks/
│   │   ├── useActiveLinks.ts          # unchanged
│   │   └── useGraveyardLinks.ts       # NEW: same ~60s polling pattern as useActiveLinks, calls fetchGraveyardLinks
│   ├── components/
│   │   ├── UrlCaptureForm.tsx         # unchanged
│   │   ├── LinkList.tsx               # MODIFIED: + optional `emptyMessage` prop (default preserves current active text),
│   │   │                             #           + optional `granularity` prop forwarded to each LinkListItem (FR-017)
│   │   ├── LinkListItem.tsx           # MODIFIED: + optional `openable` prop (wraps label in a new-tab anchor when true),
│   │   │                             #           + optional `granularity: 'fine' | 'coarse'` prop (default 'fine'; 'coarse'
│   │   │                             #           shows whole days beyond 1 day remaining, hour-only within the final day — FR-017)
│   │   ├── LinkCount.tsx              # RENAMED from ActiveCount.tsx: + optional `label` prop (default preserves current active text)
│   │   ├── NavTabs.tsx                # NEW: lightweight Active/Graveyard tab toggle (DaisyUI tab classes)
│   │   ├── ActiveView.tsx             # NEW: extracted wrapper — UrlCaptureForm + LinkCount + LinkList, driven by useActiveLinks
│   │   └── GraveyardView.tsx          # NEW: wrapper — LinkCount + LinkList (openable, granularity="coarse", graveyard empty message), driven by useGraveyardLinks
│   └── types/
│       └── link.ts                    # unchanged — same Link shape reused for graveyard responses
└── tests/
    ├── UrlCaptureForm.test.tsx        # unchanged
    ├── LinkList.test.tsx              # MODIFIED: + emptyMessage prop test
    ├── LinkListItem.test.tsx          # NEW: openable-prop rendering/anchor behavior, + granularity ('fine' vs 'coarse') formatting test (FR-017)
    ├── LinkCount.test.tsx             # RENAMED from ActiveCount.test.tsx: + label prop test
    ├── useActiveLinks.test.ts         # unchanged
    ├── useGraveyardLinks.test.ts      # NEW: mirrors useActiveLinks.test.ts against fetchGraveyardLinks
    └── NavTabs.test.tsx               # NEW: toggling behavior, active-tab indication
```

**Structure Decision**: Same monorepo layout as Feature 1 (`backend/` + `frontend/` siblings, no
new top-level folders). All backend changes stay inside the existing `link` package — no new
package, no new layer. All frontend additions stay inside the existing flat `components/`/`hooks`/
`api` structure — no routing library, no state-management library; the two-view toggle is plain
`useState` in `App.tsx`.

## Complexity Tracking

*No Constitution Check violations — this section is intentionally empty.*
