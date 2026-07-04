# Feature Specification: Warm Visual Redesign & Card-Level Link Actions

**Feature Branch**: `003-visual-redesign-link-actions`

**Created**: 2026-07-04

**Status**: Draft

**Input**: User description: "Give ShelfLife a complete visual redesign and add two new card-level capabilities, without changing any of the existing expiration, graveyard, or countdown timing logic — this feature is purely additive on top of what already works. Visual identity: adopt a warm, earthy color palette built from these tones — #DDCBB7 (warm sand), #7B4B36 (rich brown), #264025 (deep forest green), #82896E (muted olive), #AD6B4B (terracotta) — used thoughtfully across backgrounds, text, accents, and interactive states, rather than applied uniformly and flatly. The overall feel should be warm, calm, and inviting, like a well-worn field journal or a cozy reading nook, not sterile or corporate. Every link should be presented as a proper card: soft, rounded corners, comfortable internal spacing, and a subtle shadow that gives it a gentle sense of depth without looking heavy. Typography should be clean and warm, comfortable to read, with clear visual hierarchy between a link's title, its URL, and its countdown. Each card should show the linked page's actual title as its primary heading (falling back to the raw URL when a title can't be retrieved, as originally planned), plus the site's favicon as a small visual identifier next to it — so at a glance, a user recognizes their saved links the way they'd recognize icons on a phone's home screen, not by parsing raw URLs. The countdown indicator on each card should feel like a natural part of the card's design, not a bolted-on timestamp, and its visual treatment (e.g., color intensity) may shift subtly as a link nears its deadline, reinforcing urgency through the design itself rather than through text alone. Add a small, understated way to permanently delete a single link directly from its card, available identically on both the active list and the graveyard, removing that link's data entirely regardless of which stage it's currently in. Because deletion is irreversible, require one small, lightweight confirmation step before it's carried out — not a heavy modal dialog, just enough friction to prevent an accidental tap from destroying something the user meant to keep. Bring a touch of personality through simple, hand-drawn-style doodle illustrations, used sparingly and purposefully — for example, in the empty states for the active list and graveyard (replacing today's plain 'nothing here yet' text with a small illustrated moment) — rather than scattered decoratively throughout the interface. The goal is charm without clutter: the app should feel a little delightful to open, not busy or noisy. Out of scope for this feature: any concept of favoriting, pinning, or otherwise exempting a link from expiration (that's a separate, later feature), any change to the active-list or graveyard countdown/expiration logic, and any change to the navigation structure between the two views."

## Clarifications

### Session 2026-07-04

- Q: Should the countdown urgency cue rely on color intensity alone, or must it also include a non-color signal? → A: Color intensity must be paired with a small non-color cue at the same urgency thresholds — specifically a nature-themed motif consistent with the app's visual identity (e.g., a small leaf icon that visually shifts from fresh to wilted as a link ages), not a generic icon, so the accessibility signal reinforces the doodle/charm personality rather than feeling bolted on.
- Q: What specifically dismisses an armed (unconfirmed) delete confirmation state back to normal, without deleting the link? → A: Both a short auto-timeout (~3 seconds) and clicking/tapping elsewhere cancel the armed state — whichever happens first.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A warm, card-based redesign across both views (Priority: P1)

A user opens ShelfLife and, instead of a plain list of rows, sees every saved link presented as a soft, rounded card with comfortable spacing and a gentle shadow, rendered in a warm, earthy color palette that feels calm and inviting rather than sterile. This treatment is identical in spirit on both the active list and the graveyard, so switching between them never feels like entering a different app.

**Why this priority**: This is the foundation every other change in this feature sits on top of — the card shell, palette, and typography hierarchy must exist before title/favicon display, countdown styling, delete controls, or empty-state illustrations can be placed within it.

**Independent Test**: Load the active list and the graveyard with several links present in each, and verify both render link entries as rounded, shadowed cards using the warm palette, with a clear typographic hierarchy (title most prominent, URL secondary, countdown distinct), and that the visual language matches between the two views.

**Acceptance Scenarios**:

1. **Given** the active list has one or more links, **When** the user views the page, **Then** each link is presented as a card with rounded corners, internal padding, and a subtle shadow, rather than a plain unstyled row.
2. **Given** the graveyard has one or more links, **When** the user views the page, **Then** each link is presented using the same card treatment, palette, and typography conventions as the active list.
3. **Given** a card is displayed, **When** the user looks at it, **Then** the title (or fallback label), the URL, and the countdown are visually distinguishable from one another through clear hierarchy (e.g., size, weight, or color), not presented as undifferentiated text.
4. **Given** the redesigned interface, **When** the user views any screen in scope for this feature, **Then** the warm palette (sand, brown, forest green, olive, terracotta) is applied thoughtfully across backgrounds, text, accents, and interactive states — not as one flat, uniform color block.

