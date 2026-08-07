package io.github.baokhang83.blastradius.cachewarmer.slicekey;

import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Computes a module's cache key from its own source-tree content plus the running JDK version -
 * not from a git commit SHA. Content hashing is what lets a slice published from one branch
 * legitimately hit for another branch that never touched this module; a commit-SHA key would
 * only ever hit a later build of that exact commit. The JDK version rides along because Tier A
 * (bytecode) and Tier C (compiler state) are both toolchain-sensitive in ways a POM property
 * alone doesn't capture (e.g. a runner image upgrade, not just a maven.compiler.release bump).
 *
 * <p>The same computer serves every tier: a publisher (T5) and a warmer (T6/T7) for the same
 * module+tier must derive the identical key independently, with nothing shared at runtime except
 * this class.
 */
public class SliceKeyComputer {

    public String keyFor(MavenProject module, Tier tier) {
        Path basedir = module.getBasedir().toPath();
        MessageDigest digest = sha256();
        for (Path file : sortedSourceFiles(basedir)) {
            digest.update(basedir.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
            digest.update(readBytes(file));
        }
        digest.update(System.getProperty("java.version").getBytes(StandardCharsets.UTF_8));

        String hex = HexFormat.of().formatHex(digest.digest());
        return tier.name().toLowerCase(Locale.ROOT) + "/" + module.getArtifactId() + "/" + hex;
    }

    private static List<Path> sortedSourceFiles(Path basedir) {
        try (Stream<Path> walk = Files.walk(basedir)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(file -> !basedir.relativize(file).startsWith("target"))
                    .sorted(Comparator.comparing(file -> basedir.relativize(file).toString()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] readBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }
}
