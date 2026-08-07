package io.github.baokhang83.blastradius.cachewarmer.warmer;

import io.github.baokhang83.blastradius.cachewarmer.cache.SliceCache;
import io.github.baokhang83.blastradius.cachewarmer.cache.SliceIntegrity;
import io.github.baokhang83.blastradius.cachewarmer.dependency.DependencyCoordinate;
import io.github.baokhang83.blastradius.cachewarmer.dependency.DependencyManifest;
import io.github.baokhang83.blastradius.cachewarmer.slicekey.DependencySliceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencySliceWarmerTest {

    @Test
    void warm_restoresEachCacheHitAtItsMavenRepositoryPath(@TempDir Path repository) {
        DependencyCoordinate junit = coordinate("org.junit.jupiter", "junit-jupiter-api", "5.10.2");
        DependencyCoordinate slf4j = coordinate("org.slf4j", "slf4j-api", "2.0.13");
        DependencyManifest manifest = new DependencyManifest(List.of(junit, slf4j));
        SliceCache cache = cache(Map.of(
                DependencySliceKey.keyFor(junit), "junit bytes".getBytes(),
                DependencySliceKey.keyFor(slf4j), "slf4j bytes".getBytes()));

        List<WarmResult> results = new DependencySliceWarmer(cache).warm(manifest, repository);

        assertEquals(List.of(WarmResult.WarmStatus.RESTORED, WarmResult.WarmStatus.RESTORED),
                results.stream().map(WarmResult::status).toList());
        assertEquals("junit bytes", read(repository.resolve(junit.repositoryPath())));
        assertEquals("slf4j bytes", read(repository.resolve(slf4j.repositoryPath())));
    }

    @Test
    void warm_preservesAnExistingArtifactWithoutFetchingIt(@TempDir Path repository) {
        DependencyCoordinate junit = coordinate("org.junit.jupiter", "junit-jupiter-api", "5.10.2");
        Path existing = repository.resolve(junit.repositoryPath());
        write(existing, "keep local bytes");
        AtomicInteger fetches = new AtomicInteger();

        List<WarmResult> results = new DependencySliceWarmer(failingIfFetched(fetches))
                .warm(new DependencyManifest(List.of(junit)), repository);

        assertEquals(List.of(WarmResult.WarmStatus.SKIPPED), results.stream().map(WarmResult::status).toList());
        assertTrue(results.getFirst().reason().contains("already exists"));
        assertEquals(0, fetches.get());
        assertEquals("keep local bytes", read(existing));
    }

    @Test
    void warm_leavesCacheMissesAndFailuresColdWhileRestoringOtherArtifacts(@TempDir Path repository) {
        DependencyCoordinate hit = coordinate("org.junit.jupiter", "junit-jupiter-api", "5.10.2");
        DependencyCoordinate miss = coordinate("org.slf4j", "slf4j-api", "2.0.13");
        DependencyCoordinate failure = coordinate("org.apache.commons", "commons-lang3", "3.14.0");
        Map<String, byte[]> entries = new HashMap<>();
        SliceCache cache = new SliceCache() {
            @Override
            public Optional<byte[]> fetch(String key) {
                if (key.equals(DependencySliceKey.keyFor(failure))) {
                    throw new IllegalStateException("cache unavailable");
                }
                return Optional.ofNullable(entries.get(key));
            }

            @Override
            public void put(String key, byte[] data) {
                entries.put(key, data);
            }
        };
        SliceIntegrity.put(cache, DependencySliceKey.keyFor(hit), "hit bytes".getBytes());

        List<WarmResult> results = new DependencySliceWarmer(cache)
                .warm(new DependencyManifest(List.of(hit, miss, failure)), repository);

        assertEquals(
                List.of(WarmResult.WarmStatus.RESTORED, WarmResult.WarmStatus.SKIPPED, WarmResult.WarmStatus.SKIPPED),
                results.stream().map(WarmResult::status).toList());
        assertEquals("hit bytes", read(repository.resolve(hit.repositoryPath())));
        assertFalse(Files.exists(repository.resolve(miss.repositoryPath())));
        assertFalse(Files.exists(repository.resolve(failure.repositoryPath())));
        assertTrue(results.get(1).reason().contains("no cached dependency"));
        assertTrue(results.get(2).reason().contains("could not fetch dependency"));
    }

    @Test
    void warm_rejectsACoordinateWhosePathEscapesTheLocalRepository(@TempDir Path repository) {
        DependencyCoordinate escaping = coordinate("..", "outside", "1");
        AtomicInteger fetches = new AtomicInteger();

        List<WarmResult> results = new DependencySliceWarmer(failingIfFetched(fetches))
                .warm(new DependencyManifest(List.of(escaping)), repository);

        assertEquals(List.of(WarmResult.WarmStatus.SKIPPED), results.stream().map(WarmResult::status).toList());
        assertTrue(results.getFirst().reason().contains("escapes local Maven repository"));
        assertEquals(0, fetches.get());
        assertFalse(Files.exists(repository.getParent().resolve("outside")));
    }

    @Test
    void warm_skipsADependencyWhoseChecksumIsMissing(@TempDir Path repository) {
        DependencyCoordinate junit = coordinate("org.junit.jupiter", "junit-jupiter-api", "5.10.2");
        String key = DependencySliceKey.keyFor(junit);

        List<WarmResult> results = new DependencySliceWarmer(rawCache(Map.of(key, "junit bytes".getBytes())))
                .warm(new DependencyManifest(List.of(junit)), repository);

        assertEquals(List.of(WarmResult.WarmStatus.SKIPPED), results.stream().map(WarmResult::status).toList());
        assertTrue(results.getFirst().reason().contains("checksum missing"));
        assertFalse(Files.exists(repository.resolve(junit.repositoryPath())));
    }

    private static DependencyCoordinate coordinate(String groupId, String artifactId, String version) {
        return new DependencyCoordinate(groupId, artifactId, "jar", "", version, "compile");
    }

    private static SliceCache cache(Map<String, byte[]> entries) {
        Map<String, byte[]> verifiedEntries = new HashMap<>();
        SliceCache cache = rawCache(verifiedEntries);
        entries.forEach((key, value) -> SliceIntegrity.put(cache, key, value));
        return cache;
    }

    private static SliceCache rawCache(Map<String, byte[]> entries) {
        return new SliceCache() {
            @Override
            public Optional<byte[]> fetch(String key) {
                return Optional.ofNullable(entries.get(key));
            }

            @Override
            public void put(String key, byte[] data) {
                entries.put(key, data);
            }
        };
    }

    private static SliceCache failingIfFetched(AtomicInteger fetches) {
        return new SliceCache() {
            @Override
            public Optional<byte[]> fetch(String key) {
                fetches.incrementAndGet();
                throw new AssertionError("cache should not be fetched");
            }

            @Override
            public void put(String key, byte[] data) {
                throw new UnsupportedOperationException("not needed by the warmer");
            }
        };
    }

    private static void write(Path file, String contents) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
