# API Contract: Link Metadata & Manual Delete

Base path: `/api/links`. JSON over HTTP. No authentication (single implicit user, unchanged). This
document covers what this feature adds or changes on top of the existing endpoints — see
`001-link-capture-and-expiry/contracts/links-api.md` for the unchanged `POST /api/links` request
shape and `002-graveyard-cleanup/contracts/graveyard-api.md` for the unchanged
`GET /api/links/graveyard` ordering/envelope behavior. `POST /api/links` itself is otherwise
unchanged by this feature — it still returns instantly and does not include a title/favicon in its
immediate response (see §1 below).

All timestamps are ISO-8601 strings in UTC, the JSON serialization of a Java `Instant` — unchanged
convention.

---

## 1. `GET /api/links` and `GET /api/links/graveyard` — response envelope extended

Both existing read endpoints (unchanged URLs, unchanged ordering, unchanged query/filter absence)
now include two additional fields per entry: `title` and `faviconUrl`. Before building the
response, the server backfills metadata (title fetch + favicon URL construction) for any link in
the result set that has never had it attempted (`titleFetchedAt == null` and/or
`faviconUrl == null`), fanned out concurrently (research.md §4) — so the response always reflects
the most current metadata state as of this read, not a stale prior value.

### Response — `200 OK`

```json
{
  "links": [
    {
      "id": 40,
      "url": "https://example.com/some/article",
      "savedAt": "2026-07-01T09:00:00Z",
      "expiresAt": "2026-07-08T09:00:00Z",
      "title": "How To Read Faster — Example Blog",
      "faviconUrl": "https://www.google.com/s2/favicons?domain=example.com&sz=64"
    },
    {
      "id": 41,
      "url": "https://unreachable-or-titleless.example.org/page",
      "savedAt": "2026-07-01T10:00:00Z",
      "expiresAt": "2026-07-08T10:00:00Z",
      "title": "https://unreachable-or-titleless.example.org/page",
      "faviconUrl": "https://www.google.com/s2/favicons?domain=unreachable-or-titleless.example.org&sz=64"
    }
  ]
}
```

- `title` is **never null**: it is the retrieved page title when available, otherwise the link's
  own `url` (FR-005/FR-006). The frontend renders this field directly as the card's heading — it
  does not need to know whether the value came from a fetch or the fallback.
- `faviconUrl` is **nullable**. It is `null` only in the narrow window before the read-time backfill
  has ever run for that link (practically: it is populated for every link after its first read post
  upgrade, since favicon-URL construction cannot fail — research.md §2). The frontend renders a
  generic fallback icon when `null` (FR-008).
- In the graveyard response, `title`/`faviconUrl` behave identically to the active response — only
  `expiresAt`'s meaning (permanent-deletion deadline) differs, unchanged from Feature 2.
- No new query parameters, no pagination — same as Features 1–2.

### Error format

Unchanged — no new error responses are introduced by the read endpoints.

---

## 2. `POST /api/links` — unchanged request/response shape, metadata deferred

The request and immediate response shape are **unchanged** by this feature: creating a link is
still instant, and its `title`/`faviconUrl` are resolved (title lazily on next read; favicon
synchronously, effectively always present) rather than included at creation time by contract — a
client should not assume `title` reflects a fetched value immediately after `POST`; it will be the
raw URL until the first subsequent `GET` backfills it (typically within seconds, per the existing
~60s poll cadence).

```json
// POST /api/links  { "url": "example.com" }
// 201 Created
{
  "id": 42,
  "url": "https://example.com",
  "savedAt": "2026-07-04T12:00:00Z",
  "expiresAt": "2026-07-11T12:00:00Z",
  "title": "https://example.com",
  "faviconUrl": "https://www.google.com/s2/favicons?domain=example.com&sz=64"
}
```

(`faviconUrl` is present immediately since it's computed synchronously at save time, per research.md
§2; `title` reflects the raw-URL fallback until backfilled by a later read.)

---

## 3. `DELETE /api/links/{id}` — new endpoint

Permanently and irreversibly deletes a single link's data, regardless of whether it is currently
active or in the graveyard (User Story 3; FR-013, FR-016).

### Request

No request body. `id` is the link's numeric identifier (same `id` returned by `POST`/`GET`).

```
DELETE /api/links/42
```

### Response — `204 No Content`

Returned in **all** of the following cases, with identical semantics (idempotent delete):

- The link with this `id` is currently active.
- The link with this `id` is currently in the graveyard.
- No link with this `id` currently exists (already deleted, never existed, or lost a race with the
  automatic graveyard-deletion sweep) — this is a successful no-op, **not** an error, per the
  spec's edge case for a stale/duplicate delete request.

No response body in any case.

### Side effects

- Removes the link's row entirely — a real `DELETE`, not a soft-delete or filter (mirrors Feature
  2's "actual removal" requirement for automatic graveyard cleanup).
- Does **not** modify any other link's `expiresAt`, ordering, or count (FR-018).
- Does **not** trigger, skip, or otherwise interact with the automatic graveyard-deletion sweep
  that already runs as part of `GET /api/links/graveyard` (Feature 2) — the two deletion paths are
  independent and idempotent with respect to each other.

### Error format

This endpoint has no failure mode under normal operation (see idempotency above); unexpected server
errors, if any, share the existing error shape:

```json
{
  "error": "<machine-readable-code>",
  "message": "<human-readable-explanation>"
}
```

### What remains unsupported

Consistent with Feature 2's FR-010–FR-012 (still in force): there is still no `PATCH`/`PUT`
endpoint and no way to rescue a link from the graveyard back to active, resurrect a deleted link,
or pin/favorite a link to exempt it from expiration. This feature adds exactly one new capability
(`DELETE`) and changes nothing else about the API surface.
