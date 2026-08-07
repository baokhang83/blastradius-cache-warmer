# Session: Restore safe module slices at the compiler boundary

- **intent:** Restore safe module slices at the compiler boundary
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

- **RuntimeBuildContext** — holds the one session cache and its computed impact set, answering
  whether a module is safe to restore and, when it is not, the concrete cold-build reason ·
  status: documented
- **CacheLifecycleListener** — wraps Maven's existing execution listener and restores Tier A and
  Tier C only before the production compiler goal for a safe module · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

<!-- - **<the non-obvious thing>** — <why it's this way / what breaks otherwise> · status: documented -->

- **`afterProjectsRead` is too early for target files** — Maven's clean lifecycle runs after it,
  so restoring there would be silently deleted by `mvn clean verify`; compiler-start is the first
  useful boundary · status: documented
- **Existing listener events are forwarded** — a core extension must add cache behavior without
  suppressing Maven's other observers and event spies · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: restore after clean at compiler start

- **where:** `src/main/java/io/github/baokhang83/blastradius/cachewarmer/CacheLifecycleListener.java`
- **why:** The compiler boundary is after clean and before compilation, so restored bytecode and compiler state survive and can be consumed without warming changed modules.
- **alternative:** Restore during afterProjectsRead — rejected: mvn clean verify deletes target after that callback, wasting the restore.
- **design:** ../design.md#sequence-successful-clean-verify
- **constitution:** §3
- **trust:** ✓ verified
