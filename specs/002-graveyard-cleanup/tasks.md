---

description: "Task list for Graveyard Page & Automatic Permanent Cleanup"
---

# Tasks: Graveyard Page & Automatic Permanent Cleanup

**Input**: Design documents from `/specs/002-graveyard-cleanup/`

**Prerequisites**: plan.md, spec.md, data-model.md, contracts/graveyard-api.md, research.md, quickstart.md

**Tests**: Included. The project constitution (Testing Standards, Principle II) mandates dedicated
tests for every link-lifecycle transition and every time-based boundary — this feature adds the
graveyard→deleted transition and its 29d23h59m/30d00h00m/30d00h01m boundary, plus the delete-then-read
lifecycle-enforcement logic against zero/one/many eligible rows, plus dedicated coverage for
attempted invalid transitions (e.g., rescuing a graveyard link, resurrecting a deleted one) — so
these are hard governance requirements, not optional extras. Contract/component tests for the
other stories follow directly from the spec's acceptance scenarios and `contracts/graveyard-api.md`.

**Organization**: Tasks are grouped by user story (from spec.md) to enable independent
implementation and testing of each story. Since Feature 1 already provides the monorepo, stack,
and shared `Link` entity/`LinkRepository`, there is no project-initialization "Setup" phase for
this feature — everything begins at Foundational.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no unmet dependencies)
- **[Story]**: Which user story this task belongs to (US1–US6)
- Paths are relative to the repository root (`backend/`, `frontend/` are sibling top-level folders)

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: The single piece of query infrastructure every subsequent user story (US1, US2, US3)
depends on — nothing else is genuinely shared across multiple stories for this feature, so
Foundational is intentionally minimal.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T001 Add two new query methods to `LinkRepository` in `backend/src/main/java/com/shelflife/backend/link/LinkRepository.java`: `deleteByExpiresAtLessThanEqual(Instant threshold)` as a `@Modifying @Transactional @Query("DELETE FROM Link l WHERE l.expiresAt <= :threshold")` bulk delete (one SQL statement, not per-row), and `findByExpiresAtLessThanEqualAndExpiresAtAfterOrderByExpiresAtAsc(Instant activeThreshold, Instant graveyardThreshold)` as a derived query — both reuse the existing `expires_at` index, no schema change (research.md §1–§3; data-model.md Repository operations table)

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 2: User Story 1 - Expired links land in the graveyard instead of vanishing (Priority: P1) 🎯 MVP

**Goal**: The moment a link's 168-hour active period ends, it becomes visible in the graveyard
with its own fresh 30-day countdown — no manual step, no disappearance.

**Independent Test**: Persist (or let naturally expire) a link past its 168-hour mark and confirm,
at the service layer, that it is absent from `listActiveLinks()` and present in
`listGraveyardLinks()` with a deadline of `expiresAt + 30 days`.

### Tests for User Story 1

- [X] T002 [P] [US1] Unit test in `LinkServiceTest`: a link at exactly 168h since `savedAt` is absent from `listActiveLinks()` and present in `listGraveyardLinks()` in the same evaluation moment (FR-001, US1 AC1) — `backend/src/test/java/com/shelflife/backend/link/LinkServiceTest.java`
- [X] T003 [P] [US1] Unit test in `LinkServiceTest`: a graveyard link's deadline is `expiresAt + 30 days`, independent of its original `savedAt` (FR-002, US1 AC2) — same file
- [X] T004 [P] [US1] Test in `LinkServiceTest` confirming a link whose active period elapsed via a directly-persisted past-`expiresAt` fixture (simulating "the app was closed") is already present in `listGraveyardLinks()` on the very next read, with no prior live observation needed (US1 AC3) — same file

### Implementation for User Story 1

- [X] T005 [US1] Implement `LinkService.listGraveyardLinks()`: add a `GRAVEYARD_DAYS = 30` constant alongside the existing `EXPIRY_HOURS`, compute `now` and `graveyardThreshold = now.minus(GRAVEYARD_DAYS, ChronoUnit.DAYS)`, call `linkRepository.deleteByExpiresAtLessThanEqual(graveyardThreshold)`, then return `linkRepository.findByExpiresAtLessThanEqualAndExpiresAtAfterOrderByExpiresAtAsc(now, graveyardThreshold)` in `backend/src/main/java/com/shelflife/backend/link/LinkService.java` (depends on T001; resolves T002-T004)

