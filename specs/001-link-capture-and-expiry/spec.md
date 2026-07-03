# Feature Specification: Link Capture & Active List with Automatic Expiry

**Feature Branch**: `001-link-capture-and-expiry`

**Created**: 2026-07-02

**Status**: Draft

**Input**: User description: "Build the core capture-and-view experience for ShelfLife, a bookmark manager. The primary interaction: the user pastes a URL into a single input field and presses enter. The link is saved instantly, with zero additional steps (no folders, no tags, no categorization, no confirmation dialogs). The landing page shows a list of all currently active (unexpired) links. Each link entry displays enough of the URL to recognize it (title if easily available, otherwise the raw URL) and a visible countdown showing time remaining until it expires — measured as exactly 168 hours (7 days) from the moment it was saved. Order the list by soonest-to-expire first, so the links closest to disappearing are the most visible. When a link's 168-hour countdown reaches zero, it is automatically removed from the active list — no manual action or confirmation from the user is required. This is a fully automatic background process; the app must keep expiring links correctly even if it wasn't open at the exact expiration moment (e.g., checking on every page load or via a background schedule, not just live in the browser). There is a single user, no login or authentication required for this phase. The user should be able to see, at a glance, how many links are currently saved. Out of scope for this feature: what happens to a link after it expires (that's a separate, later feature), link previews/thumbnails/favicons, search or filtering, and manual 'mark as done' action."

## Clarifications

### Session 2026-07-02

- Q: Should the active list attempt to retrieve and display a page title for each link, or always show the raw URL? → A: Always show the raw URL as the label; title retrieval (and favicons/previews) is entirely out of scope for this feature and belongs to the later "Polish" feature.
- Q: Does expiration require an actual recurring scheduled job, or is read-time filtering sufficient for this feature? → A: Read-time filtering (computed at query/page-load time) is sufficient — no dedicated background job is required for this feature. "Removed from the active list" means excluded from the active-list query/view, not deleted from storage: the underlying link record MUST persist unchanged past its 168-hour mark so the later graveyard feature can use it.
- Q: Should the visible countdown tick live while the page is open, or only reflect the value as of the last load? → A: Something in between — the countdown re-renders on a periodic interval (every 60 seconds, since the window is measured in days) without requiring a manual reload. Each tick recomputes remaining time fresh from the server-provided expiration timestamp (`remaining = expiresAt - now()`) rather than locally decrementing a stored value, so there is no client-clock-drift risk.
- Q: Should a URL pasted without a scheme (e.g., "example.com") be rejected as invalid, or auto-normalized by prepending `https://`? → A: Auto-normalize by prepending `https://` and save it — matches the product's zero-friction, zero-configuration philosophy for a common, natural input pattern.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Capture a link instantly (Priority: P1)

A user has a URL they want to save for later. They paste it into a single input field on the page and press Enter. The link is saved immediately with no further steps — no dialogs, no categorization choices.

**Why this priority**: This is the single core action of the product. Without frictionless capture, nothing else in the app matters.

**Independent Test**: Paste a valid URL into the input field, press Enter, and verify the link is saved (persisted) and the input is ready to accept the next URL — without any additional prompts appearing.

**Acceptance Scenarios**:

1. **Given** the landing page is open and the input field is empty, **When** the user pastes a valid URL and presses Enter, **Then** the link is saved immediately and appears in the active links list without any confirmation step.
2. **Given** the input field is empty, **When** the user presses Enter without typing anything, **Then** no link is saved and no error is shown.
3. **Given** the user pastes text that is not a valid URL even after attempting to normalize a missing scheme, **When** they press Enter, **Then** the link is not saved and the user sees a clear, non-blocking indication that the input was not a valid URL.
4. **Given** a link was just saved, **When** the user immediately pastes and submits another URL, **Then** the second link is also saved as an independent entry.
5. **Given** the user pastes a URL missing a scheme (e.g., "example.com"), **When** they press Enter, **Then** the link is saved with `https://` prepended, with no error or extra step.

---

### User Story 2 - View active links ordered by urgency (Priority: P1)

A user opens the landing page to see everything they've saved that hasn't expired yet, with the links closest to expiring shown first so nothing catches them by surprise.

**Why this priority**: Capture without a way to see what's about to disappear defeats the purpose of a self-expiring list — this is equally core to the product's value as capture itself.

