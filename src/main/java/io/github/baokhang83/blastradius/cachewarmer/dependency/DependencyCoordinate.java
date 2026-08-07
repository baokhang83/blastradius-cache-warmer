package io.github.baokhang83.blastradius.cachewarmer.dependency;

import java.nio.file.Path;

/** A resolved third-party Maven artifact that can be addressed beneath a local repository. */
public record DependencyCoordinate(String groupId, String artifactId, String type, String classifier, String version, String scope) {
    public Path repositoryPath() {
        String file = artifactId + "-" + version + (classifier.isEmpty() ? "" : "-" + classifier) + "." + type;
        return Path.of(groupId.replace('.', '/'), artifactId, version, file);
    }

    public String ga() { return groupId + ":" + artifactId; }
}
