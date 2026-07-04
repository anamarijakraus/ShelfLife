# Quickstart: Warm Visual Redesign & Card-Level Link Actions

Validates this feature end-to-end against the acceptance scenarios in [spec.md](./spec.md). See
[data-model.md](./data-model.md) for the extended `Link` fields and
[contracts/link-metadata-and-delete-api.md](./contracts/link-metadata-and-delete-api.md) for the
extended read responses and the new `DELETE` endpoint. This builds on Features 1–2's setup — the
same backend/frontend processes serve all three features.

## Prerequisites

- Java 21, Maven, Node.js (for Vite/npm) installed locally.
- No external services required to run the app itself. Note: server-side title fetching makes real
  outbound HTTP requests to whatever URL a link points at — for fully offline testing of the
  fallback path, point a saved link at an address that will not resolve/respond (see scenario 2).
- Features 1–2 (capture + active list + graveyard) already working.

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

### 1. A warm, card-based redesign across both views (User Story 1)

1. Save two or three links, then switch between the Active and Graveyard tabs.
2. **Expect**: every link renders as a rounded card with visible padding and a subtle shadow (not a
   plain row) on both tabs, using the same warm palette and typographic hierarchy — the title (or
   fallback) most prominent, the URL secondary, the countdown visually distinct — in both places.

### 2. Recognize a saved link via title and favicon (User Story 2)

1. Save a link to a real, reachable page with a normal `<title>` and favicon (e.g., a well-known
   public site).
2. Reload/wait for the next poll tick.
3. **Expect**: the card's heading shows the page's actual title, and a favicon appears next to it.
4. Save a link to an address that will not respond (e.g., an unreachable host or one that times
   out).
5. Reload/wait for the next poll tick.
6. **Expect**: the card's heading falls back to the raw URL, and a neutral generic icon appears
   instead of a favicon — the card layout is not broken or left in a loading state.
7. Confirm saving itself is still instant regardless of the destination's reachability (title/
   favicon retrieval never blocks the `POST`).
8. For a link that existed before this feature (or was inserted directly with `pageTitle`/
   `faviconUrl` left `null`), confirm its next `GET` read populates title/favicon the same way a
   newly saved link's would (per contracts §1's backfill-on-read behavior).

### 3. Permanently delete a single link from its card (User Story 3)

1. On the active list, activate a card's delete control once.
2. **Expect**: the card shows a lightweight armed/confirm state, and the link is **not** deleted
   yet.
3. Click elsewhere on the page (not the control).
4. **Expect**: the card returns to normal, and the link is still present.
5. Activate the delete control again, and this time confirm it (activate it a second time within
   ~3 seconds).
6. **Expect**: the link disappears from the active list immediately; a direct `GET /api/links`
   (or a database check) confirms its row no longer exists.
7. Repeat steps 1–6 on a graveyard card.
8. **Expect**: identical behavior — permanent removal, no trace left anywhere.
9. Arm a card's delete control and wait ~3 seconds without confirming or clicking elsewhere.
10. **Expect**: the card auto-reverts to normal on its own; the link is not deleted.
11. Call `DELETE /api/links/{id}` twice in a row for the same id (or once after already deleting it
    via the UI).
12. **Expect**: both calls return `204 No Content` — the second is a no-op, not an error (contracts
    §3).

### 4. Countdown urgency expressed through the card's design (User Story 4)

1. Save a link (large amount of time remaining) and, separately, insert (or wait for) a link close
   to its deadline.
2. **Expect**: the far-from-expiring card shows a calm color and a "fresh" leaf motif; the
   near-deadline card shows a visibly more intense/warm color and a "wilted" leaf motif.
3. Confirm the actual displayed remaining-time text and the moment the link disappears/transitions
   are unaffected — only the color/motif changed (spot-check against the existing 168h/30d boundary
   tests, which must still pass unmodified).

### 5. A little delight in empty states (User Story 5)

1. With zero active links, view the active list.
2. **Expect**: a small illustration accompanies the empty message (not plain text alone).
3. With zero graveyard links, view the graveyard.
4. **Expect**: a distinct small illustration accompanies the graveyard's own empty message.
5. Confirm no illustrations appear anywhere else in the interface (active list/graveyard with links
   present, capture form, nav tabs).

## Notes

- No new dependency was added on either side — title fetching uses the JDK's built-in
  `java.net.http.HttpClient`; favicons are a constructed URL against a public favicon service; the
  frontend redesign uses a custom DaisyUI theme plus inline SVGs.
- The existing 168-hour active-expiration and 30-day graveyard-permanent-deletion boundary tests
  (Features 1–2) must continue to pass completely unmodified — this feature changes presentation
  and adds manual deletion only; it does not touch timing logic (SC-006).
- There is still no way to rescue, resurrect, pin, or favorite a link — this feature does not
  reopen any of Feature 2's closed scope beyond adding the one new manual-delete capability
  described above.
