# Session: Publish only after a successful Maven session

- **intent:** Publish only after a successful Maven session
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

- **`afterSessionEnd` publication** — starts Tier A/C publication only when Maven reports no
  session exceptions, keeping failed build output out of the cache · status: documented
- **RuntimeCachePublisher** — attempts each module independently and converts a cache-write
  outage into an explanatory log entry without changing the Maven build result · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

<!-- - **<the non-obvious thing>** — <why it's this way / what breaks otherwise> · status: documented -->

- **A successful compiler goal is not a successful build** — later modules or verification goals
  can still fail, so cache writes must wait for the session result rather than each compile event ·
  status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: publish cache slices only after successful completion

- **where:** `src/main/java/io/github/baokhang83/blastradius/cachewarmer/CacheWarmerExtension.java; RuntimeCachePublisher.java`
- **why:** Maven exceptions make build output untrustworthy, so publication starts only after success and each cache write remains isolated from Maven and other modules.
- **alternative:** Publish as each compiler invocation succeeds — rejected: a later failure could leave incomplete output available for a future restore.
- **design:** ../design.md#sequence-successful-clean-verify
- **constitution:** §3
- **trust:** ✓ verified
