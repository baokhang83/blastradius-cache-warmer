# Session: benchmark the real Blastradius Maven reactor without making unproven speed claims

- **intent:** benchmark the real Blastradius Maven reactor without making unproven speed claims
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

- **Benchmark harness** — creates temporary worktrees for the same Blastradius revision, primes an unmeasured Maven repository template, then runs every cold and warm trial against a copy of that template · status: documented
- **Warm evidence classifier** — scans each warm build log for a cache-warmer restore event and includes that evidence alongside elapsed time and the Maven exit code in `results.tsv` · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

<!-- - **<the non-obvious thing>** — <why it's this way / what breaks otherwise> · status: documented -->

- **The current Core Extension is not yet wired to the Tier A/C components** — the harness must return an inconclusive result until logs show a real restore, even if the warm invocation happens to be faster · status: documented
- **Prepared local repositories must be copied, not shared** — Maven writes metadata during a build, so sharing one mutable repository between cold and warm trials would contaminate the comparison · status: documented
- **Nested Maven builds need the benchmark repository through `MAVEN_OPTS`** — Blastradius's integration tests launch child Maven processes that do not inherit the parent's command-line `-Dmaven.repo.local`, so the extension must be resolvable through inherited JVM options as well · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: require restore evidence before reporting a speedup

- **where:** `scripts/benchmark-blastradius.sh result classification`
- **why:** Equivalent isolated workspaces make the timing comparison fair, but only a warm log that proves restoration can attribute a difference to cache warming.
- **alternative:** Report elapsed-time deltas without checking restore output — rejected: ordinary variance or Blastradius test selection could be misreported as a cache-warming benefit.
- **design:** ../design.md#rationale
- **constitution:** §3
- **trust:** ✓ verified
