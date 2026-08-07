# Session: S3SliceStore: AWS SDK v2-backed SliceCache implementation

- **intent:** S3SliceStore: AWS SDK v2-backed SliceCache implementation
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

- **`S3SliceStore`** — implements `SliceCache` against `S3Client` (AWS SDK v2). Takes an
  already-built `S3Client`, a bucket, and an optional key prefix; prepends the prefix to every
  object key (`"<prefix>/<key>"`, or bare `<key>` when the prefix is empty) so multiple
  environments/CI setups can share one bucket without key collisions · status: documented
- **AWS SDK dependency shape (`pom.xml`)** — `software.amazon.awssdk:s3` (compile scope, not
  `provided` - unlike `maven-core`/`slf4j-api`, Maven doesn't supply this) plus
  `url-connection-client`, the lightest sync HTTP client v2 ships (backed by
  `java.net.HttpURLConnection`, no Netty/Apache HttpClient). Versions are pinned together via the
  `software.amazon.awssdk:bom` import in `dependencyManagement` · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **`s3`'s default runtime HTTP client (`apache5-client`) had to be excluded** — its own
  transitive `httpcore5` versions didn't converge with the BOM (`httpcore5:5.4` vs `5.4.2` pulled
  in by different paths through AWS's own dependency graph), tripping the enforcer's
  `dependencyConvergence` rule. Excluding it is safe because `url-connection-client` supplies the
  HTTP implementation `S3Client` actually needs; this wasn't a choice we made, it was fixing a
  version mismatch inside AWS SDK's own published poms · status: documented
- **`S3Client` is fakeable directly** — it's an interface with only `serviceName()`/`close()` as
  truly abstract methods (everything else, including `getObjectAsBytes`/`putObject`, is a default
  method); implementing it directly and overriding just those four methods gave a real,
  no-mocking-framework fake that still proves the actual request/response types line up
  · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: AWS SDK v2 over hand-rolled S3 HTTP client

- **where:** `pom.xml`
- **why:** SigV4 request signing and the standard credential provider chain (env vars, ~/.aws/credentials, instance/task role) are correctness- and security-critical plumbing. Using the official SDK means that's vetted code, not ours to get subtly wrong. Core Extensions resolve their own dependency graph into an isolated realm (confirmed against T1's pom - no shading needed), so the extra dependency weight doesn't touch Maven's own classpath.
- **alternative:** Hand-roll a minimal HTTP client + our own SigV4 signer - zero dependency footprint, but reinvents exactly the kind of security-sensitive code the constitution's Safety over speed principle warns against reimplementing.
- **constitution:** §3
- **trust:** ⚠ not independently verified

## Decision: byte[] payload, not a stream

- **where:** `src/main/java/io/github/baokhang83/blastradius/cachewarmer/cache/SliceCache.java`
- **why:** Slices are one module's compiled classes or compiler state - realistically tens of KB to a few MB. byte[] keeps the interface and its tests simple (no content-length bookkeeping, no stream lifecycle across the interface boundary), and that simplicity is worth more than streaming's memory savings at this size.
- **alternative:** InputStream-based fetch/put - more correct for large payloads, but adds real ceremony (S3 uploads need a known content length; streams need explicit closing across the SliceCache boundary) that isn't earning its cost yet.
- **constitution:** §2
- **trust:** ⚠ not independently verified

## Decision: NoSuchKeyException is a miss; every other SdkException is a failure

- **where:** `src/main/java/io/github/baokhang83/blastradius/cachewarmer/cache/S3SliceStore.java`
- **why:** A clean cache miss (Optional.empty()) and a cache that couldn't be reached (SliceCacheException) need to be distinguishable by the caller, because a warmer's correct reaction differs: a miss just means 'nothing to restore, proceed cold' with no reason to log; a transport failure is worth logging so a human can tell 'the cache is unhealthy' apart from 'this module just isn't cached yet'. Catching NoSuchKeyException specifically (not e.g. checking a response code) uses the SDK's own typed signal for the one case that isn't an error.
- **alternative:** Treat every non-2xx S3 response as Optional.empty() - simpler code, but silently swallows real outages (wrong credentials, bucket deleted, network partition) as ordinary misses, which is exactly the silent-staleness failure mode Safety over speed rules out.
- **constitution:** §3
- **trust:** ⚠ not independently verified
