package io.github.baokhang83.blastradius.cachewarmer;

import io.github.baokhang83.blastradius.cachewarmer.cache.SliceCache;
import org.apache.maven.project.MavenProject;

import java.util.Optional;

/** Immutable cache state derived once from the reactor before Maven executes build mojos. */
final class RuntimeBuildContext {

    private final SliceCache cache;
    private final ImpactedModules impacts;

    RuntimeBuildContext(SliceCache cache, ImpactedModules impacts) {
        this.cache = cache;
        this.impacts = impacts;
    }

    SliceCache cache() {
        return cache;
    }

    boolean isSafeToWarm(MavenProject project) {
        return !impacts.isReactorWide() && impacts.impacts().stream()
                .noneMatch(impact -> impact.module().artifactId().equals(project.getArtifactId()));
    }

    Optional<String> coldBuildReason(MavenProject project) {
        if (impacts.isReactorWide()) {
            return impacts.reactorWideReason();
        }
        return impacts.impacts().stream()
                .filter(impact -> impact.module().artifactId().equals(project.getArtifactId()))
                .map(ModuleImpact::reason)
                .findFirst();
    }
}
