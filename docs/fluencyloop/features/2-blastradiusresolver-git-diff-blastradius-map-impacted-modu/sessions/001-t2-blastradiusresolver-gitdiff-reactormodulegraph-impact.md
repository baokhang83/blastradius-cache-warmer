# Session: T2: BlastRadiusResolver - GitDiff + ReactorModuleGraph -> ImpactedModules

- **intent:** T2: BlastRadiusResolver - GitDiff + ReactorModuleGraph -> ImpactedModules
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

- **GitDiff** — wraps `git diff --name-only <baseRef>...HEAD` as a subprocess; the triple-dot
  form diffs against the merge-base, not baseRef's current tip, so commits baseRef picks up
  after the branch point never show up as "changed." Throws `GitDiffException` on a non-zero
  exit or missing binary; the caller (T3+'s wiring into `applyGate`) is expected to catch and
  fail open, same posture as `BlastradiusGate`. · status: documented
- **ReactorModuleGraph** — built fresh per `from(List<MavenProject>)` call from the live
  session's projects, not from re-parsed POMs; anchors all basedirs on whichever project has
  `isExecutionRoot() == true`. `moduleOf`/`dependentsOf`/`isReactorWide` are the only public
  surface. · status: documented
- **BlastRadiusResolver** — the orchestrator: `resolve(projects, baseRef)` pulls changed paths
  from `GitDiff`, builds a `ReactorModuleGraph`, and folds each changed path into a
  `Map<ModuleId, String>` of first-reason-wins impacts. Not annotated `@Named`/`@Singleton` and
  not called from `CacheWarmerExtension` yet - deliberately, since nothing wires it up in this
  slice. · status: follow-up (wiring into `applyGate` is a later slice)

### Hard-won conditions (gotchas, root causes, limitations)

- **`moduleOf`'s prefix match makes the root module a catch-all by construction** — a basedir of
  `""` satisfies `String.startsWith("")` for any path, so once an execution-root project is
  present, `isReactorWide` can never fire; it's purely the "no execution-root project in the
  list at all" fallback. This assumes the Maven execution root's basedir *is* the git repository
  root - true for every setup this repo targets, but worth knowing if cache-warmer ever needs to
  support a Maven root nested inside a larger monorepo. · status: documented
- **Test `MavenProject`s need `setFile(...)`, not just a `Model`** — `getBasedir()` derives from
  `getFile().getParentFile()`; `new MavenProject(model)` alone leaves it `null` and any
  basedir-relativizing code NPEs. The path doesn't need to exist on disk, only the `File` object
  math has to work. · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: Own module graph + git CLI, not blastradius-core

- **where:** `src/main/java/.../reactor/ReactorModuleGraph.java, .../git/GitDiff.java`
- **why:** CacheWarmerExtension already runs inside a live MavenSession (T1), so session.getProjects() gives real basedirs + declared dependencies directly - no need for blastradius-core's git-tree POM parsing, which exists only because blastradius's own validator has no live MavenProject. Changed files come from a git CLI subprocess, not JGit - zero new dependencies on the Core Extension's shared classpath.
- **alternative:** Depend on blastradius-core and reuse ReactorModuleGraph/GitComparison directly - more code reuse and guaranteed semantic parity with blastradius, but pulls JGit+Jackson+ASM onto Maven's shared classpath (needing shading, the same collision risk T1 flagged) and couples cache-warmer to blastradius-core's internal, non-public-API classes.
- **constitution:** §2, §3
- **trust:** ✓ verified

## Decision: Parent-POM inheritance is a dependency edge

- **where:** `ReactorModuleGraph.from - dependsOn edges`
- **why:** A module's in-reactor parent (project.getParent()) is added as a dependency edge alongside declared <dependency> entries, so dependentsOf(parentModule) already includes every child transitively. A root/parent POM change fans out through the normal transitive-dependents path instead of needing a separate reactor-wide special case.
- **alternative:** Special-case 'root pom.xml changed' as always reactor-wide - simpler to state, but coarser than necessary (it would mark every module impacted even when only one child actually inherits the changed part of the POM) and duplicates a concept the dependency graph can already express.
- **constitution:** §2
- **trust:** ✓ verified

## Decision: ImpactedModules keeps isEmpty() and isReactorWide() distinct

- **where:** `ImpactedModules.java`
- **why:** 'Nothing changed' (safe to fully trust the cache) and 'the module graph couldn't be attributed' (unsafe - treat everything as impacted even though nothing specific can be named) are opposite conclusions that would otherwise both show up as an empty impacts set. Collapsing them would let a caller that only checks impacts().isEmpty() warm from cache exactly when it shouldn't.
- **alternative:** Represent 'reactor-wide' by populating impacts with every known module - fails precisely in the case that matters, since reactor-wide only fires when the graph has no modules to enumerate (no execution-root project found).
- **constitution:** §3, §4
- **trust:** ✓ verified
