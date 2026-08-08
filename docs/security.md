# Cache storage security

`CacheWarmerExtension` uses GitHub Actions cache by default. This document defines the S3 boundary
the explicit `-Dblastradius.cache.backend=s3` alternative preserves.

## Trust boundary

Cache-warmer determines safe restores from Git changes and Maven's reactor graph. That controls
build correctness, not authorization: repository inputs can be changed by an untrusted
contributor. AWS IAM determines whether the CI workload may read or write cache objects.

Every S3-backed cache must set `blastradius.cache.s3.bucket` and a nonempty, versioned
`blastradius.cache.s3.namespace`, for example
`example-org/cache-warmer/v1`. `S3SliceStore` rejects a blank, root-like, or traversal-like
namespace. This makes the configured key shape and the IAM resource match directly:

```text
s3://CACHE_BUCKET/example-org/cache-warmer/v1/<cache-key>
arn:aws:s3:::CACHE_BUCKET/example-org/cache-warmer/v1/*
```

Never grant a cache role `s3:*`, a bucket-wide object ARN, or access to a bucket root merely for
convenience.

## Roles for CI

Use a workload identity that assumes an IAM role and receives temporary credentials. Do not put
long-lived AWS access keys in the repository, Maven settings, or CI secrets when a role can be
used. Replace `CACHE_BUCKET` and `CACHE_NAMESPACE` in every template before applying it.

### Cache reader

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "ReadOnlyCacheNamespace",
    "Effect": "Allow",
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::CACHE_BUCKET/CACHE_NAMESPACE/*"
  }]
}
```

### Cache writer

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "ReadAndWriteCacheNamespace",
    "Effect": "Allow",
    "Action": ["s3:GetObject", "s3:PutObject"],
    "Resource": "arn:aws:s3:::CACHE_BUCKET/CACHE_NAMESPACE/*"
  }]
}
```

Writers do not receive deletion rights. The checksum sidecars introduced in T12 detect accidental
corruption and key-payload mixups, but do not authenticate a writer: a principal that can replace
an object can replace its sidecar too. Restrict who can assume the writer role.

### Emergency purge role

Invalidation normally advances the namespace, for example from `v1` to `v2`; it does not delete
the old prefix. For an exceptional purge, use a separately assumed, temporary role. Recursive
AWS CLI deletion needs `ListBucket` to discover candidates and `DeleteObject` to remove them, so
the list permission is constrained to the one namespace:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ListOnlyTheCacheNamespace",
      "Effect": "Allow",
      "Action": "s3:ListBucket",
      "Resource": "arn:aws:s3:::CACHE_BUCKET",
      "Condition": {
        "StringLike": {"s3:prefix": ["CACHE_NAMESPACE/*"]}
      }
    },
    {
      "Sid": "DeleteOnlyTheCacheNamespace",
      "Effect": "Allow",
      "Action": "s3:DeleteObject",
      "Resource": "arn:aws:s3:::CACHE_BUCKET/CACHE_NAMESPACE/*"
    }
  ]
}
```

Run [purge-s3-cache-prefix.sh](../scripts/purge-s3-cache-prefix.sh) with no `--apply` first and
inspect its candidates. The script refuses blank, root-like, or traversal-like prefixes. Only a
second, explicit invocation with `--apply` performs deletion.

## Bucket baseline

- Enable all S3 Block Public Access settings at the account and bucket level unless public access
  is an explicit requirement.
- Require HTTPS with a bucket-policy deny for `aws:SecureTransport` set to `false`.
- Keep server-side encryption enabled. SSE-S3 is sufficient for many caches; use SSE-KMS only
  when its key controls are required, and then grant the relevant role only the matching KMS
  permissions.
- Prefer bucket-owner-enforced object ownership and avoid ACL-based sharing.
- Enable CloudTrail data events or equivalent audit coverage for cache writes and purges.

AWS reference material: [S3 security best practices](https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html),
[Block Public Access](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html),
and [required S3 API permissions](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-with-s3-policy-actions.html).
