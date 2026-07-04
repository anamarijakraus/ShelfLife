# Implementation Plan: Warm Visual Redesign & Card-Level Link Actions

**Branch**: `003-visual-redesign-link-actions` | **Date**: 2026-07-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-visual-redesign-link-actions/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Give ShelfLife's existing active list and graveyard a warm, card-based redesign built on a custom
DaisyUI theme from five earthy hex tones, and add two purely-additive card-level capabilities: (1)
recognizing a saved link via its retrieved page title and site favicon, with graceful fallback to
the raw URL and a generic icon, and (2) a small on-card control to permanently delete a single link
from either view, guarded by a lightweight arm/confirm interaction. Title retrieval is a
server-side, SSRF-guarded fetch with a short timeout, a response-size cap, and manual redirect
validation against private/loopback/link-local IP ranges; it is triggered lazily the next time a
link lacking metadata is returned by a read (mirroring this project's established read-time-
computation philosophy), never inline with save, and never repeated once attempted. Favicons are
never fetched from the target site at all — a favicon URL is built directly from the link's domain
against a public favicon service, which is cheap enough to do eagerly. The countdown's urgency is
now conveyed through both a color-intensity shift and a small leaf-motif SVG (fresh → wilted),
computed entirely client-side from data already in the response — no backend timing change. No
existing expiration/graveyard timing logic, response fields' meaning, or navigation structure
changes. Stack is unchanged: Java 21/Spring Boot 3.5/Spring Data JPA/H2/Maven backend (title
fetching uses the JDK's built-in `java.net.http.HttpClient`, so no new Maven dependency is added),
React 19/Vite/TypeScript/Tailwind+DaisyUI/Vitest+RTL frontend (a custom DaisyUI theme is added via
CSS variables, no new npm dependency).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.x with React 19 (frontend) — unchanged from
Features 1–2.

**Primary Dependencies**: Spring Boot 3.5.x (Spring Web, Spring Data JPA), Maven (backend); React
19, Vite, Tailwind CSS 4 + DaisyUI 5 (frontend) — reused as-is. Title fetching uses the JDK 21
built-in `java.net.http.HttpClient` (no new Maven dependency); favicons are resolved via a public
favicon service URL built from the link's domain (no HTTP client needed for that path at all). No
new dependency is added on either side, per explicit user instruction.

**Storage**: The same H2 file-based `links` table, extended with three new nullable columns
(`page_title`, `title_fetched_at`, `favicon_url`) applied via Hibernate `ddl-auto=update` — no
migration tooling needed at this scale, no new table, no change to the existing `expires_at` index
or any timing-related column.

**Testing**: JUnit 5 + Spring Boot Test (`@DataJpaTest`, `@WebMvcTest`/`@SpringBootTest`) for
backend; Vitest + React Testing Library for frontend — unchanged.

**Target Platform**: Self-hosted/local web server (backend) served to a modern browser (frontend);
single-user, no deployment-scale infrastructure — unchanged.

**Project Type**: Web application monorepo with `backend/` and `frontend/` as sibling top-level
folders — unchanged structure, extended in place.

**Performance Goals**: No fixed numeric SLA (personal-scale, per constitution). Saving a link MUST
remain instant (unchanged Feature 1 SC-001, <2s) — title/favicon retrieval MUST NOT run inline with
`POST /api/links`. When a `GET` read returns one or more links that have never had metadata
retrieval attempted, the backend MUST fetch their metadata concurrently (not serially) so that a
page load with several never-fetched links adds roughly one fetch's worth of latency rather than
the sum of all of them, per the constitution's "UI must never feel sluggish" principle. The delete
action MUST feel immediate client-side.

**Constraints**: Server-side title fetch MUST enforce a 3–5 second timeout, a capped response read
size, and MUST NOT follow a redirect (including transitively) into a loopback, private (RFC 1918),
link-local, or other non-public IP range — validated against the resolved IP at every hop, not just
the original URL (SSRF guard against user-submitted URLs, arbitrary redirect targets, and DNS
rebinding). Favicon retrieval MUST NOT fetch the destination site directly — only a domain-keyed
URL against a third-party favicon service is constructed. Metadata retrieval MUST be triggered
lazily at read time only (no scheduled/background job), applied identically to links saved before
and after this feature ships, and MUST be attempted at most once per link (success or failure)
persisted via a `title_fetched_at` marker, so a permanently unreachable site is not re-fetched on
every subsequent read. The manual delete endpoint MUST be idempotent (deleting an already-gone or
never-existed id is a successful no-op, not an error) and MUST NOT alter any other link's
`expires_at`, ordering, or count. Existing active-list/graveyard expiration and permanent-deletion
timing logic (168h / 30d), the meaning of existing response fields, and the active/graveyard
navigation structure MUST NOT change (FR-021–FR-023).

**Scale/Scope**: Single user, expected link volume in the hundreds (per constitution) — unchanged.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Code Quality** — PASS. Backend adds one new concrete collaborator,
  `LinkMetadataFetcher`, that isolates the SSRF-guarded HTTP title fetch and favicon-URL
  construction behind a single-implementation seam — analogous to how `LinkRepository` already
  isolates persistence from `LinkService`. This is not a speculative interface (no second
  implementation is anticipated); it exists because the SSRF-guard/redirect-validation logic is
  intricate enough to deserve isolated unit tests without booting a full Spring context or a live
  HTTP server. `LinkService` gains two focused methods (`deleteLink`, a metadata-backfill step
  folded into the existing list methods) — no new layer. Frontend redesign work stays inside the
  existing flat `components/` structure; new small presentational components (leaf-motif SVG,
  empty-state SVGs, delete control) are added only because they are now genuinely reused across
  the active list and graveyard cards, not introduced speculatively.
- **II. Testing Standards** — PASS, with new obligations. This feature adds two new ways to reach
  the "deleted" state (active → deleted and graveyard → deleted via manual delete) alongside the
  existing automatic graveyard → deleted transition; both MUST have dedicated tests, including:
  deleting a non-existent id is a no-op (not an error), and deleting one link leaves every other
  link's `expiresAt`, order, and count unaffected. The SSRF guard (loopback/private/link-local
  rejected, including via a redirect hop) and the title-fetch fallback paths (timeout, oversized
  response, no `<title>`, unreachable host, and "already attempted, don't retry") MUST have
  dedicated tests, since this is exactly the kind of hard-to-verify-by-inspection logic the
  constitution calls out. All existing 168-hour and 30-day boundary tests MUST continue to pass
  unmodified, proving zero timing regressions (SC-006). One existing test,
  `LinkControllerTest.controllerExposesOnlyGetAndPostNoRescueResurrectEarlyDeleteOrPinEndpoint`,
  asserted no `DELETE` endpoint existed under Feature 2's stricter scope; this feature explicitly
  and intentionally supersedes that constraint, so the test must be updated to assert the new
  `DELETE` contract (idempotent success) while continuing to assert that `PATCH`/`PUT` remain
  unsupported (no rescue/resurrect/pin endpoint is introduced).
