# Session: add key-bound checksum verification contract

- **intent:** add key-bound checksum verification contract
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

- **SliceIntegrity** — is the shared payload-plus-sidecar protocol, publishing a raw SHA-256 digest and returning a payload only after a matching digest is available for the same requested key · status: documented
- **SliceIntegrityException** — distinguishes an absent or mismatched digest from a clean cache miss, allowing existing warmer fail-open handling to report the integrity reason without restoring untrusted bytes · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **Digest input is key-bound** — the requested key, a byte separator, and payload bytes are hashed together, so an otherwise valid payload/checksum pair copied to a different cache key does not validate there · status: documented
- **Checksum scope is deliberately limited** — a sidecar detects corruption and accidental mix-ups but does not establish publisher identity because a writer could replace both objects; IAM and trust-boundary controls remain T13 work · status: follow-up

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: Use a shared key-bound SHA-256 checksum sidecar

- **where:** `cache/SliceIntegrity`
- **why:** A single helper gives every tier one publish and verification protocol. Hashing the requested key with the payload catches corruption and rejects a valid object pair accidentally placed at the wrong key while keeping SliceCache storage-neutral.
- **alternative:** Payload-only checksums or per-tier digest code — rejected: a payload-only digest cannot bind bytes to their intended cache address, and duplicated implementations would let tiers drift.
- **design:** ../design.md#rationale
- **constitution:** §3
- **trust:** ✓ verified
