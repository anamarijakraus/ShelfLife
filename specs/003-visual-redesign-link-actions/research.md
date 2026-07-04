# Phase 0 Research: Warm Visual Redesign & Card-Level Link Actions

The user's plan instructions already fixed the stack, the SSRF-mitigation approach, the
favicon-via-third-party-service decision, the lazy/read-time backfill trigger, the single `DELETE`
endpoint, and the frontend implementation choices (custom DaisyUI theme, inline SVG motifs, a
per-card timer + click-outside hook for the delete control). No `NEEDS CLARIFICATION` markers exist
in the Technical Context. This document resolves the remaining implementation-level design
decisions needed before Phase 1.

## 1. Distinguishing "not yet attempted" from "attempted and failed" for title fetch

**Decision**: Add a `title_fetched_at` (`Instant`, nullable) column alongside `page_title`. A read
triggers a metadata-fetch attempt for a link only when `titleFetchedAt == null`. After the attempt
— whether it succeeds, times out, or the destination has no `<title>` — `titleFetchedAt` is set to
the current time and the row is saved, so the link is never re-attempted on a later read.

**Rationale**: FR-009/FR-010 require retrieval to be asynchronous relative to save and applied
lazily to old and new links alike, and the user's plan instruction says to store the result "rather
than re-fetching on every read." If the trigger condition were simply "`pageTitle` is null," a link
whose destination is permanently unreachable would be re-fetched (and re-timeout) on every single
subsequent read forever — silently reintroducing per-read latency and repeated outbound requests to
a URL already known to fail. A separate "attempted" marker, independent of the outcome, is the
minimal state needed to make the fetch-once guarantee actually hold. This mirrors the project's
existing pattern of deriving behavior from a timestamp rather than a status enum (Constitution
Principle I; Feature 2's `expiresAt`-only lifecycle).

**Alternatives considered**: Using `pageTitle == null` as the trigger — rejected for the repeated-
retry reason above. A boolean `titleFetchAttempted` flag instead of a timestamp — functionally
equivalent but a timestamp costs nothing extra and is more debuggable (when did this fail?); no
functional difference, timestamp chosen for free diagnostic value. A retry-with-backoff scheme —
explicitly out of scope; the user's instruction and the constitution's "no dedicated background
process" both point to a strict fetch-once model appropriate for a personal-scale tool.

## 2. Favicon: computed eagerly, not fetched at all

**Decision**: `faviconUrl` is a pure string computed from the link's domain
(`https://www.google.com/s2/favicons?domain={host}&sz=64`, or an equivalent public favicon
service), built directly in `LinkService.createLink(...)` at save time — no network call, no
"fetched" flag needed for it, since constructing the URL can never fail or time out.

**Rationale**: The user's instruction explicitly avoids fetching the target site for favicons at
all, using a third-party service "keyed by domain." Because this is string formatting rather than
I/O, it costs nothing to do synchronously at save time, so pre-existing links only need this
backfilled once (during the same read-time pass that also handles title backfill, guarded by
`faviconUrl == null` — a safe sentinel here specifically because computing it cannot meaningfully
fail).

**Alternatives considered**: Treating favicon like title (lazy, `title_fetched_at`-gated) —
rejected as unnecessary complexity for an operation with no failure mode and no network cost.
Fetching the site's own `<link rel="icon">` — explicitly rejected by the user's instruction (avoids
the backend ever parsing/fetching arbitrary site assets for this purpose, removing an entire class
of SSRF/parsing risk).

## 3. SSRF guard for server-side title fetch

**Decision**: Use the JDK's built-in `java.net.http.HttpClient` with `HttpClient.Redirect.NEVER`
(automatic redirect-following disabled) and a manual redirect loop capped at a small number of hops
(e.g., 3). Before connecting to any hop (the original URL and each subsequent redirect target),
resolve the host via `InetAddress.getAllByName(host)` and reject the request if any resolved
address is loopback, site-local (RFC 1918), link-local, multicast, or the wildcard/any-local
address (`InetAddress.isLoopbackAddress()/isSiteLocalAddress()/isLinkLocalAddress()/
isMulticastAddress()/isAnyLocalAddress()`). Apply a 3–5 second connect+read timeout per hop, and
cap the amount of response body read (e.g., the first ~64KB) — enough to contain a `<title>` tag on
virtually all real pages — reading via `HttpResponse.BodyHandlers.ofInputStream()` and stopping
once the cap is reached, closing the connection early rather than draining a huge response.
Extract the title with a simple bounded regex against the captured prefix (e.g., first
`<title>...</title>` occurrence, case-insensitive, HTML-entity-decoded for common entities); no
title found within the captured prefix is treated the same as "no title available."

**Rationale**: The link's URL is entirely user-submitted, and the server making an outbound request
to it is a textbook SSRF vector — a malicious or careless submission could otherwise be used to
probe internal services, cloud metadata endpoints, or loopback-only admin interfaces, including via
an innocent-looking public URL that redirects to a private address. Validating the resolved IP at
every hop (not just the original host) closes the "redirect to private IP" and "DNS rebinding
after initial check" gaps that a origin-only check would miss. This is achievable with zero new
dependencies using only `java.net.InetAddress`, matching Constitution Principle I's bias toward the
simplest sufficient mechanism. The response-size cap and timeout bound worst-case resource
consumption per fetch, and folding title-extraction into a simple regex (rather than a full HTML
parser) avoids pulling in a parsing library the project doesn't otherwise need — `<title>` tags
appear near the top of virtually every real page's `<head>`, so a small captured prefix is
sufficient in practice, and a miss simply falls back to the raw URL exactly as FR-006 requires.

**Alternatives considered**: A dependency like Jsoup for robust HTML parsing — rejected as an
unnecessary new dependency for extracting one tag, contradicting the user's "no new major
dependencies" instruction and Constitution Principle I. Validating only the original URL's host
before the request and letting the HTTP client follow redirects automatically — rejected because it
does not protect against a redirect to a private address, a well-known SSRF bypass. An allowlist of
permitted schemes/ports only, without IP-range validation — insufficient on its own since a public
hostname can still resolve to a private IP.

## 4. Read-time backfill trigger and concurrency

**Decision**: `LinkService.listActiveLinks()` and `listGraveyardLinks()` each, after loading their
result list, partition it into links needing a metadata backfill (`titleFetchedAt == null` or
`faviconUrl == null`) and links that don't. For the ones needing it, dispatch each fetch to
`Executors.newVirtualThreadPerTaskExecutor()`, `invokeAll`-style, wait for all to complete (each
individually bounded by its own timeout), persist the updated rows, then proceed to build the
response from the now-current in-memory list.

**Rationale**: The user's instruction is explicit that retroactive fetching is "triggered the next
time an old link is read... consistent with this project's established read-time-computation
philosophy" (mirroring Feature 2's delete-then-read pattern in `listGraveyardLinks()`). Doing this
serially, however, would mean a page load touching several never-fetched links could take on the
order of (count × up to 5 seconds) — a real, visible violation of the constitution's "UI must never
feel sluggish" / "no visible jank" requirement. Java 21 virtual threads make concurrent fan-out a
one-line change (`Executors.newVirtualThreadPerTaskExecutor()`) with no new dependency and no added
architectural complexity, so the added latency for a read is bounded by the slowest single fetch
rather than their sum. Because each link is fetched at most once ever (per decision #1), this cost
is paid at most once per link's lifetime, not on every read.

**Alternatives considered**: A serial loop — rejected for the sluggishness reason above. A
dedicated `@Async`/thread-pool executor bean — unnecessary extra configuration surface when
`Executors.newVirtualThreadPerTaskExecutor()` inline achieves the same fan-out with less ceremony,
consistent with Principle I's simplicity bias. Deferring backfill to a scheduled job — explicitly
rejected by the user's instruction and by the project's established no-background-job precedent.

## 5. Delete endpoint: idempotent single `DELETE`, no distinct 404 case

**Decision**: `DELETE /api/links/{id}` always returns `204 No Content`, whether or not a link with
that id currently exists. Implementation: `if (linkRepository.existsById(id)) { linkRepository.
deleteById(id); }` — `deleteById` alone would throw `EmptyResultDataAccessException` for a missing
id, so the existence check makes the operation genuinely idempotent rather than merely swallowing
an exception.

**Rationale**: The spec's edge case is explicit: deleting an already-gone link (e.g., a stale second
tab, or a race with the automatic graveyard sweep) "is a no-op... the system does not error." A
uniform `204` regardless of prior existence is the simplest contract satisfying that, and matches
standard `DELETE` idempotency semantics (repeating a `DELETE` should not become an error). This
requires updating one existing test:
`LinkControllerTest.controllerExposesOnlyGetAndPostNoRescueResurrectEarlyDeleteOrPinEndpoint`
currently asserts `DELETE /api/links/1` returns a 4xx — that assertion encoded Feature 2's
narrower scope (no delete capability existed yet) and is intentionally superseded by this feature;
the test is updated to assert the new contract while continuing to prove `PATCH`/`PUT` remain
unsupported everywhere (no rescue/resurrect/pin endpoint is introduced).

**Alternatives considered**: Returning `404` for an unknown id — rejected; it would make the
frontend's delete-confirm action into something that can "fail" on an id that simply lost a race
with automatic cleanup, which the spec explicitly says must not surface as an error.

## 6. Response shape: fallback resolved server-side

**Decision**: `LinkResponse` gains `title` (never null — the fetched title if present, otherwise
the raw `url`, resolved once server-side) and `faviconUrl` (nullable — the frontend renders a
generic fallback icon when null). `forGraveyard(...)` is extended identically, keeping its existing
`expiresAt`-reinterpretation behavior unchanged.

**Rationale**: Resolving the title fallback once, server-side, keeps a single source of truth
instead of duplicating "if empty, use the url" branching in both `ActiveView`/`GraveyardView`
render paths — consistent with how Feature 2's `forGraveyard` factory already centralizes its one
piece of response-shaping logic rather than pushing it to the frontend.

**Alternatives considered**: Sending `pageTitle` as nullable and resolving the fallback in
`LinkListItem` — rejected as needless duplication across both views for a decision that has exactly
one correct answer and no UI-specific variation.

## 7. Frontend: custom DaisyUI theme via CSS variables

**Decision**: Define a single custom DaisyUI theme in `index.css` (DaisyUI 5's CSS-first theming,
already in use via `@plugin "daisyui"`) mapping the five hex tones to DaisyUI's semantic color
roles — e.g., warm sand (`#DDCBB7`) → `base-100`/`base-200` (page/card background), rich brown
(`#7B4B36`) → `base-content`/primary text, deep forest green (`#264025`) → `primary` (calm/"fresh"
accents), muted olive (`#82896E`) → `neutral`/secondary text and borders, terracotta (`#AD6B4B`) →
`accent` (urgency/warm interactive highlights). This theme is applied globally, so both the active
list and graveyard automatically share it with zero per-view divergence.

**Rationale**: The user's instruction is explicit — a custom DaisyUI theme via CSS-variable
theming, not one-off inline styles — which is also what keeps Constitution Principle III's
cross-view consistency requirement structurally guaranteed rather than dependent on developer
discipline (there is only one theme definition to keep in sync).

**Alternatives considered**: Inline Tailwind utility classes with the raw hex values scattered
across components — rejected per the user's explicit instruction and because it would risk the two
views drifting apart over time (each place a color is used is a place it could be gotten slightly
wrong).

## 8. Countdown urgency bands (color + leaf motif) computed client-side

**Decision**: Compute a link's remaining-life fraction as `(expiresAt - now) / totalDuration`,
where `totalDuration` is the existing fixed `168h` for the active list or `30d` for the graveyard
(both already implicit client-side constants mirroring `LinkService.EXPIRY_HOURS`/
`GRAVEYARD_DAYS`). Bucket the fraction into three urgency bands — fresh (≥ 50% remaining), turning
(10–50%), wilted (< 10%) — driving both the countdown's color intensity (mapped to the existing
palette: calm forest-green/olive tones for "fresh," progressively warmer terracotta/brown tones for
"turning"/"wilted") and the `LeafMotif` SVG's fresh → wilted variant, at the same band boundaries so
the two cues always agree. This is purely a rendering computation from fields already present in
the API response; no backend or timing change.

