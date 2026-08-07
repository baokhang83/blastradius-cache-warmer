# Session: restore Tier C compiler state through the shared atomic archive boundary

- **intent:** restore Tier C compiler state through the shared atomic archive boundary
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

- **CompilerStateWarmer** — computes the Tier C key and restores the cached Maven Compiler Plugin state only when the module's configured build directory has no `maven-status` tree, returning a human-readable restored or skipped reason for every outcome · status: documented
- **ArchiveRestorer** — provides the shared Tier A and Tier C ZIP restore boundary, extracting into a sibling staging directory and moving it to the destination only after all archive files have been safely written · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

<!-- - **<the non-obvious thing>** — <why it's this way / what breaks otherwise> · status: documented -->

- **Maven compiler state is optional acceleration state** — a missing key, cache failure, malformed ZIP, unsafe entry, or pre-existing destination must remain a cold build rather than becoming a Maven compilation failure or partial state · status: documented
- **Path normalization is necessary before writing an archive entry** — an entry such as `../../outside` must be rejected before filesystem writes, otherwise a cache payload can escape the intended build output tree · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: share staged ZIP restoration between Tier A and Tier C

- **where:** `warmer/ArchiveRestorer and warmer/SiblingBytecodeWarmer`
- **why:** Both tiers restore the same archive format, so one staged extraction boundary keeps path traversal protection and all-or-nothing publication identical.
- **alternative:** Duplicate the Tier A ZIP extraction in CompilerStateWarmer — rejected: safety and correctness fixes could drift between warmers.
- **design:** ../design.md#rationale
- **constitution:** §3
- **trust:** ✓ verified

## Decision: preserve existing Maven compiler state and fail open

- **where:** `warmer/CompilerStateWarmer`
- **why:** Compiler state is an optimization only, so the warmer never overwrites a local maven-status tree and returns a skipped result for any unusable cache condition.
- **alternative:** Overwrite the target or fail the build when restore cannot proceed — rejected: either path can turn an optional acceleration into stale or broken compilation state.
- **design:** ../design.md#rationale
- **constitution:** §3
- **trust:** ✓ verified
