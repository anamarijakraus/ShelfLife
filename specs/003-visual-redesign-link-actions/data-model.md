# Data Model: Warm Visual Redesign & Card-Level Link Actions

## Entity: Link (extended — three new nullable columns, no change to existing columns)

This feature extends the existing `Link` entity/`links` table (`001-link-capture-and-expiry/
data-model.md`, unchanged by `002-graveyard-cleanup`) with three new nullable, presentation-
supporting columns. **No existing column, index, or timing semantics change.**

| Field            | Type      | Constraints / Notes                                                                                  |
|------------------|-----------|--------------------------------------------------------------------------------------------------------|
| `id`             | `Long`    | Primary key, auto-generated (identity) — unchanged                                                     |
| `url`            | `String`  | Not null, not blank, scheme-normalized — unchanged                                                     |
| `savedAt`        | `Instant` | Not null; set once at creation, never modified — unchanged                                             |
| `expiresAt`      | `Instant` | Not null; indexed; `savedAt + 168h`; source of truth for active/graveyard/deleted stages — unchanged   |
| `pageTitle`      | `String`  | **NEW.** Nullable. The retrieved `<title>` text, set at most once. `null` until a fetch is attempted, and remains `null` thereafter if none could be retrieved (unreachable, timeout, no `<title>` tag). Never re-attempted once `titleFetchedAt` is set. |
| `titleFetchedAt` | `Instant` | **NEW.** Nullable. `null` means "metadata fetch never attempted for this link" (the read-time backfill trigger). Set to the attempt's timestamp the moment a fetch is attempted, regardless of outcome — this is what makes the fetch-once guarantee hold. |
| `faviconUrl`     | `String`  | **NEW.** Nullable. A URL against a third-party favicon service, built from the link's domain. Computed synchronously (no network call, cannot meaningfully fail) — populated at save time for new links, and opportunistically for pre-existing links the first time they're read with it still `null`. |

**Still no status/state field.** Whether a link is active, in the graveyard, or deleted continues
to be derived purely from `expiresAt` and the current time (unchanged from Feature 2). The three
new columns are orthogonal to lifecycle stage — they describe *what to show*, never *whether to
show it* or *for how long*.

### Validation rules

- `pageTitle`, when present, is stored as retrieved (truncated to a reasonable column length, e.g.
  512 chars, to guard against a pathological `<title>` — display-side truncation per FR-006's
  "long title doesn't break layout" edge case still applies independently on the frontend).
- `faviconUrl`, when present, is always a well-formed URL against the configured favicon service —
  it is never derived from arbitrary/untrusted content, only from the link's own already-validated
  domain (the domain was already validated as URL-shaped at capture time, per Feature 1's FR-003).
- No new validation is added to the existing create path (`POST /api/links`); this feature adds no
  new write-time validation, only new post-save enrichment.

### Relationships

None — same as Features 1–2. `Link` remains a single, independent entity.

### New behavior added by this feature (no schema beyond the three columns above)

| Operation | Kind | Purpose |
|-----------|------|---------|
| Metadata backfill | In-memory mutation + existing `save`, applied before mapping to `LinkResponse` in `listActiveLinks()`/`listGraveyardLinks()` | For each link in the result set with `titleFetchedAt == null` or `faviconUrl == null`, attempt the SSRF-guarded title fetch and/or compute the favicon URL, then persist. Fanned out concurrently via virtual threads (research.md §4) — not a new persisted query, just additional writes on already-loaded rows. |
| `deleteLink(Long id)` | `existsById` + `deleteById` (both inherited from `JpaRepository`, no new repository method) | Permanently removes a link's row regardless of whether it is currently active or in the graveyard. Idempotent: a missing id is a successful no-op, not an error (research.md §5). |

No new repository query methods are introduced — this feature relies entirely on `JpaRepository`'s
built-in `existsById`/`deleteById`, and mutates entities already loaded by the existing
`findByExpiresAtAfterOrderByExpiresAtAsc`/`findByExpiresAtLessThanEqualAndExpiresAtAfterOrderBy
ExpiresAtAsc` queries.

### Lifecycle note

This feature does not add a stage to the link lifecycle state machine (active → graveyard →
deleted, per the constitution). It adds a second *path* into the existing terminal "deleted" state
— manual deletion, callable from either the active or graveyard stage — alongside the pre-existing
automatic graveyard → deleted transition. Both paths converge on the same end state: the row no
longer exists. Per the constitution's testing standard, both new transitions (active → deleted,
graveyard → deleted via manual delete) require dedicated tests, in addition to the existing
automatic-transition tests, which remain unmodified and must continue to pass.

## Non-persisted, client-side-only concept: countdown urgency band

Not part of the data model — computed entirely in the frontend from already-returned `savedAt`/
`expiresAt` fields and fixed constants (168h / 30d), per research.md §8. No new field is returned
by the API to represent it; `title` and `faviconUrl` (below) are the only new wire fields this
feature introduces.

## `LinkResponse` wire shape changes

| Field        | Type              | Notes                                                                              |
|--------------|-------------------|-------------------------------------------------------------------------------------|
| `id`         | `Long`            | unchanged                                                                          |
| `url`        | `String`          | unchanged                                                                          |
| `savedAt`    | `Instant`         | unchanged                                                                          |
| `expiresAt`  | `Instant`         | unchanged (still reinterpreted as the permanent-deletion deadline in the graveyard response, per Feature 2) |
| `title`      | `String`          | **NEW.** Never null: `pageTitle` if retrieved, otherwise `url` (fallback resolved server-side, research.md §6) |
| `faviconUrl` | `String \| null`  | **NEW.** Nullable: the constructed favicon-service URL, or `null` if not yet computed (frontend shows a generic fallback icon) |

See `contracts/link-metadata-and-delete-api.md` for full request/response examples.