- **III. User Experience Consistency** — PASS. The custom DaisyUI theme (built from the five
  provided hex tones) is defined once and applied identically to both the active list and
  graveyard, so the two views continue to feel like one application. The capture input remains the
  single most prominent element on the active view — the new delete control and countdown motif
  are deliberately small/understated per the spec, not competing visually. No new settings screen,
  toggle, or configuration is introduced.
- **IV. Performance Requirements** — PASS. The manual delete is a single indexed primary-key
  operation (`existsById` + `deleteById`), not a scan. The read-time metadata backfill fans out
  concurrently across a request's never-fetched links (using JDK 21 virtual threads via
  `Executors.newVirtualThreadPerTaskExecutor()`) rather than serially, bounding added latency to
  roughly the slowest single fetch; once a link's metadata has been attempted, subsequent reads do
  no network I/O for it at all. No N+1 query pattern is introduced — metadata backfill mutates
  already-loaded entities and saves them, it does not issue additional per-row `SELECT`s.

No violations identified; Complexity Tracking table is not needed.

**Post-Phase 1 re-check**: Re-evaluated after producing `data-model.md`,
`contracts/link-metadata-and-delete-api.md`, and `quickstart.md`. The design introduces no new
dependencies, no new package/layer beyond the one justified collaborator class above, and no
change to existing timing columns or logic. All four principles still PASS with no changes to this
section.

## Project Structure

### Documentation (this feature)