**Checkpoint**: The active→graveyard transition is provably correct at the service layer,
independent of any endpoint or UI.

---

## Phase 3: User Story 2 - View the graveyard, ordered by urgency (Priority: P1)

**Goal**: A standalone graveyard view — reachable directly via the API/component, not yet wired
into app navigation (that's User Story 4) — lists every graveyard link soonest-to-be-deleted
first, with a label, remaining time (in the graveyard's own coarser granularity, per FR-017), and
an empty state.

**Independent Test**: With several links at different points in their graveyard countdown, call
`GET /api/links/graveyard` (or render `GraveyardView` directly) and verify all of them are listed,
soonest-to-be-deleted first, each with a label and remaining time.

### Tests for User Story 2

- [X] T006 [P] [US2] Contract tests for `GET /api/links/graveyard` — soonest-to-be-deleted-first ordering, raw URL label, `expiresAt` reinterpreted as the permanent-deletion deadline, empty array when nothing is in the graveyard — in `backend/src/test/java/com/shelflife/backend/link/LinkControllerTest.java`
- [X] T007 [P] [US2] `@DataJpaTest` test for the graveyard range query's ordering with multiple persisted links at different points in their 30-day window in `backend/src/test/java/com/shelflife/backend/link/LinkRepositoryTest.java`
- [X] T008 [P] [US2] Component test for `useGraveyardLinks` (fetches on mount, refetches on the ~60s periodic tick) in `frontend/tests/useGraveyardLinks.test.ts`
- [X] T009 [P] [US2] Component test for `LinkList`'s new `emptyMessage` prop (renders the custom message when provided, falls back to the existing active-list copy by default) in `frontend/tests/LinkList.test.tsx`
- [X] T010 [P] [US2] Component test for `GraveyardView` (renders links via `LinkList` in received order with a graveyard-specific empty message; empty state with zero links) in `frontend/tests/GraveyardView.test.tsx`
- [X] T011 [P] [US2] Test for graveyard-mode remaining-time formatting in `LinkListItem` — whole-day granularity while more than 1 day remains (e.g. "12d"), hour-level granularity with no minutes within the final day (e.g. "18h") — verified against the active list's unchanged, finer-grained day/hour/minute formatting — in `frontend/tests/LinkListItem.test.tsx` (FR-017)

### Implementation for User Story 2

- [X] T012 [P] [US2] Add `LinkResponse.forGraveyard(Link link)` static factory (sets the response's `expiresAt` to `link.getExpiresAt().plus(30, ChronoUnit.DAYS)`, the permanent-deletion deadline) in `backend/src/main/java/com/shelflife/backend/link/LinkResponse.java` (research.md §4)
- [X] T013 [US2] Implement `GET /api/links/graveyard` in `LinkController`: call `linkService.listGraveyardLinks()`, map each link via `LinkResponse.forGraveyard`, return `200` with the shared list-envelope record (rename the existing `ActiveLinksResponse` inner record to a neutral `LinksResponse` reused by both endpoints) in `backend/src/main/java/com/shelflife/backend/link/LinkController.java` (depends on T005, T012)
- [X] T014 [P] [US2] Add `fetchGraveyardLinks()` to `frontend/src/api/linksApi.ts` (calls `GET /api/links/graveyard`, reuses the existing `Link` type)
- [X] T015 [P] [US2] Implement `useGraveyardLinks` hook: same ~60s polling pattern as `useActiveLinks`, calling `linksApi.fetchGraveyardLinks`, in `frontend/src/hooks/useGraveyardLinks.ts` (depends on T014)
- [X] T016 [P] [US2] Add optional `emptyMessage?: string` prop to `LinkList` (default preserves the current active-list copy exactly) in `frontend/src/components/LinkList.tsx` (resolves T009)
- [X] T017 [P] [US2] Add a `granularity?: 'fine' | 'coarse'` prop (default `'fine'`, preserving the active list's exact day/hour/minute formatting unchanged) to `LinkListItem` and `LinkList` (forwarding to each item); in `'coarse'` mode, `LinkListItem` shows whole-day granularity while more than 1 day remains (e.g. "12d"), switching to hour-level (no minutes) within the final day (e.g. "18h") — in `frontend/src/components/LinkListItem.tsx` and `frontend/src/components/LinkList.tsx` (FR-017; resolves T011)
- [X] T018 [US2] Implement `GraveyardView` component: `LinkList` with the graveyard empty message and `granularity="coarse"` (openable links deferred to User Story 6), driven by `useGraveyardLinks`, in `frontend/src/components/GraveyardView.tsx` (depends on T015, T016, T017)

**Checkpoint**: The graveyard is independently viewable end-to-end via the API and a standalone
component (not yet reachable through app navigation — that's User Story 4).

---

## Phase 4: User Story 3 - Graveyard links are permanently and irreversibly deleted (Priority: P2)

**Goal**: Prove that the delete-then-read mechanism correctly removes links at and past their
30-day graveyard deadline at every boundary, and that "deleted" means a genuinely absent row, not
a filtered one.

**Independent Test**: Persist a link with its graveyard deadline artificially in the past, call
`GET /api/links/graveyard`, confirm it is absent, then confirm the row no longer exists in the
database at all.

### Tests for User Story 3

- [X] T019 [P] [US3] Boundary unit tests in `LinkServiceTest`: a link at 29d23h59m since entering the graveyard is included in `listGraveyardLinks()`; a link at exactly 30d00h00m is excluded (and deleted); a link at 30d00h01m is excluded (and deleted) — in `backend/src/test/java/com/shelflife/backend/link/LinkServiceTest.java`
- [X] T020 [P] [US3] `@DataJpaTest` boundary tests for the bulk delete + range query using directly persisted fixtures at 29d23h59m / exactly 30d00h00m / 30d00h01m in `backend/src/test/java/com/shelflife/backend/link/LinkRepositoryTest.java`
- [X] T021 [P] [US3] Test confirming a link past its 30-day graveyard deadline no longer exists in the database at all after `listGraveyardLinks()` runs — a direct row-existence check post-sweep, not merely its absence from the query result (FR-014, FR-015) — in `backend/src/test/java/com/shelflife/backend/link/LinkRepositoryTest.java`
- [X] T022 [P] [US3] Tests for the delete-then-read behavior against zero, one, and many overdue rows simultaneously (constitution Testing Standards: lifecycle-enforcement logic against zero/one/many eligible links) in `backend/src/test/java/com/shelflife/backend/link/LinkRepositoryTest.java`

### Implementation for User Story 3

- [X] T023 [US3] Confirm/adjust the threshold comparisons in `LinkRepository`/`LinkService` so the exact-boundary instant (graveyard deadline == now) is treated as due-for-deletion, per FR-014/FR-015, in `backend/src/main/java/com/shelflife/backend/link/LinkRepository.java` and `LinkService.java` (depends on T005; resolves any failures from T019-T022)

**Checkpoint**: Permanent deletion is verified correct at every required boundary and confirmed as
genuine row removal, with no scheduled job anywhere in this feature.

---

## Phase 5: User Story 4 - Navigate between Active and Graveyard views (Priority: P2)

**Goal**: A lightweight tab lets the user switch between the active list and the graveyard, with
clear indication of which is currently showing.

**Independent Test**: From the active list view, use the tab to reach the graveyard view and
back, confirming both views render their respective, correct lists.

### Tests for User Story 4

- [X] T024 [P] [US4] Component test for `NavTabs` (renders both tabs, clicking switches the selected tab, visually indicates which is currently selected) in `frontend/tests/NavTabs.test.tsx`

### Implementation for User Story 4

- [X] T025 [P] [US4] Implement `NavTabs` component: two DaisyUI-styled tab buttons ("Active", "Graveyard"), calling an `onSelect` callback, with an `active` prop indicating the current selection, in `frontend/src/components/NavTabs.tsx`
- [X] T026 [US4] Extract `ActiveView` component (the existing `UrlCaptureForm` + `ActiveCount` + `LinkList`, driven by `useActiveLinks`) out of the current `App.tsx` body into `frontend/src/components/ActiveView.tsx`
- [X] T027 [US4] Rewrite `App.tsx` as a thin shell: `useState` holding the current view (`'active' | 'graveyard'`), rendering `NavTabs` plus either `ActiveView` or `GraveyardView` in `frontend/src/App.tsx` (depends on T018, T025, T026)

**Checkpoint**: Both views are reachable and toggled via a single lightweight control — the
graveyard is now actually reachable in the running app.

---

## Phase 6: User Story 5 - See graveyard count at a glance (Priority: P3)

**Goal**: The graveyard page shows a live count of graveyard links, matching what's in the list.

**Independent Test**: With a known number of graveyard links, verify the displayed count matches;
let one be permanently deleted or let another arrive and verify the count updates.

### Tests for User Story 5

- [X] T028 [P] [US5] Rename `frontend/tests/ActiveCount.test.tsx` to `frontend/tests/LinkCount.test.tsx` and extend it with a case for a custom `label` prop (e.g., "in the graveyard"), alongside the existing default-label case

### Implementation for User Story 5

- [X] T029 [US5] Rename `frontend/src/components/ActiveCount.tsx` to `frontend/src/components/LinkCount.tsx`, adding an optional `label?: string` prop (default preserves the current "saved" copy exactly); update its import in `ActiveView.tsx` accordingly (depends on T026; resolves T028)
- [X] T030 [US5] Integrate `LinkCount` (with a graveyard-specific `label`, e.g. "in the graveyard") into `GraveyardView.tsx`, driven by the same links state `LinkList` uses, in `frontend/src/components/GraveyardView.tsx` (depends on T018, T029)

**Checkpoint**: Both views show an at-a-glance count, matching FR-007.

---

## Phase 7: User Story 6 - Open a graveyard link's destination (Priority: P3)

**Goal**: A graveyard link can be clicked/activated to open its original URL in a new tab, with no
effect on its remaining time or position. The active list's current (non-clickable) rendering is
left completely unchanged, per FR-016 — only `GraveyardView` opts in explicitly.

**Independent Test**: From the graveyard view, activate a listed link and confirm its original
destination opens in a new tab, with no change to its remaining time or position afterward.

### Tests for User Story 6

- [X] T031 [P] [US6] Extend `frontend/tests/LinkListItem.test.tsx` (created in T011) with cases for the `openable` prop: renders a plain, non-clickable label by default (`openable` defaults to `false`); renders an `<a target="_blank" rel="noopener noreferrer">` only when `openable={true}` is explicitly passed

### Implementation for User Story 6

- [X] T032 [US6] Add an `openable?: boolean` prop (default `false`, preserving the active list's current non-clickable rendering exactly) to `LinkListItem` — wraps the URL label in `<a href={link.url} target="_blank" rel="noopener noreferrer">` only when `true` — and forward an equivalent `openable` prop from `LinkList` to each `LinkListItem` (default `false`), in `frontend/src/components/LinkListItem.tsx` and `frontend/src/components/LinkList.tsx` (FR-009, FR-018; resolves T031)
- [X] T033 [US6] Pass `openable={true}` to the `LinkList` element within `GraveyardView.tsx`, enabling graveyard links to open their destination (the active list, via `ActiveView.tsx`, is untouched and stays non-clickable) in `frontend/src/components/GraveyardView.tsx` (depends on T018, T032)

**Checkpoint**: All six user stories are independently functional and integrated together.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Final consistency pass across the whole feature

- [X] T034 [P] Apply consistent Tailwind/DaisyUI spacing and typography across `ActiveView`, `GraveyardView`, and `NavTabs` in `frontend/src/components/`
- [X] T035 Run all `quickstart.md` validation scenarios end-to-end with backend and frontend running together, and fix any discrepancies found
- [X] T036 [P] Add an architectural/API-surface test in `LinkControllerTest` proving no endpoint exists to rescue a graveyard link back to active, resurrect a deleted one, manually clear/delete a link early, or pin/favorite a link — confirm `LinkController` maps only `GET /api/links`, `GET /api/links/graveyard`, and `POST /api/links` (e.g., assert `PATCH`/`PUT`/`DELETE` requests to `/api/links/{id}` and `/api/links/graveyard/{id}` return `404`/`405`) — in `backend/src/test/java/com/shelflife/backend/link/LinkControllerTest.java` (constitution Testing Standards: dedicated coverage for attempted invalid transitions; satisfies FR-010, FR-011, FR-012, and FR-013 together) (depends on Foundational and T013, since it must exercise the full controller surface including the graveyard endpoint)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — BLOCKS all user stories.
- **User Story 1 (Phase 2)**: Depends on Foundational only.
- **User Story 2 (Phase 3)**: Depends on Foundational and on US1's `listGraveyardLinks()` existing (T005); independently testable via its own acceptance scenarios (ordering, labeling, empty state) once that dependency is met.
- **User Story 3 (Phase 4)**: Depends on Foundational and on US1's `listGraveyardLinks()` (T005); hardens that same query with boundary tests rather than adding new endpoints.
- **User Story 4 (Phase 5)**: Depends on Foundational and on US2's `GraveyardView` existing (T018) to have something to route to.
- **User Story 5 (Phase 6)**: Depends on US2's `GraveyardView` (T018) and US4's `ActiveView` extraction (T026).
- **User Story 6 (Phase 7)**: Depends on US2's `GraveyardView` (T018).
- **Polish (Phase 8)**: Depends on all six user stories being complete. T036 additionally only needs Foundational and US2's `GET /api/links/graveyard` endpoint (T013) to exercise the full controller surface — it does not need to wait for US4–US6.

### Within Each User Story

- Tests are written alongside/before their corresponding implementation task and MUST pass once that task is done.
- Repository/service changes before controller endpoints; hooks/components before view-level integration.

### Parallel Opportunities

- All Foundational-consuming test tasks within a story marked [P] can run in parallel with each other (different files, or additive independent test methods in the same file).
- US2 and US3 both depend only on US1's T005, not on each other — once T005 is done, US2 and US3 backend work can proceed in parallel.
- US5 and US6 both depend only on US2's T018 (plus, for US5, US4's T026) — not on each other — so they can proceed in parallel once those are done.

---

## Parallel Example: User Story 2

```bash
# Launch US2 tests together:
Task: "Contract tests for GET /api/links/graveyard in backend/src/test/java/com/shelflife/backend/link/LinkControllerTest.java"
Task: "@DataJpaTest ordering test in backend/src/test/java/com/shelflife/backend/link/LinkRepositoryTest.java"
Task: "Component test for useGraveyardLinks in frontend/tests/useGraveyardLinks.test.ts"
Task: "Component test for LinkList's emptyMessage prop in frontend/tests/LinkList.test.tsx"
Task: "Component test for GraveyardView in frontend/tests/GraveyardView.test.tsx"
Task: "Test for graveyard-mode remaining-time formatting in frontend/tests/LinkListItem.test.tsx"

# Launch independent US2 implementation pieces together:
Task: "Add LinkResponse.forGraveyard factory in backend/src/main/java/com/shelflife/backend/link/LinkResponse.java"
Task: "Add fetchGraveyardLinks() in frontend/src/api/linksApi.ts"
Task: "Add emptyMessage prop to LinkList in frontend/src/components/LinkList.tsx"
Task: "Add granularity prop to LinkListItem/LinkList in frontend/src/components/"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. Complete Phase 1: Foundational (blocks everything else)
2. Complete Phase 2: User Story 1 (transition correctness)
3. Complete Phase 3: User Story 2 (graveyard is viewable) — together these two P1 stories deliver the graveyard's core value
4. **STOP and VALIDATE**: Run the quickstart's Scenario 1 and 2 checks
5. Deploy/demo if ready

### Incremental Delivery

1. Foundational → shared query infrastructure ready
2. Add User Story 1 → verify the transition via service-level tests → demo
3. Add User Story 2 → verify the graveyard list renders correctly ordered, with coarser-granularity countdowns → demo
4. Add User Story 3 → verify permanent-deletion boundary correctness with fixture data → demo
5. Add User Story 4 → verify the graveyard is reachable via the tab → demo (feature now fully usable end-to-end)
6. Add User Story 5 → verify the count → demo
7. Add User Story 6 → verify clicking opens the destination from the graveyard view only, with the active list unaffected → demo
8. Polish → final consistency pass

---

## Notes

- [P] tasks touch different files (or add independent, non-conflicting test methods to the same file) with no unmet dependencies within their phase.
- [Story] labels map each task to its user story for traceability.
- Commit after each task or logical group.
- Stop at each checkpoint to validate that story independently before moving on.
- No scheduled/background job exists anywhere in this task list by design — permanent deletion is
  always a read-time delete-then-read against the graveyard endpoint specifically (spec
  Clarifications, 2026-07-03, and FR-015).
