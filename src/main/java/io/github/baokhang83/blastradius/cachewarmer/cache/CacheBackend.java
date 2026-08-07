package io.github.baokhang83.blastradius.cachewarmer.cache;

import java.util.Locale;
import java.util.Properties;

/**
 * Cache backend selection for the future extension wiring. GitHub Actions is the default because
 * it needs no separately managed object store when Maven runs in an Actions job.
 */
public enum CacheBackend {
    GITHUB_ACTIONS,
    S3;

    public static CacheBackend fromConfiguredValue(String value) {
        if (value == null || value.isBlank()) {
            return GITHUB_ACTIONS;
        }
        return switch (value.toUpperCase(Locale.ROOT).replace('-', '_')) {
            case "GITHUB_ACTIONS" -> GITHUB_ACTIONS;
            case "S3" -> S3;
            default -> throw new IllegalArgumentException(
                    "Unsupported cache backend '" + value + "'. Expected github-actions or s3");
        };
    }

    public static CacheBackend fromSystemProperties(Properties properties) {
        return fromConfiguredValue(properties.getProperty("blastradius.cache.backend"));
    }
}
