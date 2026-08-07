package io.github.baokhang83.blastradius.cachewarmer.reactor;

/**
 * Identifies one module in the reactor: its artifactId (used to match declared
 * {@code <dependency>}/{@code <parent>} references against other modules) and its basedir,
 * relative to the reactor's execution root, forward-slashed with no leading/trailing slash -
 * {@code ""} for the execution root itself.
 */
public record ModuleId(String artifactId, String basedir) {
}
