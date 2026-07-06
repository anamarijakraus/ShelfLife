---

description: "Task list for Favorites Tab & Link Pinning"
---

# Tasks: Favorites Tab & Link Pinning

**Input**: Design documents from `/specs/004-favorites-pinning/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/favorites-pin-api.md, quickstart.md (all present)

**Tests**: Included. The constitution (Principle II) and plan.md's Constitution Check explicitly require dedicated tests for every new lifecycle transition (active/graveyard→favorites via pin, favorites→active via unpin), the true-no-op idempotency guard on both actions, and the correctness-critical guarantee that a pinned link survives past the 168-hour and 30-day boundaries without being excluded from favorites or swept by the graveyard's automatic deletion — in addition to the frontend rendering/interaction tests plan.md calls for.

**Organization**: Tasks are grouped by user story (per spec.md's P1/P1/P2/P3 priorities) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)
- File paths are exact and relative to the repository root

## Path Conventions

Web app monorepo, unchanged from Features 1–3: `backend/src/main/java/com/shelflife/backend/...` and `backend/src/test/java/com/shelflife/backend/...` (Java/Maven); `frontend/src/...` and `frontend/tests/...` (React/TypeScript/Vitest).

---

## Phase 1: Setup

**Purpose**: Establish a regression baseline before touching any code

- [X] T001 Run the existing backend suite (`mvn test` in `backend/`) and frontend suite (`npm test` in `frontend/`) and confirm all currently pass, establishing the pre-change baseline that SC-006 (zero regressions for non-pinned links) will be checked against

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The persisted `pinned`/`pinnedAt` fields and the pinned-exclusion query predicate are required by every user story — none of the four stories are meaningful without them, unlike Feature 3 where User Story 1 needed no backend change at all.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T002 [P] Add `pinned` (`boolean`, `NOT NULL`, default `false`) and `pinnedAt` (`Instant`, nullable) fields with getters/setters to `backend/src/main/java/com/shelflife/backend/link/Link.java`
- [X] T003 In `backend/src/main/java/com/shelflife/backend/link/LinkRepository.java`: rename `findByExpiresAtAfterOrderByExpiresAtAsc` → `findByPinnedFalseAndExpiresAtAfterOrderByExpiresAtAsc`, rename `findByExpiresAtLessThanEqualAndExpiresAtAfterOrderByExpiresAtAsc` → `findByPinnedFalseAndExpiresAtLessThanEqualAndExpiresAtAfterOrderByExpiresAtAsc`, and rename `deleteByExpiresAtLessThanEqual` → `deleteByPinnedFalseAndExpiresAtLessThanEqual` (**correctness-critical**, research.md §1: without this predicate a pinned link's stale `expiresAt` would let the automatic graveyard sweep delete it); also add the new `findByPinnedTrueOrderByPinnedAtDesc()` query (depends on T002)
- [X] T004 Update `listActiveLinks()` and `listGraveyardLinks()` (including its sweep call) in `backend/src/main/java/com/shelflife/backend/link/LinkService.java` to call the renamed repository methods from T003 (depends on T003)
- [X] T005 [P] Update the direct repository-method call sites in `backend/src/test/java/com/shelflife/backend/link/LinkRepositoryTest.java` and `backend/src/test/java/com/shelflife/backend/link/LinkServiceTest.java` to the renamed methods from T003 — mechanical rename only, no new assertions here; a link with `pinned = false` (the default) must continue behaving identically under the new method names (depends on T003)

**Checkpoint**: Schema and query layer are in place; `pinned` defaults to `false` everywhere so there is zero observable behavior change yet, and all existing tests still pass under the renamed methods.

---

## Phase 3: User Story 1 - Pin a link to exempt it from expiration (Priority: P1) 🎯 MVP

**Goal**: A pin control on every active-list and graveyard card immediately moves that link into a pinned state, removing it from its current view and permanently exempting it from active-expiry and graveyard-deletion timing for as long as it stays pinned.

**Independent Test**: From the active list, activate a card's pin control and verify the link disappears from the active list and is exempt from further countdown; repeat from the graveyard and verify identical behavior (quickstart.md scenarios 1–3, pin portions).

- [X] T006 [US1] Implement `pinLink(Long id)` in `backend/src/main/java/com/shelflife/backend/link/LinkService.java`: a true no-op if the link is already pinned or the id doesn't exist (in particular, `pinnedAt` is **not** re-stamped on an already-pinned link, per research.md §2); otherwise sets `pinned = true`, `pinnedAt = Instant.now()`, and saves (depends on T004)
- [X] T007 [US1] Add `POST /api/links/{id}/pin` returning `204 No Content` to `backend/src/main/java/com/shelflife/backend/link/LinkController.java` (depends on T006)
- [X] T008 [P] [US1] Add `pinLink` tests to `backend/src/test/java/com/shelflife/backend/link/LinkServiceTest.java`: pinning an active link removes it from `listActiveLinks()`; pinning a graveyard link removes it from `listGraveyardLinks()`; pinning an already-pinned link is a true no-op (`pinnedAt` unchanged, asserted by value, not just "no error"); pinning a nonexistent id is a no-op; and — critically — a pinned link survives past both the 168-hour and 30-day boundaries (simulate a stale `expiresAt` well past both thresholds) without being excluded from a `findByPinnedTrueOrderByPinnedAtDesc` lookup or deleted by `listGraveyardLinks()`'s sweep (depends on T006)
- [X] T009 [P] [US1] Add pinned-exclusion tests to `backend/src/test/java/com/shelflife/backend/link/LinkRepositoryTest.java`: a `pinned = true` row otherwise eligible for the active-list query is excluded; a `pinned = true` row otherwise eligible for the graveyard-list query is excluded; a `pinned = true` row whose `expiresAt` is already past the 30-day graveyard threshold survives `deleteByPinnedFalseAndExpiresAtLessThanEqual` undeleted (the correctness-critical case from research.md §1) (depends on T003)
- [X] T010 [P] [US1] Add `POST /pin` contract tests to `backend/src/test/java/com/shelflife/backend/link/LinkControllerTest.java`: pinning an active link returns `204` and the link disappears from a subsequent `GET /api/links`; pinning a graveyard link returns `204` and the link disappears from a subsequent `GET /api/links/graveyard`; pinning a nonexistent id returns `204` as a no-op (depends on T007)
- [X] T011 [US1] Rename and narrow the existing `controllerExposesOnlyGetPostAndDeleteNoRescueResurrectOrPinEndpoint` test in `LinkControllerTest.java`: keep asserting `PATCH`/`PUT` return 4xx everywhere (still true), but remove the "no pin endpoint" claim — this feature intentionally and explicitly supersedes it, mirroring Feature 3's analogous update for `DELETE` (depends on T007)
- [X] T012 [P] [US1] Add `pinLink(id: number): Promise<void>` and `unpinLink(id: number): Promise<void>` to `frontend/src/api/linksApi.ts` (both added now so the single `PinControl` built below is complete and reusable as-is when User Story 3 lands its backend endpoint)
- [X] T013 [US1] Create `frontend/src/components/PinControl.tsx`: a small icon toggle button with no arm/confirm state (pinning/unpinning is immediate and reversible, unlike delete); calls `pinLink` when its `pinned` prop is `false` or `unpinLink` when `true`, then invokes `onToggled`; the button's `aria-label` (and icon fill/state) MUST differ between the two states (e.g., "Pin link" vs "Unpin link") to satisfy FR-009's "distinct pinned visual state" (depends on T012)
- [X] T014 [P] [US1] Write `frontend/tests/PinControl.test.tsx`: rendered unpinned, a click calls `pinLink` and then `onToggled`; rendered pinned, a click calls `unpinLink` and then `onToggled`; the control's accessible name (`aria-label`) differs between the two states (e.g., "Pin link" when unpinned, "Unpin link" when pinned), proving FR-009's "distinct pinned visual state" is an actual rendering difference, not just a behavioral one (depends on T013)
- [X] T015 [US1] Add `pinned?`/`onPinToggled?` props to `frontend/src/components/LinkListItem.tsx`; render `PinControl` unconditionally alongside the existing `DeleteControl` (depends on T013)
- [X] T016 [US1] Add `pinned?`/`onPinToggled?` passthrough props to `frontend/src/components/LinkList.tsx` (depends on T015)
- [X] T017 [US1] Wire `onPinToggled={refresh}` into `frontend/src/components/ActiveView.tsx` and `frontend/src/components/GraveyardView.tsx` so a successful pin immediately refreshes that view's list (depends on T016)
- [X] T018 [P] [US1] Add `PinControl` presence/wiring assertions to `frontend/tests/LinkListItem.test.tsx`: an unpinned card renders a "Pin link" control that invokes the provided `onPinToggled` after a successful pin (depends on T015)

**Checkpoint**: Pinning from either the active list or the graveyard works end to end and the link is permanently exempt from both timing mechanics — verify via quickstart.md scenarios 1–3 (pin portions) before proceeding.

---

## Phase 4: User Story 2 - View pinned links in the Favorites tab (Priority: P1)

**Goal**: A third "Favorites" tab, positioned after "Graveyard," shows every currently pinned link using the same card design/palette/title-favicon conventions as the other two views, with a "Pinned" indicator in place of a countdown, an accurate count, and its own distinct empty-state illustration.

**Independent Test**: Pin several links from the active list and graveyard, open the Favorites tab, and verify all of them appear with the established design conventions, a pinned indicator instead of a countdown, and an accurate count; empty Favorites entirely and verify its illustrated empty state (quickstart.md scenarios 1 [viewing], 6).

- [X] T019 [US2] Implement `listFavoriteLinks()` in `backend/src/main/java/com/shelflife/backend/link/LinkService.java`: `linkRepository.findByPinnedTrueOrderByPinnedAtDesc()` followed by the existing `backfillMetadata(...)` reused as-is (depends on T003, T004)
- [X] T020 [P] [US2] Add `forFavorites(Link)` factory to `backend/src/main/java/com/shelflife/backend/link/LinkResponse.java`: reuses the existing `title`/`faviconUrl` fallback resolution, but always sends `expiresAt` as `null` (research.md §5) (depends on T002)
- [X] T021 [US2] Add `GET /api/links/favorites` to `backend/src/main/java/com/shelflife/backend/link/LinkController.java`, mapping via `LinkResponse::forFavorites` (depends on T019, T020)
- [X] T022 [P] [US2] Add `GET /favorites` contract tests to `backend/src/test/java/com/shelflife/backend/link/LinkControllerTest.java`: empty array when nothing is pinned; pinned links returned ordered most-recently-pinned first, each with `"expiresAt": null` (depends on T021)
- [X] T023 [P] [US2] Add `listFavoriteLinks` tests to `backend/src/test/java/com/shelflife/backend/link/LinkServiceTest.java`: multiple pinned links are ordered by `pinnedAt` descending; a pre-existing pinned link with no title/favicon attempt yet is backfilled on its next favorites read (depends on T019)
- [X] T024 [P] [US2] Add a `findByPinnedTrueOrderByPinnedAtDesc` ordering test to `backend/src/test/java/com/shelflife/backend/link/LinkRepositoryTest.java` (depends on T003)
- [X] T025 [P] [US2] Change `expiresAt: string` → `expiresAt: string | null` in `frontend/src/types/link.ts`
- [X] T026 [P] [US2] Add `fetchFavoriteLinks(): Promise<Link[]>` to `frontend/src/api/linksApi.ts`
- [X] T027 [P] [US2] Create `frontend/src/hooks/useFavoriteLinks.ts`, mirroring `useActiveLinks`/`useGraveyardLinks`'s shape (same 60s poll cadence) (depends on T026)
- [X] T028 [P] [US2] Create `frontend/src/components/PinnedBadge.tsx`: a static "Pinned" indicator reusing the countdown pill's visual shape (rounded pill, badge-circle icon slot) with a filled pin icon and "Pinned" text — no motion, no countdown value
- [X] T029 [US2] In `frontend/src/components/LinkListItem.tsx`, when `pinned` is `true`, render `PinnedBadge` instead of the countdown pill/`HourglassMotif`/progress bar (depends on T015, T025, T028)
- [X] T030 [P] [US2] Create `frontend/src/components/EmptyFavoritesIllustration.tsx`: a third distinct hand-drawn-style SVG matching the Active/Graveyard illustrations' `viewBox`/stroke/palette conventions but a different subject
- [X] T031 [US2] Create `frontend/src/components/FavoritesView.tsx`: mirrors `GraveyardView`'s shape — `useFavoriteLinks`, `LinkCount` (label "pinned"), `LinkList` with `pinned`, `openable`, `emptyIllustration={<EmptyFavoritesIllustration />}`, `onDeleted={refresh}`, and `onPinToggled={refresh}` (depends on T016, T027, T030)
- [X] T032 [US2] Extend `View` to `'active' | 'graveyard' | 'favorites'` and add a third "Favorites" tab, positioned after "Graveyard," to `frontend/src/components/NavTabs.tsx`
- [X] T033 [US2] Add the `FavoritesView` branch (`view === 'favorites'`) to `frontend/src/App.tsx` (depends on T031, T032)
- [X] T034 [P] [US2] Add pinned-badge/countdown-suppression assertions to `frontend/tests/LinkListItem.test.tsx`: a pinned card renders `PinnedBadge` and does not render the countdown pill or progress bar (depends on T029)
- [X] T035 [P] [US2] Write `frontend/tests/FavoritesView.test.tsx`: empty state renders `EmptyFavoritesIllustration`; renders pinned links via `LinkList` with an accurate count (depends on T031)
- [X] T036 [P] [US2] Add third-tab assertions to `frontend/tests/NavTabs.test.tsx`: "Favorites" tab renders and appears after "Graveyard" (tab order), selection/`aria-selected` behavior (depends on T032)

**Checkpoint**: The Favorites tab fully renders pinned links with the correct card design, pinned badge, count, and empty state — verify via quickstart.md scenarios 1 (viewing) and 6.

---

## Phase 5: User Story 3 - Unpin a link back to the active list (Priority: P2)

**Goal**: The same pin control, shown in its "pinned" state on a Favorites card, immediately returns that link to the active list with a fresh 168-hour countdown measured from the moment of unpinning.

**Independent Test**: Pin a link, wait, then unpin it from the Favorites tab, and verify it appears in the active list with a fresh 168-hour countdown measured from the moment of unpinning, not its original save time (quickstart.md scenario 4).

- [X] T037 [US3] Implement `unpinLink(Long id)` in `backend/src/main/java/com/shelflife/backend/link/LinkService.java`: a true no-op if the link is already unpinned or the id doesn't exist (`expiresAt` is **not** touched, per research.md §2); otherwise sets `pinned = false` and `expiresAt = Instant.now().plus(168, ChronoUnit.HOURS)`, and saves (depends on T004)
- [X] T038 [US3] Add `POST /api/links/{id}/unpin` returning `204 No Content` to `backend/src/main/java/com/shelflife/backend/link/LinkController.java` (depends on T037)
- [X] T039 [P] [US3] Add `unpinLink` tests to `backend/src/test/java/com/shelflife/backend/link/LinkServiceTest.java`: unpinning sets `expiresAt` to approximately `now + 168h`, independent of `savedAt` and of however long the link sat pinned (simulate an old `pinnedAt`); unpinning an already-unpinned link is a true no-op (`expiresAt` unchanged, asserted by value); unpinning a nonexistent id is a no-op (depends on T037)
- [X] T040 [P] [US3] Add `POST /unpin` contract tests to `backend/src/test/java/com/shelflife/backend/link/LinkControllerTest.java`: unpinning a pinned link returns `204` and it reappears in a subsequent `GET /api/links` with a fresh `expiresAt`; unpinning an already-unpinned or nonexistent id returns `204` as a no-op (depends on T038)

**Checkpoint**: Unpinning works end to end with **zero additional frontend code** — `PinControl`'s unpin branch and `FavoritesView`'s `onPinToggled={refresh}` wiring were already built in User Stories 1 and 2 — verify via quickstart.md scenario 4.

---

## Phase 6: User Story 4 - Permanently delete a pinned link (Priority: P3)

**Goal**: The existing manual delete control, already available on every card, continues to work identically on a pinned link's Favorites card.

**Independent Test**: From the Favorites tab, trigger the delete control on a pinned card, confirm it, and verify the link disappears from Favorites and is not present anywhere else in the system (quickstart.md scenario 5).

**Note**: `DeleteControl` requires **zero production code changes** — it is already rendered unconditionally by `LinkListItem` (including on pinned cards, since T015 added `PinControl` *alongside*, not instead of, `DeleteControl`) and its `existsById`/`deleteById` logic is already agnostic to `pinned`. This phase exists purely to add the regression coverage the constitution requires, proving FR-012/FR-016 actually hold for pinned links rather than merely assuming they do because no code path treats `pinned` specially.

- [X] T041 [P] [US4] Add a test to `backend/src/test/java/com/shelflife/backend/link/LinkServiceTest.java`: `deleteLink` on a pinned link removes it entirely, regardless of its pinned state (depends on T006)
- [X] T042 [P] [US4] Add a test to `backend/src/test/java/com/shelflife/backend/link/LinkControllerTest.java`: `DELETE /api/links/{id}` on a pinned link returns `204` and the link is absent from a subsequent `GET /api/links/favorites` (depends on T021)
- [X] T043 [P] [US4] Add a delete arm/confirm/cancel assertion to `frontend/tests/FavoritesView.test.tsx` (or a `pinned={true}` case in `frontend/tests/LinkListItem.test.tsx`) proving `DeleteControl` behaves identically on a pinned card (depends on T031)

**Checkpoint**: A pinned link can be permanently deleted from Favorites, identically to the other two views, with no production code path treating pinned links specially — verify via quickstart.md scenario 5.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final regression and end-to-end validation across all four stories

- [X] T044 [P] Run the full backend suite (`mvn test` in `backend/`) and confirm all existing 168h/30d boundary, delete, and metadata tests still pass unmodified for `pinned = false` links (SC-006)
- [X] T045 [P] Run the full frontend suite (`npm test` in `frontend/`) and confirm all existing tests still pass
- [X] T046 Execute quickstart.md validation scenarios 1–6 end-to-end against the running app
- [X] T047 [P] Verify `PinControl` and `PinnedBadge` remain small and visually understated, and do not compete with the capture input's prominence on the active view (Constitution Principle III)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all four user stories (unlike Feature 3, every story here needs the persisted `pinned`/`pinnedAt` fields and the pinned-exclusion query predicate to exist)
- **User Story 1 (Phase 3)**: Depends on Foundational only — no dependency on any other story
- **User Story 2 (Phase 4)**: Depends on Foundational directly for its backend tasks (T019, T020, T024); its frontend tasks (T029, T031) depend on User Story 1's `pinned`/`onPinToggled` plumbing (T015, T016) already existing on `LinkListItem`/`LinkList`
- **User Story 3 (Phase 5)**: Depends on Foundational only for its backend tasks (T037, T038 depend on T004) — its frontend behavior is already fully built by User Story 1 (`PinControl`'s unpin branch) and User Story 2 (`FavoritesView`'s refresh wiring), so this phase adds no new frontend files
- **User Story 4 (Phase 6)**: Depends on User Story 1 (a pinned fixture must exist, T006) and User Story 2 (`GET /favorites`/`FavoritesView` must exist to observe the deletion from) — it adds no production code, only tests proving already-existing behavior
- **Polish (Phase 7)**: Depends on all four user stories being complete

### User Story Dependencies

Per spec.md, all four stories are independently valuable, but this feature's stories are more sequentially coupled than Feature 3's: User Story 2 (viewing Favorites) has little observable value until User Story 1 (pinning) can actually put something there, User Story 3 (unpinning) needs User Story 2's Favorites tab to unpin *from*, and User Story 4 (deleting a pinned link) needs both 1 and 2 to have a pinned link to delete in the first place. The plan's build order (US1 → US2 → US3 → US4) reflects this real dependency chain rather than four fully parallel tracks — though the *backend* halves of US1/US2/US3 have no code dependency on each other and could be built in parallel by different contributors, converging only in `LinkListItem.tsx`/`FavoritesView.tsx`.

### Parallel Opportunities

- Within Foundational, T002 has no parallel peer (T003 depends on it), but T005 can run in parallel with US1 setup once T003 lands
- Within US1, T008/T009/T010 (backend tests) can run in parallel with each other once their respective implementation tasks land; T012 (frontend API client) can start in parallel with T006 (backend service method) — different files, no shared dependency
- Within US2, T020/T022/T023/T024 (backend) and T025/T026/T027/T028/T030 (frontend) can each proceed in parallel within their own track
- T044 and T045 (backend/frontend full suite runs) can run in parallel
- T041, T042, T043 (US4) can all run in parallel — three independent test files, no production code to sequence around

---

## Parallel Example: User Story 2

```bash
# Backend and frontend prep can start together once Foundational is done:
Task: "Add forFavorites(Link) factory to backend/src/main/java/com/shelflife/backend/link/LinkResponse.java"
Task: "Change expiresAt: string to string | null in frontend/src/types/link.ts"
Task: "Create frontend/src/components/EmptyFavoritesIllustration.tsx"

