# blastradius-cache-warmer

<a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-blue.svg" alt="Apache 2.0 license" /></a>
<a target="_blank" href="https://www.oracle.com/technetwork/java/javase/downloads/index.html"><img src="https://img.shields.io/badge/JDK-21+-green.svg" /></a>
<a target="_blank" href="https://github.com/baokhang83/blastradius"><img src="https://img.shields.io/badge/requires-blastradius-orange.svg" /></a>
<img src="https://img.shields.io/badge/status-PoC%20(M1%20in%20progress)-lightgrey.svg" />

A Maven Core Extension that pre-warms a CI build's `~/.m2/repository`, sibling `target/classes`,
and `target/maven-status` from cloud storage — scoped to exactly the modules a change's
**blast radius** doesn't touch, using [blastradius](https://github.com/baokhang83/blastradius)'s
own dependency map. Everywhere else, an ephemeral CI runner spends 2–5 minutes of every build
re-downloading a monolithic dependency cache and recompiling code nobody changed. This tool
skips both, for the modules a diff proves are unaffected.

It only ever activates for [blastradius](https://github.com/baokhang83/blastradius) users — see
[Requires blastradius](#requires-blastradius) — and it fails open: anything it can't warm
confidently, it simply leaves for Maven to build cold, same as if this extension weren't
installed at all.

## Status

This project is at the start of its roadmap: only **T1 — the Core Extension skeleton and the
blastradius-presence gate** is built so far. Tiers A/B/C, cloud storage, and integrity
verification are tracked as upcoming milestones, not shipped behavior — see
[Roadmap](#roadmap) before assuming any tier below actually restores anything yet.

## How it works

1. **Gate.** `afterProjectsRead` — Maven's pre-resolution hook, before any dependency is
   fetched or any module is compiled — checks whether `blastradius-maven-plugin` is declared
   anywhere in the reactor. Absent, or anything about the check goes wrong: no-op, cold build,
   continue exactly as if this extension weren't installed.
2. **Diff.** *(M1, not yet built)* Reads blastradius's own dependency map plus the current git
   diff to compute which modules are inside the change's blast radius versus provably
   unaffected by it.
3. **Fetch.** *(M1, not yet built)* For every unaffected module, fetches its cached slices —
   bytecode, dependency jars, compiler state — from cloud storage (S3 first; a GitHub Actions
   cache backend is a later milestone), keyed by a hash of that module's source tree.
4. **Restore.** *(M1, not yet built)* Verifies each slice, then drops it into place before
   Maven ever reaches dependency resolution or compilation — so Maven finds warm state and
   skips the work entirely, module by module.

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

T1 only builds the gate — there is nothing yet for it to hand off to, so this is the entire
observable behavior right now:

```
[cache-warmer] blastradius-maven-plugin not found in reactor - skipping (no-op)
```

or, in a reactor that declares it:

```
[cache-warmer] blastradius-maven-plugin detected - gate passed
```

Once M1 lands, that second line will be followed by a per-module warm/skip report with a
concrete reason for each decision — the [constitution](docs/fluencyloop/constitution.md)'s
explainability principle (§4) — not just this pass/no-op boundary.

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

1. **PoC** — Tier A + C, S3-backed storage only. *(in progress — T1 done)*
2. **Tier B** — segmented third-party dependency restore.
3. **Security hardening** — integrity verification before any slice is restored.
4. **Alternate storage backend** — GitHub Actions cache, for teams without S3.
5. **Gradle support** *(future)* — parked until the Maven path is proven and secured.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
