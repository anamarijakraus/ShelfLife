# Data Model: Graveyard Page & Automatic Permanent Cleanup

## Entity: Link (unchanged schema, extended lifecycle semantics)

This feature reuses the exact same `Link` entity and `links` table introduced in Feature 1
(`001-link-capture-and-expiry/data-model.md`) with **no schema change** — no new column, no new
index, no new table.

| Field       | Type        | Constraints / Notes                                                         |
|-------------|-------------|-------------------------------------------------------------------------------|
| `id`        | `Long`      | Primary key, auto-generated (identity) — unchanged                            |
| `url`       | `String`    | Not null, not blank, scheme-normalized — unchanged                            |
| `savedAt`   | `Instant`   | Not null; set once at creation, never modified — unchanged                    |
| `expiresAt` | `Instant`   | Not null; `savedAt + 168 hours`; **indexed**; now the single source of truth for all three lifecycle stages below, not just active/expired |

**Still no status/state field.** Whether a link is active, in the graveyard, or due for permanent
deletion is never stored — always derived from `expiresAt` and the current time at read time (spec
Clarifications, 2026-07-03), exactly extending the read-time-derivation approach Feature 1
established.

### Derived/query-time lifecycle stages (not stored)

- **Active**: `expiresAt.isAfter(now)` — unchanged from Feature 1; shown on the active list.
- **Graveyard**: `!expiresAt.isAfter(now) && expiresAt.isAfter(now.minus(30, DAYS))` — i.e., past
  active expiration but not yet past its 30-day graveyard deadline. Shown on the graveyard page
  (FR-003).
- **Due for deletion**: `!expiresAt.isAfter(now.minus(30, DAYS))` — i.e., the graveyard deadline
  (`expiresAt + 30 days`) has already passed. Rows in this stage are deleted (a real `DELETE`, per
  FR-014/FR-015) the next time the graveyard is read, before that read's `SELECT` runs, so they are
  never actually returned by any query — this stage is transient by design (Clarifications,
  2026-07-03).
- **Graveyard permanent-deletion deadline**: `expiresAt.plus(30, DAYS)` — the timestamp the
  graveyard list's remaining-time countdown counts down to (FR-002, FR-005). Computed on demand,
  never persisted.
- **Graveyard display order**: graveyard links ordered by `expiresAt` ascending (FR-004) — since
  the deadline is a fixed +30-day offset of `expiresAt`, ordering by `expiresAt` is equivalent to
  ordering by the deadline itself.
- **Graveyard count**: `size()` of the graveyard-list query result (FR-007) — no separate stored
  counter, consistent with how the active count avoids a second source of truth (Feature 1).

### Validation rules

Unchanged from Feature 1 — this feature adds no new write path for `Link` (no create/update
endpoint is introduced; the only new write is the permanent-deletion bulk `DELETE`, which removes
rows rather than modifying them).

### Relationships

None — same as Feature 1. `Link` remains a single, independent entity.

### Repository operations added by this feature

| Method | Kind | Purpose |
|--------|------|---------|
| `deleteByExpiresAtLessThanEqual(Instant threshold)` | `@Modifying @Transactional @Query` bulk `DELETE` | Permanently removes every link whose graveyard deadline (`expiresAt + 30 days`) has passed, in one indexed, set-based SQL statement — called with `threshold = now.minus(30, DAYS)` (FR-014, FR-015) |
| `findByExpiresAtLessThanEqualAndExpiresAtAfterOrderByExpiresAtAsc(Instant activeThreshold, Instant graveyardThreshold)` | derived query | Returns the current graveyard contents (past active expiration, not yet past the graveyard deadline), soonest-to-be-deleted first — called with `activeThreshold = now`, `graveyardThreshold = now.minus(30, DAYS)` (FR-003, FR-004) |

Both operations reuse the single existing `idx_link_expires_at` index — no new index required.

### Lifecycle note

This feature completes the link lifecycle state machine named in the constitution: active →
graveyard → deleted. Feature 1 guaranteed expired rows persist untouched past 168 hours
specifically so this feature could promote them into the graveyard purely by re-evaluating the
same `expiresAt` column — no migration or backfill of existing rows is needed (spec Clarifications,
2026-07-03: pre-existing expired rows get their graveyard deadline computed with the same
`expiresAt + 30 days` formula, no special-casing). Once a row's graveyard deadline passes, this
feature's bulk delete removes it — there is no further stage after "deleted"; the row ceases to
exist.