---

### User Story 2 - Recognize a saved link at a glance via title and favicon (Priority: P1)

A user scans their active list or graveyard and recognizes each saved link the way they'd recognize an app icon on a phone's home screen — by its page title and site favicon — instead of having to parse a raw URL string.

**Why this priority**: This directly fulfills the "at a glance recognition" goal called out in the request and was explicitly deferred from the original capture feature ("title retrieval... belongs to the later Polish feature"); it's a primary, user-facing capability of this feature, on par with the visual shell itself.

**Independent Test**: Save a link to a page with a retrievable title and favicon, and a link to a page where one or both cannot be retrieved; verify the first card shows the real title and favicon, and the second gracefully falls back to the raw URL and/or a generic icon without breaking the layout.

**Acceptance Scenarios**:

1. **Given** a link whose destination page title can be retrieved, **When** its card is displayed, **Then** the retrieved title is shown as the card's primary heading.
2. **Given** a link whose destination page title cannot be retrieved (e.g., unreachable, times out, no title present), **When** its card is displayed, **Then** the raw URL is shown as the primary heading instead, exactly as it was before this feature.
3. **Given** a link whose site favicon can be retrieved, **When** its card is displayed, **Then** the favicon appears as a small visual identifier alongside the title.
4. **Given** a link whose site favicon cannot be retrieved, **When** its card is displayed, **Then** a neutral, generic fallback icon is shown in its place, and the card layout remains intact.
5. **Given** a link that was saved before this feature existed, **When** its card is displayed after this feature ships, **Then** it also shows a retrieved title and favicon where available, following the same fallback rules as newly saved links.
6. **Given** the user submits a new URL, **When** the link is saved, **Then** saving completes instantly as before — title/favicon retrieval happens without delaying or blocking the save action.

---

### User Story 3 - Permanently delete a single link from its card (Priority: P2)

A user decides they no longer want a specific saved link — whether it's still active or already sitting in the graveyard — and removes it for good directly from its card, with one small extra tap to guard against an accidental removal.

**Why this priority**: This is a genuinely new capability (not previously possible at all) and is explicitly called out as a required addition, but it is secondary to being able to see and recognize links well in the first place, so it follows the redesign and identification stories.

**Independent Test**: From the active list, trigger the delete control on a card, confirm it, and verify the link disappears from the active list and is not present anywhere else in the system. Repeat from the graveyard and verify identical behavior.

**Acceptance Scenarios**:

1. **Given** a link card in the active list, **When** the user activates its delete control, **Then** the card enters a lightweight confirmation state rather than deleting immediately.
2. **Given** a card in its confirmation state, **When** the user confirms (e.g., activates the control a second time), **Then** the link's data is permanently removed and it no longer appears in the active list, the graveyard, or anywhere else.
3. **Given** a card in its confirmation state, **When** the user clicks/taps elsewhere, or approximately 3 seconds pass without a second activation, **Then** the card returns to its normal state and the link is not deleted.
4. **Given** a link card in the graveyard, **When** the user activates and confirms its delete control, **Then** the link is permanently removed with the same behavior as deleting from the active list.
5. **Given** a link has just been permanently deleted, **When** the user looks at the remaining list, **Then** every other link's countdown, order, and count are unaffected by the deletion.
6. **Given** a link is permanently deleted, **When** any part of the system is later queried, **Then** no trace of that link's data remains.

---

### User Story 4 - Countdown urgency expressed through the card's own design (Priority: P2)

A user glances at a card and senses how urgent a link's remaining time is from the countdown's own visual treatment — its color growing more intense as the deadline nears, paired with a small nature-themed motif (e.g., a leaf icon shifting from fresh to wilted) so the urgency reads clearly even without relying on color perception — without needing to read or calculate the number themselves.

**Why this priority**: This reinforces the redesign's goal of making urgency felt through design rather than text, but it is a refinement of the countdown's presentation, not a new fact being shown, so it ranks below the core recognition and deletion capabilities.

**Independent Test**: View cards at varying points in their countdown (e.g., far from expiring, close to expiring) and verify both the countdown's color intensity and its accompanying leaf motif are visibly different between them, while the underlying displayed time value is unaffected.

**Acceptance Scenarios**:

1. **Given** a link with a large amount of time remaining, **When** its card is displayed, **Then** the countdown is rendered as an integrated part of the card design (not a detached timestamp) using a calm, low-urgency color and a "fresh" leaf motif.
2. **Given** a link nearing its deadline, **When** its card is displayed, **Then** both the countdown's color intensity and its leaf motif have visibly shifted toward a higher-urgency appearance (the leaf appearing more "wilted") compared to a link with much more time left.
3. **Given** the countdown's visual urgency has shifted, **When** the underlying remaining time is checked, **Then** the actual countdown value and the moment it reaches zero are exactly as they were before this feature — only the presentation changed.
4. **Given** two cards at different points in their countdown are viewed without relying on color perception (e.g., in grayscale), **When** the user compares their leaf motifs, **Then** the relative urgency between the two links is still distinguishable from the leaf motif alone.

