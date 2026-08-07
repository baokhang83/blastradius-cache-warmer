# Session: wire verified slices through all publishers and warmers

- **intent:** wire verified slices through all publishers and warmers
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

- **Tier A/C and Tier B publishers** — route every payload through `SliceIntegrity.put`, so each normal cache object has a corresponding key-bound checksum sidecar written through the unchanged storage interface · status: documented
- **All three warmers** — call `SliceIntegrity.fetchVerified` before archive extraction or Maven-repository placement, so no destination write begins until the exact requested payload validates · status: documented
- **WarmResult failure path** — catches the integrity exception through the existing fetch-failure boundary and reports the checksum problem as a skipped warm, leaving Maven's cold behavior available · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **A partial publication is fail-safe** — if payload publication succeeds but its sidecar cannot be written, a later warmer treats the missing sidecar as untrusted and restores nothing, rather than trusting a lone payload · status: documented
- **Each tier has an explicit missing-checksum test** — bytecode, compiler state, and dependency JAR restoration all demonstrate that absent integrity metadata prevents a write to the local build or Maven repository · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: Verify every cached payload before any restore

- **where:** `publisher and warmer Tier A, B, and C callers`
- **why:** All publishers emit the same key-bound checksum sidecar and all warmers fetch through the verifier. Missing or mismatched integrity data becomes an explainable skipped warm, so no local bytecode, compiler state, or Maven JAR is written from an unchecked cache payload.
- **alternative:** Verify only selected tiers or embed checksum logic in each warmer — rejected: an unchecked tier would remain a correctness hole, while per-tier implementations could drift from the publisher contract.
- **design:** ../design.md#rationale
- **constitution:** §3
- **trust:** ✓ verified
