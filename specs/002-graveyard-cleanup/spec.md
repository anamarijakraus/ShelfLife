# Feature Specification: Graveyard Page & Automatic Permanent Cleanup

**Feature Branch**: `002-graveyard-cleanup`

**Created**: 2026-07-03

**Status**: Draft

**Input**: User description: "Build the "graveyard page" and automatic cleanup mechanic for ShelfLife, extending the existing capture-and-list feature. Currently, when a link's 168-hour active countdown reaches zero, it simply disappears from the active list. Change that ending: instead of disappearing, the "expired" link moves into a new "graveyard" page, where it gets a fresh 30-day countdown of its own. The graveyard page lists these expired-but-not-yet-deleted links, following the same conventions as the active list — ordered soonest-to-be-permanently-deleted first, with a visible remaining-time indicator for each, and a count of how many links currently sit in the graveyard. When a graveyard link's 30-day countdown reaches zero, it is permanently and irreversibly deleted — its data no longer exists anywhere in the system. This entire lifecycle is fully automatic, with zero manual intervention at any stage: there is no way to rescue a link back to active status once it enters the graveyard, and no way to manually clear or delete a graveyard link early. The only two things that ever happen to a graveyard link are: its countdown runs out and it's gone, or it hasn't run out yet. This preserves the app's existing zero-maintenance philosophy. A graveyard link should still be clickable/openable — the 30 days represent a final chance to consume content that was let slip, even though it's no longer front-and-center. The app needs simple navigation between the active page and the graveyard page (e.g., a tab) — lightweight. Out of scope for this feature: any manual action on a link at any stage (rescuing, early removal, or "clear now" actions in either the active list or the graveyard), any concept of pinning/favoriting a link to exempt it from expiration entirely, and any change to how the active-list countdown or capture flow already work."

## Clarifications

### Session 2026-07-03

