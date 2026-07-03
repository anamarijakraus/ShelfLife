# Phase 0 Research: Link Capture & Active List with Automatic Expiry

All major technology choices were specified directly by the user (see plan.md Technical
Context). This document resolves the remaining implementation-level decisions needed before
design (Phase 1) so no `NEEDS CLARIFICATION` markers remain.

## 1. Timestamp storage and expiration computation

**Decision**: Store both `savedAt` and `expiresAt` as `Instant` (UTC) columns on the `Link`
entity. `expiresAt` is computed once at save time (`savedAt.plus(168, HOURS)`) and persisted,
not recomputed per query.

**Rationale**: Persisting `expiresAt` lets the active-list query filter with a single indexed
comparison (`WHERE expires_at > :now`) instead of computing `saved_at + 168h` per row on every
read — this is what Constitution Principle IV (indexed, set-based operations) requires. `Instant`
avoids timezone ambiguity entirely, which matters for exact-boundary correctness (167h59m /
168h00m / 168h01m tests).

**Alternatives considered**: Storing only `savedAt` and computing `expiresAt` in the query
(`WHERE saved_at > :now.minus(168h)`) — rejected only because it's marginally less readable, not
for performance (both are equally indexable); persisted `expiresAt` was chosen for clarity and to
give the future graveyard feature an unambiguous, precomputed field to promote on.

## 2. Active-list query approach

**Decision**: A single Spring Data JPA derived query method,
`findByExpiresAtAfterOrderByExpiresAtAsc(Instant now)`, backed by an index on `expires_at`.

**Rationale**: Matches FR-006, FR-007, FR-010 directly (include not-yet-expired, exclude expired,
order soonest-first) in one indexed, set-based read — no application-level filtering or sorting
needed. Consistent with Constitution Principle IV and avoids introducing a custom
`@Query`/specification layer that Principle I would flag as unneeded abstraction for this simple
a predicate.

**Alternatives considered**: A `@Query`-annotated JPQL method — rejected as unnecessary; a
derived query name expresses the same predicate without extra syntax. A native SQL query —
rejected, no need to bypass JPA for a single comparison + sort.

## 3. URL validation and scheme normalization

**Decision**: In `LinkService` (not the controller, to keep the controller a thin HTTP adapter
per Principle I), before validation: if the trimmed input does not match a scheme prefix
(`^[a-zA-Z][a-zA-Z0-9+.-]*://`), prepend `https://`. Then validate using Java's `java.net.URI`
parsing (must produce an absolute URI with a host). Reject (400 Bad Request) if still invalid
after normalization.

**Rationale**: Directly implements FR-003 and the 2026-07-02 clarification (auto-normalize,
don't reject bare domains). `java.net.URI` is part of the JDK — no extra validation dependency
needed, consistent with avoiding unnecessary dependencies.

**Alternatives considered**: A regex-only validator — rejected, `URI` parsing is more correct and
already available. A dedicated URL-validation library (e.g., Apache Commons Validator) —
rejected as an unjustified dependency for a need the JDK already covers.

## 4. Frontend polling strategy for the countdown

**Decision**: A single custom hook, `useActiveLinks()`, that fetches `GET /api/links` on mount
and again every 60 seconds via `setInterval`, storing the result in `useState`. Each
`LinkListItem` computes its own displayed remaining time from the link's `expiresAt` (received
from the server) and the current client time at render — no locally-decremented countdown state.

**Rationale**: Directly implements FR-009 and the countdown-cadence clarification: periodic
(~60s) refresh, recomputed from the server-provided timestamp each tick, no drift. A single hook
avoids a state-management library, per the explicit instruction and Principle I (no dependency
until the current feature needs it — one hook is sufficient for one view).

**Alternatives considered**: WebSocket/SSE push updates — rejected as unjustified complexity for
a personal-scale, single-user app where a 60s poll already meets SC-002's "a few minutes"
tolerance. A third-party data-fetching library (e.g., React Query) — rejected; plain `fetch` +
`useEffect`/`setInterval` is sufficient for one endpoint and matches the explicit "no
state-management library" instruction.

## 5. CORS / dev-server integration

**Decision (revised during implementation)**: Use a Vite dev-server proxy (`server.proxy`
forwarding `/api` to `http://localhost:8080`) as the primary mechanism, in addition to keeping
the backend `WebConfig` CORS rule scoped to `/api/**` for `http://localhost:5173`.

**Rationale**: The original plan chose CORS alone, with the proxy noted only as a fallback. During
implementation (manual end-to-end verification, see quickstart.md), CORS alone did not work: the
frontend's `linksApi.ts` calls a relative path (`/api/links`) so that the same code works whether
the frontend is served standalone in dev or bundled behind the same origin as the backend in a
future packaged build. A relative fetch resolves against the *page's own origin* — in dev that's
the Vite server (port 5173), not the backend (port 8080) — so the browser never even reached the
backend for CORS to apply; it 404'd against Vite's own dev server instead. Adding the proxy makes
the relative-path request same-origin from the browser's perspective (Vite forwards it
server-side), which is exactly the "reasonable alternative" flagged in the original research.
CORS is kept as well since it's harmless and remains correct defense if the frontend is ever
served from a different origin than the proxy assumes.

**Alternatives considered**: CORS alone (the original decision) — confirmed insufficient by
itself given the relative-URL API client design; an absolute backend URL
(`http://localhost:8080/api/links`) baked into `linksApi.ts` — rejected, since it would need an
environment-specific base URL to still work once frontend and backend are served from the same
origin in a packaged build.

## 6. H2 file-based database configuration

**Decision**: `spring.datasource.url=jdbc:h2:file:./data/shelflife` (relative to the backend
working directory), `spring.jpa.hibernate.ddl-auto=update`, H2 console disabled by default.

**Rationale**: File-based H2 persists data across application restarts (required by FR-015)
without standing up a separate database server, appropriate for a personal-scale, single-user
app per the user's explicit instruction. `ddl-auto=update` avoids requiring a migration tool at
this stage, also per instruction.

**Alternatives considered**: In-memory H2 — rejected, does not satisfy FR-015 (persistence across
restarts). A file-based embedded alternative like SQLite — not chosen; user explicitly specified
H2.

## Outcome

No `NEEDS CLARIFICATION` markers remain in the Technical Context. All decisions above are
consistent with the spec's functional requirements and the project constitution.
