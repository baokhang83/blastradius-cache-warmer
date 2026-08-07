# Session: publish segmented third-party JAR cache objects

- **intent:** publish segmented third-party JAR cache objects
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

- **DependencySliceKey** — derives one shared `dependency_slice/<repository path>` identity from a resolved third-party coordinate, so the publisher and the future warmer independently address the same object without module-specific state · status: documented
- **DependencySlicePublisher** — resolves each T9-selected coordinate beneath the supplied local Maven repository and writes its raw JAR bytes through the storage-neutral `SliceCache` interface · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **Absent local artifacts are intentionally skipped** — a partially populated Maven repository is normal, and omitting an unavailable publication cannot make a later build wrong because the warmer will treat the corresponding object as a cache miss and Maven can resolve it cold · status: documented
- **Tier B is not folded into `SlicePublisher`** — Tier A/C archive module output trees under source-content keys, while Tier B transfers individual coordinate-addressed repository files; combining those incompatible contracts would obscure the restore protocol · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: Use one cache object per Maven repository path

- **where:** `publisher/DependencySlicePublisher and slicekey/DependencySliceKey`
- **why:** Coordinate-derived keys let unrelated modules reuse an identical third-party JAR and let the future warmer reconstruct the exact object without a remote manifest.
- **alternative:** A dependency ZIP or remote manifest protocol — rejected: it either transfers unrelated JARs together or adds remote state that deterministic Maven coordinates already provide.
- **design:** ../design.md#rationale
- **constitution:** §2
- **trust:** ✓ verified

## Decision: Skip unavailable local artifacts during publication

- **where:** `publisher/DependencySlicePublisher`
- **why:** A cache publisher must not turn a normal partially populated Maven repository into a build prerequisite. Skipping an absent JAR leaves a later warmer with an ordinary cache miss, after which Maven resolves it cold.
- **alternative:** Fail publication when any manifest JAR is absent — rejected: it makes the optional cache path fail closed even though Maven has a correct fallback.
- **design:** ../design.md#rationale
- **constitution:** §3
- **trust:** ✓ verified
