package io.github.baokhang83.blastradius.cachewarmer.publisher;

import io.github.baokhang83.blastradius.cachewarmer.cache.SliceCache;
import io.github.baokhang83.blastradius.cachewarmer.slicekey.SliceKeyComputer;
import io.github.baokhang83.blastradius.cachewarmer.slicekey.Tier;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Publishes the build output needed by the Tier A and Tier C warmers as one ZIP payload per tier.
 * A caller invokes this only after a successful module build; absent or empty output directories
 * deliberately produce no cache entry.
 */
public class SlicePublisher {

    private final SliceCache cache;
    private final SliceKeyComputer keys;

    public SlicePublisher(SliceCache cache, SliceKeyComputer keys) {
        this.cache = cache;
        this.keys = keys;
    }

    /** Publishes a module's compiled production classes and incremental compiler state when present. */
    public void publish(MavenProject module) {
        Build build = module.getBuild();
        publishIfPresent(module, Tier.SIBLING_BYTECODE, buildPath(module, build.getOutputDirectory()));
        publishIfPresent(
                module,
                Tier.COMPILER_STATE,
                buildPath(module, build.getDirectory()).resolve("maven-status"));
    }

    private void publishIfPresent(MavenProject module, Tier tier, Path outputDirectory) {
        archive(outputDirectory).ifPresent(data -> cache.put(keys.keyFor(module, tier), data));
    }

    private static Path buildPath(MavenProject module, String directory) {
        Path path = Path.of(directory);
        return path.isAbsolute() ? path : module.getBasedir().toPath().resolve(path);
    }

    private static Optional<byte[]> archive(Path directory) {
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }

        List<Path> files = filesIn(directory);
        if (files.isEmpty()) {
            return Optional.empty();
        }

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Path file : files) {
                ZipEntry entry = new ZipEntry(directory.relativize(file).toString().replace('\\', '/'));
                entry.setTime(0L);
                zip.putNextEntry(entry);
                Files.copy(file, zip);
                zip.closeEntry();
            }
            zip.finish();
            return Optional.of(bytes.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not archive build output at " + directory, e);
        }
    }

    private static List<Path> filesIn(Path directory) {
        try (var paths = Files.walk(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .sorted((left, right) -> directory.relativize(left).compareTo(directory.relativize(right)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read build output at " + directory, e);
        }
    }
}