# Once the favorites endpoint exists, its contract test can run alongside frontend wiring:
Task: "Add GET /favorites contract tests to backend/src/test/java/com/shelflife/backend/link/LinkControllerTest.java"
Task: "Create frontend/src/hooks/useFavoriteLinks.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1 (pin action)
4. **STOP and VALIDATE**: quickstart.md scenarios 1–3 (pin portions) — note that without User Story 2, a pinned link has no visible destination yet; it simply vanishes from the active list/graveyard, which is still independently verifiable and testable per spec.md's own framing
5. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → foundation ready (schema + query-layer changes, zero observable behavior change)
2. Add User Story 1 → validate → deploy/demo (pinning exempts a link, though it's not yet visible anywhere)
3. Add User Story 2 → validate → deploy/demo (Favorites tab makes pinning observable — this is where the feature's value becomes visible end to end)
4. Add User Story 3 → validate → deploy/demo (unpinning closes the loop — zero new frontend code required)
5. Add User Story 4 → validate → deploy/demo (regression coverage confirming delete already works on pinned links)
6. Phase 7: full regression + quickstart pass

### Parallel Team Strategy

With multiple developers, after Foundational:
- Developer A: User Story 1's backend (T006–T011), then User Story 3's backend (T037–T040) — same file, sequential for one person, but independent of the frontend track
- Developer B: User Story 1's frontend (T012–T018), then continues into User Story 2's frontend (T025–T036) since both live in the same components
- Developer C: User Story 2's backend (T019–T024), independent of the frontend
- Developer D: User Story 4 (fully test-only, can start as soon as T006 and T021 exist)

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- All new backend tests (T008, T009, T010, T022, T023, T024, T039, T040, T041, T042) and frontend tests (T014, T018, T034, T035, T036, T043) satisfy the constitution's Principle II obligation for this feature's two new lifecycle transitions (active/graveyard→favorites via pin, favorites→active via unpin), their true-no-op idempotency guarantees, and the correctness-critical pinned-survives-both-boundaries guarantee
- Existing 168h/30d boundary tests (for `pinned = false` links), `LinkCount.test.tsx`, `UrlCaptureForm.test.tsx`, `DeleteControl.test.tsx`, `GraveyardView.test.tsx`, `useActiveLinks.test.ts`, and `useGraveyardLinks.test.ts` are unchanged by this feature and must continue to pass unmodified (T044, T045)
- `DeleteControl.tsx` and `HourglassMotif.tsx` require no code changes at all in this feature
- Commit after each task or logical group
- Stop at any checkpoint to validate a story independently
