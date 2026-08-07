# Session: Add a GitHub Actions SliceCache backend

- **intent:** Add a GitHub Actions SliceCache backend
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

<!-- - **<component / role / mechanism>** — <what it does, and under what conditions> · status: documented -->

- **`GitHubActionsSliceStore`** — preserves the two-method `SliceCache` contract while mapping an
  exact runner-service miss to `Optional.empty()` and surfacing transfer failures as
  `SliceCacheException` · status: documented
- **`GitHubActionsCacheClient`** — authenticates only cache-service control requests with the
  runner's short-lived token, then transfers raw bytes through service-issued signed URLs and
  finalizes the exact key and byte count · status: documented
- **`CacheBackend`** — resolves an absent backend setting to GitHub Actions and requires the
  explicit `blastradius.cache.backend=s3` selection for S3 · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **The runner protocol is not the public REST API** — repository cache endpoints can list and
  delete metadata, but only the runner cache service supplies the signed content URLs required by
  a `SliceCache` implementation · status: documented
- **Signed uploads identify an Azure block blob** — the upload request carries
  `x-ms-blob-type: BlockBlob`, which lets the signed storage endpoint accept the byte payload
  before the runner service finalizes the cache entry · status: documented
- **No implicit local S3 fallback** — GitHub Actions environment settings are deliberately
  required for this backend, and the still-unwired extension remains cold outside Actions rather
  than inferring a different store from ambient credentials · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: Use the runner cache service behind a SliceCache adapter

- **where:** `GitHubActionsCacheClient and GitHubActionsSliceStore`
- **why:** The adapter preserves storage-neutral publishers and warmers while the client confines short-lived runner token, v2 protocol, and signed URL handling to one boundary.
- **alternative:** Call the public repository cache REST API or leak HTTP details into warmers — rejected: it cannot transfer workflow cache content and would couple storage protocol to cache consumers.
- **design:** ../design.md#design-call
- **constitution:** §2
- **trust:** ✓ verified

## Decision: Default to GitHub Actions and require an explicit S3 opt-in

- **where:** `CacheBackend`
- **why:** GitHub Actions cache is available to workflow jobs without a separately managed S3 account or long-lived storage credentials.
- **alternative:** Default to S3 or infer it from ambient credentials — rejected: it preserves avoidable account setup and makes backend selection implicit.
- **design:** ../design.md#approved-rationale
- **constitution:** §5
- **trust:** ✓ verified
