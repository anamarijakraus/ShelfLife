# Feature Specification: Favorites Tab & Link Pinning

**Feature Branch**: `004-favorites-pinning`

**Created**: 2026-07-06

**Status**: Draft

**Input**: User description: "Add a favorites/pinning mechanic to ShelfLife, exempting selected links from the app's automatic expiration entirely, on top of the existing active list and graveyard. Every card, in both the active list and the graveyard, gets a small pin-icon control. Activating it moves that link immediately (no confirmation needed, since this is a fully reversible action) into a new "Favorites" section, removing it from whichever view it was previously in. A pinned link is permanently exempt from expiration and graveyard-deletion timing for as long as it remains pinned — it does not count down toward anything, and is not automatically moved or deleted on any schedule. Favorites is a third tab in the app's navigation, alongside Active and Graveyard, using the same tab-bar pattern and switching behavior already established. The Favorites view shows every currently pinned link using the same card design, palette, and title/favicon conventions as the other two views, but without a countdown or urgency indicator, since pinned links don't expire — replace that space with a simple visual indication that the link is pinned. Like the other two views, Favorites shows a count of how many links it currently holds, and has its own small illustrated empty state when it holds none, consistent with the existing empty-state pattern. A pinned link can be unpinned from the Favorites view via the same pin control (now shown in its "pinned" state). Unpinning returns the link to the active list with a fresh 168-hour countdown starting from the moment of unpinning — not its original save time, and not a graveyard countdown. The manual delete control already available on every card continues to work identically on pinned links, permanently removing them regardless of being pinned. Out of scope for this feature: any limit on the number of links that can be pinned, any priority or ordering concept among favorites beyond a simple list, any change to how the active list or graveyard already behave for links that are not pinned, and any change to the existing manual-delete mechanic itself beyond it also applying here."

## Clarifications

### Session 2026-07-06

- Q: Where should the Favorites tab sit relative to Active and Graveyard? → A: Last position, after Graveyard — tab order is Active, Graveyard, Favorites.
- Q: Should the Favorites empty state reuse one of the existing illustrations? → A: No — it gets its own distinct third illustration, matching the style and color palette of the Active and Graveyard illustrations, not a reused or repurposed one.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pin a link to exempt it from expiration (Priority: P1)

A user viewing either the active list or the graveyard sees a link they never want to lose to automatic expiration or deletion. They activate its pin control, and the link moves immediately into a new "Favorites" collection, with no confirmation step, disappearing from whichever view it was in.

**Why this priority**: This is the core capability the entire feature exists to deliver — without it, there is no way to exempt a link from the existing timing mechanics at all.

**Independent Test**: From the active list, activate a card's pin control and verify the link disappears from the active list and is exempt from further countdown. Repeat from the graveyard and verify identical behavior.

**Acceptance Scenarios**:

1. **Given** a link card in the active list, **When** the user activates its pin control, **Then** the link is immediately removed from the active list and becomes pinned, with no confirmation prompt shown.
2. **Given** a link card in the graveyard, **When** the user activates its pin control, **Then** the link is immediately removed from the graveyard and becomes pinned, with no confirmation prompt shown.
3. **Given** a link has just been pinned, **When** any amount of time subsequently passes, **Then** the link does not count down toward, and is not automatically moved or deleted as a result of, either the active-expiration or graveyard-deletion mechanic, for as long as it remains pinned.
4. **Given** a link is pinned, **When** the user looks at the active list and the graveyard, **Then** that link appears in neither.

---

### User Story 2 - View pinned links in the Favorites tab (Priority: P1)

A user opens a new "Favorites" tab, positioned alongside the existing Active and Graveyard tabs, and sees every link they've pinned, presented with the same card design, palette, and title/favicon treatment as the other views, but showing a simple pinned indicator instead of a countdown.

**Why this priority**: Pinning a link has no visible value unless the user can see and revisit what they've pinned; this is as essential as the pinning action itself and ships alongside it.

**Independent Test**: Pin several links from the active list and graveyard, then open the Favorites tab and verify all of them appear as cards with the established design conventions, a pinned indicator in place of a countdown, and an accurate count. Empty Favorites entirely and verify its illustrated empty state.

**Acceptance Scenarios**:

