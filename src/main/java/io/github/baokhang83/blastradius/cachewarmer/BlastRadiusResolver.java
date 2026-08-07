package io.github.baokhang83.blastradius.cachewarmer;

import io.github.baokhang83.blastradius.cachewarmer.git.GitDiff;
import io.github.baokhang83.blastradius.cachewarmer.reactor.ModuleId;
import io.github.baokhang83.blastradius.cachewarmer.reactor.ReactorModuleGraph;
import org.apache.maven.project.MavenProject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * git diff + the reactor's module graph -&gt; the set of modules a cache restore isn't safe for,
 * with a reason per module (SS4 Explainability). Not wired into {@link CacheWarmerExtension} yet
 * - that's a follow-up slice once this is solid, the same split T1 used between
 * {@link BlastradiusGate} and the extension. No {@code @Named}/{@code @Singleton} here yet either
 * for the same reason: adding DI annotations before anything actually injects this would claim a
 * wiring that doesn't exist.
 */
public class BlastRadiusResolver {

    private final GitDiff gitDiff;

    public BlastRadiusResolver(GitDiff gitDiff) {
        this.gitDiff = gitDiff;
    }

    public ImpactedModules resolve(List<MavenProject> projects, String baseRef) {
        List<String> changedPaths = gitDiff.changedPaths(baseRef);
        if (changedPaths.isEmpty()) {
            return ImpactedModules.of(Set.of());
        }

        ReactorModuleGraph graph = ReactorModuleGraph.from(projects);
        Map<ModuleId, String> reasons = new LinkedHashMap<>();
        for (String path : changedPaths) {
            if (graph.isReactorWide(path)) {
                return ImpactedModules.reactorWide(
                        "reactor-wide change: " + path + " (outside any module's basedir)");
            }
            ModuleId owner = graph.moduleOf(path).orElseThrow();
            for (ModuleId dependent : graph.dependentsOf(owner)) {
                reasons.putIfAbsent(dependent, dependent.equals(owner)
                        ? "changed: " + path
                        : "depends on " + owner.artifactId() + ", which changed via " + path);
            }
        }

        Set<ModuleImpact> impacts = new LinkedHashSet<>();
        reasons.forEach((module, reason) -> impacts.add(new ModuleImpact(module, reason)));
        return ImpactedModules.of(impacts);
    }
}
