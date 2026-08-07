# Design: T8 PoC benchmark harness on a real monorepo run

started: 2026-08-07
branch: feature/8-t8-poc-benchmark-harness-on-a-real-monorepo-run

## Class diagram

```mermaid
classDiagram
  class BenchmarkHarness {
    +run(source, ref, runs) exit code
  }
  class IsolatedWorkspace {
    +checkout ref
    +local Maven repository
    +target directories
  }
  class MavenRun {
    +cold run
    +warm run
    +elapsed seconds
    +build result
  }
  class WarmEvidence {
    +restored events
    +skipped events
    +status
  }
  class BenchmarkReport {
    +median duration
    +delta
    +evidence status
  }

  BenchmarkHarness --> IsolatedWorkspace : creates per run
  BenchmarkHarness --> MavenRun : invokes
  MavenRun --> WarmEvidence : emits build log
  BenchmarkHarness --> BenchmarkReport : writes TSV and summary
```

## Rationale

The harness compares equivalent cold and warm Blastradius checkouts. Each measured run receives a
fresh checkout and a copy of the same pre-populated Maven repository, keeping dependency download
time outside this Tier A/C measurement. It records the Maven exit code, elapsed time, and
cache-warmer output for every trial, then reports medians only when the builds pass.

A timing delta is meaningful only when the warm build log proves that cache state was restored.
Without that evidence, the report is inconclusive and exits nonzero rather than attributing a
change caused by normal variation or Blastradius test selection to cache warming. This matters for
the current code because the Core Extension still gates but does not invoke the publisher or
warmers. Timing-only reporting was rejected because it would allow a false performance claim.

## Sequence: compare the same Blastradius revision

```mermaid
sequenceDiagram
  participant User
  participant Harness as BenchmarkHarness
  participant Cold as Cold workspace
  participant Warm as Warm workspace
  participant Maven
  participant Report as Benchmark report

  User->>Harness: benchmark Blastradius ref
  Harness->>Cold: create isolated checkout and local repository
  Harness->>Maven: run cold build and record elapsed time
  Maven-->>Cold: build log and exit code
  Harness->>Warm: create equivalent isolated checkout and local repository
  Harness->>Maven: run warm build with cache-warmer extension
  Maven-->>Warm: build log and exit code
  Harness->>Harness: require restored warm evidence
  alt evidence present and builds pass
    Harness->>Report: write median times and delta
    Report-->>User: measured result
  else no restore evidence or failed build
    Harness->>Report: write inconclusive result and diagnostics
    Report-->>User: no speedup claim
  end
```
