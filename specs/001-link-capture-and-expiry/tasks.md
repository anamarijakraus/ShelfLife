---

description: "Task list for Link Capture & Active List with Automatic Expiry"
---

# Tasks: Link Capture & Active List with Automatic Expiry

**Input**: Design documents from `/specs/001-link-capture-and-expiry/`

**Prerequisites**: plan.md, spec.md, data-model.md, contracts/links-api.md, research.md, quickstart.md

**Tests**: Included. The project constitution (Testing Standards, Principle II) mandates dedicated
tests for every link-lifecycle time boundary (167h59m / exactly 168h00m / 168h01m) and for the
logic that determines active vs. expired — this is a hard governance requirement, not an optional
extra, so boundary and query tests are part of User Story 3's tasks below. Contract/component
tests for the other stories follow directly from the spec's acceptance scenarios and the API
contract.

**Organization**: Tasks are grouped by user story (from spec.md) to enable independent
implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Paths are relative to the repository root (`backend/`, `frontend/` are sibling top-level folders)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization for the monorepo

- [X] T001 Create monorepo structure with `backend/` and `frontend/` as sibling top-level directories at the repository root
- [X] T002 [P] Initialize backend Maven project in `backend/pom.xml` with Spring Boot 3.5.x parent, `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `com.h2database:h2`, Java 21 source/target
- [X] T003 [P] Initialize frontend Vite + React 19 + TypeScript project in `frontend/` (`frontend/package.json`, `frontend/tsconfig.json`, `frontend/vite.config.ts`)
- [X] T004 [P] Configure Tailwind CSS and the DaisyUI plugin in `frontend/vite.config.ts` (`@tailwindcss/vite` plugin) and `frontend/src/index.css` (`@import "tailwindcss"; @plugin "daisyui";`) — Tailwind v4 uses CSS-first config, no `tailwind.config.js` needed
- [X] T005 [P] Configure Vitest and React Testing Library in `frontend/vite.config.ts` (test block) and `frontend/src/setupTests.ts`
- [X] T006 [P] Configure H2 file-mode datasource and `ddl-auto=update` in `backend/src/main/resources/application.properties` (plus an in-memory `ddl-auto=create-drop` variant in `backend/src/test/resources/application.properties` so tests don't touch the dev data file)

**Checkpoint**: Both projects build/run empty shells; ready for foundational code.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core entity, repository, DTO types, and API plumbing every user story depends on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T007 Create `Link` JPA entity (`id`, `url: String` with a max length of 2048 chars, `savedAt: Instant`, `expiresAt: Instant`, index on `expiresAt`) in `backend/src/main/java/com/shelflife/backend/link/Link.java`
- [X] T008 Create `LinkRepository` extending `JpaRepository<Link, Long>` with derived query `findByExpiresAtAfterOrderByExpiresAtAsc(Instant now)` in `backend/src/main/java/com/shelflife/backend/link/LinkRepository.java` (depends on T007)
- [X] T009 [P] Create `BackendApplication` main class in `backend/src/main/java/com/shelflife/backend/BackendApplication.java`
- [X] T010 [P] Configure CORS for the local Vite dev server origin, scoped to `/api/**`, in `backend/src/main/java/com/shelflife/backend/WebConfig.java` — plus a Vite dev-server proxy (`/api` → `http://localhost:8080`) added in `frontend/vite.config.ts` during end-to-end verification, since the frontend's relative-path `fetch` calls need same-origin proxying to reach the backend in dev (see research.md §5 for details)
- [X] T011 [P] Create shared `Link` TypeScript type (`id`, `url`, `savedAt`, `expiresAt`) matching the API contract in `frontend/src/types/link.ts`
- [X] T012 [P] Create API client module with `createLink(url)` (POST) and `fetchActiveLinks()` (GET) functions wrapping `fetch` in `frontend/src/api/linksApi.ts` (uses the type from T011)

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 - Capture a link instantly (Priority: P1) 🎯 MVP

**Goal**: Paste a URL, press Enter, and have it saved immediately with zero extra steps.

**Independent Test**: Submit a valid URL via `POST /api/links` (or the form) and confirm it is
persisted and returned with a normalized URL, a `savedAt`, and an `expiresAt` 168 hours later.

### Tests for User Story 1

- [X] T013 [P] [US1] Contract tests for `POST /api/links` — valid URL, missing-scheme normalization, blank input (400), unparsable input (400), and submitting the same URL twice yields two distinct links with independent `id`/`savedAt`/`expiresAt` (FR-016) — in `backend/src/test/java/com/shelflife/backend/link/LinkControllerTest.java`
- [X] T014 [P] [US1] Unit tests for `LinkService` URL validation and scheme normalization (blank, missing scheme, malformed even after normalization) in `backend/src/test/java/com/shelflife/backend/link/LinkServiceTest.java`
- [X] T015 [P] [US1] Component test for `UrlCaptureForm` (submits valid URL, empty submit is a no-op, invalid URL shows a non-blocking inline error) in `frontend/tests/UrlCaptureForm.test.tsx`

### Implementation for User Story 1

- [X] T016 [P] [US1] Create `CreateLinkRequest` DTO in `backend/src/main/java/com/shelflife/backend/link/CreateLinkRequest.java`
- [X] T017 [P] [US1] Create `LinkResponse` DTO in `backend/src/main/java/com/shelflife/backend/link/LinkResponse.java`
- [X] T018 [US1] Implement `LinkService.createLink(url)`: trim input, prepend `https://` if no scheme is present, validate via `java.net.URI`, reject if still invalid, else set `savedAt = now()`, `expiresAt = savedAt + 168h`, and persist via `LinkRepository` in `backend/src/main/java/com/shelflife/backend/link/LinkService.java` (depends on T007, T008, T016, T017) — `savedAt` is truncated to millisecond precision so H2/JDBC timestamp comparisons at exact boundaries are consistent
- [X] T019 [US1] Implement `POST /api/links` in `LinkController`, returning `201` with `LinkResponse` on success or `400` with the error-shape body on validation failure, in `backend/src/main/java/com/shelflife/backend/link/LinkController.java` (depends on T018)
- [X] T020 [P] [US1] Implement `UrlCaptureForm` component: single input, submit-on-Enter, calls `linksApi.createLink`, clears the field and shows a non-blocking inline error on rejection, in `frontend/src/components/UrlCaptureForm.tsx` (depends on T012)
- [X] T021 [US1] Create `App.tsx` rendering `UrlCaptureForm` as the page's primary element in `frontend/src/App.tsx` (depends on T020)

**Checkpoint**: A link can be captured end-to-end (form → API → persisted row) independent of any
list UI.

---

## Phase 4: User Story 2 - View active links ordered by urgency (Priority: P1)

**Goal**: The landing page lists all active links, soonest-to-expire first, each showing its raw
URL and remaining time, refreshing on a periodic tick without a manual reload.

**Independent Test**: With several links saved at different times, call `GET /api/links` (or load
the page) and verify results are ordered soonest-to-expire first, each with a URL label and
remaining time.

### Tests for User Story 2

- [X] T022 [P] [US2] Contract tests for `GET /api/links` — soonest-first ordering, raw URL as label, empty array when nothing is saved — in `backend/src/test/java/com/shelflife/backend/link/LinkControllerTest.java`
- [X] T023 [P] [US2] `@DataJpaTest` tests for `findByExpiresAtAfterOrderByExpiresAtAsc` ordering with multiple persisted links in `backend/src/test/java/com/shelflife/backend/link/LinkRepositoryTest.java`
- [X] T024 [P] [US2] Component test for `useActiveLinks` (fetches on mount, refetches on the periodic tick) in `frontend/tests/useActiveLinks.test.ts`
- [X] T025 [P] [US2] Component test for `LinkList` (renders items in received order, shows an empty state with zero links) in `frontend/tests/LinkList.test.tsx`

### Implementation for User Story 2

- [X] T026 [US2] Implement `LinkService.listActiveLinks(now)` calling `LinkRepository.findByExpiresAtAfterOrderByExpiresAtAsc` in `backend/src/main/java/com/shelflife/backend/link/LinkService.java` (depends on T008)
- [X] T027 [US2] Implement `GET /api/links` in `LinkController`, returning `200` with `{ "links": [...] }` in `backend/src/main/java/com/shelflife/backend/link/LinkController.java` (depends on T026)
- [X] T028 [P] [US2] Implement `useActiveLinks` hook: fetch on mount via `linksApi.fetchActiveLinks`, re-fetch every ~60 seconds via `setInterval`, expose the list via `useState` in `frontend/src/hooks/useActiveLinks.ts` (depends on T012)
- [X] T029 [P] [US2] Implement `LinkListItem` component: raw URL label (truncated with an ellipsis via a Tailwind/DaisyUI utility class when it would otherwise break the layout), remaining time computed from `expiresAt` and the current render time (no locally-decremented state) in `frontend/src/components/LinkListItem.tsx`
- [X] T030 [US2] Implement `LinkList` component: renders `LinkListItem`s in the order received, shows an empty-state message with zero links, in `frontend/src/components/LinkList.tsx` (depends on T029)
- [X] T031 [US2] Integrate `useActiveLinks` and `LinkList` into `App.tsx` alongside `UrlCaptureForm` in `frontend/src/App.tsx` (depends on T021, T028, T030)

**Checkpoint**: User Stories 1 and 2 together deliver the MVP capture-and-view loop.

---

## Phase 5: User Story 3 - Links expire automatically and reliably (Priority: P2)

**Goal**: Prove that the read-time filter correctly excludes links at and past their 168-hour
mark at every relevant boundary, and that expired rows are never modified or deleted.

**Independent Test**: Persist a link with an `expiresAt` in the past, call `GET /api/links`,
confirm it is absent from the response, then confirm its row is still present and unchanged in
the database.

### Tests for User Story 3

- [X] T032 [P] [US3] Boundary unit tests in `LinkServiceTest`: a link at 167h59m since `savedAt` is included in `listActiveLinks`; a link at exactly 168h00m is excluded; a link at 168h01m is excluded — in `backend/src/test/java/com/shelflife/backend/link/LinkServiceTest.java`
- [X] T033 [P] [US3] `@DataJpaTest` boundary tests for `findByExpiresAtAfterOrderByExpiresAtAsc` using directly persisted fixtures at 167h59m / exactly 168h00m / 168h01m in `backend/src/test/java/com/shelflife/backend/link/LinkRepositoryTest.java`
- [X] T034 [P] [US3] Test confirming a link excluded from `GET /api/links` for being expired still exists in the database with unmodified `url`/`savedAt`/`expiresAt` (FR-017) in `backend/src/test/java/com/shelflife/backend/link/LinkRepositoryTest.java`

### Implementation for User Story 3

- [X] T035 [US3] Confirm/adjust the query and any comparison logic in `LinkRepository`/`LinkService` so the exact-boundary instant (`expiresAt == now`) is treated as expired (excluded), per FR-007, in `backend/src/main/java/com/shelflife/backend/link/LinkRepository.java` and `LinkService.java` (depends on T008, T026; resolves any failures from T032–T034) — confirmed correct: `findByExpiresAtAfterOrderByExpiresAtAsc` uses strict `After` (>), so `expiresAt == now` is excluded; no code change needed, all boundary tests pass

**Checkpoint**: Expiration correctness is verified at every required time boundary with no
scheduled job, satisfying the constitution's testing-rigor requirement via direct query/service
tests.

---

## Phase 6: User Story 4 - See saved-link count at a glance (Priority: P3)

**Goal**: The landing page shows a live count of active links, matching what's in the list.

**Independent Test**: With a known number of active links, verify the displayed count matches;
save or let a link expire and verify the count updates on the next refresh.

### Tests for User Story 4

- [X] T036 [P] [US4] Component test for `ActiveCount` (renders the count matching the number of links passed in, updates when the links prop changes) in `frontend/tests/ActiveCount.test.tsx`

### Implementation for User Story 4

- [X] T037 [P] [US4] Implement `ActiveCount` component, deriving its value from `links.length` (no separate API call or stored counter) in `frontend/src/components/ActiveCount.tsx`
- [X] T038 [US4] Integrate `ActiveCount` into `App.tsx`, driven by the same links state `LinkList` uses, in `frontend/src/App.tsx` (depends on T031, T037)

**Checkpoint**: All four user stories are independently functional and integrated together.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final consistency pass across the whole feature

- [X] T039 [P] Apply consistent Tailwind/DaisyUI spacing, typography, and empty-state styling across `UrlCaptureForm`, `LinkList`, and `ActiveCount` in `frontend/src/components/`
- [X] T040 Run all `quickstart.md` validation scenarios end-to-end with backend and frontend running together, and fix any discrepancies found — found and fixed a real bug (relative-path `fetch` calls needed the Vite proxy, not just CORS, to reach the backend in dev; see research.md §5); verified via a real headless-browser session: capture, scheme normalization, invalid-input rejection, ordering, countdown, and count all work correctly

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational only.
- **User Story 2 (Phase 4)**: Depends on Foundational only; independently testable from US1, though `App.tsx` integration (T031) composes with US1's T021.
- **User Story 3 (Phase 5)**: Depends on Foundational and on US2's `listActiveLinks`/query existing (T026); it hardens that same query with boundary tests rather than adding new endpoints.
- **User Story 4 (Phase 6)**: Depends on Foundational and on US2's list data being available in `App.tsx` (T031) for the count to derive from.
- **Polish (Phase 7)**: Depends on all four user stories being complete.

### Within Each User Story

- Tests are written alongside/before their corresponding implementation task and MUST pass once that task is done.
- DTOs/entities before services; services before controllers/endpoints; hooks/components before `App.tsx` integration.

### Parallel Opportunities

- All Setup tasks (T002–T006) can run in parallel once T001 exists.
- Foundational tasks T009–T012 can run in parallel after T007–T008.
- US1 and US2 backend work can proceed in parallel (different HTTP methods on the same controller file — coordinate T019/T027 if implemented by different people).
- All test tasks marked [P] within a story can run in parallel with each other before their implementation tasks.
- US4 can be built in parallel with US3, since both depend only on Foundational + US2, not on each other.

---

## Parallel Example: User Story 1

```bash
# Launch US1 tests together:
Task: "Contract tests for POST /api/links in backend/src/test/java/com/shelflife/backend/link/LinkControllerTest.java"
Task: "Unit tests for LinkService validation/normalization in backend/src/test/java/com/shelflife/backend/link/LinkServiceTest.java"
Task: "Component test for UrlCaptureForm in frontend/tests/UrlCaptureForm.test.tsx"

# Launch US1 DTOs together:
Task: "Create CreateLinkRequest DTO in backend/src/main/java/com/shelflife/backend/link/CreateLinkRequest.java"
Task: "Create LinkResponse DTO in backend/src/main/java/com/shelflife/backend/link/LinkResponse.java"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (blocks everything else)
3. Complete Phase 3: User Story 1 (capture)
4. Complete Phase 4: User Story 2 (view) — together these two P1 stories are the MVP
5. **STOP and VALIDATE**: Run the quickstart's Scenario 1 and 2 checks
6. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. Add User Story 1 → verify capture works via API → demo
3. Add User Story 2 → verify the list renders correctly ordered → demo (MVP complete)
4. Add User Story 3 → verify boundary correctness with fixture data → demo
5. Add User Story 4 → verify the count → demo
6. Polish → final consistency pass

---

## Notes

- [P] tasks touch different files with no unmet dependencies within their phase.
- [Story] labels map each task to its user story for traceability.
- Commit after each task or logical group.
- Stop at each checkpoint to validate that story independently before moving on.
- No scheduled/background job exists anywhere in this task list by design — expiration is always
  a read-time query filter (see spec Clarifications, 2026-07-02, and plan.md's Constitution Check).

---

## Phase 8: Convergence

- [X] T041 Add host-shape validation to `LinkService.normalize` rejecting single-label hosts (e.g., "https://a", "https://localhost") that lack a dot separating a label from a TLD-like suffix, in `backend/src/main/java/com/shelflife/backend/link/LinkService.java` per FR-003 (contradicts)
- [X] T042 [P] Add unit tests in `LinkServiceTest` covering rejection of single-label hosts (e.g., "https://a", "https://localhost") and acceptance of multi-label hosts (e.g., "a.com", "example.co.uk") in `backend/src/test/java/com/shelflife/backend/link/LinkServiceTest.java` per FR-003 (missing)
- [X] T043 [P] Add a contract test in `LinkControllerTest` for `POST /api/links` confirming a single-label-host URL (e.g., "https://localhost") is rejected with `400` in `backend/src/test/java/com/shelflife/backend/link/LinkControllerTest.java` per FR-003 (missing)
