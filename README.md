# blastradius-cache-warmer

<a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-blue.svg" alt="Apache 2.0 license" /></a>
<a target="_blank" href="https://www.oracle.com/technetwork/java/javase/downloads/index.html"><img src="https://img.shields.io/badge/JDK-21+-green.svg" /></a>
<a target="_blank" href="https://github.com/baokhang83/blastradius"><img src="https://img.shields.io/badge/requires-blastradius-orange.svg" /></a>
<img src="https://img.shields.io/badge/status-M3%20security%20hardening%20complete-yellow.svg" />

A Maven Core Extension foundation for pre-warming a CI build's `~/.m2/repository`, sibling
`target/classes`, and `target/maven-status` from remote cache storage. Its intended runtime path
uses a git-diff blast-radius calculation to restore only state belonging to unaffected modules.
That avoids the usual ephemeral-runner cost of re-downloading dependencies and recompiling code
nobody changed.

It only ever activates for [blastradius](https://github.com/baokhang83/blastradius) users — see
[Requires blastradius](#requires-blastradius) — and it fails open: anything it can't warm
confidently, it simply leaves for Maven to build cold, same as if this extension weren't
installed at all.

## Status

The Tier A, B, and C cache primitives are built and covered by unit tests:

- a fail-open blastradius presence gate, git-diff/reactor impact resolver, source-tree keying,
  and a storage-neutral `SliceCache` with an S3 implementation;
- Tier A/C publication and restore for sibling bytecode and compiler state;
- Tier B dependency-tree filtering, one-object-per-third-party-JAR publication, and
  down-selected Maven local-repository restore.

The runtime integration is deliberately still incomplete. `CacheWarmerExtension` currently runs
only the gate: it does not construct a configured cache, compute impacts, or invoke a publisher
or warmer. Installing the extension therefore still leaves Maven to build cold. Every existing
warmer now verifies integrity before restore, but runtime cache wiring and its storage
configuration remain future work.

## How it works

The intended path is:

1. **Gate.** `afterProjectsRead` — Maven's pre-resolution hook, before any dependency is
   fetched or any module is compiled — checks whether `blastradius-maven-plugin` is declared
   anywhere in the reactor. Absent, or anything about the check goes wrong: no-op, cold build,
   continue exactly as if this extension weren't installed.
2. **Diff.** The standalone `BlastRadiusResolver` maps a git diff onto the reactor dependency
   graph to identify changed modules and their dependents. Wiring it into the extension remains
   pending.
3. **Fetch and restore.** The tier warmers can fetch sibling bytecode, compiler state, or only
   the third-party JARs selected by a module's dependency manifest. Tier B uses one cache object
   per JAR, placed at Maven's normal repository path, so unrelated dependencies remain cold.
4. **Verify.** Every cache restore verifies its payload against the matching integrity sidecar;
   mismatches fail open to a cold build.

Storage roles, bucket controls, and the dry-run-first purge procedure are documented in
[security.md](docs/security.md).

## The 3-tier caching strategy

Storage is split into lightweight, per-module slices rather than one monolithic cache blob, so
a change to one module never invalidates the whole reactor's cache:

| Tier | Restores | Why |
|---|---|---|
| **A — Sibling bytecode** | `target/classes/` | If module A depends on module B and B is outside the blast radius, there's no reason to recompile B — its bytecode is dropped in directly. |
| **B — Segmented dependencies** | `~/.m2/repository/` | A change to an isolated module doesn't need every third-party jar the whole monorepo uses — only the down-selected subset that module's own dependency tree requires. |
| **C — Incremental compiler state** | `target/maven-status/` | `maven-compiler-plugin` tracks incremental-build state locally; a clean CI runner has none, forcing a full rebuild even for genuinely unaffected code. Restoring it preserves incremental tracking across ephemeral runners. |

Full detail: [MANIFESTO.md](MANIFESTO.md).

## What you'll see in the Maven output today

The extension currently wires only the gate, so this is the entire observable behavior today:

```
[cache-warmer] blastradius-maven-plugin not found in reactor - skipping (no-op)
```

or, in a reactor that declares it:

```
[cache-warmer] blastradius-maven-plugin detected - gate passed
```

Once runtime integration lands, it will add the per-artifact warm/skip reasons already returned
by the primitives, following the [constitution](docs/fluencyloop/constitution.md)'s
explainability principle (§4).

## Requires blastradius

This is a hard dependency, by design: cache-warmer only activates for reactors that already
run [blastradius](https://github.com/baokhang83/blastradius), and it uses blastradius's own
ground-truth dependency map to decide what's inside a change's blast radius. It is not a
general-purpose Maven cache — see [Constitution §2](docs/fluencyloop/constitution.md) on why
that scope is deliberate, not a limitation to work around later.

## Roadmap

Planned in 5 milestones — see
[the full plan](docs/fluencyloop/plans/blastradius-cache-warmer-maven-core-extension-that-pre-warms/plan.md)
and its [GitHub milestones](https://github.com/baokhang83/blastradius-cache-warmer/milestones)
for the task-level breakdown:

1. **PoC** — Tier A + C primitives and benchmark harness. *(task set complete, runtime wiring
   still pending)*
2. **Tier B** — segmented third-party dependency publication and restore. *(T9-T11 complete,
   runtime wiring still pending)*
3. **Security hardening** — integrity verification and S3 storage permissioning. *(T12-T13
   complete)*
4. **Alternate storage backend** — GitHub Actions cache, for teams without S3.
5. **Gradle support** *(future)* — parked until the Maven path is proven and secured.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