1. **Given** the app's navigation, **When** the user views it, **Then** a "Favorites" tab is present after the "Active" and "Graveyard" tabs, in that order (Active, Graveyard, Favorites), using the same tab-bar pattern and switching behavior as the existing two.
2. **Given** one or more links are pinned, **When** the user views the Favorites tab, **Then** every pinned link is shown as a card using the same card design, color palette, and title/favicon conventions as the active list and graveyard.
3. **Given** a pinned link's card is displayed in Favorites, **When** the user looks at it, **Then** no countdown or urgency indicator is shown, and a simple, clear visual indication that the link is pinned occupies that space instead.
4. **Given** a known number of links are pinned, **When** the user views the Favorites tab, **Then** a visible count matching that number is shown, and the count updates as links are pinned, unpinned, or deleted.
5. **Given** no links are currently pinned, **When** the user views the Favorites tab, **Then** a small illustrated empty state is shown, using its own distinct illustration (not reused from the active list or graveyard) that matches their style and color palette, consistent with the established empty-state pattern.

---

### User Story 3 - Unpin a link back to the active list (Priority: P2)

A user viewing the Favorites tab decides a pinned link no longer needs to be exempt from expiration. They activate its pin control, now shown in its "pinned" state, and the link returns to the active list with a brand-new 168-hour countdown starting from that moment.

**Why this priority**: Unpinning is what makes pinning a genuinely reversible, low-commitment action rather than a one-way move; it depends on links already being pinned (User Story 1/2) and follows them in priority.

**Independent Test**: Pin a link, wait, then unpin it from the Favorites tab, and verify it appears in the active list with a fresh 168-hour countdown measured from the moment of unpinning, not from its original save time.

**Acceptance Scenarios**:

1. **Given** a pinned link's card in the Favorites tab, **When** the user activates its pin control, **Then** the link is immediately removed from Favorites and returned to the active list, with no confirmation prompt shown.
2. **Given** a link was just unpinned, **When** its remaining time is checked, **Then** it shows a fresh 168-hour countdown measured from the exact moment of unpinning, not from its original save time and not a graveyard-style countdown.
3. **Given** a link is unpinned, **When** the user views the Favorites tab, **Then** that link no longer appears there, and the displayed count decreases accordingly.
4. **Given** a link had already been pinned for longer than 168 hours, **When** it is unpinned, **Then** it still receives a full, fresh 168-hour countdown rather than being treated as already expired.

---

### User Story 4 - Permanently delete a pinned link (Priority: P3)

A user viewing the Favorites tab decides they no longer want a specific pinned link at all, and permanently removes it using the same delete control already available on every other card, with the same confirmation step.

**Why this priority**: This is a straightforward extension of an already-existing capability to a new view, rather than new behavior in its own right, so it ranks below the pinning, viewing, and unpinning capabilities that define this feature.

**Independent Test**: From the Favorites tab, trigger the delete control on a pinned card, confirm it, and verify the link disappears from Favorites and is not present anywhere else in the system.

**Acceptance Scenarios**:

1. **Given** a pinned link's card in the Favorites tab, **When** the user activates its delete control, **Then** the card enters the same lightweight confirmation state used on every other card.
2. **Given** a pinned card in its confirmation state, **When** the user confirms, **Then** the link's data is permanently removed and it no longer appears in Favorites, the active list, the graveyard, or anywhere else.
3. **Given** a pinned card in its confirmation state, **When** the user clicks/taps elsewhere or the confirmation window elapses without a second activation, **Then** the card returns to its normal pinned appearance and the link is not deleted.
4. **Given** a pinned link has just been permanently deleted, **When** the user looks at the remaining Favorites list, **Then** every other pinned link's status and the displayed count are unaffected.

---

### Edge Cases

