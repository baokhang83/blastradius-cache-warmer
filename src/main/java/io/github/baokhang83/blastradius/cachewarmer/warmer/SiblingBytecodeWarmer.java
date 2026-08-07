package io.github.baokhang83.blastradius.cachewarmer.warmer;

import io.github.baokhang83.blastradius.cachewarmer.cache.SliceCache;
import io.github.baokhang83.blastradius.cachewarmer.slicekey.SliceKeyComputer;
import io.github.baokhang83.blastradius.cachewarmer.slicekey.Tier;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Restores a cached Tier A sibling-bytecode archive into a clean module output directory. */
public class SiblingBytecodeWarmer {

    private final SliceCache cache;
    private final SliceKeyComputer keys;

    public SiblingBytecodeWarmer(SliceCache cache, SliceKeyComputer keys) {
        this.cache = cache;
        this.keys = keys;
    }

    /**
     * Restores the Tier A archive for {@code module} when its configured output directory is
     * absent. Callers select only safe-to-warm modules using the blast-radius result.
     */
    public WarmResult warm(MavenProject module) {
        Path outputDirectory = outputDirectory(module);
        if (Files.exists(outputDirectory)) {
            return WarmResult.skipped("bytecode output already exists at " + outputDirectory);
        }

        String key;
        try {
            key = keys.keyFor(module, Tier.SIBLING_BYTECODE);
        } catch (RuntimeException e) {
            return WarmResult.skipped("could not compute sibling bytecode key: " + e.getMessage());
        }
        Optional<byte[]> slice;
        try {
            slice = cache.fetch(key);
        } catch (RuntimeException e) {
            return WarmResult.skipped("could not fetch sibling bytecode for key '" + key + "': " + e.getMessage());
        }
        if (slice.isEmpty()) {
            return WarmResult.skipped("no cached sibling bytecode for key '" + key + "'");
        }

        try {
            restore(slice.get(), outputDirectory);
            return WarmResult.restored("restored sibling bytecode from key '" + key + "'");
        } catch (IOException | IllegalArgumentException e) {
            return WarmResult.skipped("could not restore sibling bytecode for key '" + key + "': " + e.getMessage());
        }
    }

    private static Path outputDirectory(MavenProject module) {
        Build build = module.getBuild();
        Path output = Path.of(build.getOutputDirectory());
        return output.isAbsolute() ? output : module.getBasedir().toPath().resolve(output);
    }

    private static void restore(byte[] archive, Path outputDirectory) throws IOException {
        Path parent = outputDirectory.getParent();
        Files.createDirectories(parent);
        Path staging = Files.createTempDirectory(parent, ".cache-warmer-");
        boolean restored = false;
        try {
            int files = extract(archive, staging);
            if (files == 0) {
                throw new IOException("archive contains no files");
            }
            Files.move(staging, outputDirectory);
            restored = true;
        } finally {
            if (!restored) {
                deleteTree(staging);
            }
        }
    }

    private static int extract(byte[] archive, Path staging) throws IOException {
        int files = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path destination = staging.resolve(entry.getName()).normalize();
                if (!destination.startsWith(staging)) {
                    throw new IllegalArgumentException("archive entry escapes output directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(zip, destination);
                    files++;
                }
                zip.closeEntry();
            }
        }
        return files;
    }

    private static void deleteTree(Path directory) {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // A failed restore remains a cold build. Best-effort cleanup must not change that.
        }
    }
}
