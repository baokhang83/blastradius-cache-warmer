# Session: T6 restore Tier A sibling bytecode safely from SliceCache

- **intent:** T6 restore Tier A sibling bytecode safely from SliceCache
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

- **SiblingBytecodeWarmer** — retrieves the Tier A cache key shared with T5, then restores a
  complete bytecode archive only when Maven's configured output directory is absent; callers use
  T2's blast-radius result to choose safe modules before calling it · status: documented
- **WarmResult** — carries `RESTORED` or `SKIPPED` plus a human-readable reason, allowing the
  eventual extension integration to emit a per-module explanation without treating normal cache
  misses as failures · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

<!-- - **<the non-obvious thing>** — <why it's this way / what breaks otherwise> · status: documented -->

- **Staged ZIP extraction** — normalizes every entry beneath a fresh sibling directory and
  rejects escapes before moving the completed tree into place, preventing ZIP-slip and partial
  output from a malformed archive · status: documented
- **Existing classes are preserved** — the warmer skips instead of deleting or merging with an
  existing output tree, because this pre-build optimization must never overwrite unknown local
  bytecode merely to obtain a cache hit · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: stage and validate Tier A archives before restore

- **where:** `src/main/java/io/github/baokhang83/blastradius/cachewarmer/warmer/SiblingBytecodeWarmer.java`
- **why:** A complete staging tree with normalized, contained paths prevents a malformed cache archive from producing partial classes or escaping Maven's output directory.
- **alternative:** Extract ZIP entries directly into the classes directory - rejected: an I/O error or traversal entry could leave unsafe or partial output where Maven may consume it.
- **design:** ../design.md#key-design-choice
- **constitution:** §3
- **trust:** ✓ verified

## Decision: return an explicit restored or skipped result

- **where:** `src/main/java/io/github/baokhang83/blastradius/cachewarmer/warmer/WarmResult.java`
- **why:** The future extension needs a concrete per-module outcome and reason for build output, while cache misses and unusable slices remain safe cold-build paths.
- **alternative:** Use exceptions or a boolean - rejected: exceptions misclassify expected misses and a boolean loses the actionable reason required for debugging.
- **design:** ../design.md#class-diagram
- **constitution:** §4
- **trust:** ✓ verified
