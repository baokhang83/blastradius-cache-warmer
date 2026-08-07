package io.github.baokhang83.blastradius.cachewarmer.git;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link GitDiff} against a real, throwaway git repo per test rather than mocking the
 * subprocess - a fake process would only prove GitDiff parses what we told it to, not that its
 * git invocation is actually correct.
 */
class GitDiffTest {

    private Path repo;
    private GitDiff gitDiff;

    @BeforeEach
    void initRepo(@TempDir Path repo) {
        this.repo = repo;
        this.gitDiff = new GitDiff(repo.toFile());
        run("git", "init", "-q", "-b", "main");
        run("git", "config", "user.email", "test@example.com");
        run("git", "config", "user.name", "Test");
    }

    @Test
    void changedPaths_isEmpty_whenTheBranchHasNoChangesSinceTheBaseRef() {
        writeAndCommit("README.md", "hello", "initial commit");

        assertEquals(List.of(), gitDiff.changedPaths("main"));
    }

    @Test
    void changedPaths_listsFilesChangedSinceTheMergeBaseWithTheBaseRef() {
        writeAndCommit("README.md", "hello", "initial commit");
        run("git", "checkout", "-q", "-b", "feature");
        writeAndCommit("module-a/Foo.java", "class Foo {}", "add Foo");
        writeAndCommit("pom.xml", "<project/>", "touch pom");

        List<String> changed = gitDiff.changedPaths("main");

        assertEquals(List.of("module-a/Foo.java", "pom.xml"), changed);
    }

    @Test
    void changedPaths_diffsAgainstTheMergeBase_notTheBaseRefsCurrentTip() {
        writeAndCommit("README.md", "hello", "initial commit");
        run("git", "checkout", "-q", "-b", "feature");
        writeAndCommit("module-a/Foo.java", "class Foo {}", "add Foo");
        // main moves on after the branch point - that change must not show up as "changed" here.
        run("git", "checkout", "-q", "main");
        writeAndCommit("unrelated.txt", "noise", "unrelated main commit");
        run("git", "checkout", "-q", "feature");

        assertEquals(List.of("module-a/Foo.java"), gitDiff.changedPaths("main"));
    }

    @Test
    void changedPaths_throws_whenTheBaseRefDoesNotExist() {
        writeAndCommit("README.md", "hello", "initial commit");

        assertThrows(GitDiffException.class, () -> gitDiff.changedPaths("does-not-exist"));
    }

    private void writeAndCommit(String relativePath, String content, String message) {
        try {
            Path file = repo.resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        run("git", "add", relativePath);
        run("git", "commit", "-q", "-m", message);
    }

    private void run(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(repo.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            assertTrue(exitCode == 0, "command " + List.of(command) + " failed: " + output);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
