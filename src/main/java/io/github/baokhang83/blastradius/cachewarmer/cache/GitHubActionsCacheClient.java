package io.github.baokhang83.blastradius.cachewarmer.cache;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal client for the GitHub Actions runner cache service v2. The runner supplies the service
 * URL and short-lived token, while this client uses service-issued signed URLs for byte transfer.
 */
final class GitHubActionsCacheClient {

    private static final String SERVICE_NAME = "github.actions.results.api.v1.CacheService";
    private static final Pattern JSON_BOOLEAN = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");

    private final HttpClient httpClient;
    private final URI resultsUrl;
    private final String runtimeToken;

    GitHubActionsCacheClient(HttpClient httpClient, URI resultsUrl, String runtimeToken) {
        this.httpClient = httpClient;
        this.resultsUrl = resultsUrl;
        this.runtimeToken = runtimeToken;
    }

    static GitHubActionsCacheClient fromEnvironment(HttpClient httpClient, java.util.Map<String, String> environment) {
        String resultsUrl = requiredEnvironment(environment, "ACTIONS_RESULTS_URL");
        String runtimeToken = requiredEnvironment(environment, "ACTIONS_RUNTIME_TOKEN");
        return new GitHubActionsCacheClient(httpClient, URI.create(resultsUrl), runtimeToken);
    }

    Optional<URI> lookup(String key, String cacheVersion) throws IOException, InterruptedException {
        String response = post("GetCacheEntryDownloadURL", requestBody(key, cacheVersion));
        return acceptedSignedUrl(response, "signed_download_url");
    }

    Optional<URI> reserve(String key, String cacheVersion) throws IOException, InterruptedException {
        String response = post("CreateCacheEntry", requestBody(key, cacheVersion));
        return acceptedSignedUrl(response, "signed_upload_url");
    }

    byte[] download(URI signedDownloadUrl) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = httpClient.send(
                HttpRequest.newBuilder(signedDownloadUrl).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        requireSuccess("download", response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        return response.body();
    }

    void upload(URI signedUploadUrl, byte[] data) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(signedUploadUrl)
                        .header("x-ms-blob-type", "BlockBlob")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        requireSuccess("upload", response.statusCode(), response.body());
    }

    void finalizeUpload(String key, String cacheVersion, long sizeBytes) throws IOException, InterruptedException {
        String body = "{\"key\":\"" + escape(key) + "\",\"version\":\"" + escape(cacheVersion)
                + "\",\"size_bytes\":\"" + sizeBytes + "\"}";
        String response = post("FinalizeCacheEntryUpload", body);
        if (!accepted(response)) {
            throw new IOException("GitHub Actions cache service rejected finalization: " + response);
        }
    }

    private String post(String method, String body) throws IOException, InterruptedException {
        URI endpoint = resultsUrl.resolve("/twirp/" + SERVICE_NAME + "/" + method);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Authorization", "Bearer " + runtimeToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        requireSuccess(method, response.statusCode(), response.body());
        return response.body();
    }

    private static Optional<URI> acceptedSignedUrl(String response, String field) throws IOException {
        if (!accepted(response)) {
            return Optional.empty();
        }
        return Optional.of(URI.create(jsonString(response, field)));
    }

    private static boolean accepted(String response) throws IOException {
        Matcher matcher = JSON_BOOLEAN.matcher(response);
        if (!matcher.find()) {
            throw new IOException("GitHub Actions cache service response did not contain 'ok': " + response);
        }
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static String jsonString(String json, String field) throws IOException {
        Matcher matcher = Pattern.compile(
                        "\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"")
                .matcher(json);
        if (!matcher.find()) {
            throw new IOException("GitHub Actions cache service response did not contain '" + field + "': " + json);
        }
        return matcher.group(1).replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String requestBody(String key, String cacheVersion) {
        return "{\"key\":\"" + escape(key) + "\",\"version\":\"" + escape(cacheVersion) + "\"}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String requiredEnvironment(java.util.Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required for the GitHub Actions cache backend");
        }
        return value;
    }

    private static void requireSuccess(String operation, int statusCode, String body) throws IOException {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("GitHub Actions cache " + operation + " failed with HTTP " + statusCode + ": " + body);
        }
    }
}
