# Design: T11 Tier B warmer down-selected Maven repository restore

started: 2026-08-07
branch: feature/11-t11-tier-b-warmer-down-selected-maven-repository-restore

## Scope

T11 restores only the third-party JARs selected by T9's `DependencyManifest`. Each coordinate
maps to the same `DependencySliceKey` that T10 used for publication, and the fetched bytes are
placed at that coordinate's standard Maven local-repository path. The caller supplies the
already down-selected manifest and the local repository root.

This task is the Tier B restore primitive, not Core Extension lifecycle wiring. A future
integration caller will choose safe modules using T2, build their manifests, and call this warmer
before Maven dependency resolution.

## Class diagram

```mermaid
classDiagram
  class DependencySliceWarmer {
    -SliceCache cache
    +warm(DependencyManifest, Path) List~WarmResult~
  }
  class DependencyManifest {
    +artifacts List~DependencyCoordinate~
  }
  class DependencyCoordinate {
    +repositoryPath() Path
  }
  class DependencySliceKey {
    +keyFor(DependencyCoordinate) String
  }
  class SliceCache {
    <<interface>>
    +fetch(String) Optional~byte[]~
  }
  class WarmResult {
    +status WarmStatus
    +reason String
  }
  class LocalMavenRepository {
    <<directory>>
  }
  DependencySliceWarmer --> DependencyManifest : consumes selected artifacts
  DependencyManifest --> DependencyCoordinate
  DependencySliceWarmer --> DependencySliceKey : derives cache keys
  DependencySliceWarmer --> SliceCache : fetches JAR bytes
  DependencySliceWarmer --> LocalMavenRepository : writes missing JARs
  DependencySliceWarmer --> WarmResult : reports each result
```

## Rationale

One `WarmResult` is returned for each manifest coordinate, rather than an all-or-nothing result.
Tier B can have a mixture of hits, misses, locally present JARs, and transient cache failures,
per-artifact reasons make that behavior observable and allow Maven to resolve only what remains
cold. A single aggregate result would hide which dependency did or did not benefit from the
cache.

The warmer writes only an absent destination inside the normalized local repository root. It
never replaces an existing Maven artifact, and it rejects a coordinate whose computed path escapes
that root. Cache failures, misses, and filesystem errors return `WarmResult.skipped` rather than
interrupting the build. That preserves the cache as an optimization and applies constitution
§3's fail-open rule.

T11 fetches raw JAR bytes rather than using `ArchiveRestorer`: T10 publishes individual JAR
objects, so there is no archive to unpack. The raw-file restore avoids inventing a second archive
format and uses Maven's repository path as the complete placement contract.

## Sequence: restore selected third-party JARs

```mermaid
sequenceDiagram
  participant Caller as pre-resolution integration
  participant Warmer as DependencySliceWarmer
  participant Manifest as DependencyManifest
  participant Repo as local Maven repository
  participant Cache as SliceCache

  Caller->>Warmer: warm(manifest, repository)
  loop each selected coordinate
    Warmer->>Manifest: read repositoryPath
    Warmer->>Repo: validate absent destination inside root
    alt destination is safe and absent
      Warmer->>Cache: fetch(dependency_slice/path)
      alt cache hit
        Cache-->>Warmer: JAR bytes
        Warmer->>Repo: place JAR from staging file
        Warmer-->>Caller: restored reason
      else cache miss or failure
        Cache-->>Warmer: no bytes or error
        Warmer-->>Caller: skipped reason
      end
    else destination exists or path escapes root
      Warmer-->>Caller: skipped reason
    end
  end
```

## Verification

Temporary repository tests will prove that selected cache hits are restored at their exact Maven
paths, existing artifacts are preserved, cache misses and failures do not stop other entries, and
an escaping coordinate cannot write outside the supplied repository root.
