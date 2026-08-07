package io.github.baokhang83.blastradius.cachewarmer.git;

/**
 * A {@code git} invocation failed - the message carries git's own stderr/stdout so the cause is
 * visible without re-running the command by hand (SS4 Explainability).
 */
public class GitDiffException extends RuntimeException {

    public GitDiffException(String message) {
        super(message);
    }

    public GitDiffException(String message, Throwable cause) {
        super(message, cause);
    }
}
