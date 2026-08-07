# Session: T5 SlicePublisher archive and cache Tier A/C build outputs

- **intent:** T5 SlicePublisher archive and cache Tier A/C build outputs
- **started:** 2026-08-07

<!--
FluencyLoop Stage 3 — a session is a slice of the build. It holds two persistent records:
  1. Knowledge transfer — what the developer was made fluent in this slice (you write it).
  2. Decisions — the genuine forks, appended by `fluencyloop decision` (the script formats them).

Everything below is scaffolding in comments — nothing to delete. Write knowledge transfer under
its headings; add each decision with
  fluencyloop decision --where <file/area> --why <rationale> [--alternative <rejected + why>] \
                       [--title <chose X over Y>] [--constitution §N] [--trust verified|unverified]
so the block is formatted deterministically and you never hand-write the bullet schema. No
`commits:` field: the feature is a branch, so the PR view derives commits live from git.

KNOWLEDGE-TRANSFER — one bullet per component/role/mechanism explained:
  **<subject>** — <what it does, under what conditions> · status: documented | follow-up
  Make it RICH: cover the inventory AND the non-obvious, hard-won lessons (a bug's root cause,
  why something is done an odd way, a documented limitation). Describe the WORK, never a person
  (no competence, no "who knew what") — these files are committed and name an author via git.

DECISION fields (assembled by `fluencyloop decision`):
  where        — file/area (NOT a line number — survives refactoring)
  why          — the rationale, taught live before it was written
  alternative  — the rejected option and why (what makes it rationale, not description)
  design       — (optional) ../design.md#anchor
  constitution — (optional) §N
  trust        — ✓ verified | ⚠ not independently verified (about the DECISION, never the person)
-->

---

## Knowledge transfer

_The ground this slice makes understandable — components, roles, and conditions explained,
persisted so the fluency doesn't evaporate with the conversation. About the work, never a person._

### Components (role, conditions)

<!-- - **<component / role / mechanism>** — <what it does, and under what conditions> · status: documented -->

- **SlicePublisher** — converts a module's Tier A production classes and Tier C
  `maven-status` tree into separate cache payloads, using the shared `SliceKeyComputer` so a
  later warmer derives the exact same module-and-tier key independently · status: documented
- **Tier archive** — writes regular files in relative-path order into a ZIP with fixed entry
  timestamps, so a given output tree has a stable archive shape and the cache sees one opaque
  payload for the complete tier · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

<!-- - **<the non-obvious thing>** — <why it's this way / what breaks otherwise> · status: documented -->

- **Configured Maven build paths** — resolves relative `Build` output and build directories
  against the module base directory instead of assuming `target`, which keeps publication correct
  for reactors that customise Maven's layout · status: documented
- **Absent and empty output trees** — are intentionally skipped rather than stored as empty
  cache values, because they contain no warmable work and a later consumer can treat cache absence
  as a normal no-op · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: store one deterministic ZIP per Tier A/C output tree

- **where:** `src/main/java/io/github/baokhang83/blastradius/cachewarmer/publisher/SlicePublisher.java`
- **why:** One opaque archive preserves SliceCache's two-method boundary and makes each tier publication a complete cache candidate for future warmers.
- **alternative:** Store every output file separately with a manifest - rejected: it would add partial-publication and directory-protocol concerns to the minimal cache abstraction.
- **design:** ../design.md#key-design-choice
- **constitution:** §2
- **trust:** ✓ verified

## Decision: use Maven Build paths instead of literal target paths

- **where:** `src/main/java/io/github/baokhang83/blastradius/cachewarmer/publisher/SlicePublisher.java`
- **why:** Maven projects may configure their build or output directory, so publication must follow the project model to archive the outputs the build actually produced.
- **alternative:** Assume target/classes and target/maven-status - rejected: custom Maven layouts would silently publish the wrong files or nothing.
- **design:** ../design.md#key-design-choice
- **constitution:** §2
- **trust:** ✓ verified
