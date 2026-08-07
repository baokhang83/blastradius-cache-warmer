# Design: T13 security review for cache gating storage permissions and invalidation

started: 2026-08-07
branch: feature/13-t13-security-review-for-cache-gating-storage-permissions

## Scope

T13 turns the cache security review into three enforceable or operational boundaries:

- `S3SliceStore` requires an explicit, validated cache namespace rather than silently using a
  bucket root,
- `docs/security.md` describes the real trust boundary, least-privilege reader and writer roles,
  and the integrity limitation that T12 intentionally leaves to IAM,
- a purge script defaults to dry-run and requires an explicit `--apply` before it can delete a
  precisely named bucket prefix.

The blastradius plugin-presence gate remains a scope check, not an authorization mechanism. The
extension is not yet wired to remote storage, so this task establishes the contract integration
must follow rather than claiming that a POM declaration proves trust.

## Security rationale

The cache namespace is a safety boundary rather than an optional convenience. Requiring a
nonempty, normalized prefix in `S3SliceStore` means one configured cache can be mapped directly
to one narrow IAM resource ARN. It rejects the simpler empty-prefix alternative because a missing
setting must not silently broaden operations to an entire bucket.

The Maven plugin-presence gate is deliberately not used as security proof: repository metadata is
input to the build, not an identity that AWS can trust. CI should instead receive temporary
credentials through an IAM role that permits only `GetObject` for readers and `GetObject` plus
`PutObject` for writers under the configured namespace. A temporary, separately granted purge
role may use `DeleteObject` only under that namespace.

Purge is an exceptional recovery operation. Normal invalidation advances to a new versioned
namespace, keeping the previous objects intact for diagnosis and rollback. The purge helper
therefore previews the exact recursive target by default and performs deletion only with an
explicit `--apply` flag.

Integrity sidecars verify accidental corruption and key-payload mixups, but they do not
authenticate writers: a principal that can replace an object can replace its sidecar too. Narrow
IAM roles, private bucket controls, encrypted transport, and encryption at rest make that writer
trust boundary explicit.

## Class diagram

```mermaid
classDiagram
  class S3SliceStore {
    -String bucket
    -String keyPrefix
    +fetch(String) Optional~byte[]~
    +put(String, byte[]) void
  }
  class CacheNamespace {
    <<validated value>>
  }
  class SliceIntegrity {
    +fetchVerified(SliceCache, String) Optional~byte[]~
  }
  class S3ReaderRole {
    <<IAM policy>>
  }
  class S3WriterRole {
    <<IAM policy>>
  }
  class PurgeScript {
    +dry-run(bucket, prefix) void
    +apply(bucket, prefix) void
  }
  S3SliceStore --> CacheNamespace : requires nonempty prefix
  S3SliceStore --> S3ReaderRole : GetObject only
  S3SliceStore --> S3WriterRole : PutObject and GetObject only
  SliceIntegrity --> S3SliceStore : verifies payload sidecars
  PurgeScript --> CacheNamespace : exact explicit target
```

## Sequence: bounded cache use and purge

```mermaid
sequenceDiagram
  participant Config as cache configuration
  participant Store as S3SliceStore
  participant IAM as CI IAM role
  participant Operator
  participant Purge as purge script
  participant S3

  Config->>Store: bucket and versioned namespace
  Store->>Store: reject absent or unsafe namespace
  Store->>IAM: request object under namespace
  IAM->>S3: allow only approved action and prefix
  Operator->>Purge: bucket and exact prefix
  Purge->>S3: list deletion candidates in dry-run mode
  alt operator supplies --apply
    Purge->>S3: delete only the exact prefix
  else no --apply
    Purge-->>Operator: no deletion performed
  end
```
