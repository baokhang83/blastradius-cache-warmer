# Session: Create the runtime cache configuration boundary

- **intent:** Create the runtime cache configuration boundary
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

- **RuntimeCacheFactory** — creates the one `SliceCache` a gated Maven session uses, selecting
  GitHub Actions when no backend property is supplied and S3 only after it is explicitly selected
  with both required properties · status: documented
- **S3 configuration validation** — reads the bucket and namespace before opening an AWS client,
  leaving the runtime boundary free to turn a configuration error into a cold-build reason ·
  status: documented

### Hard-won conditions (gotchas, root causes, limitations)

<!-- - **<the non-obvious thing>** — <why it's this way / what breaks otherwise> · status: documented -->

- **Store creation is not cache use** — constructing a `SliceCache` must not fetch, restore, or
  publish data, so an invalid configuration can be rejected before a build directory is touched ·
  status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: construct one configured cache per Maven session

- **where:** `src/main/java/io/github/baokhang83/blastradius/cachewarmer/cache/RuntimeCacheFactory.java`
- **why:** Centralizing backend selection makes GitHub Actions the default and rejects incomplete S3 configuration before any restore can touch the build.
- **alternative:** Let each warmer choose and configure its own store — rejected: duplicated setup could yield inconsistent tier behavior and partial cache interaction.
- **design:** ../design.md#class-diagram
- **constitution:** §2
- **trust:** ✓ verified
