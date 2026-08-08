# Session: Skip only a fully restored production compiler execution

- **intent:** Skip only a fully restored production compiler execution
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

- **MavenCompilerSkipper** — adds `skipMain=true` to the current Maven Compiler Plugin execution
  without discarding its existing configuration, such as a configured release level · status: documented
- **CacheLifecycleListener warm-result gate** — captures both Tier A and Tier C outcomes and
  invokes the skipper only when both are verified restores for a production compile · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **Partial restoration is not a cache-backed compile hit** — output classes without matching
  compiler state, or vice versa, leave the compiler configuration untouched and force Maven's
  normal cold path · status: documented
- **Skip scope must be per execution** — a global compiler property would cross the blast-radius
  boundary and could skip a module whose inputs were never validated · status: documented
- **Maven Compiler Plugin distinguishes main and test skips** — the `compile` goal consumes
  `skipMain`, while `skip` belongs to test compilation. Setting the latter would not remove the
  production compile that this cache feature targets · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: Set skip on the exact safe compiler execution

- **where:** `CacheLifecycleListener and MavenCompilerSkipper`
- **why:** Only a module with verified restored bytecode and compiler state receives a per-execution compiler skip, so changed modules and partial restores remain cold.
- **alternative:** Set a global Maven compiler skip property — rejected: it can suppress compilation outside the validated module and violates the fail-open safety boundary.
- **design:** ../design.md#sequence-safe-warm-compile
- **constitution:** §3
- **trust:** ✓ verified
