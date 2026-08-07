package io.github.baokhang83.blastradius.cachewarmer.publisher;

import io.github.baokhang83.blastradius.cachewarmer.cache.SliceCache;
import io.github.baokhang83.blastradius.cachewarmer.cache.SliceIntegrity;
import io.github.baokhang83.blastradius.cachewarmer.dependency.DependencyCoordinate;
import io.github.baokhang83.blastradius.cachewarmer.dependency.DependencyManifest;
import io.github.baokhang83.blastradius.cachewarmer.slicekey.DependencySliceKey;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Publishes every locally available third-party JAR selected for Tier B as its own cache object.
 * Missing files are skipped because a cache is an optional acceleration, not a Maven prerequisite.
 */
public class DependencySlicePublisher {

    private final SliceCache cache;

    public DependencySlicePublisher(SliceCache cache) {
        this.cache = cache;
    }

    public void publish(DependencyManifest manifest, Path localRepository) {
        manifest.artifacts().forEach(coordinate -> publishIfPresent(localRepository, coordinate));
    }

    private void publishIfPresent(Path localRepository, DependencyCoordinate coordinate) {
        Path artifact = localRepository.resolve(coordinate.repositoryPath());
        if (!Files.isRegularFile(artifact)) {
            return;
        }
        try {
            SliceIntegrity.put(cache, DependencySliceKey.keyFor(coordinate), Files.readAllBytes(artifact));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read dependency JAR at " + artifact, e);
        }
    }
}