**Rationale**: FR-012/FR-012a require the color shift and the non-color leaf-motif cue to move
together "at the same urgency thresholds" without altering the underlying countdown value — a
client-side-only, response-data-derived computation satisfies this with no backend change and
keeps the actual expiration/deletion moment exactly as computed by the unmodified backend logic
(SC-006). Three bands are enough to convey "plenty of time / getting close / urgent" (per US4)
without inventing a large, arbitrary threshold table the spec doesn't call for.

**Alternatives considered**: A continuous (non-banded) color gradient with a matching continuously-
morphing SVG — rejected as unnecessary complexity (would require many SVG variants or runtime SVG
manipulation) for a "subtle shift" the spec describes in qualitative terms; three discrete,
easy-to-reason-about bands are simpler to implement, test, and reason about while still fulfilling
the requirement. Deriving urgency from a hardcoded absolute time (e.g., "under 24 hours") instead of
a fraction of total lifespan — rejected because it wouldn't scale sensibly across the active list's
168-hour window and the graveyard's 30-day window without two separate threshold tables; a
fraction-of-total-lifespan works identically for both.

## 9. Delete control: per-card local state, not a global modal

**Decision**: `DeleteControl` holds its own `armed: boolean` state via `useState`. Arming starts a
`setTimeout(..., 3000)` that reverts to normal, and a single `mousedown`/`pointerdown` listener
(attached only while armed, via a small `useEffect`) that reverts to normal if the event target is
outside the card's ref. A second activation while armed calls `deleteLink(id)` then the card's
`onDeleted` callback; both the timer and the listener are cleared on unmount/rearm/confirm.

**Rationale**: Matches the user's explicit instruction ("implemented as a per-card timer plus a
single click-outside hook, not a global modal") and the spec's requirement for a lightweight,
inline confirmation rather than a heavy dialog. Scoping the listener to only be attached while a
card is armed (rather than always-on for every card) keeps the implementation simple and avoids
unnecessary global event-listener churn on a list of many cards.

**Alternatives considered**: A shared/global "which card is armed" state lifted to a parent —
rejected as unneeded complexity; each card's armed state is fully local and independent (arming one
card has no effect on any other, per the spec's edge cases), so component-local state is the
simplest sufficient design (Principle I).

## Outcome

No `NEEDS CLARIFICATION` markers remain in the Technical Context. All decisions above are
consistent with the spec's functional requirements, its 2026-07-04 Clarifications, the user's
explicit plan instructions, and the project constitution.
