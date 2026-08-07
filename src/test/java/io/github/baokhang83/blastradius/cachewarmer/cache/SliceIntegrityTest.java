package io.github.baokhang83.blastradius.cachewarmer.cache;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SliceIntegrityTest {

    @Test
    void put_storesThePayloadAndAKeyBoundChecksumSidecar() {
        Map<String, byte[]> entries = new HashMap<>();
        SliceIntegrity.put(cache(entries), "sibling_bytecode/core/abc", "payload".getBytes());

        assertArrayEquals("payload".getBytes(), entries.get("sibling_bytecode/core/abc"));
        assertArrayEquals(
                SliceIntegrity.checksumFor("sibling_bytecode/core/abc", "payload".getBytes()),
                entries.get("checksums/sibling_bytecode/core/abc"));
    }

    @Test
    void fetchVerified_returnsAPayloadWithAMatchingChecksum() {
        Map<String, byte[]> entries = new HashMap<>();
        SliceIntegrity.put(cache(entries), "dependency_slice/org/example/demo.jar", "payload".getBytes());

        Optional<byte[]> payload = SliceIntegrity.fetchVerified(cache(entries), "dependency_slice/org/example/demo.jar");

        assertEquals(Optional.of("payload"), payload.map(String::new));
    }

    @Test
    void fetchVerified_rejectsAMissingChecksum() {
        Map<String, byte[]> entries = Map.of("compiler_state/core/abc", "payload".getBytes());

        SliceIntegrityException exception = assertThrows(
                SliceIntegrityException.class,
                () -> SliceIntegrity.fetchVerified(cache(entries), "compiler_state/core/abc"));

        assertEquals("checksum missing for key 'compiler_state/core/abc'", exception.getMessage());
    }

    @Test
    void fetchVerified_rejectsPayloadBytesThatDoNotMatchTheirStoredChecksum() {
        Map<String, byte[]> entries = new HashMap<>();
        SliceIntegrity.put(cache(entries), "dependency_slice/org/example/demo.jar", "original".getBytes());
        entries.put("dependency_slice/org/example/demo.jar", "tampered".getBytes());

        SliceIntegrityException exception = assertThrows(
                SliceIntegrityException.class,
                () -> SliceIntegrity.fetchVerified(cache(entries), "dependency_slice/org/example/demo.jar"));

        assertEquals("checksum mismatch for key 'dependency_slice/org/example/demo.jar'", exception.getMessage());
    }

    @Test
    void fetchVerified_rejectsAValidPayloadChecksumPairCopiedToAnotherKey() {
        Map<String, byte[]> entries = new HashMap<>();
        String sourceKey = "dependency_slice/org/example/source.jar";
        String destinationKey = "dependency_slice/org/example/destination.jar";
        SliceIntegrity.put(cache(entries), sourceKey, "payload".getBytes());
        entries.put(destinationKey, entries.get(sourceKey));
        entries.put(SliceIntegrity.checksumKeyFor(destinationKey), entries.get(SliceIntegrity.checksumKeyFor(sourceKey)));

        SliceIntegrityException exception = assertThrows(
                SliceIntegrityException.class,
                () -> SliceIntegrity.fetchVerified(cache(entries), destinationKey));

        assertEquals("checksum mismatch for key 'dependency_slice/org/example/destination.jar'", exception.getMessage());
    }

    private static SliceCache cache(Map<String, byte[]> entries) {
        return new SliceCache() {
            @Override
            public Optional<byte[]> fetch(String key) {
                return Optional.ofNullable(entries.get(key));
            }

            @Override
            public void put(String key, byte[] data) {
                entries.put(key, data);
            }
        };
    }
}
