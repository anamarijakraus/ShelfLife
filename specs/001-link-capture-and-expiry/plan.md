# Implementation Plan: Link Capture & Active List with Automatic Expiry

**Branch**: `001-link-capture-and-expiry` | **Date**: 2026-07-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-link-capture-and-expiry/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Deliver ShelfLife's core loop: a single URL input that saves a link instantly, and a landing
page listing all active (unexpired) links ordered soonest-to-expire, each showing its raw URL
and a periodically-refreshed countdown toward its fixed 168-hour lifespan. Expiration is enforced
purely by a read-time query filter (`expiresAt > now()`) — no scheduled job — and expired link
rows are left untouched in storage for a future graveyard feature to consume. Backend is a
Spring Boot 3.5 (Java 21) REST API over an H2 file-based database; frontend is a React 19 +
Vite + TypeScript single page styled with Tailwind CSS and DaisyUI, polling the API on a fixed
interval to keep the countdown and list current.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.x with React 19 (frontend)

**Primary Dependencies**: Spring Boot 3.5.x (Spring Web, Spring Data JPA), Maven (backend);
React 19, Vite, Tailwind CSS + DaisyUI plugin (frontend)

**Storage**: H2 database in file-based (persistent) mode; Hibernate `ddl-auto=update` for schema
management (no migration tool at this stage)

**Testing**: JUnit 5 + Spring Boot Test (`@DataJpaTest`, `@WebMvcTest`) for backend; Vitest +
React Testing Library for frontend

**Target Platform**: Self-hosted/local web server (backend) served to a modern browser
(frontend); single-user, no deployment-scale infrastructure

**Project Type**: Web application — monorepo with `backend/` and `frontend/` as sibling
top-level folders, communicating over a JSON REST API (no GraphQL)

**Performance Goals**: No fixed numeric SLA (personal-scale, per constitution); the active-list
query MUST be a single indexed, set-based read (not per-row iteration), and UI interactions MUST
never block on network round-trips

**Constraints**: No authentication/login in this phase (single implicit user); expiration MUST be
enforced by read-time filtering only, with no scheduled/background job; expired link rows MUST
remain in storage unmodified; URLs missing a scheme are normalized (`https://` prepended)
server-side rather than rejected

**Scale/Scope**: Single user, expected link volume in the hundreds (per constitution), not
millions

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Code Quality** — PASS. Backend uses standard Spring Boot controller → service →
  repository separation for a single `Link` resource, no extra interfaces or abstraction layers
  (one concrete `LinkService`, one `LinkRepository` via Spring Data JPA — no speculative
  strategy/factory patterns). Frontend components are small and single-purpose (URL input form,
  link list, individual link row, count display), with a single custom hook for polling — no
  state-management library, matching the explicit user instruction and the "no speculative
  extensibility" principle.
- **II. Testing Standards** — PASS, with one scope note. The constitution's testing principle
  names "the scheduled expiration job" as a required test target; this feature has no scheduled
  job by design (spec Clarifications, 2026-07-02) — expiration is a read-time query filter
  instead. The equivalent obligation is fully preserved by testing that filter directly: every
  boundary (167h59m active, exactly 168h00m excluded, 168h01m excluded) MUST be covered by tests
  against the repository query / service method that computes the active list, per FR-011,
  FR-012, and User Story 3's acceptance scenarios. This is recorded as a scope note, not a
  violation — no Complexity Tracking entry needed.
- **III. User Experience Consistency** — PASS. Styling uses Tailwind CSS utility classes plus
  DaisyUI (a Tailwind plugin providing pre-built class names, not a JS component library), so no
  heavy component/design-system library is introduced. The URL input is the single, most
  prominent element on the only view this feature ships. Only one view exists in this feature
  (no graveyard view yet), so cross-view consistency is not yet applicable — components are kept
  simple rather than speculatively built for reuse, per Principle I.
- **IV. Performance Requirements** — PASS. The active-list query filters and orders by an indexed
  `expiresAt` column in one set-based query (no N+1 or per-row iteration). The frontend refreshes
  via a fixed ~60s polling interval using plain `fetch`, never blocking user input on the network
  round-trip for capture (optimistic-friendly instant feedback on submit).

No violations identified; Complexity Tracking table is not needed.

**Post-Phase 1 re-check**: Re-evaluated after producing `data-model.md`, `contracts/links-api.md`,
and `quickstart.md`. The design introduced no new dependencies, layers, or state beyond what was
assessed above (single entity, single indexed query, two REST endpoints, count derived
client-side rather than stored). All four principles still PASS with no changes to this section.

## Project Structure

### Documentation (this feature)

```text
specs/001-link-capture-and-expiry/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── links-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/shelflife/backend/
│   │   │   ├── BackendApplication.java
│   │   │   └── link/
│   │   │       ├── Link.java                 # JPA entity
│   │   │       ├── LinkRepository.java       # Spring Data JPA repository
│   │   │       ├── LinkService.java          # business logic (save, list active)
│   │   │       ├── LinkController.java       # REST endpoints
│   │   │       ├── LinkResponse.java         # outbound DTO
│   │   │       └── CreateLinkRequest.java    # inbound DTO
│   │   └── resources/
│   │       └── application.properties        # H2 file-mode config, ddl-auto=update
│   └── test/
│       └── java/com/shelflife/backend/link/
│           ├── LinkServiceTest.java           # boundary/unit tests (167h59m/168h00m/168h01m)
│           ├── LinkRepositoryTest.java        # @DataJpaTest query tests
│           └── LinkControllerTest.java        # @WebMvcTest contract tests

frontend/
├── package.json
├── vite.config.ts
├── tailwind.config.js
├── index.html
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── api/
│   │   └── linksApi.ts                # fetch wrapper for GET/POST /api/links
│   ├── hooks/
│   │   └── useActiveLinks.ts          # polling hook (~60s interval)
│   ├── components/
│   │   ├── UrlCaptureForm.tsx         # the single input + Enter-to-save
│   │   ├── LinkList.tsx               # renders ordered active links
│   │   ├── LinkListItem.tsx           # one link: URL label + countdown
│   │   └── ActiveCount.tsx            # at-a-glance count
│   └── types/
│       └── link.ts                    # shared TS types matching API DTOs
└── tests/
    ├── UrlCaptureForm.test.tsx
    ├── LinkList.test.tsx
    └── useActiveLinks.test.ts
```

**Structure Decision**: Web application monorepo with `backend/` and `frontend/` as sibling
top-level folders (per explicit instruction), communicating over a JSON REST API. Backend follows
a single feature package (`link/`) with the standard controller → service → repository layers, no
additional layers. Frontend is a flat, small-component structure with one custom hook for
polling; no state-management library or routing library, since this feature has exactly one view.

## Complexity Tracking

*No Constitution Check violations — this section is intentionally empty.*
