# Session: Fingerprint effective compiler configuration before enabling cache-backed skips

- **intent:** Fingerprint effective compiler configuration before enabling cache-backed skips
- **started:** 2026-08-08

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

- **SliceKeyComputer compiler configuration input** — hashes the effective Maven Compiler Plugin
  identity and configuration alongside the source tree and running JDK, for every Tier A and Tier
  C key calculation · status: documented
- **Compiler configuration regression test** — changes the effective compiler release in a real
  MavenProject model and asserts that the sibling-bytecode key changes · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **Parent-derived compiler settings are compile inputs** — a child module can retain identical
  sources while inherited compiler release, annotation processing, or plugin version changes.
  Those settings must invalidate a future compile skip · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: Fingerprint the effective Maven compiler configuration

- **where:** `SliceKeyComputer compiler-input hashing`
- **why:** A cache hit can later suppress compilation only when the module source tree, running JDK, and effective compiler-plugin configuration match the publisher's inputs.
- **alternative:** Hash only the module directory — rejected: parent-inherited compiler options can change without changing a child source file, which would make a cache hit unsafe.
- **design:** ../design.md#decisions
- **constitution:** §3
- **trust:** ✓ verified