```text
specs/003-visual-redesign-link-actions/
├── plan.md                                  # This file (/speckit-plan command output)
├── research.md                               # Phase 0 output (/speckit-plan command)
├── data-model.md                              # Phase 1 output (/speckit-plan command)
├── quickstart.md                               # Phase 1 output (/speckit-plan command)
├── contracts/                                   # Phase 1 output (/speckit-plan command)
│   └── link-metadata-and-delete-api.md
└── tasks.md                                      # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/com/shelflife/backend/
│   │   │   ├── WebConfig.java                     # MODIFIED: + "DELETE" added to allowedMethods
│   │   │   └── link/
│   │   │       ├── Link.java                      # MODIFIED: + pageTitle, titleFetchedAt, faviconUrl (all nullable)
│   │   │       ├── LinkMetadataFetcher.java        # NEW: SSRF-guarded title fetch (JDK HttpClient, manual
│   │   │       │                                   #      redirect validation, timeout, size cap, regex <title>
│   │   │       │                                   #      extraction) + favicon-URL builder (pure string op,
│   │   │       │                                   #      no network call)
│   │   │       ├── LinkRepository.java             # unchanged — reuses inherited existsById/deleteById
│   │   │       ├── LinkService.java                # MODIFIED: + deleteLink(Long id) (idempotent),
│   │   │       │                                   #           + backfillMetadata(List<Link>) called from
│   │   │       │                                   #           listActiveLinks()/listGraveyardLinks() before
│   │   │       │                                   #           mapping to response, fanned out via virtual threads
│   │   │       ├── LinkController.java             # MODIFIED: + DELETE /api/links/{id} (204, idempotent)
│   │   │       └── LinkResponse.java               # MODIFIED: + title (fallback-resolved: fetched title or raw
│   │   │                                           #             url), + faviconUrl (nullable); forGraveyard(..)
│   │   │                                           #             extended identically
│   │   └── resources/                              # unchanged (ddl-auto=update picks up new columns)
│   └── test/
│       └── java/com/shelflife/backend/link/
│           ├── LinkServiceTest.java                # MODIFIED: + deleteLink tests (active, graveyard, non-existent
│           │                                       #             id no-op, sibling links unaffected),
│           │                                       #           + metadata backfill/fallback tests,
│           │                                       #           + "attempted once, never retried" test
│           ├── LinkMetadataFetcherTest.java         # NEW: SSRF guard (loopback/private/link-local rejected,
│           │                                       #      rejected via redirect hop too), timeout, size cap,
│           │                                       #      title extraction, favicon URL construction
│           ├── LinkRepositoryTest.java              # unchanged — no repository query changes
│           └── LinkControllerTest.java              # MODIFIED: + DELETE /api/links/{id} contract tests
│                                                    #             (active, graveyard, non-existent id → 204);
│                                                    #           existing "no rescue/resurrect/early-delete/pin"
│                                                    #           test updated so its DELETE assertions now
│                                                    #           reflect the intentional new contract instead of
│                                                    #           asserting DELETE is unsupported

frontend/
├── src/
│   ├── index.css                          # MODIFIED: + custom DaisyUI theme block (CSS variables) built from
│   │                                       #           the five hex tones, applied via `@plugin "daisyui/theme"`
│   ├── types/
│   │   └── link.ts                        # MODIFIED: + title (string), + faviconUrl (string | null)
│   ├── api/
│   │   └── linksApi.ts                    # MODIFIED: + deleteLink(id: number): Promise<void>
│   ├── components/
│   │   ├── LinkListItem.tsx               # MODIFIED: card redesign (title heading + favicon-or-fallback-icon +
│   │   │                                   #           URL as secondary text), + urgency-aware countdown (color
│   │   │                                   #           band + inline LeafMotif SVG), + on-card DeleteControl
│   │   ├── LeafMotif.tsx                   # NEW: small inline SVG, fresh→wilted variants driven by an urgency
│   │   │                                   #      prop derived client-side from existing expiresAt/savedAt
│   │   ├── DeleteControl.tsx                # NEW: on-card icon button; per-card armed/normal state, ~3s
│   │   │                                   #      auto-revert timer, click-outside-cancels, confirmed click
│   │   │                                   #      calls deleteLink then a provided onDeleted callback
│   │   ├── LinkList.tsx                    # MODIFIED: empty state now renders an illustrated component instead
│   │   │                                   #           of plain text (keeps existing emptyMessage/granularity/
│   │   │                                   #           openable props; adds an `emptyIllustration` slot)
│   │   ├── EmptyActiveIllustration.tsx      # NEW: small inline hand-drawn-style SVG for the active list's
│   │   │                                   #      empty state
│   │   ├── EmptyGraveyardIllustration.tsx   # NEW: distinct small inline SVG for the graveyard's empty state
│   │   ├── ActiveView.tsx                  # MODIFIED: passes EmptyActiveIllustration + onDeleted refresh wiring
│   │   ├── GraveyardView.tsx               # MODIFIED: passes EmptyGraveyardIllustration + onDeleted refresh wiring
│   │   ├── LinkCount.tsx                   # unchanged
│   │   ├── NavTabs.tsx                     # unchanged (no navigation-structure change, per FR-023)
│   │   └── UrlCaptureForm.tsx              # unchanged
│   └── hooks/
│       ├── useActiveLinks.ts               # unchanged
│       └── useGraveyardLinks.ts            # unchanged
└── tests/
    ├── LinkListItem.test.tsx               # MODIFIED: + title/favicon-fallback rendering, + urgency-band/leaf
    │                                       #           motif assertions, + delete arm/confirm/cancel behavior
    ├── DeleteControl.test.tsx               # NEW: arm → confirm deletes, arm → timeout cancels, arm → click
    │                                       #      outside cancels
    ├── LinkList.test.tsx                    # MODIFIED: + empty-state illustration rendering (per view)
    ├── LinkCount.test.tsx                   # unchanged
    ├── UrlCaptureForm.test.tsx              # unchanged
    ├── NavTabs.test.tsx                     # unchanged
    ├── useActiveLinks.test.ts               # unchanged
    └── useGraveyardLinks.test.ts            # unchanged
```

**Structure Decision**: Same monorepo layout as Features 1–2 (`backend/` + `frontend/` siblings, no
new top-level folders, no new package). All backend changes stay inside the existing `link`
package, with exactly one new class (`LinkMetadataFetcher`) justified above. All frontend additions
stay inside the existing flat `components/` structure — no routing library, no state-management
library, no new npm dependency; the redesign is delivered through a custom DaisyUI theme plus small
additive components and props, consistent with how Feature 2 extended the frontend in place.

## Complexity Tracking

*No Constitution Check violations — this section is intentionally empty.*