---

### User Story 5 - A little delight in empty states (Priority: P3)

A user with no active links, or an empty graveyard, sees a small, simple hand-drawn-style illustration accompanying the empty message instead of plain text alone, making the moment feel considered rather than blank.

**Why this priority**: This is a charm-focused polish detail explicitly requested, but the app is fully functional and usable without it, making it the lowest-priority piece of this feature.

**Independent Test**: Empty the active list and view it, then empty the graveyard and view it; verify each shows a small illustrated moment in place of (or alongside) today's plain empty-state text.

**Acceptance Scenarios**:

1. **Given** there are no active links, **When** the user views the active list, **Then** the empty state includes a small, simple illustration rather than plain text alone.
2. **Given** there are no links in the graveyard, **When** the user views the graveyard, **Then** the empty state includes its own small, simple illustration rather than plain text alone.
3. **Given** the redesigned interface as a whole, **When** the user browses the active list and graveyard, **Then** illustrations appear only in these empty states and are not scattered decoratively elsewhere in the interface.

---

### Edge Cases

- What happens when a page's title or favicon takes a long time to respond, or never responds? The card shows the raw-URL fallback (and/or generic icon fallback) and is not left in a loading or broken state; retrieval may complete later and update the card without a manual reload being the only way to see it.
- What happens when a retrieved title is extremely long? It is displayed in a way that does not break the card's layout (e.g., truncation), consistent with how long raw URLs were already handled.
- What happens if a user activates a card's delete control but never confirms it? The link is not deleted; the card returns to its normal appearance immediately if the user clicks/taps elsewhere, or automatically after approximately 3 seconds pass with no second activation, whichever happens first.
- What happens if a link is deleted from the graveyard at the same moment its 30-day automatic-deletion deadline would have passed anyway? The link ends up deleted either way; no error or duplicate-deletion condition is surfaced to the user.
- What happens when a user tries to delete a link that has already been removed (e.g., a second, stale browser tab)? The action is a no-op — the system does not error, and the outcome (link absent) is the same as if the delete had succeeded.
- What happens visually as a link crosses from "plenty of time left" to "about to expire"? The countdown's visual urgency shifts gradually/step-wise rather than jarringly snapping between two disconnected looks.
- What happens on a card whose favicon and title are both unavailable? The card still renders cleanly using the raw-URL heading and generic fallback icon, with no visual gap or broken layout.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST present every link, in both the active list and the graveyard, as a card with rounded corners, comfortable internal spacing, and a subtle shadow.
- **FR-002**: System MUST apply a warm, earthy color palette (drawing from warm sand, rich brown, deep forest green, muted olive, and terracotta tones) thoughtfully across backgrounds, text, accents, and interactive states, rather than as one flat uniform color.
- **FR-003**: System MUST establish clear visual hierarchy on each card distinguishing the title (most prominent), the URL (secondary), and the countdown (visually distinct treatment), using clean, comfortable-to-read typography.
- **FR-004**: System MUST apply the same card treatment, palette, and typography conventions consistently between the active list and the graveyard.
- **FR-005**: System MUST display a link's retrieved page title as its card's primary heading when a title can be retrieved.
- **FR-006**: System MUST display a link's raw URL as its card's primary heading when a title cannot be retrieved, matching the original fallback behavior.
- **FR-007**: System MUST display the destination site's favicon as a small visual identifier alongside the title/heading when the favicon can be retrieved.
- **FR-008**: System MUST display a neutral, generic fallback icon in place of the favicon when the site's favicon cannot be retrieved, without disrupting the card's layout.
- **FR-009**: System MUST retrieve title and favicon information asynchronously, without delaying or blocking the instant-save capture flow already established.
- **FR-010**: System MUST attempt title/favicon retrieval for links saved before this feature shipped, not only for newly captured links, applying the same fallback rules to both.
- **FR-011**: System MUST render each card's countdown indicator as a visually integrated part of the card's design, not as a separate or detached timestamp element.
- **FR-012**: System MUST shift the countdown's visual treatment as a link's remaining time decreases, so urgency is perceivable from the design itself, without changing the underlying computed remaining-time value, ordering, or expiration/deletion moment.
- **FR-012a**: System MUST pair the countdown's color-intensity shift with a small non-color visual cue at the same urgency thresholds — specifically a nature-themed motif consistent with the app's illustrated identity (e.g., a small leaf icon that visually shifts from fresh to wilted as remaining time decreases) — so urgency remains perceivable without relying on color perception alone.
- **FR-013**: System MUST provide a small, understated control on every card, in both the active list and the graveyard, to permanently delete that specific link.
- **FR-014**: System MUST require exactly one lightweight confirmation step after the delete control is first activated before the deletion is carried out; a single activation MUST NOT by itself delete the link.
- **FR-015**: System MUST dismiss the armed confirmation state back to the card's normal appearance, without deleting the link, when either the user clicks/taps elsewhere or approximately 3 seconds elapse without a second (confirming) activation — whichever happens first.
- **FR-016**: System MUST, upon confirmed deletion, permanently remove that link's underlying data such that it no longer exists anywhere in the system, regardless of whether it was in the active list or the graveyard at the time.
- **FR-017**: System MUST NOT provide any way to undo or restore a link once its deletion has been confirmed.
- **FR-018**: System MUST NOT change any other link's countdown value, ordering, or count as a result of deleting a different link.
- **FR-019**: System MUST display a small, simple illustrated empty state on the active list when there are no active links, and a distinct small illustrated empty state on the graveyard when it contains no links, replacing today's plain text-only empty message.
- **FR-020**: System MUST limit illustrated content to the active-list and graveyard empty states in scope for this feature, and MUST NOT introduce decorative illustrations elsewhere in the interface.
- **FR-021**: System MUST NOT introduce any concept of favoriting, pinning, or otherwise exempting a link from its existing expiration or graveyard-deletion timing.
- **FR-022**: System MUST NOT alter the existing active-list expiration logic, the active-to-graveyard transition logic, the graveyard-to-deleted timing logic, or any existing countdown computation — this feature only changes presentation and adds the manual per-link deletion capability described above.
- **FR-023**: System MUST NOT alter the existing navigation structure between the active list and the graveyard.

