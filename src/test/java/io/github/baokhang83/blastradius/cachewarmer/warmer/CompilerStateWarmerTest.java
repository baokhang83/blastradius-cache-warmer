package io.github.baokhang83.blastradius.cachewarmer.warmer;

import io.github.baokhang83.blastradius.cachewarmer.cache.SliceCache;
import io.github.baokhang83.blastradius.cachewarmer.cache.SliceIntegrity;
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
import java.util.HashMap;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilerStateWarmerTest {

    private final SliceKeyComputer keys = new SliceKeyComputer();

    @Test
    void warm_restoresTierCArchiveIntoTheConfiguredBuildDirectory(@TempDir Path basedir) {
        MavenProject module = project(basedir);
        write(basedir, "src/main/java/Foo.java", "class Foo {}");
        String key = keys.keyFor(module, Tier.COMPILER_STATE);
        CompilerStateWarmer warmer = new CompilerStateWarmer(
                cache(Map.of(key, zip(Map.of("maven-compiler-plugin/compile/default-compile/inputFiles.lst", "Foo.java")))),
                keys);

        WarmResult result = warmer.warm(module);

        assertEquals(WarmResult.WarmStatus.RESTORED, result.status());
        assertTrue(result.reason().contains(key));
        assertEquals(
                "Foo.java",
                read(basedir.resolve("build-output/maven-status/maven-compiler-plugin/compile/default-compile/inputFiles.lst")));
    }

    @Test
    void warm_skipsWhenTheTierCKeyIsMissing(@TempDir Path basedir) {
        MavenProject module = project(basedir);
        write(basedir, "src/main/java/Foo.java", "class Foo {}");

        WarmResult result = new CompilerStateWarmer(cache(Map.of()), keys).warm(module);

        assertEquals(WarmResult.WarmStatus.SKIPPED, result.status());
        assertTrue(result.reason().contains("no cached compiler state"));
        assertFalse(Files.exists(basedir.resolve("build-output/maven-status")));
    }

    @Test
    void warm_skipsWhenComputingTheTierCKeyFails(@TempDir Path basedir) {
        MavenProject module = project(basedir);
        SliceKeyComputer failingKeys = new SliceKeyComputer() {
            @Override
            public String keyFor(MavenProject ignored, Tier tier) {
                throw new IllegalStateException("source tree is unreadable");
            }
        };

        WarmResult result = new CompilerStateWarmer(cache(Map.of()), failingKeys).warm(module);

        assertEquals(WarmResult.WarmStatus.SKIPPED, result.status());
        assertTrue(result.reason().contains("could not compute compiler state key"));
        assertFalse(Files.exists(basedir.resolve("build-output/maven-status")));
    }

    @Test
    void warm_preservesExistingCompilerState(@TempDir Path basedir) {
        MavenProject module = project(basedir);
        write(basedir, "src/main/java/Foo.java", "class Foo {}");
        write(basedir, "build-output/maven-status/maven-compiler-plugin/compile/default-compile/inputFiles.lst", "keep");

        WarmResult result = new CompilerStateWarmer(cache(Map.of()), keys).warm(module);

        assertEquals(WarmResult.WarmStatus.SKIPPED, result.status());
        assertTrue(result.reason().contains("compiler state already exists"));
        assertEquals(
                "keep",
                read(basedir.resolve("build-output/maven-status/maven-compiler-plugin/compile/default-compile/inputFiles.lst")));
    }

    @Test
    void warm_skipsCompilerStateWhoseChecksumIsMissing(@TempDir Path basedir) {
        MavenProject module = project(basedir);
        write(basedir, "src/main/java/Foo.java", "class Foo {}");
        String key = keys.keyFor(module, Tier.COMPILER_STATE);

        WarmResult result = new CompilerStateWarmer(
                rawCache(Map.of(key, zip(Map.of("inputFiles.lst", "state")))), keys).warm(module);

        assertEquals(WarmResult.WarmStatus.SKIPPED, result.status());
        assertTrue(result.reason().contains("checksum missing"));
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

    private static MavenProject project(Path basedir) {
        Build build = new Build();
        build.setDirectory("build-output");
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
