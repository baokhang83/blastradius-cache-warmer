package io.github.baokhang83.blastradius.cachewarmer;

import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/** Publishes each completed module independently, so one cache failure cannot affect another. */
final class RuntimeCachePublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeCachePublisher.class);

    private final Consumer<MavenProject> publisher;

    RuntimeCachePublisher(Consumer<MavenProject> publisher) {
        this.publisher = publisher;
    }

    void publish(List<MavenProject> projects) {
        for (MavenProject project : projects) {
            try {
                publisher.accept(project);
                LOGGER.info("[cache-warmer] {} cache slices published", project.getArtifactId());
            } catch (RuntimeException e) {
                LOGGER.warn("[cache-warmer] {} cache publication failed - continuing", project.getArtifactId(), e);
            }
        }
    }
}
