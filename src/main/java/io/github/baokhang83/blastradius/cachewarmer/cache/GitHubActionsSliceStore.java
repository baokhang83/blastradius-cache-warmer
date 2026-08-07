package io.github.baokhang83.blastradius.cachewarmer.cache;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.Optional;

/**
 * {@link SliceCache} backed by the GitHub Actions runner cache service v2.
 */
public final class GitHubActionsSliceStore implements SliceCache {

    /**
     * GitHub's cache v2 protocol requires a 64-character format hash. It is deliberately
     * independent of a slice key: it changes only when the ZIP payload or integrity-sidecar
     * format becomes incompatible with an earlier release.
     */
    static final String CACHE_VERSION = "8b1fdce126e55b8554a95e236d7e9035287b451bd4c4a9603230483743d954a4";

    private final GitHubActionsCacheClient client;

    GitHubActionsSliceStore(GitHubActionsCacheClient client) {
        this.client = client;
    }

    public static GitHubActionsSliceStore fromEnvironment(HttpClient httpClient, Map<String, String> environment) {
        return new GitHubActionsSliceStore(GitHubActionsCacheClient.fromEnvironment(httpClient, environment));
    }

    @Override
    public Optional<byte[]> fetch(String key) {
        validateKey(key);
        try {
            Optional<java.net.URI> signedUrl = client.lookup(key, CACHE_VERSION);
            if (signedUrl.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(client.download(signedUrl.get()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SliceCacheException("GitHub Actions cache fetch failed for key '" + key + "': " + e.getMessage(), e);
        } catch (IOException e) {
            throw new SliceCacheException("GitHub Actions cache fetch failed for key '" + key + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void put(String key, byte[] data) {
        validateKey(key);
        try {
            Optional<java.net.URI> signedUrl = client.reserve(key, CACHE_VERSION);
            if (signedUrl.isEmpty()) {
                throw new IOException("cache entry was not reserved, another job may own this key");
            }
            client.upload(signedUrl.get(), data);
            client.finalizeUpload(key, CACHE_VERSION, data.length);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SliceCacheException("GitHub Actions cache put failed for key '" + key + "': " + e.getMessage(), e);
        } catch (IOException e) {
            throw new SliceCacheException("GitHub Actions cache put failed for key '" + key + "': " + e.getMessage(), e);
        }
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 512 || key.contains(",")) {
            throw new IllegalArgumentException("cache key must be nonblank, at most 512 characters, and contain no commas");
        }
    }
}
