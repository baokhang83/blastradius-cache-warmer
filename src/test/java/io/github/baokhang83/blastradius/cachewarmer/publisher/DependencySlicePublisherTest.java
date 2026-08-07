package io.github.baokhang83.blastradius.cachewarmer.publisher;

import io.github.baokhang83.blastradius.cachewarmer.cache.SliceCache;
import io.github.baokhang83.blastradius.cachewarmer.dependency.DependencyCoordinate;
import io.github.baokhang83.blastradius.cachewarmer.dependency.DependencyManifest;
import io.github.baokhang83.blastradius.cachewarmer.slicekey.DependencySliceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DependencySlicePublisherTest {

    @Test
    void publish_uploadsEachPresentArtifactUnderItsRepositoryPathKey(@TempDir Path repository) {
        DependencyCoordinate junit = coordinate("org.junit.jupiter", "junit-jupiter-api", "5.10.2");
        DependencyCoordinate slf4j = coordinate("org.slf4j", "slf4j-api", "2.0.13");
        write(repository.resolve(junit.repositoryPath()), "junit bytes");
        write(repository.resolve(slf4j.repositoryPath()), "slf4j bytes");
        Map<String, byte[]> cacheEntries = new HashMap<>();

        new DependencySlicePublisher(recordingCache(cacheEntries))
                .publish(new DependencyManifest(List.of(junit, slf4j)), repository);

        assertEquals(
                Map.of(
                        DependencySliceKey.keyFor(junit), "junit bytes",
                        DependencySliceKey.keyFor(slf4j), "slf4j bytes"),
                strings(cacheEntries));
    }

    @Test
    void publish_skipsManifestArtifactsThatAreAbsentFromTheLocalRepository(@TempDir Path repository) {
        DependencyCoordinate present = coordinate("org.junit.jupiter", "junit-jupiter-api", "5.10.2");
        DependencyCoordinate missing = coordinate("org.slf4j", "slf4j-api", "2.0.13");
        write(repository.resolve(present.repositoryPath()), "present bytes");
        Map<String, byte[]> cacheEntries = new HashMap<>();

        new DependencySlicePublisher(recordingCache(cacheEntries))
                .publish(new DependencyManifest(List.of(present, missing)), repository);

        assertEquals(Map.of(DependencySliceKey.keyFor(present), "present bytes"), strings(cacheEntries));
    }

    private static DependencyCoordinate coordinate(String groupId, String artifactId, String version) {
        return new DependencyCoordinate(groupId, artifactId, "jar", "", version, "compile");
    }

    private static SliceCache recordingCache(Map<String, byte[]> entries) {
        return new SliceCache() {
            @Override
            public java.util.Optional<byte[]> fetch(String key) {
                return java.util.Optional.ofNullable(entries.get(key));
            }

            @Override
            public void put(String key, byte[] data) {
                entries.put(key, data);
            }
        };
    }

    private static Map<String, String> strings(Map<String, byte[]> entries) {
        return entries.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> new String(entry.getValue())));
    }

    private static void write(Path file, String contents) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
