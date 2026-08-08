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
 * with a reason per module (SS4 Explainability). It has no dependency on a build plugin: every
 * Maven reactor supplies the project model needed to construct the graph.
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
