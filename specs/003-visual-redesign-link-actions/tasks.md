---

description: "Task list for Warm Visual Redesign & Card-Level Link Actions"
---

# Tasks: Warm Visual Redesign & Card-Level Link Actions

**Input**: Design documents from `/specs/003-visual-redesign-link-actions/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/link-metadata-and-delete-api.md, quickstart.md (all present)

**Tests**: Included. The constitution (Principle II) and plan.md's Constitution Check explicitly require dedicated tests for every new lifecycle transition (active→deleted, graveyard→deleted via manual delete), the SSRF guard, and the fetch-fallback/fetch-once paths, in addition to the frontend rendering/interaction tests plan.md calls for.

**Organization**: Tasks are grouped by user story (per spec.md's P1/P1/P2/P2/P3 priorities) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US5)
- File paths are exact and relative to the repository root

## Path Conventions

Web app monorepo, unchanged from Features 1–2: `backend/src/main/java/com/shelflife/backend/...` and `backend/src/test/java/com/shelflife/backend/...` (Java/Maven); `frontend/src/...` and `frontend/tests/...` (React/TypeScript/Vitest).

---

## Phase 1: Setup

**Purpose**: Establish a regression baseline before touching any code

- [X] T001 Run the existing backend suite (`mvn test` in `backend/`) and frontend suite (`npm test` in `frontend/`) and confirm all currently pass, establishing the pre-change baseline that SC-006 (zero timing regressions) will be checked against

---

## Phase 2: Foundational

**Purpose**: Blocking prerequisites shared by every user story

**Note**: This feature adds no infrastructure shared by *all* five stories — User Story 1 is a pure frontend restyle of already-existing fields (url, countdown) and needs no backend change; the new `Link` columns are needed only starting at User Story 2, and the `DELETE` route only at User Story 3. Each story's own prerequisites are scoped to that story's phase below, so there are no tasks in this phase.

**Checkpoint**: No blocking work here — proceed directly to User Story 1.

---

## Phase 3: User Story 1 - A warm, card-based redesign across both views (Priority: P1) 🎯 MVP

**Goal**: Every link on the active list and the graveyard renders as a rounded, shadowed card in the warm five-tone palette, with a clear title/URL/countdown hierarchy, identical between the two views.

**Independent Test**: Load the active list and the graveyard with several links present in each; verify both render rounded, shadowed, warm-palette cards with title/URL/countdown visually distinguishable, and that the two views look like the same application (quickstart.md scenario 1 — manual/visual, no new automated test per plan.md).

- [X] T002 [P] [US1] Define a custom DaisyUI theme in `frontend/src/index.css` mapping the five hex tones (sand `#DDCBB7`, brown `#7B4B36`, forest green `#264025`, olive `#82896E`, terracotta `#AD6B4B`) to DaisyUI semantic roles (`base-100`/`base-200` backgrounds, `base-content` text, `primary`, `neutral`, `accent`) per research.md §7
- [X] T003 [US1] Redesign the card shell in `frontend/src/components/LinkListItem.tsx`: rounded corners, comfortable internal padding, a subtle shadow, and clear typographic hierarchy (heading most prominent, URL secondary, countdown visually distinct) driven by the new theme's tokens (depends on T002)
- [X] T004 [US1] Align list/container spacing in `frontend/src/components/LinkList.tsx` so the active list and graveyard share identical card layout conventions (depends on T003)

**Checkpoint**: Active list and graveyard both render the new warm card shell identically — visually verify via quickstart.md scenario 1 before proceeding.

---

## Phase 4: User Story 2 - Recognize a saved link at a glance via title and favicon (Priority: P1)

**Goal**: Every card shows the destination page's retrieved title and favicon, falling back gracefully to the raw URL and a generic icon, for both newly saved and pre-existing links.

**Independent Test**: Save a link to a page with a retrievable title/favicon and a link where one or both cannot be retrieved; verify the first card shows the real title/favicon and the second falls back cleanly with no broken layout (quickstart.md scenario 2).

