package io.github.baokhang83.blastradius.cachewarmer;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeCachePublisherTest {

    @Test
    void continuesPublishingLaterModulesWhenOneCacheWriteFails() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeCachePublisher publisher = new RuntimeCachePublisher(project -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("simulated cache outage");
            }
        });

        publisher.publish(List.of(project("first"), project("second")));

        assertEquals(2, attempts.get());
    }

    private static MavenProject project(String artifactId) {
        MavenProject project = new MavenProject();
        project.setArtifactId(artifactId);
        return project;
    }
}
