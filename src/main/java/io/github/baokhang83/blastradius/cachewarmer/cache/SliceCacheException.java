package io.github.baokhang83.blastradius.cachewarmer.cache;

/**
 * The cache itself failed to answer a {@link SliceCache#fetch(String)} or
 * {@link SliceCache#put(String, byte[])} - a transport/auth/configuration failure, never a clean
 * miss. The message always names the operation and key alongside the underlying failure so a
 * caller that logs it (rather than swallowing it) has something actionable without re-running
 * the request by hand (SS4 Explainability).
 */
public class SliceCacheException extends RuntimeException {

    public SliceCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
