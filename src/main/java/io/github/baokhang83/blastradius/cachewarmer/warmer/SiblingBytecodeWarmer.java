package io.github.baokhang83.blastradius.cachewarmer.warmer;

import io.github.baokhang83.blastradius.cachewarmer.cache.SliceCache;
import io.github.baokhang83.blastradius.cachewarmer.slicekey.SliceKeyComputer;
import io.github.baokhang83.blastradius.cachewarmer.slicekey.Tier;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

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
            ArchiveRestorer.restore(slice.get(), outputDirectory);
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

}
