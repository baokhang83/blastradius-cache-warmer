package io.github.baokhang83.blastradius.cachewarmer.git;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Changed files since the current branch diverged from a base ref, via a {@code git} CLI
 * subprocess - not JGit. Keeps the extension's dependency footprint at zero new libraries on
 * Maven's shared Core Extension classpath (the same posture T1 already established for
 * {@code provided}-scope deps), and git is already a hard requirement of any environment running
 * blastradius in the first place.
 */
public class GitDiff {

    private final File workingDirectory;

    public GitDiff(File workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    /**
     * Files changed on {@code HEAD} since it diverged from {@code baseRef} - git's
     * {@code baseRef...HEAD} triple-dot form, which diffs against the merge-base rather than
     * baseRef's current tip, so commits baseRef picked up after the branch point aren't
     * attributed to this branch.
     */
    public List<String> changedPaths(String baseRef) {
        String output = run("diff", "--name-only", baseRef + "...HEAD");
        return output.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }

    private String run(String... gitArgs) {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(gitArgs));
        try {
            Process process = new ProcessBuilder(command)
                    .directory(workingDirectory)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new GitDiffException(
                        "git " + String.join(" ", gitArgs) + " failed (exit " + exitCode + "): " + output.strip());
            }
            return output;
        } catch (IOException e) {
            throw new GitDiffException("could not run git in " + workingDirectory, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitDiffException("interrupted while running git in " + workingDirectory, e);
        }
    }
}
