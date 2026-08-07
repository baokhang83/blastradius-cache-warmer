# Session: Document least-privilege IAM and safe cache purging

- **intent:** Document least-privilege IAM and safe cache purging
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

- **Reader and writer IAM roles** — allow only object reads, or reads plus writes, beneath the
  configured cache namespace; neither role includes routine listing or deletion · status:
  documented
- **Emergency purge role and helper** — combines namespace-constrained `ListBucket` with
  object-level `DeleteObject` because recursive AWS CLI deletion must first discover candidates;
  the helper runs that discovery as a dry run unless `--apply` is explicit · status: documented
- **Versioned invalidation** — advances the cache namespace for normal recovery, leaving prior
  objects available for diagnosis or rollback rather than treating deletion as the default ·
  status: documented

### Hard-won conditions (gotchas, root causes, limitations)

<!-- - **<the non-obvious thing>** — <why it's this way / what breaks otherwise> · status: documented -->

- **Recursive purge needs bounded list permission** — `DeleteObject` alone cannot support a
  recursive CLI purge, but a bucket-level `ListBucket` grant is safe only when its `s3:prefix`
  condition names the same namespace as the object delete grant · status: documented
- **Checksums are not writer authentication** — a writer able to replace a payload can replace
  its integrity sidecar, so integrity checks must be paired with narrow IAM trust boundaries ·
  status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: Separate routine cache roles from emergency purge

- **where:** `docs/security.md and scripts/purge-s3-cache-prefix.sh`
- **why:** Routine readers and writers remain unable to delete while an explicitly assumed purge role lists and deletes only one namespace, and the command previews its target unless --apply is supplied.
- **alternative:** Give CI writers deletion rights or permit broad recursive deletion — rejected: an ordinary credential or operator mistake could irreversibly delete unrelated cache data.
- **design:** ../design.md#security-rationale
- **constitution:** §5
- **trust:** ✓ verified