- What happens if a link is pinned at the exact instant it would have crossed from active to graveyard, or from graveyard to permanent deletion? The pin takes effect immediately and the link is exempted going forward — it never completes that transition.
- What happens if a user attempts to pin a graveyard link that has already been permanently deleted (e.g., a stale, un-refreshed view)? The action is a no-op — the system does not error, and the outcome (link absent) is the same as if the pin had succeeded on an already-gone link.
- What happens if a user rapidly toggles a link's pin control multiple times? The link ends up in whichever state (pinned or not) the last activation set, with no duplicate entries in either location.
- What happens to a link's title, favicon, and identifying details while it is pinned? They are preserved unchanged and displayed exactly as they would be in the active list or graveyard.
- What happens when the last pinned link is unpinned or deleted? The Favorites tab immediately shows its illustrated empty state and a count of zero.
- What happens if a user tries to delete a pinned link that has already been removed (e.g., a second, stale browser tab)? The action is a no-op, consistent with the existing delete behavior on other views.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a pin control on every link card, in both the active list and the graveyard.
- **FR-002**: System MUST, upon activation of a link's pin control from the active list or the graveyard, immediately move that link into a new Favorites collection, with no confirmation step, and remove it from whichever view it was previously shown in.
- **FR-003**: System MUST permanently exempt a pinned link from the active-list expiration mechanic and the graveyard-deletion mechanic for as long as it remains pinned — a pinned link MUST NOT count down toward, and MUST NOT be automatically moved or deleted as a result of, either mechanic.
- **FR-004**: System MUST provide a third navigation tab, "Favorites," positioned after the existing "Active" and "Graveyard" tabs (tab order: Active, Graveyard, Favorites), using the same tab-bar pattern, switching behavior, and current-view indication already established.
- **FR-005**: System MUST display, on the Favorites tab, every currently pinned link using the same card design, color palette, and title/favicon conventions used by the active list and graveyard.
- **FR-006**: System MUST NOT display a countdown or urgency indicator on a pinned link's card, and MUST instead display, in that same space, a simple, clear visual indication that the link is pinned.
- **FR-007**: System MUST display, on the Favorites tab, a count of how many links currently reside there, mirroring the count convention already used by the active list and graveyard.
- **FR-008**: System MUST display a small illustrated empty state on the Favorites tab when it holds no pinned links, using its own distinct illustration — not reused or repurposed from the active list's or graveyard's empty-state illustrations — that matches their hand-drawn style and color palette, consistent with the existing empty-state pattern.
- **FR-009**: System MUST provide the same pin control on every card within the Favorites tab, shown in a distinct "pinned" visual state.
- **FR-010**: System MUST, upon activation of a pinned link's pin control from the Favorites tab, immediately move that link out of Favorites and into the active list, with no confirmation step.
- **FR-011**: System MUST assign an unpinned link a fresh 168-hour active countdown beginning at the exact moment it is unpinned, independent of its original save time and independent of any time it spent pinned.
- **FR-012**: System MUST provide the existing manual delete control, with its existing confirmation behavior, identically on every card in the Favorites tab, and MUST permanently remove a pinned link's data upon confirmation, exactly as it does for active-list and graveyard links.
- **FR-013**: System MUST NOT impose any limit on the number of links that may be pinned at once.
- **FR-014**: System MUST NOT introduce any priority or ordering concept among favorites beyond presenting them as a simple list.
- **FR-015**: System MUST NOT change any existing active-list or graveyard behavior, timing, ordering, or count for links that are not pinned.
- **FR-016**: System MUST NOT change the existing manual-delete control's underlying behavior or confirmation mechanic, beyond making it available on Favorites cards as described in FR-012.

### Key Entities

- **Link**: The existing saved-bookmark entity, now additionally able to carry a pinned state. While pinned, the link's active-expiration and graveyard-deletion computations do not apply — it is excluded from both timing mechanics entirely. Unpinning clears the pinned state and establishes a brand-new active-countdown reference point (the moment of unpinning), which replaces any prior countdown reference; it does not restore or resume the link's original save-time-based countdown or any graveyard countdown.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can pin a link from either the active list or the graveyard, and see it appear in Favorites, in a single action with zero confirmation steps.
- **SC-002**: A pinned link never disappears, moves, or is deleted automatically, no matter how long it remains pinned.
- **SC-003**: A user can identify, within 2 seconds of viewing the Favorites tab, how many links are currently pinned.
- **SC-004**: A user can unpin a link in a single action and see it reappear in the active list with a full, fresh 168-hour countdown.
- **SC-005**: 100% of Favorites cards render with the established card design, palette, and title/favicon conventions, showing a pinned indicator in place of a countdown.
- **SC-006**: Every existing active-list and graveyard timing behavior for links that are not pinned continues to hold exactly as before this feature, with zero regressions.

## Assumptions

- The pin control is a small icon-based toggle placed consistently on every card across all three views (active list, graveyard, Favorites), matching the placement and interaction conventions already established for other card-level controls (e.g., delete).
- The Favorites empty-state illustration is a new, third hand-drawn-style doodle — distinct in subject from the active-list and graveyard illustrations, but consistent with them in linework style and the app's established color palette.
- The "simple visual indication that the link is pinned" is a small static icon or label occupying the space the countdown/urgency indicator would otherwise use — no motion, animation, or additional interactivity is required.
- Since any priority or ordering concept among favorites is explicitly out of scope, the Favorites list is presented in a single, consistent, deterministic order (e.g., most-recently-pinned first) with no user-facing sorting or reordering controls.
- Pinning a graveyard link whose underlying data has already been permanently deleted (per the graveyard's existing read-time deletion evaluation) before the pin action completes is treated as a no-op, consistent with the existing no-op precedent for acting on already-removed links.
- No authentication/authorization changes are introduced; this remains a single-user, no-login application.
- This feature does not introduce any new user-facing settings, toggles, or configuration, consistent with the product's zero-configuration philosophy.