### Key Entities

- **Link**: The existing saved-bookmark entity, now additionally associated with a retrieved page title and a retrieved favicon (both optional/best-effort, with defined fallbacks). These are presentation-supporting attributes only; they do not participate in, and do not alter, the link's existing save time, active-expiration moment, or graveyard-deletion moment. A link may also now be permanently removed on demand via manual deletion, in addition to the existing automatic graveyard-deletion path — both paths result in the same end state (data no longer exists).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can recognize a previously saved link from its card (via title and/or favicon) without reading its full raw URL, for links where that metadata is available.
- **SC-002**: 100% of link cards, across both the active list and the graveyard, render with the card treatment (rounded corners, spacing, shadow) and a title-or-fallback heading, regardless of whether title/favicon retrieval succeeded for that link.
- **SC-003**: A user can permanently delete a single link, from either view, in exactly two intentional interactions (arm, then confirm); zero single-tap interactions result in permanent deletion.
- **SC-004**: A user can visually tell a link that is close to its deadline apart from one that has plenty of time left, using the countdown's visual treatment alone — including the leaf motif viewed without relying on color perception — without reading the numeric value.
- **SC-005**: 100% of empty-state views (empty active list, empty graveyard) display a small illustration in place of plain-text-only messaging.
- **SC-006**: Every existing active-list and graveyard timing acceptance scenario (expiration at 168 hours, graveyard transition, 30-day permanent deletion, ordering, counts) continues to hold exactly as before this feature, with zero regressions introduced by the redesign or new capabilities.

## Assumptions

- Title and favicon retrieval is best-effort and asynchronous: when a title or favicon cannot be retrieved (unreachable page, timeout, missing data), the card falls back to the raw URL as its heading and a neutral generic icon in place of the favicon, matching the fallback behavior already anticipated by the original capture feature.
- Title/favicon retrieval applies opportunistically to links saved before this feature ships, not only to newly captured links, since this is a card-level presentation capability rather than a change to the save flow itself.
- The delete control's "lightweight confirmation step" is implemented as an inline, on-card interaction: a first activation arms a confirm state, a second activation within ~3 seconds carries out the deletion, and clicking/tapping elsewhere or letting the ~3 second window elapse cancels the armed state — rather than a separate modal dialog, page, or navigation step.
- The countdown's shifting visual urgency (e.g., color intensity) is a presentation-only change layered on top of the existing, unmodified remaining-time computation already used by the active list and graveyard; no new time thresholds or calculations are introduced by this feature.
- Doodle illustrations are simple, static, hand-drawn-style line art with no animation or interactivity required.
- No authentication/authorization changes are introduced; this remains a single-user, no-login application.
- This feature does not introduce any new user-facing settings, toggles, or configuration — the redesign and new capabilities apply uniformly, consistent with the product's zero-configuration philosophy.
