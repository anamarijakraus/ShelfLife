# Phase 0 Research: Favorites Tab & Link Pinning

The user's plan instructions already fixed the stack (unchanged from Features 1–3) and flagged the
five design questions that matter most for this feature: how a persisted `pinned` flag coexists with
the read-time-computation model, what happens to `expiresAt` while pinned, the endpoint shape for the
three views, reuse of existing UI primitives, and tab order/illustration. No `NEEDS CLARIFICATION`
markers exist in the Technical Context. This document resolves the remaining implementation-level
design decisions needed before Phase 1.

## 1. `pinned` as the first genuine state field — how it coexists with read-time computation

**Decision**: Add exactly one new boolean column, `pinned` (`NOT NULL`, default `false`), plus one
companion `pinnedAt` (`Instant`, nullable) used only to order the favorites list. Every existing and
new repository query that determines active/graveyard membership is extended with a `pinned = false`
predicate, so pinned links are excluded from those queries' result sets entirely — not filtered out
after loading, and not merely hidden in the UI.

**Rationale**: The user's plan instruction #1 is explicit that exclusion must happen at the read-path
level, not as a display filter. This matters concretely in one place beyond the obvious active-list
query: `LinkRepository.deleteByExpiresAtLessThanEqual(threshold)` (Feature 2's automatic graveyard
sweep) must also gain the `pinned = false` predicate. Without it, a link pinned while sitting in the
graveyard — whose stored `expiresAt` (interpreted as its graveyard deletion deadline) may already be
in the past by the time it's pinned, or may cross that threshold later while still pinned — would be
silently and permanently deleted by the next sweep despite being pinned, directly violating FR-003
("MUST NOT be automatically moved or deleted as a result of \[the graveyard-deletionmechanic\]").
This is the single sharpest correctness risk in this feature and is why every timing-derived query
gains the same predicate rather than only the display-facing ones.

**Alternatives considered**: A separate `favorites` join table or a `status` enum
(`ACTIVE`/`GRAVEYARD`/`FAVORITE`) replacing the timestamp-derived model — rejected. The constitution
and Features 1–3 deliberately derive lifecycle from timestamps rather than a stored status, and nothing
about this feature requires abandoning that for the active/graveyard distinction — pinning is an
orthogonal override, not a fourth lifecycle stage replacing the other three. Introducing a full status
enum would be a much larger, unjustified redesign (Constitution Principle I: no speculative
abstraction). Filtering pinned links out of the response after the existing queries run — rejected
per plan instruction #1 and the graveyard-sweep risk above; it would also mean a pinned link still
participates in `deleteByExpiresAtLessThanEqual`, which is an actual-row-deletion bulk statement, not
a read filter, so post-load filtering cannot protect it at all.

## 2. `expiresAt` while pinned: frozen and ignored, not cleared; recomputed fresh on unpin

**Decision**: `expiresAt` remains untouched (whatever stale value it held at the moment of pinning)
for as long as a link is pinned — it is simply never read or acted upon while `pinned = true`, since
every timing query now excludes pinned rows. On unpin, `LinkService` overwrites it with
`Instant.now().plus(168, ChronoUnit.HOURS)` (the same computation `createLink` uses) and sets
`pinned = false`, in a single save. `pinnedAt` is left as-is (stale, unused) rather than cleared,
mirroring the same "don't bother clearing a field once it stops being read" pattern.

Critically, this recomputation only happens on an *actual* pinned → unpinned transition. `unpinLink`
first checks `link.isPinned()`; if it is already `false`, the method returns without touching
`expiresAt` at all. Symmetrically, `pinLink` first checks `!link.isPinned()`; if already `true`, it
returns without re-stamping `pinnedAt`. Without this guard, "idempotent" would be true only in the
narrow sense of "doesn't throw" — a second `pin` call on an already-pinned link would silently bump it
to the top of the favorites ordering, and a second `unpin` call on an already-unpinned link would
silently re-arm a fresh 168-hour countdown with no corresponding pin having happened in between. Both
are exactly the class of quiet, timing-adjacent bug the constitution's testing standard exists to
catch, so both `LinkServiceTest`'s idempotency tests assert the *field value itself* is unchanged
across a repeated call, not merely that the call doesn't error.

