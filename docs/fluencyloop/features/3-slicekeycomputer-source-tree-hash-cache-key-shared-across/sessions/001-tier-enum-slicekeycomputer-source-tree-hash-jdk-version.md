# Session: Tier enum + SliceKeyComputer: source-tree hash + JDK version -> tiered cache key

- **intent:** Tier enum + SliceKeyComputer: source-tree hash + JDK version -> tiered cache key
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

- **`Tier`** — enumerates the manifesto's three warming tiers by name (`SIBLING_BYTECODE`,
  `DEPENDENCY_SLICE`, `COMPILER_STATE`) instead of an A/B/C code; `SliceKeyComputer` uses
  `tier.name().toLowerCase()` as the key's leading path segment · status: documented
- **`SliceKeyComputer.keyFor(module, tier)`** — walks `module.getBasedir()`, filters out
  `target/` and non-regular files, sorts the rest by relative path, then feeds each
  (relative-path, content) pair plus `System.getProperty("java.version")` into a single SHA-256
  digest; returns `"<tier>/<artifactId>/<hex>"`. Stateless and has no dependency on T1/T2 - any
  caller with a `MavenProject` can compute a key · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **Sorting must happen on the relative path, not iteration order** — `Files.walk` gives no
  ordering guarantee across platforms/filesystems, so the digest would be nondeterministic
  without an explicit `.sorted(...)` on the relativized path string · status: documented
- **`target/` is excluded by checking the path's first name element**, via
  `relativize(file).startsWith("target")` — this only strips a top-level `target/` under the
  module's own basedir; it does not recurse into or special-case nested directories that happen
  to be named `target` deeper in the tree (none expected in practice) · status: documented
- **No storage/network here** — this class only derives a string; `SliceCache` (T4) and the
  publisher/warmers (T5-T7) are the callers that actually read or write anything at that key
  · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: Content-hash keys, not commit-SHA keys

- **where:** `src/main/java/io/github/baokhang83/blastradius/cachewarmer/slicekey/SliceKeyComputer.java`
- **why:** A content hash is stable across branches: two branches whose diffs never touch a module still produce the same key for it, so a slice published on one branch is a legitimate cache hit on the other. A commit-SHA key only ever hits a rebuild of that exact commit, so most branch/PR builds would miss even when nothing relevant changed.
- **alternative:** Key by (module path, commit SHA) - cheaper to compute (no filesystem walk) but only pays off on exact-commit rebuilds, defeating the PoC's actual goal of cutting sibling/PR build latency across branches.
- **constitution:** §4
- **trust:** ⚠ not independently verified

## Decision: Bake the running JDK version into the key

- **where:** `src/main/java/io/github/baokhang83/blastradius/cachewarmer/slicekey/SliceKeyComputer.java`
- **why:** Tier A (sibling bytecode) and Tier C (compiler state) are both toolchain-sensitive: the same source tree compiled under a different JDK can produce different, incompatible bytecode/compiler state. Folding java.version into the digest means a runner-image JDK bump naturally invalidates old slices instead of serving a stale, wrong-toolchain hit.
- **alternative:** Rely on maven.compiler.release / a POM property alone - misses JDK upgrades that don't touch the POM (e.g. a base image bump), which is exactly the silent-staleness failure mode SS3 (safety over speed) rules out.
- **constitution:** §3
- **trust:** ⚠ not independently verified
