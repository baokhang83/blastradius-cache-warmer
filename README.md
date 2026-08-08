# blastradius-cache-warmer

<a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-blue.svg" alt="Apache 2.0 license" /></a>
<a target="_blank" href="https://www.oracle.com/technetwork/java/javase/downloads/index.html"><img src="https://img.shields.io/badge/JDK-21+-green.svg" /></a>
<img src="https://img.shields.io/badge/Maven-Core%20Extension-blue.svg" alt="Maven Core Extension" />
<img src="https://img.shields.io/badge/status-M4%20alternate%20backend%20complete-yellow.svg" />

> **Skip redundant Maven compilation in CI with verified bytecode reuse.**

Cache Warmer is a Maven Core Extension that uses the Git diff and Maven reactor to identify
unchanged modules. For a safe cache hit, it restores verified production `.class` files and Maven
Compiler Plugin state, then skips that module's production compilation. Changed or uncertain
modules build normally.

It works with Maven reactors directly and does not cache or skip test execution. It fails open:
anything it cannot warm confidently simply builds cold, as if this extension were not installed.

## Status

The Tier A, B, and C cache primitives are built and covered by unit tests:

- a git-diff/reactor impact resolver, source-tree keying, and a storage-neutral `SliceCache` with
  GitHub Actions and S3 implementations;
- Tier A/C publication and restore for sibling bytecode and compiler state;
- Tier B dependency-tree filtering, one-object-per-third-party-JAR publication, and
  down-selected Maven local-repository restore.

`CacheWarmerExtension` now wires Tier A and Tier C into Maven's runtime lifecycle. It creates the
configured cache, computes the reactor blast radius, restores unaffected sibling bytecode and
compiler state immediately before compilation, and publishes successful module output after the
session ends. Tier B dependency slices remain available as primitives but need a separate
dependency-manifest lifecycle before they can safely run before Maven resolves dependencies.

## How it works

The intended path is:

1. **Initialize.** `afterProjectsRead` — Maven's pre-resolution hook, before any dependency is
   fetched or any module is compiled — creates the configured cache and runtime listener. A cache,
   git, or reactor setup error fails open to a cold build.
2. **Diff.** `BlastRadiusResolver` maps the git diff onto the Maven reactor dependency graph to identify
   changed modules and their dependents. Those modules stay cold.
3. **Fetch and restore.** Just before `maven-compiler-plugin:compile`, Tier A restores sibling
   bytecode and Tier C restores compiler state for safe modules. Maven's resources phase may
   already have created `target/classes`; Tier A merges only verified `.class` files and leaves
   the current build's resources intact.
4. **Skip only a verified compile hit.** When both restores succeed for the same safe module,
   the extension sets `skipMain=true` on that exact compiler execution. Maven then reuses the
   restored output instead of deciding from checkout timestamps that it must compile again. A
   partial restore, cache miss, integrity failure, or setup error leaves the execution unchanged
   and compiles cold.
5. **Publish.** After a successful Maven session, Tier A archives production `.class` files and
   Tier C archives compiler state for a later compatible build. Failed sessions publish nothing.
6. **Verify.** Every cache restore verifies its payload against the matching integrity sidecar;
   mismatches fail open to a cold build.

Storage roles, bucket controls, and the dry-run-first purge procedure are documented in
[security.md](docs/security.md).

## Cache backends

GitHub Actions cache is the default backend. It uses the short-lived runner token and signed
transfer URLs already available to an Actions job, so it does not require an S3 account. S3 is an
explicit alternative:

```text
-Dblastradius.cache.backend=s3
-Dblastradius.cache.s3.bucket=example-cache-bucket
-Dblastradius.cache.s3.namespace=example-org/cache-warmer/v1
```

An unavailable runner service, invalid configuration, S3 error, cache miss, or failed integrity
check always leaves Maven to build cold.

See [GitHub Actions cache backend](docs/github-actions-cache.md) for the runner requirements and
security boundary.

## The 3-tier caching strategy

Storage is split into lightweight, per-module slices rather than one monolithic cache blob, so
a change to one module never invalidates the whole reactor's cache:

| Tier | Restores | Why |
|---|---|---|
| **A — Sibling bytecode** | production `.class` files in `target/classes/` | If module A depends on module B and B is outside the blast radius, there's no reason to recompile B — verified bytecode is restored directly while current resources remain intact. |
| **B — Segmented dependencies** | `~/.m2/repository/` | A change to an isolated module doesn't need every third-party jar the whole monorepo uses — only the down-selected subset that module's own dependency tree requires. |
| **C — Incremental compiler state** | `target/maven-status/` | `maven-compiler-plugin` tracks incremental-build state locally; a clean CI runner has none, forcing a full rebuild even for genuinely unaffected code. Restoring it preserves incremental tracking across ephemeral runners. |

Full detail: [MANIFESTO.md](MANIFESTO.md).

## What you'll see in the Maven output

```
[cache-warmer] runtime restore listener registered
[cache-warmer] example-module sibling bytecode: restored sibling bytecode from key '...'
[cache-warmer] example-module compiler state: restored compiler state from key '...'
[cache-warmer] example-module compiler: skipped after verified bytecode and compiler-state restore
```

Cache misses and failures produce similarly explicit cold-build reasons, following the
[constitution](docs/fluencyloop/constitution.md)'s explainability principle (§4).

## Consumer requirements

Cache-warmer is a Maven Core Extension: a consumer opts in by adding it to
`.mvn/extensions.xml`. Its safe warm path needs a Git checkout with the configured base ref and
a Maven reactor. GitHub Actions cache also needs the runner's short-lived cache runtime exposed
to the Maven shell step; GitHub scopes those values to Actions by default. S3 is an explicit
alternative for other CI systems.

[Blastradius](https://github.com/baokhang83/blastradius) is the pilot reactor used to validate
the extension, not an adoption prerequisite.

## Roadmap

Planned in 5 milestones — see
[the full plan](docs/fluencyloop/plans/blastradius-cache-warmer-maven-core-extension-that-pre-warms/plan.md)
and its [GitHub milestones](https://github.com/baokhang83/blastradius-cache-warmer/milestones)
for the task-level breakdown:

1. **PoC** — Tier A + C primitives, runtime wiring, and benchmark harness. *(complete)*
2. **Tier B** — segmented third-party dependency publication and restore. *(primitives complete;
   dependency-manifest runtime wiring pending)*
3. **Security hardening** — integrity verification and S3 storage permissioning. *(T12-T13
   complete)*
4. **Alternate storage backend** — GitHub Actions cache, the default backend. *(T14 complete)*
5. **Gradle support** *(future)* — parked until the Maven path is proven and secured.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
