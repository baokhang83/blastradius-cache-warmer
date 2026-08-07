package io.github.baokhang83.blastradius.cachewarmer.warmer;

import io.github.baokhang83.blastradius.cachewarmer.cache.SliceCache;
import io.github.baokhang83.blastradius.cachewarmer.dependency.DependencyCoordinate;
import io.github.baokhang83.blastradius.cachewarmer.dependency.DependencyManifest;
import io.github.baokhang83.blastradius.cachewarmer.slicekey.DependencySliceKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Restores selected Tier B JARs into an otherwise cold Maven local repository. Every artifact is
 * independent so misses and failures leave Maven to resolve only that artifact normally.
 */
public class DependencySliceWarmer {

    private final SliceCache cache;

    public DependencySliceWarmer(SliceCache cache) {
        this.cache = cache;
    }

    public List<WarmResult> warm(DependencyManifest manifest, Path localRepository) {
        Path repository = localRepository.toAbsolutePath().normalize();
        return manifest.artifacts().stream()
                .map(coordinate -> warm(coordinate, repository))
                .toList();
    }

    private WarmResult warm(DependencyCoordinate coordinate, Path repository) {
        Path destination = repository.resolve(coordinate.repositoryPath()).normalize();
        if (!destination.startsWith(repository)) {
            return WarmResult.skipped("dependency path escapes local Maven repository: " + coordinate.repositoryPath());
        }
        if (Files.exists(destination)) {
            return WarmResult.skipped("dependency already exists at " + destination);
        }

        String key = DependencySliceKey.keyFor(coordinate);
        Optional<byte[]> slice;
        try {
            slice = cache.fetch(key);
        } catch (RuntimeException e) {
            return WarmResult.skipped("could not fetch dependency for key '" + key + "': " + e.getMessage());
        }
        if (slice.isEmpty()) {
            return WarmResult.skipped("no cached dependency for key '" + key + "'");
        }

        Path staging = null;
        try {
            Files.createDirectories(destination.getParent());
            staging = Files.createTempFile(destination.getParent(), ".cache-warmer-", ".jar");
            Files.write(staging, slice.get());
            Files.move(staging, destination);
            return WarmResult.restored("restored dependency from key '" + key + "'");
        } catch (IOException e) {
            return WarmResult.skipped("could not restore dependency for key '" + key + "': " + e.getMessage());
        } finally {
            if (staging != null) {
                try {
                    Files.deleteIfExists(staging);
                } catch (IOException ignored) {
                    // A failed cleanup must not turn a cache optimization into a failed build.
                }
            }
        }
    }
}
