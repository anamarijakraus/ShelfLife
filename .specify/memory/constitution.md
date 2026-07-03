<!--
Sync Impact Report
==================
Version change: 1.0.0 → 1.0.1 (PATCH: wording clarification, no normative change)
Modified principles:
  - II. Testing Standards — replaced "the scheduled expiration job" with
    "the expiration/lifecycle enforcement logic (whether implemented as a
    scheduled job or read-time evaluation)" to reflect that Feature 1
    (001-link-capture-and-expiry) uses read-time filtering rather than a
    scheduled job, and future features may use either mechanism. The
    underlying testing obligation (rigorous coverage of this logic's
    behavior against zero/one/many eligible links) is unchanged.
Added sections: None
Removed sections: None
Templates requiring updates:
  - ✅ .specify/templates/plan-template.md (Constitution Check gate is derived
    dynamically from this file at plan time; no hardcoded principle text to sync)
  - ✅ .specify/templates/spec-template.md (no constitution-specific references)
  - ✅ .specify/templates/tasks-template.md (no constitution-specific references)
  - ✅ .specify/templates/checklist-template.md (no constitution-specific references)
Known residual reference (intentionally left unchanged per amendment scope —
user requested Testing Standards only, not a full rewrite):
  - Governance section's Compliance review line still reads "...the scheduled
    expiration job..." — a future amendment may want to reword that line for
    full consistency, but it was explicitly out of scope for this amendment.
Follow-up TODOs: None
-->

# ShelfLife Constitution

## Core Principles

### I. Code Quality

Backend code MUST follow idiomatic Spring Boot conventions: a standard
controller → service → repository separation, with each layer doing exactly
one job (controllers handle HTTP concerns, services hold business logic,
repositories handle persistence). New interfaces, abstract base classes, or
generic wrapper layers MUST NOT be introduced unless there are at least two
concrete implementations today or a Spring mechanism (e.g., testing,
transaction boundaries) requires the seam — "we might swap it later" is not
sufficient justification.

Frontend React components MUST be small and single-purpose: a component that
renders unrelated pieces of UI or mixes data-fetching, state management, and
presentation concerns MUST be split. Shared logic is extracted into hooks
only once it is actually shared by two or more components, not in
anticipation of future reuse.

Simplicity is the default. Design patterns (factories, strategies, visitors,
etc.), speculative extensibility points, and configuration flags for
hypothetical future needs MUST NOT be added unless the current feature
requires them. Readability for the next contributor outweighs cleverness or
architectural purity.

**Rationale**: This is a personal-scale application with a small, well-known
problem domain. Unnecessary abstraction adds cognitive overhead without
paying for itself — every layer or pattern must justify its existence
against a real, current requirement.

### II. Testing Standards

Tests are not required to precede implementation — there is no strict TDD
ritual — and no fixed coverage percentage is enforced. Instead, testing
effort MUST be concentrated where correctness is hardest to verify by
inspection: time-based and state-based logic.

The following MUST have dedicated, passing tests before a change touching
them is considered complete:

- Every transition in the link lifecycle state machine (active → expired →
  graveyard → deleted), including attempted invalid transitions (e.g.,
  deleted → active).
- The behavior of the expiration/lifecycle enforcement logic (whether
  implemented as a scheduled job or read-time evaluation), including what
  happens when it runs against zero, one, and many eligible links.
- Every time-based boundary condition, tested at the boundary itself, one
  unit before it, and one unit after it (e.g., a link at exactly 168 hours
  old, at 167h59m, and at 168h01m; a graveyard entry at exactly 30 days, one
  day short, and one day past).

**Rationale**: Time-based logic is the easiest thing in this app to get
subtly wrong — off-by-one errors in hour/day boundaries silently misfire in
ways that are hard to catch through manual testing. Concentrating test rigor
on the state machine and scheduler, rather than chasing a coverage number,
targets effort where bugs actually hide.

### III. User Experience Consistency

The UI MUST be sleek, modern, and reflect the product's own philosophy: zero
clutter, zero required configuration. There are no settings screens,
onboarding wizards, or optional toggles unless a future feature spec
explicitly justifies one.

Styling MUST use a utility-first CSS approach (e.g., Tailwind). Heavy
component or design-system libraries MUST NOT be introduced, since they pull
in visual conventions and bundle weight that work against a minimal,
purpose-built interface. Spacing, typography, and interaction patterns
(hover states, transitions, empty states, confirmation patterns) MUST be
visually consistent between the main view and the graveyard view — a user
switching between them should never feel like they entered a different
application.

The core action — paste a URL, hit enter — MUST always be the single most
prominent element on the page, in every view where it is present. No other
element (navigation, stats, secondary actions) may compete with it visually.

**Rationale**: ShelfLife's value proposition is disappearing complexity, not
accumulating it. A UI that adds friction or visual noise undermines the
product's core premise.

### IV. Performance Requirements

This is a personal-scale application. No fixed numeric SLAs (e.g., specific
p95 latency targets or throughput numbers) are required or should be
invented.

The expiration/graveyard scheduling logic MUST remain efficient as the
number of stored links grows into the hundreds: scheduled jobs and lifecycle
queries MUST use indexed, set-based database operations rather than
per-row iteration or N+1 query patterns, so that job duration scales
sub-linearly with obvious complexity as link count grows.

The UI MUST never feel sluggish for a single active user: interactions
(adding a link, viewing the graveyard, deleting an entry) MUST reflect
immediately in the interface, with any network round-trip happening without
blocking input or producing visible jank.

**Rationale**: Correctness and simplicity matter far more than raw
throughput at this scale, but the one place scale actually bites is
time-based batch logic running against a growing table — that must be
written efficiently from the start rather than "fixed later," since lifecycle
jobs are exactly the kind of code that's expensive to retrofit for
correctness and performance simultaneously.

## Governance

This constitution supersedes ad-hoc conventions for all work in this
repository. Where a pull request or implementation choice conflicts with a
principle above, the principle wins unless an explicit, documented exception
is recorded.

**Amendment procedure**: Amendments are made by editing this file directly.
Any amendment MUST update the Sync Impact Report at the top of this file,
bump the version per the policy below, and update the `Last Amended` date.
Amendments that materially change a principle's meaning MUST be called out
in the PR/commit description.

**Versioning policy**: This constitution follows semantic versioning:
- MAJOR: A principle is removed or redefined in a backward-incompatible way
  (e.g., dropping the testing-boundary requirement, allowing a heavy UI
  component library).
- MINOR: A new principle or materially expanded guidance is added.
- PATCH: Wording clarifications, typo fixes, and non-semantic refinements.

**Compliance review**: Any plan produced by `/speckit-plan` MUST pass a
Constitution Check against the principles above before implementation
begins, and MUST re-check after Phase 1 design. Any violation MUST be
justified in the plan's Complexity Tracking table or the design MUST be
simplified to comply. Code review for any PR touching the link lifecycle
state machine, the scheduled expiration job, or shared UI primitives MUST
explicitly verify compliance with Principles II and III respectively.

**Version**: 1.0.1 | **Ratified**: 2026-07-02 | **Last Amended**: 2026-07-02
