package io.github.baokhang83.blastradius.cachewarmer.cache;

import java.util.Optional;

/**
 * Stores and retrieves opaque slice payloads by key - deliberately kept to two methods (SS2
 * Simplicity) so a backend (S3, later GitHub Actions cache) can be swapped without leaking
 * storage concerns into the publisher/warmers that call it. Neither method knows or cares what a
 * slice's bytes represent.
 *
 * <p>A clean miss is {@code Optional.empty()} - never an exception. {@link SliceCacheException}
 * is reserved for the cache itself failing to answer (network, auth, a missing bucket); a caller
 * must be able to tell "nothing is cached here" apart from "the cache couldn't be reached" so it
 * can fail open correctly (SS3 Safety over speed).
 */
public interface SliceCache {

    Optional<byte[]> fetch(String key) throws SliceCacheException;

    void put(String key, byte[] data) throws SliceCacheException;
}