**Independent Test**: With several links saved at different times, load the landing page and verify all non-expired links are listed in order from soonest-to-expire to furthest-from-expiring, each showing an identifying label and remaining time.

**Acceptance Scenarios**:

1. **Given** multiple active links exist with different save times, **When** the user views the landing page, **Then** the links are ordered with the soonest-to-expire link first and the furthest-from-expiring link last.
2. **Given** an active link, **When** it is displayed in the list, **Then** its raw URL is shown as its label.
3. **Given** an active link, **When** it is displayed in the list, **Then** the time remaining until it expires (out of its total 168-hour lifespan) is visibly shown.
4. **Given** the landing page has been open for a while without a reload, **When** a periodic update tick occurs (approximately every 60 seconds), **Then** each link's displayed remaining time is refreshed to reflect the current time, computed from its expiration timestamp rather than a locally decremented value.
5. **Given** no links have been saved yet, **When** the user views the landing page, **Then** the list is empty and the page clearly conveys there is nothing saved yet, without an error state.

---

### User Story 3 - Links expire automatically and reliably (Priority: P2)

A link a user saved a week ago quietly disappears from the active list once its 168-hour lifespan is up — with no action required from the user, whether or not the app happened to be open at that exact moment.

**Why this priority**: This is what makes the list trustworthy as "things that matter right now" rather than an ever-growing pile; it depends on capture and viewing already working, so it follows them in priority.

**Independent Test**: Save a link, artificially advance its saved time to more than 168 hours in the past (or wait for a real link to cross the boundary), reload the page without any live session in between, and verify the link is no longer present in the active list and the displayed count has decreased accordingly.

**Acceptance Scenarios**:

1. **Given** an active link reaches exactly 168 hours since it was saved while the app is open, **When** the countdown reaches zero, **Then** the link is removed from the active list without requiring the user to confirm or dismiss it.
2. **Given** an active link's 168-hour lifespan elapsed while the application was closed, **When** the user next opens the landing page, **Then** that link is already absent from the active list.
3. **Given** a link is exactly at 167 hours 59 minutes since being saved, **When** the list is evaluated, **Then** the link still appears as active with remaining time shown.
4. **Given** a link is exactly at 168 hours 1 minute since being saved, **When** the list is evaluated, **Then** the link does not appear in the active list.

---

### User Story 4 - See saved-link count at a glance (Priority: P3)

A user glances at the landing page and immediately knows how many links are currently active, without counting list entries.

**Why this priority**: A helpful at-a-glance indicator, but the app is fully usable without it since the list itself already conveys this information.

**Independent Test**: With a known number of active links saved, load the landing page and verify the displayed count matches the number of active links shown in the list; save or let a link expire and verify the count updates accordingly.

**Acceptance Scenarios**:

1. **Given** a known number of active links exist, **When** the user views the landing page, **Then** a visible count matching that number is shown.
2. **Given** the count is currently displayed, **When** a new link is saved or an existing link expires, **Then** the displayed count reflects the change.

---

### Edge Cases

