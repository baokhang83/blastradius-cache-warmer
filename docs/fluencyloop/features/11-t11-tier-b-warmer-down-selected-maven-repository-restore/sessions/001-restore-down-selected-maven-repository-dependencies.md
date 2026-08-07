# Session: restore down-selected Maven repository dependencies

- **intent:** restore down-selected Maven repository dependencies
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

- **DependencySliceWarmer** — consumes T9's selected dependency manifest, derives T10's shared per-coordinate key, and restores each cache hit to the matching Maven repository path while returning an observable result for every artifact · status: documented
- **Staging-file placement** — writes a fetched JAR into a temporary sibling before moving it to its final repository path, so an I/O failure cannot leave a partially written destination visible to Maven · status: documented
- **WarmResult list** — retains the restored or skipped reason per coordinate, including the cache key when one was fetched, so a mixed warm run remains explainable rather than collapsing into one opaque batch outcome · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **Existing and escaping destinations are rejected before cache fetch** — an existing Maven artifact is not overwritten, and normalized containment under the supplied repository root blocks a malformed coordinate from writing elsewhere · status: documented
- **Failures are local to one coordinate** — a cache miss, cache exception, or filesystem error returns a skip for that dependency and leaves other manifest entries eligible to warm, preserving cold Maven resolution as the correct fallback · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: Restore each dependency independently with a per-artifact result

- **where:** `warmer/DependencySliceWarmer`
- **why:** Tier B manifests naturally contain a mixture of cache hits, misses, and transient failures. Returning one result per coordinate keeps each outcome explainable and lets a failed artifact fall back to Maven without preventing other hits.
- **alternative:** Fail or report one aggregate result for the whole manifest — rejected: it hides which dependency remained cold and makes an optional cache failure broader than necessary.
- **design:** ../design.md#rationale
- **constitution:** §4
- **trust:** ✓ verified

## Decision: Protect the local Maven repository before restoring bytes

- **where:** `warmer/DependencySliceWarmer`
- **why:** The warmer normalizes each coordinate path, rejects paths outside the supplied repository, preserves existing artifacts, and stages writes before moving them into place. Cache and filesystem problems become skips so Maven can still resolve correctly.
- **alternative:** Resolve and overwrite paths directly — rejected: a malformed coordinate or interrupted write could damage local Maven state, and a cache optimization must not make the build fail closed.
- **design:** ../design.md#rationale
- **constitution:** §3
- **trust:** ✓ verified
