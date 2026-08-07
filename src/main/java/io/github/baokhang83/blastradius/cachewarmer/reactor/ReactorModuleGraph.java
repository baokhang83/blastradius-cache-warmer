package io.github.baokhang83.blastradius.cachewarmer.reactor;

import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The reactor's module dependency graph, built from a live {@link MavenProject} list rather than
 * re-parsing POMs: {@code afterProjectsRead} already hands us every module's real basedir and
 * declared dependencies, so there's nothing to re-derive. An edge exists module -> module for
 * both a declared {@code <dependency>} on another reactor module and an in-reactor
 * {@code <parent>} relationship - a parent POM change reaches its children the same way a
 * dependency change does, without a separate reactor-wide special case.
 *
 * <p>Rebuilt fresh per {@link #from} call; nothing here is cached across builds.
 */
public final class ReactorModuleGraph {

    private final List<ModuleId> modules;
    private final Map<ModuleId, Set<ModuleId>> transitiveDependents;

    private ReactorModuleGraph(List<ModuleId> modules, Map<ModuleId, Set<ModuleId>> transitiveDependents) {
        this.modules = modules;
        this.transitiveDependents = transitiveDependents;
    }

    /**
     * Every module's basedir is relativized against the execution-root project's basedir, which
     * is assumed to be the git repository root - the setup {@code GitDiff}'s paths and this
     * graph's basedirs need to share one coordinate space in. Without an execution-root project
     * to anchor on, nothing can be relativized, so the graph is empty and every lookup falls back
     * to {@link #isReactorWide} being {@code true} - never guess narrower than we can support.
     */
    public static ReactorModuleGraph from(List<MavenProject> projects) {
        Optional<MavenProject> root = projects.stream().filter(MavenProject::isExecutionRoot).findFirst();
        if (root.isEmpty()) {
            return new ReactorModuleGraph(List.of(), Map.of());
        }
        Path rootBasedir = root.get().getBasedir().toPath();

        Map<String, ModuleId> byArtifactId = new LinkedHashMap<>();
        for (MavenProject project : projects) {
            String basedir = normalize(rootBasedir.relativize(project.getBasedir().toPath()).toString());
            byArtifactId.put(project.getArtifactId(), new ModuleId(project.getArtifactId(), basedir));
        }

        Map<ModuleId, Set<ModuleId>> dependsOn = new LinkedHashMap<>();
        for (MavenProject project : projects) {
            ModuleId id = byArtifactId.get(project.getArtifactId());
            Set<ModuleId> dependencies = new LinkedHashSet<>();
            for (Dependency dependency : project.getDependencies()) {
                ModuleId target = byArtifactId.get(dependency.getArtifactId());
                if (target != null) {
                    dependencies.add(target);
                }
            }
            MavenProject parent = project.getParent();
            if (parent != null) {
                ModuleId parentId = byArtifactId.get(parent.getArtifactId());
                if (parentId != null) {
                    dependencies.add(parentId);
                }
            }
            dependsOn.put(id, dependencies);
        }

        Map<ModuleId, Set<ModuleId>> directDependents = new LinkedHashMap<>();
        for (ModuleId id : byArtifactId.values()) {
            directDependents.put(id, new LinkedHashSet<>());
        }
        dependsOn.forEach((dependant, dependencies) ->
                dependencies.forEach(dependency -> directDependents.get(dependency).add(dependant)));

        Map<ModuleId, Set<ModuleId>> transitiveDependents = new LinkedHashMap<>();
        for (ModuleId id : byArtifactId.values()) {
            transitiveDependents.put(id, closure(id, directDependents));
        }

        return new ReactorModuleGraph(List.copyOf(byArtifactId.values()), transitiveDependents);
    }

    /** Every module that depends on {@code module}, directly or transitively, plus itself. */
    public Set<ModuleId> dependentsOf(ModuleId module) {
        return transitiveDependents.getOrDefault(module, Set.of(module));
    }

    /** The module whose basedir most specifically contains {@code repoRelativePath}, if any. */
    public Optional<ModuleId> moduleOf(String repoRelativePath) {
        String normalized = normalize(repoRelativePath);
        return modules.stream()
                .filter(module -> owns(module.basedir(), normalized))
                .max(Comparator.comparingInt(module -> module.basedir().length()));
    }

    /**
     * True when {@code repoRelativePath} can't be attributed to any module - conservatively,
     * this means treat every module as impacted, never guess at a narrower set.
     */
    public boolean isReactorWide(String repoRelativePath) {
        return moduleOf(repoRelativePath).isEmpty();
    }

    private static boolean owns(String basedir, String path) {
        return basedir.isEmpty() || path.equals(basedir) || path.startsWith(basedir + "/");
    }

    private static Set<ModuleId> closure(ModuleId start, Map<ModuleId, Set<ModuleId>> directDependents) {
        Set<ModuleId> visited = new LinkedHashSet<>();
        Deque<ModuleId> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            ModuleId current = pending.remove();
            if (visited.add(current)) {
                pending.addAll(directDependents.getOrDefault(current, Set.of()));
            }
        }
        return Set.copyOf(visited);
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }
}
