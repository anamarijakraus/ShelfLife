# Quickstart: Graveyard Page & Automatic Permanent Cleanup

Validates this feature end-to-end against the acceptance scenarios in [spec.md](./spec.md). See
[data-model.md](./data-model.md) for the extended `Link` lifecycle and
[contracts/graveyard-api.md](./contracts/graveyard-api.md) for the new endpoint's shape. This
builds on Feature 1's setup (`001-link-capture-and-expiry/quickstart.md`) — the same
backend/frontend processes serve both features.

## Prerequisites

- Java 21, Maven, Node.js (for Vite/npm) installed locally.
- No external services required — H2 runs in file mode, no separate database server.
- Feature 1 (capture + active list) already working, since a link must pass through active
  expiration before it can be validated in the graveyard.

## Setup & run

```bash
# Backend (from backend/)
mvn spring-boot:run

# Frontend (from frontend/, in a second terminal)
npm install
npm run dev
```

Open the frontend URL in a browser.

## Validation scenarios

### 1. Expired links land in the graveyard instead of vanishing (User Story 1)

1. Insert (or let naturally expire) a `Link` row whose `expiresAt` is a few seconds in the past
   and whose graveyard deadline (`expiresAt + 30 days`) is still far in the future.
2. Call `GET /api/links` (or reload the active view).
3. **Expect**: the link is absent from the active list.
4. Call `GET /api/links/graveyard` (or switch to the graveyard tab).
5. **Expect**: the same link is now present, with its `expiresAt` field holding the
   permanent-deletion deadline (original active expiration + 30 days), not the original value.

### 2. View the graveyard, ordered by urgency (User Story 2)

1. With two or more graveyard-eligible links at different points in their 30-day window, call
   `GET /api/links/graveyard` (or open the graveyard tab).
2. **Expect**: links are ordered soonest-to-be-permanently-deleted first, each showing its raw URL
   and a remaining-time countdown.
3. Leave the graveyard page open for a couple of minutes without reloading.
4. **Expect**: the displayed remaining time decreases on its own (no reload needed), on the same
   ~60-second cadence as the active list.
5. With zero graveyard links, view the graveyard page.
6. **Expect**: an empty state is shown (graveyard-specific copy, not the active list's), not an
   error, and the count reads zero.

### 3. Graveyard links are permanently and irreversibly deleted (User Story 3)

Because permanent deletion is read-time-evaluated (no scheduled job), the fastest way to validate
this without waiting 30 days is directly against the database/API using a manually inserted row
with a past graveyard deadline:

1. Insert a `Link` row directly with `expiresAt` set so that `expiresAt + 30 days` is a few seconds
   in the past (i.e., `expiresAt` itself is just over 30 days in the past).
2. Call `GET /api/links/graveyard`.
3. **Expect**: that link is absent from the response.
4. Query the row directly in the database.
5. **Expect**: the row no longer exists at all — it was actually deleted (not merely filtered),
   per FR-014/FR-015.
6. For the automated version of this check, see the boundary tests in
   `backend/src/test/java/.../link/LinkServiceTest.java` (29d23h59m included, exactly 30d00h00m and
   30d00h01m excluded-and-deleted) and the delete-then-read tests against zero/one/many eligible
   rows.

### 4. Navigate between Active and Graveyard views (User Story 4)

1. Load the app; confirm the active list is shown by default.
2. Use the tab control to switch to the graveyard view.
3. **Expect**: the graveyard's own list, count, and ordering are shown, and the tab control
   visibly indicates the graveyard tab is now selected.
4. Switch back to the active tab.
5. **Expect**: the active list, count, and ordering are shown again, unchanged from before.

### 5. See graveyard count at a glance (User Story 5)

1. With N graveyard links, open the graveyard tab.
2. **Expect**: a visible count reads exactly N.
3. Let one link cross its permanent-deletion deadline (or insert a new graveyard-eligible row).
4. **Expect**: the count updates on the next poll tick (≤ ~60s) without a manual reload.

### 6. Open a graveyard link's destination (User Story 6)

1. On the graveyard tab, click/activate a listed link.
2. **Expect**: the link's original URL opens in a new browser tab; the ShelfLife app tab remains
   open and unchanged.
3. Return to the graveyard tab and reload the list (or wait for the next poll tick).
4. **Expect**: that link's remaining time and position are unchanged — opening it had no effect on
   its lifecycle.

## Notes

- There is no way to rescue a graveyard link back to active, nor to manually delete/clear a
  graveyard (or active) link early — no such action exists anywhere in the UI or API by design
  (FR-010, FR-011, FR-012).
- No pinning/favoriting concept exists to exempt any link from either transition (FR-013).
- The active list's countdown, ordering, and capture flow are unaffected by this feature (FR-016)
  — Feature 1's quickstart scenarios should still pass unchanged.
