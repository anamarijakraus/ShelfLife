# Data Model: Favorites Tab & Link Pinning

## Entity: Link (extended — two new columns; three existing repository queries gain a predicate)

This feature extends the existing `Link` entity/`links` table (`001-link-capture-and-expiry`,
extended by `002-graveyard-cleanup` and `003-visual-redesign-link-actions`) with two new columns.
**No existing column's meaning changes**, but for the first time a stored field (`pinned`) — not a
derived timestamp comparison — determines lifecycle-query membership (research.md §1).

| Field            | Type      | Constraints / Notes                                                                                  |
|------------------|-----------|--------------------------------------------------------------------------------------------------------|
| `id`             | `Long`    | Primary key, auto-generated (identity) — unchanged                                                     |
| `url`            | `String`  | Not null, not blank, scheme-normalized — unchanged                                                     |
| `savedAt`        | `Instant` | Not null; set once at creation, never modified — unchanged                                             |
| `expiresAt`      | `Instant` | Not null; indexed. **Meaning now conditional on `pinned`**: while `pinned = false`, unchanged semantics (active-expiry moment, or +30d graveyard-deletion deadline). While `pinned = true`, its stored value is stale/inert — never read by any query or response (research.md §2) — until unpin recomputes it fresh. |
| `pageTitle`      | `String`  | Nullable — unchanged (Feature 3)                                                                       |
| `titleFetchedAt` | `Instant` | Nullable — unchanged (Feature 3)                                                                       |
| `faviconUrl`     | `String`  | Nullable — unchanged (Feature 3)                                                                       |
| `pinned`         | `boolean` | **NEW.** Not null, default `false`. `true` while the link is exempt from active-expiry and graveyard-deletion timing (FR-003). The only field this feature adds that changes *whether* a link is shown in a given view. |
| `pinnedAt`       | `Instant` | **NEW.** Nullable. Set to the current time whenever a link transitions to `pinned = true`; used only to order the Favorites list (most-recently-pinned first). Left stale/unused once unpinned — mirrors `expiresAt`'s "frozen while inert" treatment (research.md §2, §5). |

### Validation rules

- `pinned` defaults to `false` for every link created via the existing `POST /api/links` flow —
  this feature adds no new write-time validation to link creation.
- No constraint ties `pinned`/`pinnedAt` to `expiresAt`/`titleFetchedAt`/`faviconUrl` — they are
  fully orthogonal, per FR-003's "permanently exempt... for as long as it remains pinned."

### Relationships

None — same as Features 1–3. `Link` remains a single, independent entity.

### Lifecycle note (Constitution Principle II)

This feature does **not** add a fourth stage to the timestamp-derived active → graveyard → deleted
state machine. `pinned` is an orthogonal override on top of it: a pinned link is excluded from every
query that determines active/graveyard membership, regardless of what its `expiresAt` would
otherwise imply. Two new transitions are added, both requiring dedicated tests per the constitution:

| Transition | Trigger | Effect |
|---|---|---|
| active or graveyard → favorites | `POST /api/links/{id}/pin`, link currently **not** pinned | `pinned = true`, `pinnedAt = now()`. `expiresAt` untouched (frozen/inert). Link immediately excluded from active/graveyard queries and included in the favorites query. |
| favorites → active | `POST /api/links/{id}/unpin`, link currently pinned | `pinned = false`, `expiresAt = now() + 168h` (fresh countdown, same formula as `createLink`). `pinnedAt` left stale/unused. |

**True no-op, not just "no error," when already in the target state**: `pinLink` on a link that is
already pinned MUST NOT re-stamp `pinnedAt` — doing so would silently reorder it to the top of the
favorites list on every repeated (e.g., double-clicked, or retried) pin request, which is not a no-op
in any observable sense. Symmetrically, `unpinLink` on a link that is already unpinned MUST NOT
recompute `expiresAt` — doing so would silently re-arm a fresh 168-hour countdown on a link that was
never actually re-pinned in between, which is exactly the kind of quiet, hard-to-notice timing bug
this project's testing standard exists to catch (Principle II). Both methods MUST check the link's
current `pinned` value first and skip the mutating branch entirely when the link is already in the
requested state — mirroring `deleteLink`'s existing `existsById` check before `deleteById`, which is
the same "verify, then act" idempotency shape this codebase already establishes.

Both transitions compose with the pre-existing ones unchanged: a pinned link can still reach
`deleted` via the existing manual `DELETE /api/links/{id}` (FR-012/FR-016) — pinning does not gate or
alter that path at all. An unpinned link re-enters the *same* active → graveyard → deleted timeline
Feature 1/2 already define, just with a new `savedAt`-independent starting point for `expiresAt`.

## Repository query changes (all existing timing queries gain `pinned = false`; one new query added)

| Query | Change | Why |
|---|---|---|
| Active list | `findByExpiresAtAfterOrderByExpiresAtAsc` → `findByPinnedFalseAndExpiresAtAfterOrderByExpiresAtAsc` | Excludes pinned links from the active list at the query level (FR-003), not by post-load filtering. |
| Graveyard list | `findByExpiresAtLessThanEqualAndExpiresAtAfterOrderByExpiresAtAsc` → `findByPinnedFalseAndExpiresAtLessThanEqualAndExpiresAtAfterOrderByExpiresAtAsc` | Same reasoning for the graveyard view. |
| **Graveyard automatic-deletion sweep** | `deleteByExpiresAtLessThanEqual` → `deleteByPinnedFalseAndExpiresAtLessThanEqual` | **Correctness-critical** (research.md §1): without this predicate, a pinned link whose stale `expiresAt` already implies a past graveyard deadline would be permanently deleted by the next sweep despite being pinned, violating FR-003. |
| Favorites list (**new**) | `findByPinnedTrueOrderByPinnedAtDesc` | Backs the new `GET /api/links/favorites` endpoint; orders most-recently-pinned first (research.md §5). |

Existing repository/service tests that call the renamed methods directly (`LinkRepositoryTest`,
`LinkServiceTest`) are updated to call the new method names — this is a rename to reflect an added
predicate, not a behavior change for any non-pinned link (a link with `pinned = false`, the default,
behaves identically to before under the new method names).

## `LinkResponse` wire shape changes

| Field        | Type              | Notes                                                                              |
|--------------|-------------------|-------------------------------------------------------------------------------------|
| `id`         | `Long`            | unchanged                                                                          |
| `url`        | `String`          | unchanged                                                                          |
| `savedAt`    | `Instant`         | unchanged                                                                          |
| `expiresAt`  | `Instant \| null` | unchanged for `from`/`forGraveyard`. **NEW factory `forFavorites` sends `null`** — a pinned link's countdown concept does not apply (research.md §5), rather than exposing its stale, frozen underlying value. |
| `title`      | `String`          | unchanged (Feature 3 fallback-resolution logic, reused as-is by `forFavorites`)     |
| `faviconUrl` | `String \| null`  | unchanged (Feature 3), reused as-is by `forFavorites`                              |

No `pinned` boolean is added to the wire shape — which endpoint returned a link already fully
implies its pinned status (research.md §5).

See `contracts/favorites-pin-api.md` for full request/response examples.
