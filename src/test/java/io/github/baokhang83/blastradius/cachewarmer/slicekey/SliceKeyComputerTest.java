package io.github.baokhang83.blastradius.cachewarmer.slicekey;

import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link SliceKeyComputer} against a real, throwaway source tree per test rather than
 * an in-memory stand-in - the whole point of the class is a filesystem walk, so a fake would only
 * prove it hashes whatever list we handed it, not that it walks and excludes correctly.
 */
class SliceKeyComputerTest {

    private final SliceKeyComputer computer = new SliceKeyComputer();

    @Test
    void keyFor_isDeterministic_forAnUnchangedSourceTree(@TempDir Path basedir) {
        write(basedir, "src/main/java/Foo.java", "class Foo {}");
        MavenProject module = project(basedir, "core");

        assertEquals(
                computer.keyFor(module, Tier.SIBLING_BYTECODE),
                computer.keyFor(module, Tier.SIBLING_BYTECODE));
    }

    @Test
    void keyFor_changes_whenAFilesContentChanges(@TempDir Path basedir) {
        write(basedir, "src/main/java/Foo.java", "class Foo {}");
        MavenProject module = project(basedir, "core");
        String before = computer.keyFor(module, Tier.SIBLING_BYTECODE);

        write(basedir, "src/main/java/Foo.java", "class Foo { void bar() {} }");

        assertNotEquals(before, computer.keyFor(module, Tier.SIBLING_BYTECODE));
    }

    @Test
    void keyFor_changes_whenANewFileIsAdded(@TempDir Path basedir) {
        write(basedir, "src/main/java/Foo.java", "class Foo {}");
        MavenProject module = project(basedir, "core");
        String before = computer.keyFor(module, Tier.SIBLING_BYTECODE);

        write(basedir, "src/main/java/Bar.java", "class Bar {}");

        assertNotEquals(before, computer.keyFor(module, Tier.SIBLING_BYTECODE));
    }

    @Test
    void keyFor_ignoresTheTargetDirectory(@TempDir Path basedir) {
        write(basedir, "src/main/java/Foo.java", "class Foo {}");
        MavenProject module = project(basedir, "core");
        String before = computer.keyFor(module, Tier.SIBLING_BYTECODE);

        write(basedir, "target/classes/Foo.class", "compiled bytes, not a source input");

        assertEquals(before, computer.keyFor(module, Tier.SIBLING_BYTECODE));
    }

    @Test
    void keyFor_differsAcrossTiers_forTheSameModule(@TempDir Path basedir) {
        write(basedir, "src/main/java/Foo.java", "class Foo {}");
        MavenProject module = project(basedir, "core");

        assertNotEquals(
                computer.keyFor(module, Tier.SIBLING_BYTECODE),
                computer.keyFor(module, Tier.COMPILER_STATE));
    }

    @Test
    void keyFor_differsAcrossModules_withIdenticalSourceContent(
            @TempDir Path basedirA, @TempDir Path basedirB) {
        write(basedirA, "src/main/java/Foo.java", "class Foo {}");
        write(basedirB, "src/main/java/Foo.java", "class Foo {}");
        MavenProject moduleA = project(basedirA, "core");
        MavenProject moduleB = project(basedirB, "api");

        assertNotEquals(
                computer.keyFor(moduleA, Tier.SIBLING_BYTECODE),
                computer.keyFor(moduleB, Tier.SIBLING_BYTECODE));
    }

    @Test
    void keyFor_matchesTheDocumentedFormat(@TempDir Path basedir) {
        write(basedir, "src/main/java/Foo.java", "class Foo {}");
        MavenProject module = project(basedir, "core");

        String key = computer.keyFor(module, Tier.SIBLING_BYTECODE);

        assertTrue(key.matches("sibling_bytecode/core/[0-9a-f]{64}"), key);
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

    private static MavenProject project(Path basedir, String artifactId) {
        Model model = new Model();
        model.setArtifactId(artifactId);
        MavenProject project = new MavenProject(model);
        project.setFile(new File(basedir.toFile(), "pom.xml"));
        return project;
    }
}
