# Data Model: Link Capture & Active List with Automatic Expiry

## Entity: Link

Represents a single saved bookmark (spec Key Entities section).

| Field       | Type        | Constraints / Notes                                                         |
|-------------|-------------|-------------------------------------------------------------------------------|
| `id`        | `Long`      | Primary key, auto-generated (identity)                                       |
| `url`       | `String`    | Not null, not blank; stored already scheme-normalized (`https://` prepended if missing per FR-003); reasonable max length (e.g. 2048 chars) to guard against pathological input |
| `savedAt`   | `Instant`   | Not null; set once at creation, never modified (FR-004)                      |
| `expiresAt` | `Instant`   | Not null; computed once at creation as `savedAt + 168 hours` (FR-005), never modified; **indexed** — this is the column the active-list query filters and sorts on |

**No status/state field.** Whether a link is "active" or "expired" is never stored — it is always
derived by comparing `expiresAt` to the current time at read time (spec Clarifications,
2026-07-02: read-time filtering, no scheduled job). This keeps the record itself immutable after
creation (FR-017): expiry never triggers a write.

### Validation rules (enforced in `LinkService` before persistence)

- `url` MUST NOT be blank.
- `url`, after scheme normalization (prepend `https://` if no `scheme://` prefix is present),
  MUST parse as an absolute URI with a host (FR-003). Anything that still fails this check after
  normalization is rejected — no row is created.
- `savedAt` is always set server-side to the current instant at the moment of successful
  validation — never client-supplied (prevents a client from forging save times).
- `expiresAt` is always derived server-side as `savedAt.plus(168, ChronoUnit.HOURS)` — never
  client-supplied.

### Derived/query-time concepts (not stored)

- **Active**: `expiresAt.isAfter(now)` — true for links shown on the landing page (FR-006).
- **Expired**: `!expiresAt.isAfter(now)` — equivalently `expiresAt.isBefore(now) ||
  expiresAt.equals(now)`, matching the exactly-168h-boundary edge case (User Story 3, scenario 4:
  a link at exactly 168h01m is excluded; scenario 3: a link at 167h59m is included). The boundary
  moment itself (`expiresAt == now`) is treated as expired (excluded), consistent with FR-007
  ("has reached or passed its 168-hour expiration").
- **Active count**: `size()` of the active-list query result (FR-013) — no separate stored
  counter, avoiding a second source of truth that could drift.
- **Display order**: active links ordered by `expiresAt` ascending (soonest-to-expire first,
  FR-010).

### Relationships

None. `Link` is a single, independent entity for this feature — no user/account entity exists
yet (FR-014: single implicit user, no auth), and no graveyard/state-machine entity exists yet
(that belongs to a later feature, per spec's explicit out-of-scope note).

### Lifecycle note for future features

This feature only ever *creates* `Link` rows and *reads* them (filtered by `expiresAt`). It never
updates or deletes a row. This is intentional groundwork for the constitution's link lifecycle
state machine (active → expired → graveyard → deleted): a future feature can safely query rows
where `expiresAt` has passed to promote them into a graveyard, because this feature guarantees
those rows still exist, untouched, with their original `savedAt`/`expiresAt` values intact
(FR-017).
