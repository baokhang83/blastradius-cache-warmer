package io.github.baokhang83.blastradius.cachewarmer.warmer;

import io.github.baokhang83.blastradius.cachewarmer.cache.SliceCache;
import io.github.baokhang83.blastradius.cachewarmer.slicekey.SliceKeyComputer;
import io.github.baokhang83.blastradius.cachewarmer.slicekey.Tier;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiblingBytecodeWarmerTest {

    private final SliceKeyComputer keys = new SliceKeyComputer();

    @Test
    void warm_restoresTheTierAArchiveIntoTheConfiguredClassesDirectory(@TempDir Path basedir) {
        MavenProject module = project(basedir);
        write(basedir, "src/main/java/Foo.java", "class Foo {}");
        String key = keys.keyFor(module, Tier.SIBLING_BYTECODE);
        SiblingBytecodeWarmer warmer = new SiblingBytecodeWarmer(
                cache(Map.of(key, zip(Map.of("com/example/Foo.class", "bytecode")))), keys);

        WarmResult result = warmer.warm(module);

        assertEquals(WarmResult.WarmStatus.RESTORED, result.status());
        assertTrue(result.reason().contains(key));
        assertEquals("bytecode", read(basedir.resolve("build-output/classes/com/example/Foo.class")));
    }

    @Test
    void warm_skipsWhenTheTierAKeyIsMissing(@TempDir Path basedir) {
        MavenProject module = project(basedir);
        write(basedir, "src/main/java/Foo.java", "class Foo {}");

        WarmResult result = new SiblingBytecodeWarmer(cache(Map.of()), keys).warm(module);

        assertEquals(WarmResult.WarmStatus.SKIPPED, result.status());
        assertTrue(result.reason().contains("no cached sibling bytecode"));
        assertFalse(Files.exists(basedir.resolve("build-output/classes")));
    }

    @Test
    void warm_skipsWhenComputingTheTierAKeyFails(@TempDir Path basedir) {
        MavenProject module = project(basedir);
        SliceKeyComputer failingKeys = new SliceKeyComputer() {
            @Override
            public String keyFor(MavenProject ignored, Tier tier) {
                throw new IllegalStateException("source tree is unreadable");
            }
        };

        WarmResult result = new SiblingBytecodeWarmer(cache(Map.of()), failingKeys).warm(module);

        assertEquals(WarmResult.WarmStatus.SKIPPED, result.status());
        assertTrue(result.reason().contains("could not compute sibling bytecode key"));
        assertFalse(Files.exists(basedir.resolve("build-output/classes")));
    }

    @Test
    void warm_skipsAnArchiveThatEscapesTheClassesDirectory(@TempDir Path basedir) {
        MavenProject module = project(basedir);
        write(basedir, "src/main/java/Foo.java", "class Foo {}");
        String key = keys.keyFor(module, Tier.SIBLING_BYTECODE);
        SiblingBytecodeWarmer warmer = new SiblingBytecodeWarmer(
                cache(Map.of(key, zip(Map.of("../../outside.class", "unsafe")))), keys);

        WarmResult result = warmer.warm(module);

        assertEquals(WarmResult.WarmStatus.SKIPPED, result.status());
        assertTrue(result.reason().contains("escapes output directory"));
        assertFalse(Files.exists(basedir.resolve("outside.class")));
        assertFalse(Files.exists(basedir.resolve("build-output/classes")));
    }

    @Test
    void warm_preservesAnExistingClassesDirectory(@TempDir Path basedir) {
        MavenProject module = project(basedir);
        write(basedir, "src/main/java/Foo.java", "class Foo {}");
        write(basedir, "build-output/classes/Existing.class", "keep");

        WarmResult result = new SiblingBytecodeWarmer(cache(Map.of()), keys).warm(module);

        assertEquals(WarmResult.WarmStatus.SKIPPED, result.status());
        assertEquals("keep", read(basedir.resolve("build-output/classes/Existing.class")));
    }

    private static SliceCache cache(Map<String, byte[]> entries) {
        return new SliceCache() {
            @Override
            public Optional<byte[]> fetch(String key) {
                return Optional.ofNullable(entries.get(key));
            }

            @Override
            public void put(String key, byte[] data) {
                throw new UnsupportedOperationException("not needed by the warmer");
            }
        };
    }

    private static MavenProject project(Path basedir) {
        Build build = new Build();
        build.setOutputDirectory("build-output/classes");
        Model model = new Model();
        model.setArtifactId("core");
        model.setBuild(build);
        MavenProject project = new MavenProject(model);
        project.setFile(new File(basedir.toFile(), "pom.xml"));
        return project;
    }

    private static byte[] zip(Map<String, String> entries) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes());
                zip.closeEntry();
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path basedir, String relativePath, String content) {
        try {
            Path file = basedir.resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
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
