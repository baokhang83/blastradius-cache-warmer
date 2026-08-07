# Blastradius PoC benchmark

T8 measures Tier A and Tier C cache warming against the real
[`baokhang83/blastradius`](https://github.com/baokhang83/blastradius) Maven reactor. It compares
the same revision in isolated cold and warm worktrees, using equivalent copies of a prepared local
Maven repository. That keeps third-party dependency downloads outside this Tier A/C measurement.
Before timing, it bootstraps Blastradius's temporary self-host plugin coordinate exactly as the
repository's CI workflow does, so the `self-host-blastradius` profile resolves locally.
The trial repository is also propagated through `MAVEN_OPTS` so Maven processes launched by the
target's own integration tests resolve that same locally installed extension.

Run it from this repository:

```bash
scripts/benchmark-blastradius.sh --runs 3
```

The default build command is `mvn -B -Pself-host-blastradius clean verify`. Supply a checked-out
Blastradius clone, another Git reference, or a different Maven invocation when needed:

```bash
scripts/benchmark-blastradius.sh \
  --source /path/to/blastradius \
  --ref origin/main \
  --runs 5 \
  -- -B -Pself-host-blastradius verify
```

The output directory contains `results.tsv`, one Maven log per trial, timing files, and the
unmeasured preparation-build log. A successful measurement requires every build to pass and every
warm log to contain a cache-warmer restore event. Otherwise the script exits with status `2` and
reports `INCONCLUSIVE`.

This is deliberate: elapsed time alone is not evidence that cache warming helped. In the current
implementation, `CacheWarmerExtension` only runs the Blastradius gate and does not yet invoke the
Tier A/C publisher or warmers. Therefore a run today is expected to be inconclusive; the harness
makes that integration gap visible instead of publishing a false speedup claim.
