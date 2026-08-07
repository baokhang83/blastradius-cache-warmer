package io.github.baokhang83.blastradius.cachewarmer.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * Stores and verifies a key-bound SHA-256 checksum beside each cache payload. A checksum protects
 * against corruption and accidental key/payload mix-ups, while storage writer authorization is a
 * separate security concern.
 */
public final class SliceIntegrity {

    private static final String CHECKSUM_PREFIX = "checksums/";

    private SliceIntegrity() {}

    public static void put(SliceCache cache, String key, byte[] payload) {
        cache.put(key, payload);
        cache.put(checksumKeyFor(key), checksumFor(key, payload));
    }

    public static Optional<byte[]> fetchVerified(SliceCache cache, String key) {
        Optional<byte[]> payload = cache.fetch(key);
        if (payload.isEmpty()) {
            return Optional.empty();
        }

        Optional<byte[]> checksum = cache.fetch(checksumKeyFor(key));
        if (checksum.isEmpty()) {
            throw new SliceIntegrityException("checksum missing for key '" + key + "'");
        }
        if (!MessageDigest.isEqual(checksum.get(), checksumFor(key, payload.get()))) {
            throw new SliceIntegrityException("checksum mismatch for key '" + key + "'");
        }
        return payload;
    }

    public static String checksumKeyFor(String key) {
        return CHECKSUM_PREFIX + key;
    }

    public static byte[] checksumFor(String key, byte[] payload) {
        MessageDigest digest = sha256();
        digest.update(key.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        return digest.digest(payload);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }
}