- [X] T005 [US2] Add nullable `pageTitle` (String, capped via `@Column(length = 512)` per data-model.md's "pathological `<title>`" guard, mirroring the existing `url` field's `@Column(length = 2048)`), `titleFetchedAt` (Instant), and `faviconUrl` (String) columns to `backend/src/main/java/com/shelflife/backend/link/Link.java`
- [X] T006 [P] [US2] Create `backend/src/main/java/com/shelflife/backend/link/LinkMetadataFetcher.java`: SSRF-guarded title fetch using `java.net.http.HttpClient` with `Redirect.NEVER`, a manual redirect loop capped at 3 hops, `InetAddress`-based IP-range validation (loopback/site-local/link-local/multicast/any-local rejected) re-checked at every hop, a 3–5s connect+read timeout, a ~64KB response-size cap via `BodyHandlers.ofInputStream()`, and regex-based `<title>` extraction with common HTML-entity decoding, truncating the extracted title to 512 chars before returning it (matching T005's column cap); plus a pure `faviconUrl` builder from the link's domain against a public favicon service (no network call) (depends on T005)
- [X] T007 [P] [US2] Write `backend/src/test/java/com/shelflife/backend/link/LinkMetadataFetcherTest.java` covering: SSRF rejection of loopback/private(RFC1918)/link-local targets, SSRF rejection when a redirect hop resolves to a private/loopback address, timeout handling, response-size cap enforcement, successful `<title>` extraction, missing-`<title>` fallback, and favicon URL construction from a domain (depends on T006)
- [X] T008 [US2] Compute `faviconUrl` synchronously in `LinkService.createLink(...)` at save time in `backend/src/main/java/com/shelflife/backend/link/LinkService.java` (depends on T006)
- [X] T009 [US2] Implement `backfillMetadata(List<Link>)` in `backend/src/main/java/com/shelflife/backend/link/LinkService.java`: partition links with `titleFetchedAt == null` or `faviconUrl == null`, fan out title-fetch/favicon-build calls concurrently via `Executors.newVirtualThreadPerTaskExecutor()`, persist updated rows (depends on T006, T008)
- [X] T010 [US2] Wire `backfillMetadata(...)` into `listActiveLinks()` and `listGraveyardLinks()` in `backend/src/main/java/com/shelflife/backend/link/LinkService.java`, called on the loaded list before mapping to response (depends on T009)
- [X] T011 [US2] Add `title` (never-null, fallback-resolved: `pageTitle` or `url`) and `faviconUrl` (nullable) fields to `backend/src/main/java/com/shelflife/backend/link/LinkResponse.java`, updating **both** `from(Link)` — used by `POST /api/links` and the active-list `GET /api/links` (`LinkController.java:27,33`) — **and** `forGraveyard(Link)` with identical title/faviconUrl fallback-resolution logic in both factories, not just `forGraveyard(...)` (depends on T010)
- [X] T012 [P] [US2] Add metadata backfill/fallback tests to `backend/src/test/java/com/shelflife/backend/link/LinkServiceTest.java`: retrieved title used as `title`, missing title falls back to `url`, `faviconUrl` populated at save time, a pre-existing link with `titleFetchedAt == null` is backfilled on its next list read, a link is never re-fetched once `titleFetchedAt` is set (no second network call on a subsequent read), and — via a spy/mock `LinkMetadataFetcher` — `createLink(...)` completes without invoking its network-calling title-fetch method, giving FR-009's non-blocking-save guarantee an actual regression test rather than just an architectural assumption (depends on T009, T011)
- [X] T013 [P] [US2] Update `backend/src/test/java/com/shelflife/backend/link/LinkControllerTest.java` GET response assertions to include the new `title` and `faviconUrl` fields in both the active and graveyard responses (depends on T011)
- [X] T014 [P] [US2] Add `title` (string) and `faviconUrl` (string | null) fields to `frontend/src/types/link.ts`
- [X] T015 [US2] Update `frontend/src/components/LinkListItem.tsx` to render `link.title` as the card's primary heading and `link.faviconUrl` as a small favicon `<img>` alongside it; since `faviconUrl` is a pure, always-successful string construction (T008) and will essentially never be null in practice, the real fallback trigger is the constructed image URL failing to actually load — add an `onError` handler on the `<img>` that swaps it to a neutral generic fallback icon (also used directly when `faviconUrl` is null) (depends on T003, T014)
- [X] T016 [P] [US2] Add title/favicon rendering tests to `frontend/tests/LinkListItem.test.tsx`: retrieved title rendered as heading, favicon image rendered when `faviconUrl` present, generic fallback icon rendered when `faviconUrl` is null, and generic fallback icon rendered after simulating an image load failure (firing the favicon `<img>`'s `onError` event), layout intact in all cases (depends on T015)

**Checkpoint**: Active list and graveyard cards show real titles/favicons with correct fallbacks, for both new and backfilled links — verify via quickstart.md scenario 2.

---

## Phase 5: User Story 3 - Permanently delete a single link from its card (Priority: P2)

**Goal**: Every card, on either view, has a small delete control that requires arm-then-confirm before permanently removing that link's data everywhere.

**Independent Test**: From the active list, arm and confirm a card's delete control and verify the link is gone from everywhere; repeat from the graveyard (quickstart.md scenario 3).

- [X] T017 [US3] Add `"DELETE"` to `allowedMethods` in `backend/src/main/java/com/shelflife/backend/WebConfig.java`
- [X] T018 [US3] Implement `deleteLink(Long id)` in `backend/src/main/java/com/shelflife/backend/link/LinkService.java`: `existsById` check + `deleteById`, an idempotent no-op when the id doesn't exist
- [X] T019 [US3] Add `DELETE /api/links/{id}` endpoint returning `204 No Content` in `backend/src/main/java/com/shelflife/backend/link/LinkController.java` (depends on T017, T018)
- [X] T020 [P] [US3] Add `deleteLink` tests to `backend/src/test/java/com/shelflife/backend/link/LinkServiceTest.java`: deleting an active link removes it, deleting a graveyard link removes it, deleting a non-existent id is a no-op, deleting one link leaves every other link's `expiresAt`/order/count unaffected (depends on T018)
- [X] T021 [US3] Update `backend/src/test/java/com/shelflife/backend/link/LinkControllerTest.java`: add DELETE contract tests (active link → 204, graveyard link → 204, non-existent id → 204), and update `controllerExposesOnlyGetAndPostNoRescueResurrectEarlyDeleteOrPinEndpoint` to assert the new DELETE contract while continuing to confirm PATCH/PUT remain unsupported (depends on T019)
- [X] T022 [P] [US3] Add `deleteLink(id: number): Promise<void>` to `frontend/src/api/linksApi.ts`
- [X] T023 [P] [US3] Create `frontend/src/components/DeleteControl.tsx`: on-card icon button with local armed/normal state via `useState`, a ~3s auto-revert `setTimeout` on arm, a `mousedown`/`pointerdown` click-outside listener attached only while armed, a confirmed second activation calling `deleteLink(id)` then a provided `onDeleted` callback (depends on T022)
- [X] T024 [P] [US3] Write `frontend/tests/DeleteControl.test.tsx`: arm then confirm deletes and calls `onDeleted`, arm then wait ~3s auto-cancels without deleting, arm then click outside cancels without deleting (depends on T023)
- [X] T025 [US3] Integrate `DeleteControl` into `frontend/src/components/LinkListItem.tsx` (depends on T015, T023)
- [X] T026 [US3] Wire an `onDeleted` refresh callback through `frontend/src/components/ActiveView.tsx` and `frontend/src/components/GraveyardView.tsx` so a confirmed delete refreshes that view's list (depends on T025)
- [X] T027 [P] [US3] Add delete arm/confirm/cancel behavior assertions to `frontend/tests/LinkListItem.test.tsx` (depends on T025)

**Checkpoint**: A link can be permanently deleted, with arm/confirm/cancel behavior, identically from both views — verify via quickstart.md scenario 3.

---

## Phase 6: User Story 4 - Countdown urgency expressed through the card's own design (Priority: P2)

**Goal**: Each card's countdown color intensity and an accompanying leaf motif both shift from calm/fresh to urgent/wilted as a link nears its deadline, without changing the underlying countdown value.

**Independent Test**: View cards at varying points in their countdown and verify both the color and the leaf motif are visibly different, while the displayed remaining time is unaffected (quickstart.md scenario 4).

- [X] T028 [P] [US4] Create `frontend/src/components/LeafMotif.tsx`: small inline SVG with fresh/turning/wilted variants driven by an urgency prop
- [X] T029 [US4] Compute a client-side urgency band in `frontend/src/components/LinkListItem.tsx`: remaining fraction = `(expiresAt - now) / totalDuration` (168h when `granularity="fine"`, 30d when `granularity="coarse"`), bucketed into fresh (≥50% remaining) / turning (10–50%) / wilted (<10%), driving both the countdown badge's color-intensity class and the `LeafMotif` variant at the same thresholds (depends on T015, T028)
- [X] T030 [P] [US4] Add urgency-band/leaf-motif assertions to `frontend/tests/LinkListItem.test.tsx` across fresh/turning/wilted bands, verifying the displayed remaining-time text and expiry moment are unaffected by the urgency presentation (depends on T029)

**Checkpoint**: Countdown urgency is visible via color and leaf motif together, with zero change to the underlying timing — verify via quickstart.md scenario 4 and confirm existing 168h/30d boundary tests still pass unmodified.

---

## Phase 7: User Story 5 - A little delight in empty states (Priority: P3)

**Goal**: The active list's and the graveyard's empty states each show a small, distinct hand-drawn-style illustration instead of plain text alone.

**Independent Test**: Empty the active list and view it, then empty the graveyard and view it; verify each shows its own small illustration (quickstart.md scenario 5).

- [X] T031 [P] [US5] Create `frontend/src/components/EmptyActiveIllustration.tsx`: small inline hand-drawn-style SVG for the active list's empty state
- [X] T032 [P] [US5] Create `frontend/src/components/EmptyGraveyardIllustration.tsx`: distinct small inline SVG for the graveyard's empty state
- [X] T033 [US5] Add an `emptyIllustration` slot to `frontend/src/components/LinkList.tsx`, rendered alongside `emptyMessage` when `links.length === 0`, keeping existing `emptyMessage`/`granularity`/`openable` props unchanged
- [X] T034 [US5] Pass `EmptyActiveIllustration` into `LinkList` from `frontend/src/components/ActiveView.tsx` (depends on T031, T033)
- [X] T035 [US5] Pass `EmptyGraveyardIllustration` into `LinkList` from `frontend/src/components/GraveyardView.tsx` (depends on T032, T033)
- [X] T036 [P] [US5] Add empty-state illustration rendering tests to `frontend/tests/LinkList.test.tsx` for both the active and graveyard illustration slots (depends on T033)

**Checkpoint**: Both empty states show their own illustration, and illustrations appear nowhere else — verify via quickstart.md scenario 5.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Final regression and end-to-end validation across all five stories

- [X] T037 [P] Run the full backend suite (`mvn test` in `backend/`) and confirm all existing 168h/30d boundary tests still pass unmodified (SC-006)
- [X] T038 [P] Run the full frontend suite (`npm test` in `frontend/`) and confirm all existing tests still pass
- [X] T039 Execute quickstart.md validation scenarios 1–5 end-to-end against the running app
- [X] T040 [P] Verify a long retrieved title and a long raw URL both truncate without breaking the card layout in `frontend/src/components/LinkListItem.tsx`, per the spec's long-title edge case

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Empty — no cross-story blocking work
- **User Story 1 (Phase 3)**: Depends on Setup only — no dependency on any other story
- **User Story 2 (Phase 4)**: Depends on Setup only; T015 (frontend rendering) depends on US1's T003 for the card shell to exist, but US2's backend tasks (T005–T013) have no dependency on US1 at all
- **User Story 3 (Phase 5)**: Depends on Setup only; frontend integration (T025) depends on US2's T015 (the card must render before a delete control is placed on it), but US3's backend tasks (T017–T021) have no dependency on US1 or US2
- **User Story 4 (Phase 6)**: Depends on US2's T015 (needs the redesigned card in place to attach urgency styling to)
- **User Story 5 (Phase 7)**: Depends on Setup only — no dependency on any other story
- **Polish (Phase 8)**: Depends on all desired user stories being complete

### User Story Dependencies

Per spec.md, all five stories are independently valuable; the sequencing above reflects the plan's stated build order (US1's card shell is the shell the others render inside of) rather than a hard technical coupling — US1, US3 (backend), and US5 have no technical dependency on each other and could be built in parallel by different contributors, while US2's frontend piece and US4 both need US1's card shell (T003) first.

### Parallel Opportunities

- T002 (theme) and any US5 illustration task (T031, T032) can run in parallel with each other — different files
- Within US2, T006 (fetcher) and T014 (frontend type) can run in parallel; T007 and T012/T013 (test tasks) can run in parallel with each other once their respective implementation tasks land
- Within US3, backend (T017–T021) and frontend (T022–T024) can be built entirely in parallel, converging only at T025 (integration into `LinkListItem.tsx`)
- T037 and T038 (backend/frontend full suite runs) can run in parallel

---

## Parallel Example: User Story 2

```bash
# Backend and frontend prep can start together:
Task: "Create LinkMetadataFetcher in backend/src/main/java/com/shelflife/backend/link/LinkMetadataFetcher.java"
Task: "Add title/faviconUrl fields to frontend/src/types/link.ts"

# Once the fetcher exists, its test can run alongside backend wiring:
Task: "Write LinkMetadataFetcherTest in backend/src/test/java/com/shelflife/backend/link/LinkMetadataFetcherTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 3: User Story 1 (card shell + theme)
3. **STOP and VALIDATE**: quickstart.md scenario 1
4. Deploy/demo if ready — this alone already delivers the visual redesign's core value

### Incremental Delivery

1. Setup → Foundation ready (no foundational tasks to complete)
2. Add User Story 1 → validate → deploy/demo (MVP)
3. Add User Story 2 → validate → deploy/demo (title/favicon recognition)
4. Add User Story 3 → validate → deploy/demo (manual delete)
5. Add User Story 4 → validate → deploy/demo (countdown urgency motif)
6. Add User Story 5 → validate → deploy/demo (empty-state illustrations)
7. Phase 8: full regression + quickstart pass

### Parallel Team Strategy

With multiple developers, after Setup:
- Developer A: User Story 1, then User Story 4 (both own `LinkListItem.tsx`'s visual layer)
- Developer B: User Story 2 backend (T005–T013), independent of the frontend
- Developer C: User Story 3 backend (T017–T021) and frontend (T022–T024) in parallel, integrating once US1/US2's card shell lands
- Developer D: User Story 5 (fully independent)

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- All new backend tests (T007, T012, T013, T020, T021) and frontend tests (T016, T024, T027, T030, T036) satisfy the constitution's Principle II obligation for this feature's new lifecycle transitions (active→deleted, graveyard→deleted via manual delete) and hard-to-verify-by-inspection logic (SSRF guard, fetch-once guarantee)
- Existing 168h/30d boundary tests, `LinkRepositoryTest.java`, `LinkCount.test.tsx`, `UrlCaptureForm.test.tsx`, `NavTabs.test.tsx`, `useActiveLinks.test.ts`, and `useGraveyardLinks.test.ts` are unchanged by this feature and must continue to pass unmodified (T037, T038)
- Commit after each task or logical group
- Stop at any checkpoint to validate a story independently
