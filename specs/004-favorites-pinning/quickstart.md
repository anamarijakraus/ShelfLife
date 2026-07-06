# Quickstart: Favorites Tab & Link Pinning

Validates that pinning/unpinning, the Favorites tab, and their interaction with the existing
active/graveyard timing all work end-to-end. Assumes the backend (`backend/`, default port 8080) and
frontend (`frontend/`, Vite dev server proxying `/api` to the backend) are already set up per
Features 1–3's quickstarts.

## Prerequisites

- Backend running: `cd backend && ./mvnw spring-boot:run`
- Frontend running: `cd frontend && npm run dev`
- H2 file-based storage reset (or a fresh instance) if you want a clean slate.

## Scenario 1 — Pin from the active list, see it in Favorites

1. Save a link (paste a URL, press Enter) on the "On the shelf" tab.
2. Click its pin control.
3. **Expect**: the card disappears from "On the shelf" immediately, with no confirmation prompt.
4. Switch to the new "Favorites" tab (positioned after "Graveyard").
5. **Expect**: the link appears there, same card design/palette/title/favicon as the other views,
   showing a "Pinned" indicator in place of a countdown, and the Favorites count reads 1.

## Scenario 2 — Pin from the graveyard

1. Using the backend's H2 console (or `LinkRepository`/a direct `INSERT`), create a link whose
   `expiresAt` is in the past (so it appears in the graveyard), or simply wait for one to expire.
2. Open the "Graveyard" tab, confirm the link is listed, then click its pin control.
3. **Expect**: it disappears from the graveyard and appears in "Favorites", exactly as in Scenario 1.

## Scenario 3 — A pinned link never expires or gets swept

1. With a link pinned (from either scenario above), verify via
   `curl http://localhost:8080/api/links/favorites` that it's present with `"expiresAt": null`.
2. Manually set that link's stored `expiresAt` far in the past (e.g., via H2 console, to simulate
   time passing) — both before its original 168h mark and past a simulated 30-day graveyard deadline.
3. Reload the Favorites tab, then check `GET /api/links` and `GET /api/links/graveyard`.
4. **Expect**: the link still appears only in Favorites — never in the active list or graveyard, and
   critically, it is **not deleted** by the graveyard's automatic sweep despite its stale `expiresAt`
   implying it's long past both thresholds (this is the FR-003 correctness scenario research.md §1
   calls out).

## Scenario 4 — Unpin gives a fresh 168-hour countdown

1. With a link pinned, note the current time.
2. Click its pin control on the Favorites tab (now shown in its "pinned" state).
3. **Expect**: it disappears from Favorites and reappears on "On the shelf" immediately.
4. Check `GET /api/links` for that link's `expiresAt`.
5. **Expect**: `expiresAt` is approximately `now() + 168h` from the moment of the unpin click — not
   related to its original `savedAt`, and not extended from whatever `expiresAt` it held before or
   during pinning.

## Scenario 5 — Delete still works identically on a pinned link

1. Pin a link.
2. On the Favorites tab, activate its delete control, then confirm (same arm/confirm interaction as
   the other two views).
3. **Expect**: the link is permanently removed — check `GET /api/links/favorites`,
   `GET /api/links`, and `GET /api/links/graveyard` all omit it, and
   `DELETE /api/links/{id}` on the same id again returns `204` as a no-op.

## Scenario 6 — Favorites empty state and count

1. Ensure no links are pinned (unpin or delete any pinned links from earlier scenarios).
2. Open the "Favorites" tab.
3. **Expect**: a distinct, third illustration (not the active-list or graveyard illustration) and a
   count of 0.

## Automated coverage

These scenarios are also covered by:

- `LinkRepositoryTest` — pinned-exclusion at the query level for active/graveyard/sweep queries;
  favorites ordering by `pinnedAt DESC`.
- `LinkServiceTest` — pin/unpin transitions (active→favorites, graveyard→favorites,
  favorites→active), idempotency (pin-when-already-pinned, unpin-when-not-pinned, action on a
  nonexistent id), the fresh-168h-on-unpin computation, and that a pinned link survives past both the
  168h and 30-day boundaries without being excluded-and-swept.
- `LinkControllerTest` — the three new endpoints' contracts, including the `expiresAt: null` shape of
  `GET /api/links/favorites`.
- `PinControl.test.tsx`, `LinkListItem.test.tsx`, `FavoritesView.test.tsx`, `NavTabs.test.tsx` — frontend
  rendering/interaction coverage for the pin control, the pinned-badge-instead-of-countdown swap, the
  third tab, and the Favorites empty state.

See `contracts/favorites-pin-api.md` for full request/response shapes and `data-model.md` for the
underlying field/query changes.
