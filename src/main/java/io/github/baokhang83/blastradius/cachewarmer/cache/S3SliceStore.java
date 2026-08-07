package io.github.baokhang83.blastradius.cachewarmer.cache;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Optional;

/**
 * {@link SliceCache} backed by S3. A {@link NoSuchKeyException} is the one expected outcome of
 * "nothing is cached at this key" and is translated to {@code Optional.empty()}; every other
 * {@link SdkException} (network, auth, a bucket that doesn't exist, ...) means the cache itself
 * couldn't answer and is wrapped as {@link SliceCacheException} instead - see design.md for why
 * that distinction matters to callers.
 */
public class S3SliceStore implements SliceCache {

    private final S3Client client;
    private final String bucket;
    private final String keyPrefix;

    public S3SliceStore(S3Client client, String bucket, String keyPrefix) {
        this.client = client;
        this.bucket = bucket;
        this.keyPrefix = requireCacheNamespace(keyPrefix);
    }

    @Override
    public Optional<byte[]> fetch(String key) {
        try {
            ResponseBytes<GetObjectResponse> response = client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(objectKey(key)).build());
            return Optional.of(response.asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (SdkException e) {
            throw new SliceCacheException(
                    "S3 fetch failed for key '" + key + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void put(String key, byte[] data) {
        try {
            client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(objectKey(key)).build(),
                    RequestBody.fromBytes(data));
        } catch (SdkException e) {
            throw new SliceCacheException(
                    "S3 put failed for key '" + key + "': " + e.getMessage(), e);
        }
    }

    private String objectKey(String key) {
        return keyPrefix + "/" + key;
    }

    private static String requireCacheNamespace(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("keyPrefix must name a nonempty cache namespace");
        }
        if (keyPrefix.startsWith("/") || keyPrefix.endsWith("/")) {
            throw new IllegalArgumentException("keyPrefix must not start or end with '/'");
        }
        for (String segment : keyPrefix.split("/")) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(
                        "keyPrefix must not contain empty, '.' or '..' namespace segments");
            }
        }
        return keyPrefix;
    }
}
