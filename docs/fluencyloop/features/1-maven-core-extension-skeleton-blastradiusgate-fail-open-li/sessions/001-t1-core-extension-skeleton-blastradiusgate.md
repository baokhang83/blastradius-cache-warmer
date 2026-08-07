# Session: T1: Core Extension skeleton + BlastradiusGate

- **intent:** T1: Core Extension skeleton + BlastradiusGate
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

- **`CacheWarmerExtension`** — `AbstractMavenLifecycleParticipant` subclass; Maven's Sisu
  container instantiates it via `@Named("cache-warmer") @Singleton` and constructor-injects
  `org.slf4j.Logger` and `BlastradiusGate`. `afterProjectsRead` fires once the reactor is
  built, before dependency resolution - the manifesto's pre-resolution phase. · status:
  documented
- **`BlastradiusGate`** — pure yes/no check: does any project in the reactor declare
  `io.github.baokhang83.blastradius:blastradius-maven-plugin` in its build plugins. No file
  I/O, no exception handling of its own. · status: documented
- **`META-INF/sisu/javax.inject.Named`** — the index Maven's Sisu container reads to discover
  `@Named` components without a hand-written `components.xml`. Generated at build time by the
  `sisu-maven-plugin`'s `main-index` goal (bound to `process-classes`), scanning compiled
  bytecode - not hand-maintained. · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **Core Extensions can't isolate their classpath the way Mojos do** — a Mojo loads in an
  isolated per-plugin realm, so `blastradius-maven-plugin` can freely depend on Jackson etc.
  A Core Extension shares Maven's own core classpath, so any dependency it ships at more than
  `provided` scope risks colliding with Maven's own bundled version of the same library. All
  three of `maven-core`, `slf4j-api`, and `javax.inject` are `provided` for exactly this
  reason. · status: documented
- **blastradius's real local state is not a fixed path** — `SelectMojo`'s `indexPath` default
  (`.blastradius/index.json`) gets the merge-base SHA spliced in before the filename, so the
  actual on-disk shape is `.blastradius/<sha>/index.json`, and it may not exist at all on a
  fresh clone even for a genuine blastradius user. This is why the gate checks reactor plugin
  declarations instead of that path - see the first Decision below. · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: Gate on blastradius-maven-plugin presence, not a license file

- **where:** `src/main/java/io/github/baokhang83/blastradius/cachewarmer/BlastradiusGate.java`
- **why:** The first design gated on an invented .blastradius/license.json. Blastradius is Apache-2.0 OSS with no license concept, and its real local state (SelectMojo's indexPath) isn't even a flat file - the default splices a merge-base SHA in (.blastradius/<sha>/index.json), and is transient/absent on a fresh clone anyway. The correct, stable signal for 'is this reactor a blastradius user' is presence of blastradius-maven-plugin in the reactor's declared build plugins.
- **alternative:** Parse .blastradius/index.json for a valid index; rejected because that state doesn't exist yet on plenty of legitimate blastradius reactors (fresh clone, first CI run), which would make the gate fail-closed exactly when it must fail-open.
- **trust:** ⚠ not independently verified

## Decision: GateResult drops INVALID - only PRESENT/ABSENT

- **where:** `src/main/java/io/github/baokhang83/blastradius/cachewarmer/GateResult.java`
- **why:** A plugin declaration either exists in the reactor or it doesn't - there's no corrupt/expired state to model once the gate stopped parsing a file. Constitution SS2 (simplicity): don't carry an enum value for a state that can't occur.
- **alternative:** Keep a 3-state enum (PRESENT/ABSENT/INVALID) for forward-compatibility with some future richer check; rejected as speculative generality with no current use.
- **trust:** ⚠ not independently verified

## Decision: Fail-open catch lives in CacheWarmerExtension, not inside BlastradiusGate

- **where:** `src/main/java/io/github/baokhang83/blastradius/cachewarmer/CacheWarmerExtension.java`
- **why:** Constitution SS3 requires every gate to fail open. BlastradiusGate.check stays a plain function with no defensive try/catch; applyGate wraps the gate call (and any future tier-warmer calls) in one broad catch(RuntimeException), logs, and returns - a single, visible fail-open boundary instead of scattering try/catch through internal logic.
- **alternative:** Swallow exceptions inside BlastradiusGate itself; rejected because it hides the fail-open behavior inside a class whose job is just answering a yes/no question, and later tier warmers (T2+) need the same boundary anyway.
- **trust:** ⚠ not independently verified

## Decision: applyGate(List<MavenProject>) split out from afterProjectsRead(MavenSession)

- **where:** `src/main/java/io/github/baokhang83/blastradius/cachewarmer/CacheWarmerExtension.java`
- **why:** Constructing a real MavenSession needs a RepositorySystemSession, MavenExecutionRequest, and MavenExecutionResult this unit never touches. Splitting the actual gate-then-warm logic into a package-private method taking the project list keeps BlastradiusGateTest/CacheWarmerExtensionTest plain JUnit, matching blastradius's own no-mock-framework test style, while afterProjectsRead stays a one-line delegation Maven's lifecycle contract requires.
- **alternative:** Construct a real MavenSession in tests (possibly via Mockito); rejected - blastradius's own test suite uses no mocking framework anywhere, and a real MavenSession is integration-test weight for what's actually a unit-level gate check.
- **trust:** ⚠ not independently verified
