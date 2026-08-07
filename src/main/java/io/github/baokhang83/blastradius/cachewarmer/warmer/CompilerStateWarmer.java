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

/** Restores cached Tier C Maven Compiler Plugin state into an absent maven-status directory. */
public class CompilerStateWarmer {

    private final SliceCache cache;
    private final SliceKeyComputer keys;

    public CompilerStateWarmer(SliceCache cache, SliceKeyComputer keys) {
        this.cache = cache;
        this.keys = keys;
    }

    /**
     * Restores a module's incremental compiler state only when Maven has not already produced it.
     * Every unusable cache condition deliberately falls back to a cold Maven compilation.
     */
    public WarmResult warm(MavenProject module) {
        Path compilerStateDirectory;
        try {
            compilerStateDirectory = compilerStateDirectory(module);
        } catch (RuntimeException e) {
            return WarmResult.skipped("could not resolve compiler state directory: " + e.getMessage());
        }
        if (Files.exists(compilerStateDirectory)) {
            return WarmResult.skipped("compiler state already exists at " + compilerStateDirectory);
        }

        String key;
        try {
            key = keys.keyFor(module, Tier.COMPILER_STATE);
        } catch (RuntimeException e) {
            return WarmResult.skipped("could not compute compiler state key: " + e.getMessage());
        }
        Optional<byte[]> slice;
        try {
            slice = cache.fetch(key);
        } catch (RuntimeException e) {
            return WarmResult.skipped("could not fetch compiler state for key '" + key + "': " + e.getMessage());
        }
        if (slice.isEmpty()) {
            return WarmResult.skipped("no cached compiler state for key '" + key + "'");
        }

        try {
            ArchiveRestorer.restore(slice.get(), compilerStateDirectory);
            return WarmResult.restored("restored compiler state from key '" + key + "'");
        } catch (IOException | IllegalArgumentException e) {
            return WarmResult.skipped("could not restore compiler state for key '" + key + "': " + e.getMessage());
        }
    }

    private static Path compilerStateDirectory(MavenProject module) {
        Build build = module.getBuild();
        Path directory = Path.of(build.getDirectory());
        Path buildDirectory = directory.isAbsolute() ? directory : module.getBasedir().toPath().resolve(directory);
        return buildDirectory.resolve("maven-status");
    }
}