**Rationale**: The column is `NOT NULL` (Feature 1's schema), and there is no functional need to add
nullability just to represent "no countdown" — since the pinned predicate already removes the row
from every query that would use `expiresAt`, its stale value is inert, not merely hidden. Clearing it
would require either a nullable-column migration or a sentinel value, both unjustified complexity for
a value nothing ever reads while `pinned = true` (Constitution Principle I). Recomputing fresh from
`now()` on unpin — rather than resuming the old value or extending it — is exactly what the spec's
FR-011 requires ("independent of its original save time and independent of any time it spent
pinned"), and reusing `createLink`'s own `now + 168h` formula keeps the "fresh countdown" computation
defined in exactly one place.

**Alternatives considered**: Making `expiresAt` nullable and setting it to `null` while pinned —
rejected as an unnecessary schema/nullability change (and a wider ripple into `LinkResponse`'s
existing non-favorites factories) for a value that is already fully inert once excluded from every
query. Extending the old `expiresAt` by the time spent pinned (a "paused countdown" model) —
explicitly rejected by the spec itself (FR-011, edge case: "still receives a full, fresh 168-hour
countdown rather than being treated as already expired" even after 168+ hours pinned).

## 3. Endpoint shape: one new list endpoint per view, two new action endpoints — no shared status filter

**Decision**: Add `GET /api/links/favorites`, sibling to the existing `GET /api/links` and
`GET /api/links/graveyard` — a third fixed-path endpoint, not a shared endpoint with a `?status=`
query parameter. Add `POST /api/links/{id}/pin` and `POST /api/links/{id}/unpin`, sibling in style to
the existing `DELETE /api/links/{id}` (idempotent, no request body, `204 No Content`).

**Rationale**: Plan instruction #3 asks this explicitly. `LinkController` already establishes the
one-fixed-path-per-view precedent (`/api/links`, `/api/links/graveyard`), each backed by its own
repository query and its own `LinkResponse` factory (`from`/`forGraveyard`) — adding
`/api/links/favorites` with a `forFavorites` factory extends that exact pattern with zero disruption
to the other two. A shared `?status=` filter would require rewriting both existing endpoints'
signatures and their consumers (`fetchActiveLinks`/`fetchGraveyardLinks`) for a three-way branch that
provides no real benefit here — there is no cross-cutting behavior (pagination, shared filtering
logic) that a unified endpoint would simplify; each view already has view-specific response shaping
(`expiresAt` means something different in each). For the two state-changing actions, POST-to-a-verb
sub-path (`/pin`, `/unpin`) mirrors the project's existing avoidance of `PATCH`/`PUT` (asserted by
`LinkControllerTest`'s "no rescue/resurrect" test since Feature 2) while still being a clear,
single-purpose, idempotent action endpoint — the same shape `DELETE /api/links/{id}` already
established for "one focused state transition, no body, 204."

**Alternatives considered**: A single `PATCH /api/links/{id}` accepting `{"pinned": true/false}` —
rejected because it reintroduces exactly the generic partial-update endpoint this project has
deliberately avoided since Feature 2 (the existing controller test's name is literally
"...NoRescueResurrectOrPinEndpoint" — asserting `PATCH`/`PUT` return 4xx); two narrow, named POST
actions are more consistent with this codebase's established preference for explicit, single-purpose
endpoints over a generic update verb. A combined `GET /api/links?view=favorites` — rejected for the
reasons above; it does not reduce any real duplication and would touch two endpoints that don't need
to change.

## 4. Reuse strategy: one new pin control, one new pinned-badge, one new view/route — nothing else

**Decision**: `LinkListItem` (shared by all three views today) gains two optional props: `pinned`
(boolean, default `false`) and `onPinToggled`. It unconditionally renders a new `PinControl` next to
the existing `DeleteControl` — identical placement pattern, no arm/confirm state (pinning/unpinning
is immediate and reversible per the spec, unlike delete). When `pinned` is `true`, the existing
countdown pill + `HourglassMotif` + progress bar are replaced by a new static `PinnedBadge` occupying
the same visual slot; when `pinned` is `false` (active/graveyard), rendering is byte-for-byte
unchanged from today. `DeleteControl` itself requires zero changes — it already works on any card
regardless of pinned state, exactly per FR-012/FR-016. A new `FavoritesView` component mirrors
`GraveyardView`'s structure (`useFavoriteLinks` hook, `LinkCount`, `LinkList`, its own empty
illustration), and `NavTabs`/`App` gain the third tab/route.

**Rationale**: Plan instruction #4 is explicit — reuse the card, palette, delete control, and
tab-bar pattern; only the pin control, the Favorites view, and the persisted field/transition logic
are new. Threading `pinned`/`onPinToggled` through the same `LinkListItem`/`LinkList` components
used everywhere (rather than forking a separate `FavoriteListItem`) is the minimal change that
satisfies "same card design, palette, and title/favicon conventions as the other two views" (spec
FR-005) while keeping exactly one place that defines what a link card looks like — consistent with
Constitution Principle I's bias against premature duplication.

**Alternatives considered**: A dedicated `FavoriteCard` component duplicating `LinkListItem`'s
layout — rejected; it would immediately diverge in spacing/typography/palette from the other two
views the first time either is touched, which is exactly the drift Constitution Principle III (cross-
view consistency) warns against. Reusing `DeleteControl`'s arm/confirm pattern for pinning — rejected;
the spec is explicit that pinning/unpinning needs no confirmation step at all ("fully reversible
action"), so copying a confirmation state machine would add unrequested friction.

## 5. Favorites ordering and response shape: `pinnedAt DESC`, `expiresAt` sent as `null`

**Decision**: `GET /api/links/favorites` orders by `pinnedAt DESC` (most-recently-pinned first, per
the spec's Assumptions) and returns each link via a new `LinkResponse.forFavorites(link)` factory
that sends `expiresAt` as `null` (rather than the stale, inert value described in §2) alongside the
existing `title`/`faviconUrl` fallback resolution. The frontend's `Link` type becomes
`expiresAt: string | null`; `LinkListItem` only reads `expiresAt` when `pinned` is `false`, so the
`null` case never needs to be branched on defensively inside the countdown-formatting helpers.

**Rationale**: Sending the stale, frozen `expiresAt` value over the wire for a favorited link — even
though the frontend would never render it — invites future misuse (a later change could accidentally
compute a countdown fraction from it). Making the wire contract explicit (`null` means "no countdown
concept applies") is a small, free correctness guarantee, and mirrors how `forGraveyard` already
reinterprets `expiresAt`'s meaning per view rather than every view sharing one meaning. No `pinned`
boolean is added to the wire shape at all: which endpoint returned a given link already fully implies
its pinned status, exactly as "appearing in the graveyard response" already implies "expired but not
yet permanently deleted" without an explicit field — adding a redundant `pinned` field the frontend
would never need to branch on (since `FavoritesView` always passes `pinned` as a hardcoded prop to
`LinkList`) would be unused surface area.

**Alternatives considered**: Sending the real (stale) `expiresAt` and trusting the frontend never to
render it — rejected per the misuse risk above. Adding a `pinned: boolean` field to every
`LinkResponse` — rejected as redundant given the per-endpoint semantics already established by
`from`/`forGraveyard`/`forFavorites`. Ordering favorites by `savedAt` or `id` instead of `pinnedAt` —
rejected; the spec's own assumption calls out "most-recently-pinned first," which requires knowing
*when* each link was pinned, not when it was originally saved.

## 6. Favorites cards are openable

**Decision**: `FavoritesView` passes `openable` to `LinkList`, matching `GraveyardView`'s (not
`ActiveView`'s) convention — a favorited link's title is a clickable link to its destination in a new
tab.

**Rationale**: The spec does not explicitly state whether favorites cards are openable, and this is a
low-impact presentational choice rather than one that changes architecture, data model, or test
design. A link a user has deliberately pinned to keep around is at least as likely to be revisited as
a graveyard link (which is already openable per Feature 2 US6), so defaulting to openable is the more
useful, lower-surprise choice. This is a plan-level default, not a spec ambiguity requiring
clarification.

**Alternatives considered**: Matching `ActiveView`'s non-openable convention instead — plausible, but
provides less value for a collection the user has explicitly chosen to keep; either choice is equally
simple to implement (`openable` is already a `LinkList`/`LinkListItem` prop), so this comes down to
which default better serves the feature's evident purpose.

## Outcome

No `NEEDS CLARIFICATION` markers remain in the Technical Context. All decisions above are consistent
with the spec's functional requirements, its 2026-07-06 Clarifications, the user's explicit plan
instructions, and the project constitution.
