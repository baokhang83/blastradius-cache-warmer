package io.github.baokhang83.blastradius.cachewarmer.cache;

/** Signals that a cached payload cannot be trusted because its checksum is absent or invalid. */
public class SliceIntegrityException extends RuntimeException {

    public SliceIntegrityException(String message) {
        super(message);
    }
}
