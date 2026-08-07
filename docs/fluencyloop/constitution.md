# Constitution

**Project:** blastradius-cache-warmer

## Principles

**§1 — Test-Driven Development.** Every restore-path task ships tested before or alongside the
implementation; no restore-path code merges untested.
*Why:* this tool injects prebuilt binaries into someone else's build — an unverified restore path
fails silently as a correctness bug, not a crash, and that class of bug is expensive to catch
after the fact.

**§2 — Clean code & simplicity.** Prefer the simplest mechanism that satisfies the current
milestone; no speculative abstraction for hypothetical multi-tenant/expansion scenarios until
they're real. Names carry the load-bearing meaning: classes/enums/interfaces `PascalCase`,
methods/fields/locals `camelCase`, no abbreviations that make a reader guess (`resolver`, not
`rslvr`), booleans read as a predicate (`isPresent`, `hasChanged` — not `flag` or `check`), and
a name states what a thing *is* or *does*, not how it's implemented (`SliceCache`, not
`SliceHashMapWrapper`). This is a code-review commitment, not a linter rule — like TDD (§1),
it's checked by a reader, not a build gate, so a PR that violates it gets asked to rename, not
auto-failed.
*Why:* multi-tenant productization and Gradle support are explicitly deferred; premature
generality here is wasted surface area to maintain and review. Naming was left implicit in the
original draft of this principle, which meant "clean code" had no concrete, checkable meaning
for the part of it reviewers actually run into on every single PR — naming is the highest-
frequency judgment call in code review, so it's the part most worth writing down rather than
each reviewer improvising a standard.

**§3 — Safety over speed.** Every gate (license check, cache fetch, integrity verification) must
fail open to a cold, correct build rather than fail closed into a broken or silently-stale one.
*Why:* the entire pitch is shaving minutes off CI; the moment a warm restore produces a wrong
result instead of just a slow one, it's worse than not existing.

**§4 — Explainability.** Every module warm/skip decision must be traceable to a concrete,
human-readable reason in build output, not just a boolean.
*Why:* a developer debugging a stale-bytecode surprise needs to see "module X skipped — commit
range Y touched Z" in the log, not silence — the same standard blastradius itself holds for test
selection.

**§5 — Bound external storage.** Cache configuration must name a validated, non-root namespace,
and credentials must be scoped to that namespace only.
*Why:* a missing prefix or bucket-wide role turns a configuration mistake into broader access,
whereas a bounded namespace makes the storage target and its permission boundary explicit.

<!-- Grows as features harvest repeatable stances from real decisions. -->
