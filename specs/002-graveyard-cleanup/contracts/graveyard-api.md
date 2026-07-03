# API Contract: Graveyard

Base path: `/api/links`. JSON over HTTP. No authentication (single implicit user, unchanged from
Feature 1). This document covers only the new endpoint added by this feature; see
`001-link-capture-and-expiry/contracts/links-api.md` for the unchanged `POST /api/links` and
`GET /api/links` endpoints.

All timestamps are ISO-8601 strings in UTC (e.g., `"2026-07-02T14:30:00Z"`), the JSON
serialization of a Java `Instant` — unchanged convention from Feature 1.

---

## `GET /api/links/graveyard`

Retrieve all links currently in the graveyard — past their 168-hour active expiration but not yet
past their 30-day permanent-deletion deadline — soonest-to-be-permanently-deleted first (User
Story 2; FR-003, FR-004, FR-005, FR-006, FR-007).

Before building the response, the server first executes a bulk delete of every link whose
permanent-deletion deadline (`expiresAt + 30 days`) has already passed (User Story 3;
FR-014, FR-015; spec Clarifications, 2026-07-03) — so a link that just crossed its deadline is
both deleted and correctly absent from this same response, not returned once more before removal.

### Response — `200 OK`

```json
{
  "links": [
    {
      "id": 40,
      "url": "https://soon-to-be-deleted.example.com",
      "savedAt": "2026-05-25T09:00:00Z",
      "expiresAt": "2026-06-24T09:00:00Z"
    },
    {
      "id": 41,
      "url": "https://another-example.org/some/path",
      "savedAt": "2026-06-01T09:00:00Z",
      "expiresAt": "2026-07-01T09:00:00Z"
    }
  ]
}
```

- Same envelope shape (`{ "links": [...] }`) and same per-entry fields as `GET /api/links`, for
  frontend reuse (research.md §4/§5).
- **`expiresAt` in this response is reinterpreted**: it holds the link's permanent-deletion
  deadline (original active `expiresAt` + 30 days), not the original 168-hour active-expiration
  instant. This is the only difference from the `GET /api/links` response shape — it lets the
  frontend's existing remaining-time rendering logic work unmodified for both views (it always
  counts down to whatever `expiresAt` holds).
- `links` is always ordered by the permanent-deletion deadline ascending — equivalently, by the
  underlying `expiresAt` column ascending, since the deadline is a fixed +30-day offset of it
  (FR-004).
- `links` contains zero or more entries; an empty array is a valid, non-error response (Edge Case:
  zero graveyard links).
- The graveyard count (FR-007) is derived client-side as `links.length` — no separate count
  endpoint or field, consistent with the active list's convention.
- No pagination in this feature (personal-scale volume, per constitution Performance
  Requirements), consistent with `GET /api/links`.
- There is no request body and no query parameters — the endpoint always returns the full current
  graveyard.
- There is no way to filter, sort differently, rescue, or early-delete a link through this or any
  other endpoint (FR-010, FR-011, FR-012) — this is the only graveyard-related endpoint this
  feature introduces.

### Error format

No feature-specific error responses are introduced by this endpoint (it has no request body to
validate). Unexpected server errors, if any, share the same error shape already established in
`001-link-capture-and-expiry/contracts/links-api.md`:

```json
{
  "error": "<machine-readable-code>",
  "message": "<human-readable-explanation>"
}
```
