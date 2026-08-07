package io.github.baokhang83.blastradius.cachewarmer.cache;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fakes {@link S3Client} directly (it's an interface, so - same idiom as {@code GitDiff}'s real
 * subprocess and {@code ReactorModuleGraph}'s real {@code MavenProject} - overriding only the two
 * operations {@link S3SliceStore} actually calls proves the real request/response shapes line up,
 * without pulling in a mocking framework or a live S3 bucket).
 */
class S3SliceStoreTest {

    @Test
    void fetch_returnsEmpty_whenTheKeyIsNotPresent() {
        S3SliceStore store = new S3SliceStore(fakeClient(new HashMap<>()), "bucket", "cache/v1");

        assertEquals(Optional.empty(), store.fetch("missing"));
    }

    @Test
    void fetch_returnsTheStoredBytes_whenPresent() {
        Map<String, byte[]> objects = new HashMap<>();
        objects.put("cache/v1/sibling_bytecode/core/abc", "hello".getBytes());
        S3SliceStore store = new S3SliceStore(fakeClient(objects), "bucket", "cache/v1");

        Optional<byte[]> result = store.fetch("sibling_bytecode/core/abc");

        assertTrue(result.isPresent());
        assertArrayEquals("hello".getBytes(), result.get());
    }

    @Test
    void fetch_appliesTheKeyPrefix_whenLookingUpTheObject() {
        Map<String, byte[]> objects = new HashMap<>();
        objects.put("ci/sibling_bytecode/core/abc", "hello".getBytes());
        S3SliceStore store = new S3SliceStore(fakeClient(objects), "bucket", "ci");

        Optional<byte[]> result = store.fetch("sibling_bytecode/core/abc");

        assertTrue(result.isPresent());
        assertArrayEquals("hello".getBytes(), result.get());
    }

    @Test
    void fetch_throwsSliceCacheException_whenTheRequestFails() {
        S3SliceStore store = new S3SliceStore(
                failingClient(SdkClientException.create("connection refused")), "bucket", "cache/v1");

        SliceCacheException thrown =
                assertThrows(SliceCacheException.class, () -> store.fetch("some-key"));

        assertTrue(thrown.getMessage().contains("some-key"));
        assertTrue(thrown.getMessage().contains("connection refused"));
    }

    @Test
    void put_storesBytesAtThePrefixedKey() {
        Map<String, byte[]> objects = new HashMap<>();
        S3SliceStore store = new S3SliceStore(fakeClient(objects), "bucket", "ci");

        store.put("sibling_bytecode/core/abc", "hello".getBytes());

        assertArrayEquals("hello".getBytes(), objects.get("ci/sibling_bytecode/core/abc"));
    }

    @Test
    void put_throwsSliceCacheException_whenTheRequestFails() {
        S3SliceStore store = new S3SliceStore(
                failingClient(SdkClientException.create("connection refused")), "bucket", "cache/v1");

        SliceCacheException thrown =
                assertThrows(SliceCacheException.class, () -> store.put("some-key", "data".getBytes()));

        assertTrue(thrown.getMessage().contains("some-key"));
        assertTrue(thrown.getMessage().contains("connection refused"));
    }

    @Test
    void constructor_rejectsAnEmptyOrUnsafeCacheNamespace() {
        S3Client client = fakeClient(new HashMap<>());

        assertThrows(IllegalArgumentException.class, () -> new S3SliceStore(client, "bucket", ""));
        assertThrows(IllegalArgumentException.class, () -> new S3SliceStore(client, "bucket", "/cache/v1"));
        assertThrows(IllegalArgumentException.class, () -> new S3SliceStore(client, "bucket", "cache/../v1"));
    }

    private static S3Client fakeClient(Map<String, byte[]> objects) {
        return new S3Client() {
            @Override
            public String serviceName() {
                return "s3";
            }

            @Override
            public void close() {
                // nothing to release
            }

            @Override
            public ResponseBytes<GetObjectResponse> getObjectAsBytes(GetObjectRequest request) {
                byte[] data = objects.get(request.key());
                if (data == null) {
                    throw NoSuchKeyException.builder().message("no such key: " + request.key()).build();
                }
                return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), data);
            }

            @Override
            public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
                objects.put(request.key(), readAll(body));
                return PutObjectResponse.builder().build();
            }
        };
    }

    private static S3Client failingClient(SdkClientException toThrow) {
        return new S3Client() {
            @Override
            public String serviceName() {
                return "s3";
            }

            @Override
            public void close() {
                // nothing to release
            }

            @Override
            public ResponseBytes<GetObjectResponse> getObjectAsBytes(GetObjectRequest request) {
                throw toThrow;
            }

            @Override
            public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
                throw toThrow;
            }
        };
    }

    private static byte[] readAll(RequestBody body) {
        try (InputStream in = body.contentStreamProvider().newStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
