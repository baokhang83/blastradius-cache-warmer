package io.github.baokhang83.blastradius.cachewarmer.dependency;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Builds the safe, third-party JAR subset of a Maven dependency tree. */
public class DependencyManifestBuilder {
    private final DependencyTreeParser parser;
    public DependencyManifestBuilder(DependencyTreeParser parser) { this.parser = parser; }
    public DependencyManifest build(String tree, Set<String> reactorArtifacts) {
        List<DependencyCoordinate> artifacts = parser.parse(tree).stream()
                .filter(dependency -> dependency.type().equals("jar"))
                .filter(dependency -> !reactorArtifacts.contains(dependency.ga()))
                .filter(dependency -> !dependency.scope().equals("provided") && !dependency.scope().equals("system"))
                .sorted(Comparator.comparing(DependencyCoordinate::repositoryPath))
                .toList();
        return new DependencyManifest(artifacts);
    }
}
