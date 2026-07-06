# API Contract: Favorites List & Pin/Unpin Actions

Base path: `/api/links`. JSON over HTTP. No authentication (single implicit user, unchanged). This
document covers what this feature adds on top of the existing endpoints — see
`001-link-capture-and-expiry/contracts/links-api.md` for the unchanged `POST /api/links` shape,
`002-graveyard-cleanup/contracts/graveyard-api.md` for `GET /api/links/graveyard`'s unchanged
ordering/envelope behavior, and `003-visual-redesign-link-actions/contracts/
link-metadata-and-delete-api.md` for the unchanged `title`/`faviconUrl` fields and the existing
`DELETE /api/links/{id}` contract, which continues to work identically on pinned links (FR-012).

All timestamps are ISO-8601 strings in UTC, the JSON serialization of a Java `Instant` — unchanged
convention. `GET /api/links` and `GET /api/links/graveyard` are otherwise **unchanged** by this
feature beyond now excluding pinned links from their result sets (see §4).

---

## 1. `GET /api/links/favorites` — new endpoint

Returns every currently pinned link, most-recently-pinned first (FR-007, Assumptions).

### Response — `200 OK`

```json
{
  "links": [
    {
      "id": 42,
      "url": "https://example.com/keep-forever",
      "savedAt": "2026-06-20T09:00:00Z",
      "expiresAt": null,
      "title": "A Page Worth Keeping",
      "faviconUrl": "https://www.google.com/s2/favicons?domain=example.com&sz=64"
    }
  ]
}
```

- `expiresAt` is **always `null`** in this response — a pinned link has no active countdown or
  graveyard-deletion deadline concept while pinned (FR-003, FR-006); the frontend renders a pinned
  indicator in that space instead of a countdown.
- `title`/`faviconUrl` behave identically to the other two endpoints (Feature 3's fallback rules,
  unchanged).
- No new query parameters, no pagination — same as the existing two read endpoints.
- Metadata backfill (Feature 3) applies to favorites reads identically to the other two views: any
  pinned link that has never had title/favicon retrieval attempted is backfilled before the response
  is built.

### Empty case

```json
{ "links": [] }
```

Returned when no links are currently pinned — the frontend renders its illustrated empty state
(FR-008) rather than treating this as an error.

---

## 2. `POST /api/links/{id}/pin` — new endpoint

Pins a link, moving it into the favorites collection regardless of whether it is currently active or
in the graveyard (FR-001, FR-002). No confirmation semantics — a single request performs the action.

### Request

No request body.

```
POST /api/links/42/pin
```

### Response — `204 No Content`

Returned in **all** of the following cases (idempotent, mirroring the existing `DELETE` contract's
idempotency style):

- The link is currently active → becomes pinned; `pinnedAt` set to now; `expiresAt` untouched.
- The link is currently in the graveyard → becomes pinned; `pinnedAt` set to now; `expiresAt`
  untouched.
- The link is already pinned → no-op; `pinnedAt` is **not** re-stamped (repeating the action does
  not change its position in the favorites ordering).
- No link with this `id` currently exists → no-op, not an error (consistent with the existing delete
  no-op precedent for a stale/already-gone id).

No response body in any case. The frontend refetches the current view after a successful call
(mirroring how `DeleteControl` triggers a refresh via its `onDeleted` callback).

### Side effects

- Excludes the link from the active-list and graveyard-list queries and the automatic
  graveyard-deletion sweep from this moment on, for as long as it remains pinned (FR-003).
- Does not modify `savedAt`, `pageTitle`, `titleFetchedAt`, or `faviconUrl`.

---

## 3. `POST /api/links/{id}/unpin` — new endpoint

Unpins a link, returning it to the active list with a fresh 168-hour countdown measured from this
exact moment (FR-010, FR-011).

### Request

No request body.

```
POST /api/links/42/unpin
```

### Response — `204 No Content`

Returned in **all** of the following cases (idempotent):

- The link is currently pinned → `pinned = false`; `expiresAt = now() + 168h` (a brand-new value,
  independent of `savedAt` and of whatever `expiresAt` held before or during pinning).
- The link is **not** currently pinned (already active/graveyard, or nonexistent) → no-op; in
  particular, `expiresAt` is **not** touched — only an actual pinned → unpinned transition resets the
  countdown, so repeating this call never re-arms a fresh countdown a second time.

No response body in any case.

### Side effects

- The link reappears in the next `GET /api/links` read, ordered per the active list's existing
  soonest-to-expire-first rule, using its brand-new `expiresAt`.
- Does not modify `savedAt`, `pageTitle`, `titleFetchedAt`, or `faviconUrl`.

---

## 4. `GET /api/links` and `GET /api/links/graveyard` — pinned links excluded

Both existing read endpoints are otherwise unchanged, but their underlying queries now exclude any
link with `pinned = true` (research.md §1). A link that was pinned from either view simply stops
appearing there from that moment on; no other response field or ordering behavior changes.

### Error format

Unchanged — no new error responses are introduced by any endpoint in this feature; `pin`/`unpin`
have no failure mode under normal operation, per their idempotency above.

---

## What remains unsupported

There is still no `PATCH`/`PUT` endpoint of any kind, and no way to rescue a link from the graveyard
back to active except via the two mechanisms this feature and Feature 1/2 already define. This
feature adds exactly three new endpoints (`GET .../favorites`, `POST .../pin`, `POST .../unpin`) and
changes nothing else about the API surface — `POST /api/links`, `DELETE /api/links/{id}`, and the two
pre-existing `GET` endpoints' response shapes (beyond the pinned-exclusion above) are unchanged.
