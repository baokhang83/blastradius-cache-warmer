# Design: T7 Tier C warmer incremental compiler state restore into target/maven-status

started: 2026-08-07
branch: feature/7-t7-tier-c-warmer-incremental-compiler-state-restore-into-t

## Class diagram

```mermaid
classDiagram
  class CompilerStateWarmer {
    +warm(MavenProject) WarmResult
  }
  class SiblingBytecodeWarmer {
    +warm(MavenProject) WarmResult
  }
  class ArchiveRestorer {
    +restore(byte[], Path) void
  }
  class SliceKeyComputer {
    +keyFor(MavenProject, Tier) String
  }
  class SliceCache {
    +fetch(String) Optional~byte[]~
  }
  class WarmResult {
    +status WarmStatus
    +reason String
  }
  class MavenProject

  CompilerStateWarmer --> SliceKeyComputer : Tier.COMPILER_STATE
  CompilerStateWarmer --> SliceCache
  CompilerStateWarmer --> ArchiveRestorer
  CompilerStateWarmer --> WarmResult
  CompilerStateWarmer --> MavenProject
  SiblingBytecodeWarmer --> ArchiveRestorer
  SiblingBytecodeWarmer --> WarmResult
```

## Sequence: restore compiler state

```mermaid
sequenceDiagram
  participant Caller
  participant Warmer as CompilerStateWarmer
  participant Keys as SliceKeyComputer
  participant Cache as SliceCache
  participant Restorer as ArchiveRestorer
  participant Target as target/maven-status

  Caller->>Warmer: warm(module)
  Warmer->>Warmer: resolve build directory and check target
  alt target already exists
    Warmer-->>Caller: skipped with reason
  else target absent
    Warmer->>Keys: keyFor(module, COMPILER_STATE)
    Keys-->>Warmer: cache key
    Warmer->>Cache: fetch(key)
    alt cache miss or lookup failure
      Warmer-->>Caller: skipped with reason
    else archive found
      Warmer->>Restorer: restore archive into staging directory
      Restorer->>Target: move only after safe extraction
      alt extraction succeeds
        Warmer-->>Caller: restored with reason
      else unsafe or malformed archive
        Warmer-->>Caller: skipped with reason
      end
    end
  end
```

## Rationale

`target/maven-status` is Maven Compiler Plugin bookkeeping, not bytecode. The warmer restores it
only when the destination is absent, so it never overwrites compiler state already produced by a
local build. Any failed key computation, cache lookup, missing cache entry, malformed archive, or
unsafe archive entry returns a skipped `WarmResult` and leaves Maven to perform a cold compile.

Tier A and Tier C use the same ZIP restoration safety boundary. `ArchiveRestorer` extracts into a
sibling staging directory, rejects entries that escape it, and moves the completed tree into place
only after successful extraction. A shared helper keeps that behavior identical for both warmers.
Duplicating the ZIP code in the Tier C warmer was rejected because future correctness or security
fixes could otherwise diverge.
