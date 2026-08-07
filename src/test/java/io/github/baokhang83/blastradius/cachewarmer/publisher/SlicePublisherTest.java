package io.github.baokhang83.blastradius.cachewarmer.publisher;

import io.github.baokhang83.blastradius.cachewarmer.cache.SliceCache;
import io.github.baokhang83.blastradius.cachewarmer.cache.SliceIntegrity;
import io.github.baokhang83.blastradius.cachewarmer.slicekey.SliceKeyComputer;
import io.github.baokhang83.blastradius.cachewarmer.slicekey.Tier;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlicePublisherTest {

    private final SliceKeyComputer keys = new SliceKeyComputer();

    @Test
    void publish_storesTheConfiguredBytecodeAndCompilerStateAsSeparateTierArchives(@TempDir Path basedir) {
        write(basedir, "src/main/java/Foo.java", "class Foo {}");
        MavenProject module = project(basedir, "core", "build-output/classes", "build-output");
        write(basedir, "build-output/classes/com/example/Foo.class", "bytecode");
        write(
                basedir,
                "build-output/maven-status/maven-compiler-plugin/compile/default-compile/inputFiles.lst",
                "src/main/java/Foo.java");
        Map<String, byte[]> cacheEntries = new HashMap<>();

        new SlicePublisher(recordingCache(cacheEntries), keys).publish(module);

        assertEquals(
                Map.of("com/example/Foo.class", "bytecode"),
                unzip(cacheEntries.get(keys.keyFor(module, Tier.SIBLING_BYTECODE))));
        assertEquals(
                Map.of(
                        "maven-compiler-plugin/compile/default-compile/inputFiles.lst",
                        "src/main/java/Foo.java"),
                unzip(cacheEntries.get(keys.keyFor(module, Tier.COMPILER_STATE))));
        assertArrayEquals(
                SliceIntegrity.checksumFor(
                        keys.keyFor(module, Tier.SIBLING_BYTECODE),
                        cacheEntries.get(keys.keyFor(module, Tier.SIBLING_BYTECODE))),
                cacheEntries.get(SliceIntegrity.checksumKeyFor(keys.keyFor(module, Tier.SIBLING_BYTECODE))));
        assertArrayEquals(
                SliceIntegrity.checksumFor(
                        keys.keyFor(module, Tier.COMPILER_STATE),
                        cacheEntries.get(keys.keyFor(module, Tier.COMPILER_STATE))),
                cacheEntries.get(SliceIntegrity.checksumKeyFor(keys.keyFor(module, Tier.COMPILER_STATE))));
    }

    @Test
    void publish_skipsTiersWhoseOutputDirectoriesAreAbsentOrEmpty(@TempDir Path basedir) throws IOException {
        write(basedir, "src/main/java/Foo.java", "class Foo {}");
        MavenProject module = project(basedir, "core", "build-output/classes", "build-output");
        Files.createDirectories(basedir.resolve("build-output/classes"));
        Map<String, byte[]> cacheEntries = new HashMap<>();

        new SlicePublisher(recordingCache(cacheEntries), keys).publish(module);

        assertTrue(cacheEntries.isEmpty());
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

    private static MavenProject project(Path basedir, String artifactId, String outputDirectory, String buildDirectory) {
        Build build = new Build();
        build.setOutputDirectory(outputDirectory);
        build.setDirectory(buildDirectory);
        Model model = new Model();
        model.setArtifactId(artifactId);
        model.setBuild(build);
        MavenProject project = new MavenProject(model);
        project.setFile(new File(basedir.toFile(), "pom.xml"));
        return project;
    }

    private static Map<String, String> unzip(byte[] data) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            Map<String, String> entries = new HashMap<>();
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes()));
            }
            return entries;
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
}
