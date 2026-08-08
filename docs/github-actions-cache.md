# GitHub Actions cache backend

GitHub Actions is the default cache backend for runtime wiring. It uses `github-actions` when
`blastradius.cache.backend` is absent; use
`-Dblastradius.cache.backend=s3` only to select the existing S3 backend explicitly.

## Runner requirements

`GitHubActionsSliceStore` can run only inside a GitHub Actions job. The runner provides these
short-lived values for the current job:

- `ACTIONS_RESULTS_URL` identifies the runner cache service
- `ACTIONS_RUNTIME_TOKEN` authorizes requests to that service

The backend sends that token only to the runner cache service. That service returns signed URLs
for the actual byte download and upload, so no personal access token, AWS credential, or cache
URL belongs in repository configuration.

GitHub scopes these values to Actions rather than `run` steps. Before Maven runs, expose them to
the job environment with a runtime-export action, for example:

```yaml
- name: Expose GitHub Actions cache runtime
  uses: crazy-max/ghaction-github-runtime@04d248b84655b509d8c44dc1d6f990c879747487 # v4.0.0
```

This is an Actions platform boundary, not a Blastradius requirement. The extension fails open if
the values are unavailable, so non-Actions builds remain ordinary cold Maven builds.

Outside a GitHub Actions job, the environment is intentionally incomplete and store construction
fails with a clear configuration error. `CacheWarmerExtension` catches that setup failure and
Maven performs its normal cold build rather than selecting S3 implicitly.

## Cache behavior

The backend looks up exact slice keys with the fixed cache schema version
`blastradius-cache-warmer-v1`. A runner-service miss becomes a `SliceCache` miss. A transport,
token, upload, or finalization failure remains a `SliceCacheException`, allowing the future
extension fail-open boundary to report the cache problem and continue cold.

GitHub Actions cache content is available according to GitHub's cache access rules. Do not write
credentials, tokens, or other secrets into any directory that can become a cached slice. See
GitHub's [dependency caching reference](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching)
for cache access restrictions and retention behavior.
