# API Contract: Links

Base path: `/api/links`. JSON over HTTP. No authentication (single implicit user, per FR-014).

All timestamps are ISO-8601 strings in UTC (e.g., `"2026-07-02T14:30:00Z"`), the JSON
serialization of a Java `Instant`.

---

## `POST /api/links`

Capture a new link (User Story 1; FR-001, FR-002, FR-003).

### Request

```json
{
  "url": "example.com"
}
```

| Field | Type   | Required | Notes                                                        |
|-------|--------|----------|---------------------------------------------------------------|
| `url` | string | yes      | Raw pasted input. May be missing a scheme (normalized server-side per FR-003). |

### Response — `201 Created`

```json
{
  "id": 42,
  "url": "https://example.com",
  "savedAt": "2026-07-02T14:30:00Z",
  "expiresAt": "2026-07-09T14:30:00Z"
}
```

`url` in the response is the normalized, persisted form (scheme prepended if it was missing).

### Response — `400 Bad Request`

Returned when `url` is blank, or is not a well-formed URL even after scheme normalization
(FR-003; User Story 1, scenario 3).

```json
{
  "error": "invalid_url",
  "message": "The submitted value is not a valid URL."
}
```

---

## `GET /api/links`

Retrieve all currently active (unexpired) links, soonest-to-expire first (User Story 2; FR-006,
FR-007, FR-008, FR-009, FR-010). Expiration is evaluated fresh on every call (read-time
filtering) — no separate "refresh" action exists.

### Response — `200 OK`

```json
{
  "links": [
    {
      "id": 42,
      "url": "https://example.com",
      "savedAt": "2026-07-02T14:30:00Z",
      "expiresAt": "2026-07-09T14:30:00Z"
    },
    {
      "id": 41,
      "url": "https://another-example.org/some/path",
      "savedAt": "2026-06-30T09:00:00Z",
      "expiresAt": "2026-07-07T09:00:00Z"
    }
  ]
}
```

- `links` is always ordered by `expiresAt` ascending (FR-010).
- `links` contains zero or more entries; an empty array is a valid, non-error response
  (Edge Case: zero active links).
- The active count (FR-013, User Story 4) is derived client-side as `links.length` — there is no
  separate count endpoint or field, avoiding a second source of truth.
- No pagination in this feature (personal-scale volume, per constitution Performance
  Requirements).

---

## Error format

All error responses share this shape:

```json
{
  "error": "<machine-readable-code>",
  "message": "<human-readable-explanation>"
}
```
