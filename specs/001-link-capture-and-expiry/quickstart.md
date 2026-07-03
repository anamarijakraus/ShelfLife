# Quickstart: Link Capture & Active List with Automatic Expiry

Validates the feature end-to-end against the acceptance scenarios in
[spec.md](./spec.md). See [data-model.md](./data-model.md) for the `Link` entity and
[contracts/links-api.md](./contracts/links-api.md) for the API shape.

## Prerequisites

- Java 21, Maven, Node.js (for Vite/npm) installed locally.
- No external services required — H2 runs in file mode, no separate database server.

## Setup & run

```bash
# Backend (from backend/)
mvn spring-boot:run
# Starts the API on http://localhost:8080, creates/opens the H2 file database on first run.

# Frontend (from frontend/, in a second terminal)
npm install
npm run dev
# Starts the Vite dev server, typically on http://localhost:5173.
```

Open the frontend URL in a browser.

## Validation scenarios

### 1. Capture a link instantly (User Story 1)

1. Paste a valid URL (e.g., `https://developer.mozilla.org`) into the input field and press
   Enter.
2. **Expect**: the link appears in the list immediately, no dialog or extra step.
3. Repeat with a bare domain, e.g., `example.com`.
4. **Expect**: it is saved and displayed as `https://example.com` (scheme auto-prepended).
5. Press Enter with the field empty.
6. **Expect**: nothing is saved, no error shown.
7. Type non-URL text (e.g., `not a url at all!!`) and press Enter.
8. **Expect**: a clear, non-blocking validation message; nothing is saved.

### 2. View active links ordered by urgency (User Story 2)

1. Capture two or three links a few seconds apart.
2. **Expect**: the most recently captured link appears **last** (it has the most time
   remaining); the first-captured link appears **first** (soonest to expire).
3. **Expect**: each entry shows its raw URL as the label and a remaining-time countdown.
4. Leave the page open for a couple of minutes without reloading.
5. **Expect**: the displayed remaining time decreases on its own (no reload needed), updating
   roughly once a minute.
6. With no links saved (fresh database), load the page.
7. **Expect**: an empty state is shown, not an error, and the count reads zero.

### 3. Automatic, reliable expiration (User Story 3)

Because expiration is read-time filtering (no scheduled job), the fastest way to validate this
without waiting 7 days is directly against the database/API using a manually inserted row with a
past `expiresAt`:

1. Insert a `Link` row directly (e.g., via the H2 console or a test) with `savedAt` and
   `expiresAt` set so that `expiresAt` is a few seconds in the past.
2. Call `GET /api/links` (or reload the frontend).
3. **Expect**: that link is absent from the response/list.
4. Query the row directly in the database.
5. **Expect**: the row still exists, unchanged (`savedAt`/`expiresAt` untouched) — it was
   excluded from the view, not deleted (FR-017).
6. For the automated version of this check, see the boundary tests in
   `backend/src/test/java/.../link/LinkServiceTest.java` (167h59m included, exactly 168h00m and
   168h01m excluded).

### 4. See saved-link count at a glance (User Story 4)

1. With N active links captured, load the landing page.
2. **Expect**: a visible count reads exactly N.
3. Capture one more link.
4. **Expect**: the count updates to N+1 without a manual page reload (next poll tick, ≤ ~60s).

## Notes

- No login/authentication step exists in this feature — the app is immediately usable.
- No search, filter, tags, folders, previews, favicons, or manual dismiss action exist in this
  feature by design (see spec's explicit Out of Scope note).