- Q: How should permanent deletion of a graveyard link's data be guaranteed once its 30-day countdown reaches zero? → A: Read-time evaluation only, matching Feature 1's precedent — no dedicated background process. This evaluation MUST perform an actual delete of overdue rows the next time relevant data is read (not merely filter them out of a query's result), so that "data no longer exists" is genuinely true once that read happens, rather than the row silently persisting forever unqueried.
- Q: For links that expired under Feature 1 before this feature ships (already past their 168-hour mark, sitting in storage), how should their graveyard deadline be computed? → A: Deadline = original expiration + 30 days, using the same deterministic formula for every link with no special-casing for pre-existing data — consistent with FR-002 and the constitution's simplicity principle. In practice this is a non-issue: Feature 1 shipped 2026-07-02, so no real link can be anywhere near 30 days old at this feature's launch.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Expired links land in the graveyard instead of vanishing (Priority: P1)

A link a user saved reaches the end of its 168-hour active life. Instead of disappearing without a trace, it is automatically carried into a "graveyard" of not-yet-deleted links, where it starts a fresh 30-day countdown of its own.

**Why this priority**: This is the core behavior change this feature introduces — without it, the graveyard page would have nothing to show, and the product's promise that links get a "second chance" before disappearing forever would be false.

**Independent Test**: Let (or simulate) a link cross its 168-hour active expiration, then verify it is no longer in the active list but is now present among graveyard links with a newly computed 30-day deadline.

**Acceptance Scenarios**:

1. **Given** an active link reaches exactly 168 hours since it was saved, **When** its active countdown reaches zero, **Then** it is removed from the active list and simultaneously becomes visible in the graveyard, with no manual step required.
2. **Given** a link has just entered the graveyard, **When** its remaining time is checked, **Then** it shows a fresh 30-day countdown measured from the moment it left the active list, not from when it was originally saved.
3. **Given** a link's active period elapsed while the application was closed, **When** the user next opens the app, **Then** that link is already present in the graveyard (not the active list), exactly as if the transition had been observed live.

---

### User Story 2 - View the graveyard, ordered by urgency (Priority: P1)

A user opens the graveyard page to see everything that has expired from the active list but hasn't been permanently deleted yet, with the links closest to permanent deletion shown first.

**Why this priority**: Without a way to see what's in the graveyard and how much time is left, the graveyard's "second chance" value is invisible to the user — this is as essential as the transition itself.

**Independent Test**: With several links at different points in their graveyard countdown, load the graveyard page and verify all of them are listed, soonest-to-be-deleted first, each with a label and remaining time.

**Acceptance Scenarios**:

1. **Given** multiple graveyard links with different remaining times, **When** the user views the graveyard page, **Then** the links are ordered with the soonest-to-be-permanently-deleted link first.
2. **Given** a graveyard link, **When** it is displayed, **Then** its raw URL is shown as its label, and a visible remaining-time indicator counts down to its permanent deletion.
3. **Given** the graveyard page has been open for a while without a reload, **When** a periodic update tick occurs, **Then** each link's displayed remaining time is refreshed to reflect the current time.
4. **Given** no links are currently in the graveyard, **When** the user views the graveyard page, **Then** the list is empty and the page clearly conveys there is nothing there, without an error state.

---

### User Story 3 - Graveyard links are permanently and irreversibly deleted (Priority: P2)

A link that has sat in the graveyard for 30 days quietly and permanently disappears — its data ceases to exist anywhere in the system — with no action required from the user, whether or not the app happened to be open at that moment.

**Why this priority**: This is what makes the graveyard trustworthy as a temporary reprieve rather than a second permanent list; it depends on links already being in the graveyard (User Story 1), so it follows it in priority.

**Independent Test**: Place a link in the graveyard with its 30-day deadline artificially in the past (or wait for a real one to cross the boundary), reload without any live session in between, and verify the link is absent from the graveyard and that no trace of its data remains.

**Acceptance Scenarios**:

1. **Given** a graveyard link reaches exactly 30 days since entering the graveyard, **When** its countdown reaches zero, **Then** its data is permanently deleted and it no longer appears anywhere in the system.
2. **Given** a graveyard link's 30-day period elapsed while the application was closed, **When** the user next opens the app, **Then** that link is already gone, with no trace left behind.
3. **Given** a link is exactly at 29 days 23 hours 59 minutes in the graveyard, **When** the graveyard is evaluated, **Then** it still appears with remaining time shown.
4. **Given** a link is exactly at 30 days and 1 minute in the graveyard, **When** the graveyard is evaluated, **Then** it no longer appears, and its data has been deleted.

---

### User Story 4 - Navigate between Active and Graveyard views (Priority: P2)

A user wants to check what's about to be permanently lost, or just glance back at the active list, without friction.

**Why this priority**: The graveyard is only useful if it's easy to reach; this is a lightweight enabler rather than core lifecycle logic, so it ranks below the transition and deletion behaviors.

**Independent Test**: From the active list view, use the provided navigation to reach the graveyard view and back, confirming both views render their respective, correct lists.

**Acceptance Scenarios**:

1. **Given** the user is viewing the active list, **When** they use the provided navigation (e.g., a tab), **Then** they see the graveyard page with its own list, count, and ordering.
2. **Given** the user is viewing the graveyard page, **When** they use the provided navigation, **Then** they return to the active list page.
3. **Given** either view is open, **When** the user looks at the navigation, **Then** it is clear which of the two views is currently active.

---

### User Story 5 - See graveyard count at a glance (Priority: P3)

A user glances at the graveyard page and immediately knows how many links are currently sitting there, without counting list entries.

**Why this priority**: A helpful at-a-glance indicator, mirroring the active list's count, but the graveyard page is fully usable without it since the list itself already conveys this information.

**Independent Test**: With a known number of graveyard links, load the graveyard page and verify the displayed count matches the number of links shown; let one be deleted or let another arrive and verify the count updates.

**Acceptance Scenarios**:

1. **Given** a known number of links are in the graveyard, **When** the user views the graveyard page, **Then** a visible count matching that number is shown.
2. **Given** the count is currently displayed, **When** a link is permanently deleted or a new link enters the graveyard, **Then** the displayed count reflects the change.

---

### User Story 6 - Open a graveyard link's destination (Priority: P3)

A user spots a graveyard link they still want to read and opens it directly from the graveyard page, since the graveyard represents a final chance to consume content that was let slip.

**Why this priority**: This makes the graveyard practically useful rather than a purely informational list, but the page still functions as a countdown display without it, so it's the lowest-priority piece.

**Independent Test**: From the graveyard page, activate a listed link and confirm its original destination opens, with no change to its remaining time or position in the list afterward.

**Acceptance Scenarios**:

1. **Given** a link is listed in the graveyard, **When** the user clicks/activates it, **Then** its original URL opens in a new browser tab, leaving the graveyard page open and unchanged underneath.
2. **Given** a graveyard link was just opened, **When** the graveyard list is viewed again, **Then** that link's remaining time and position are unchanged — opening it has no effect on its lifecycle.

---

### Edge Cases

- What happens exactly at the instant a link crosses from active into the graveyard? It is excluded from the active list and included in the graveyard list from that same evaluation moment onward — it is never shown in both, and never shown in neither.
- What happens exactly at the instant a graveyard link's 30-day countdown reaches zero? It is excluded from the graveyard from that moment onward, and its underlying data is deleted; it does not linger as a visible entry past that instant.
- What happens when the application has not been opened for longer than the full active + graveyard lifecycle (168 hours + 30 days) for a given link? On next load, that link is already fully deleted — it is never shown as active or in the graveyard, even momentarily.
- What happens when there are zero links in the graveyard? The graveyard page shows an empty state and a count of zero, not an error, matching the active list's empty-state convention.
- What happens when a graveyard link's URL is very long? The label is displayed in a way that does not break the page layout (e.g., truncation), while remaining identifiable, matching the active list's convention.
- What happens if a user clicks a graveyard link at the same moment its deletion is being evaluated? The click simply opens the URL; if deletion has already occurred, the entry is no longer present to click in the first place — there is no partial or inconsistent state.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST, instead of merely excluding an expired link from view, transition it automatically into a "graveyard" of not-yet-deleted links at the exact moment its 168-hour active period ends, with no manual action required.
- **FR-002**: System MUST assign each link a fresh 30-day countdown to permanent deletion, beginning at the exact moment it enters the graveyard (i.e., its original 168-hour active expiration moment), independent of its original save time.
- **FR-003**: System MUST display, on a dedicated graveyard page, every link that has left the active list but has not yet reached its 30-day graveyard deadline.
- **FR-004**: System MUST order the graveyard list by soonest-to-be-permanently-deleted first.
- **FR-005**: For each displayed graveyard link, the system MUST show a visible remaining-time indicator counting down to its permanent deletion, refreshed periodically (matching the active list's periodic-refresh convention) without requiring a manual reload.
- **FR-006**: For each displayed graveyard link, the system MUST show its raw URL as the identifying label, consistent with the active list's labeling convention.
- **FR-007**: System MUST display a count of links currently in the graveyard on the graveyard page.
- **FR-008**: System MUST provide a lightweight way to navigate between the active list view and the graveyard view (e.g., a tab), with no login or permission barrier, and MUST make clear which view is currently showing.
- **FR-009**: System MUST allow each graveyard link to be clicked/activated to open its original URL, and doing so MUST NOT change its remaining time, its position in the graveyard, or any other aspect of its lifecycle.
- **FR-010**: System MUST NOT provide any manual action to move a link out of the graveyard back to active status.
- **FR-011**: System MUST NOT provide any manual action to delete or clear a graveyard link before its 30-day countdown elapses.
- **FR-012**: System MUST NOT provide any manual "clear now," bulk-removal, or similar action affecting either the active list or the graveyard.
- **FR-013**: System MUST NOT introduce any concept of pinning, favoriting, or otherwise exempting a link from either the active-to-graveyard or graveyard-to-deleted transition.
- **FR-014**: System MUST permanently and irreversibly delete a link's underlying data once its 30-day graveyard countdown reaches zero, such that the data no longer exists anywhere in the system; this MUST be an actual removal of the underlying record, not merely an exclusion from query results.
- **FR-015**: System MUST enforce permanent deletion via read-time evaluation, triggered specifically whenever the graveyard is read (e.g., the graveyard page is loaded or its list is queried) — not by active-list reads, since the active list's own query already excludes any non-active link independent of graveyard cleanup timing. At that moment, any graveyard link whose 30-day deadline has already passed MUST be deleted from storage, not merely filtered out of the returned result — so the guarantee holds correctly even when the application was not open at the moment the deadline elapsed, with no dedicated background/scheduled process required.
- **FR-016**: System MUST leave the existing active-list countdown behavior, ordering, and capture flow unchanged; this feature only changes what happens once a link leaves the active list.
- **FR-017**: For each displayed graveyard link, the system MUST display the remaining-time indicator using whole-day granularity while more than 1 day remains (e.g., "12d"), switching to hour-level granularity once fewer than 24 hours remain (e.g., "18h"); minute-level precision is not required for the graveyard countdown, unlike the active list's final-hour display, since the graveyard represents a lower-urgency grace period rather than the core scarcity mechanic.
- **FR-018**: System MUST open a graveyard link's original destination in a new browser tab/window when activated (FR-009), rather than replacing the current app view, so the user does not lose their place in the graveyard list.

### Key Entities

- **Link**: The existing saved-bookmark entity now carries an implicit lifecycle stage derived from time — active (before its 168-hour mark), graveyard (from its 168-hour mark until 30 days later), or deleted (from that point on, at which point the record ceases to exist). Its identifying label (raw URL) and openability are consistent across the active and graveyard stages; only its presence in a given view, and its remaining-time reference point, change.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can find and open any link that disappeared from the active list within the last 30 days by visiting the graveyard page.
- **SC-002**: A link never appears anywhere in the system — active list, graveyard list, or otherwise — more than a few minutes after its true 30-day graveyard deadline has passed.
- **SC-003**: A user can identify, within 2 seconds of viewing the graveyard page, how many links are currently there and which one will be permanently deleted soonest.
- **SC-004**: A user can switch between the active and graveyard views in a single action.
- **SC-005**: On 100% of graveyard-page visits, the list shown contains only links past their active expiration and not yet past their graveyard deadline.
- **SC-006**: A user can open any graveyard link's original destination in a single action from the graveyard page, with no observable change to that link's countdown or position afterward.

## Assumptions

- The graveyard's 30-day countdown begins at the exact moment a link's 168-hour active countdown ends (graveyard deadline = original active-expiration moment + 30 days), not from when a user happens to first view it.
- "A few minutes" of staleness before the next graveyard read triggers actual deletion (per FR-015) is acceptable at this personal, single-user scale, mirroring the staleness tolerance already accepted for active-list expiration in the prior feature.
- The graveyard page reuses the same visual and interaction conventions (styling, empty state, periodic refresh cadence) as the active list, for a consistent feel between the two views; remaining-time display granularity is intentionally coarser for the graveyard, per FR-017.
- There is no upper limit on the number of links the graveyard may hold at once, mirroring the active list's assumption.
- No authentication/authorization changes are introduced; this remains a single-user, no-login application.
- Links already sitting past their 168-hour mark in storage from before this feature ships are treated as already in the graveyard, with their graveyard deadline computed the same way (original expiration + 30 days) the first time this feature evaluates them.