- What happens when the user submits the same URL more than once? Each submission is treated as an independent new entry with its own fresh 168-hour countdown (see Assumptions).
- What happens when the user pastes a URL with no scheme (e.g., "example.com" instead of "https://example.com")? It is normalized by prepending `https://` and saved, per FR-003.
- What happens when the user submits a URL whose host is a single label with no dot, such as "https://a" or "https://localhost"? It is rejected as invalid per FR-003, since this app is for saving real web content rather than local or intranet addresses, and the user sees the same non-blocking invalid-input indication as any other malformed URL.
- What happens when the application has not been opened for longer than 168 hours? All links whose lifespan elapsed during that time are excluded from the active list on next load (via read-time filtering) — none are shown as active even momentarily; their underlying records still exist, just outside the active view.
- What happens when there are zero active links? The landing page shows an empty state and a count of zero, not an error.
- What happens when a link's URL is very long? The label is displayed in a way that does not break the page layout (e.g., truncation), while remaining identifiable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a single input field where the user can enter a URL and submit it by pressing Enter.
- **FR-002**: System MUST save a submitted valid URL as a new link immediately upon submission, requiring no additional input, categorization, or confirmation step.
- **FR-003**: System MUST validate that submitted input is a well-formed URL before saving: it MUST have valid URI syntax with a recognizable scheme, and its host MUST resemble an actual public domain — specifically, containing at least one dot separating a label from a top-level-domain-like suffix (e.g., "a.com", "example.co.uk"). If the input is missing a scheme but is otherwise a well-formed URL (e.g., "example.com"), the system MUST normalize it by prepending `https://` before saving rather than rejecting it. Input that is not a well-formed URL even after this normalization attempt — including input whose host is a single label with no dot, such as "https://a" or "https://localhost" — MUST NOT be saved, and the user MUST receive a clear, non-blocking indication that the submission was rejected.
- **FR-004**: System MUST record the exact moment each link was saved.
- **FR-005**: System MUST treat each link as expiring exactly 168 hours (7 days) after the moment it was saved.
- **FR-006**: System MUST display, on the landing page, every link that has not yet reached its 168-hour expiration.
- **FR-007**: System MUST NOT display any link that has reached or passed its 168-hour expiration.
- **FR-008**: For each displayed link, the system MUST show its raw URL as the identifying label. Retrieving or displaying a page title is out of scope for this feature.
- **FR-009**: For each displayed link, the system MUST show the remaining time until expiration, recomputed from its expiration timestamp on a periodic interval (approximately every 60 seconds) while the page is open, without requiring a manual reload.
- **FR-010**: System MUST order the active links list by soonest-to-expire first.
- **FR-011**: System MUST exclude a link from the active list automatically once it expires, without requiring any manual action or confirmation from the user. This exclusion is enforced by filtering at read time (i.e., whenever the active list is queried or loaded) rather than requiring a dedicated background job for this feature.
- **FR-012**: System MUST correctly exclude expired links from the active list even when the application was not open at the moment a link's expiration occurred, since expiration is evaluated fresh on every read rather than depending on a live/open session.
- **FR-013**: System MUST display a count of currently active links on the landing page.
- **FR-014**: System MUST operate for a single user with no login or authentication required in this phase.
- **FR-015**: System MUST persist saved links so that active links and their original save times survive an application restart.
- **FR-016**: System MUST allow submitting the same URL multiple times, each as an independent link with its own save time and expiration.
- **FR-017**: System MUST NOT delete or modify a link's underlying record when it expires; the record MUST persist unchanged past its 168-hour mark, since a later feature will use expired records to populate a graveyard view.

*Explicitly out of scope for this feature*: what happens to a link's data after it expires beyond ceasing to appear in the active list (any promotion to a graveyard view, archival, or deletion is a separate future feature), page title/link previews/thumbnails/favicons, search or filtering of the list, and any manual "mark as done" or dismiss action.

### Key Entities

- **Link**: A single saved bookmark. Represents the URL the user saved, the moment it was saved, and the moment it will expire (always exactly 168 hours after saving). Its label for display is always its raw URL. The record persists unchanged after expiration; only its presence in the active list changes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can go from pasting a URL to seeing it appear in the active list in under 2 seconds.
- **SC-002**: A link never appears in the active list more than a few minutes after its true 168-hour expiration moment has passed, regardless of whether the application was open at that moment.
- **SC-003**: A user can identify, within 2 seconds of viewing the landing page, how many links are currently active and which one will expire soonest.
- **SC-004**: On 100% of landing page visits, the active list shown contains no links past their 168-hour expiration.
- **SC-005**: A first-time user can successfully save a link without instructions, using only the input field and Enter key.

## Assumptions

- Duplicate URLs are permitted; no deduplication is performed. Each submission creates a fully independent link with its own save time and 168-hour countdown.
- A "well-formed URL" follows standard URI syntax with a recognizable scheme, and its host must resemble an actual public domain: at least one dot separating a label from a top-level-domain-like suffix (e.g., "a.com", "example.co.uk"). Input missing a scheme (e.g., "example.com") is normalized by prepending `https://` before validation and saving; input that still isn't a recognizable URL after that (e.g., random text), or whose host is a single label with no dot (e.g., "https://a", "https://localhost"), is rejected — this app is for saving real web content, not local or intranet addresses.
- The count shown to the user reflects currently active (unexpired) links only, matching what the landing page list displays.
- There is no upper limit on the number of active links a user may have at once.
- "A few minutes" of allowed staleness in expiration enforcement (SC-002) is acceptable since this is a personal-scale, single-user tool; expiration does not need to be second-precise. In practice, staleness is bounded by how frequently the active list is read/loaded, since expiration is computed at read time rather than on a fixed schedule.
- Expired links remain in storage indefinitely (or until a future feature defines retention/cleanup); this feature does not need to manage their eventual disposal.
