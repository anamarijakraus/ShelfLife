# Lifecycle & Deletion-Safety Checklist: Graveyard Page & Automatic Permanent Cleanup

**Purpose**: Validate the quality (completeness, clarity, consistency, measurability) of the
requirements governing the active→graveyard→deleted lifecycle and the permanent, irreversible
deletion guarantee, before proceeding to `/speckit-tasks` / `/speckit-implement`.
**Created**: 2026-07-03
**Feature**: [spec.md](../spec.md)

**Note**: This checklist tests the requirements as written in `spec.md` (with cross-references to
`plan.md`/`data-model.md` where the spec's own completeness depends on them) — it does NOT test
the implementation. All 18 items have been resolved: 3 by spec amendments (FR-015, FR-017, FR-018),
7 by confirming the requirement was already adequately specified on inspection, and 8 by explicitly
accepting an enterprise-grade concern as out of scope for this personal-scale, single-user tool.

## Requirement Completeness

- [x] CHK001 Are requirements defined for which read operations trigger the permanent-deletion sweep — only graveyard-list reads, or any link-related read? [Gap, Spec §FR-015] — **Resolved by spec amendment**: FR-015 now states the sweep is triggered specifically by graveyard reads, not active-list reads.
- [x] CHK002 Are requirements defined for what happens if the bulk permanent-deletion operation itself fails or is interrupted mid-execution? [Gap, Spec §FR-014] — **Accepted as-is for personal-scale scope**: partial-failure/transaction-recovery handling is an enterprise-grade concern not warranted for a single-user hobby project.
- [x] CHK003 Are the same minute-level boundary requirements that govern active-list exit (168h) explicitly cross-referenced as also governing graveyard entry, given this feature depends on that exact boundary? [Gap, Spec §Edge Cases] — **Resolved, already satisfied on inspection**: FR-016 explicitly inherits the active list's existing (already-tested) 168h boundary unchanged, so graveyard entry is governed by the same boundary by construction; no duplication needed.
- [x] CHK004 Is the display format/granularity of the remaining-time indicator for the 30-day graveyard countdown specified, beyond just its refresh cadence? [Gap, Spec §FR-005] — **Resolved by spec amendment**: new FR-017 specifies whole-day granularity beyond 1 day remaining, switching to hour-level in the final day, with no minute-level precision required.

## Requirement Clarity

- [x] CHK005 Is "a few minutes" in SC-002 quantified with a specific upper bound, or left as an implicit, unmeasurable tolerance? [Clarity/Measurability, Spec §SC-002] — **Accepted as-is for personal-scale scope**: precise SLA-style quantification is an enterprise-grade concern; matches the constitution's explicit "no fixed numeric SLAs" stance and the identical, already-accepted convention from Feature 1.
- [x] CHK006 Is the scope of "anywhere in the system" in FR-014 and User Story 3 explicitly bounded (e.g., to the persisted data store), or could it be read as also covering hypothetical caches/logs not part of this system? [Clarity, Spec §FR-014] — **Accepted as-is for personal-scale scope**: cache/log-boundary ambiguity is an enterprise-grade concern; this system has no caches or logs to disambiguate against.
- [x] CHK007 Is "lightweight" navigation (FR-008) defined with any concrete criteria, or left entirely to implementation judgment? [Clarity, Spec §FR-008] — **Resolved, already satisfied on inspection**: FR-008 already gives concrete, testable criteria (a tab-like control, clear indication of the current view); "lightweight" is descriptive framing consistent with the constitution's zero-clutter philosophy, not the operative requirement.

## Requirement Consistency

- [x] CHK008 Do FR-002's countdown-start definition and the Clarifications' pre-existing-link migration rule agree on using the original 168-hour expiration moment (not feature-launch time) as the deadline anchor? [Consistency, Spec §FR-002, §Clarifications] — **Resolved, already satisfied on inspection**: both use "original expiration moment + 30 days" with no divergence.
- [x] CHK009 Is the "opens in a new tab" behavior (currently stated only in Assumptions) also reflected as a testable functional requirement, or intentionally left as a non-binding assumption? [Consistency, Spec §Assumptions, §FR-009] — **Resolved by spec amendment**: promoted to new FR-018; the corresponding Assumptions bullet was removed to avoid duplicating a now-formal requirement.
- [x] CHK010 Do FR-010, FR-011, FR-012 (no rescue / no early delete / no clear-now), and FR-013 (no pinning/favoriting) collectively and unambiguously rule out every manual lifecycle-altering action, with no gap between them? [Consistency, Spec §FR-010-013] — **Resolved, already satisfied on inspection**: the four FRs jointly cover rescue, early removal, bulk/clear actions, and exemption mechanisms with no gap between them.

## Acceptance Criteria Quality

- [x] CHK011 Can "simultaneously becomes visible in the graveyard" (User Story 1, Acceptance Scenario 1) be objectively verified, given that active-list and graveyard-list reads are two separate API calls potentially made at slightly different instants? [Measurability, Spec §US1] — **Accepted as-is for personal-scale scope**: cross-request transaction-ordering guarantees are an enterprise-grade concern not warranted here.
- [x] CHK012 Are the graveyard boundary acceptance scenarios (29d23h59m / exactly 30d00h00m / 30d00h01m) precise enough to derive an automated test without further interpretation? [Acceptance Criteria Quality, Spec §US3] — **Resolved, already satisfied on inspection**: the same precision style as Feature 1's already-automated 168h boundary tests.

## Edge Case Coverage

- [x] CHK013 Is there a stated requirement (or explicit non-requirement) for consistency between the active-list and graveyard-list reads if a client fetches both in quick succession spanning a boundary crossing? [Edge Case, Gap] — **Accepted as-is for personal-scale scope**: cross-request consistency/race-condition guarantees are an enterprise-grade concern not warranted here.
- [x] CHK014 Does the spec define expected behavior if the permanent-deletion sweep and the subsequent display query could ever disagree on the current instant (e.g., a clock adjustment mid-request)? [Edge Case, Gap] — **Accepted as-is for personal-scale scope**: clock-adjustment races are an enterprise-grade concern not warranted here.

## Non-Functional Requirements (Deletion Safety & Performance)

- [x] CHK015 Are requirements defined for the durability/ordering of the permanent-deletion operation relative to the subsequent read — must the delete be fully applied before the read observes the data? [Gap, Non-Functional, Spec §FR-015] — **Accepted as-is for personal-scale scope**: transaction-ordering guarantees are an enterprise-grade concern not warranted here.
- [x] CHK016 Is there a stated requirement bounding how large the combined active+graveyard link volume may grow before the read-time delete-then-read pattern is expected to degrade? [Completeness, Non-Functional, Spec §Assumptions] — **Accepted as-is for personal-scale scope**: scale-bound guarantees beyond the existing "no upper limit, personal-scale" assumption are an enterprise-grade concern not warranted here.

## Dependencies & Assumptions

- [x] CHK017 Is the assumption that "no dedicated background scheduler" is required validated against the risk that a link could remain permanently un-deleted indefinitely if the app is never reopened after its deadline passes? [Assumption, Spec §Assumptions] — **Resolved, already satisfied on inspection**: this tradeoff is explicitly acknowledged and accepted in the spec's Clarifications session (2026-07-03, Q1), not an unexamined gap.
- [x] CHK018 Is the dependency on Feature 1's guarantee that expired rows persist untouched (which enables this feature's read-time promotion into the graveyard) explicitly cross-referenced rather than silently assumed? [Dependency, Spec §Assumptions] — **Resolved, already satisfied on inspection**: the Assumptions section already describes this dependency in substance (pre-existing expired links being treated as already in the graveyard necessarily relies on Feature 1 leaving those rows intact).

## Notes

- Check items off as completed: `[x]`
- Add comments or findings inline
- Link to relevant resources or documentation
- Items are numbered sequentially for easy reference
