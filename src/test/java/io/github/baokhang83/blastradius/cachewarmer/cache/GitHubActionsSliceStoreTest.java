package io.github.baokhang83.blastradius.cachewarmer.cache;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubActionsSliceStoreTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetch_returnsTheBytesFromTheSignedDownloadUrl() throws IOException {
        server = server();
        server.createContext("/twirp/github.actions.results.api.v1.CacheService/GetCacheEntryDownloadURL", exchange -> {
            assertEquals("Bearer runtime-token", exchange.getRequestHeaders().getFirst("Authorization"));
            assertTrue(readRequest(exchange).contains("\"key\":\"slice-key\""));
            respond(exchange, 200, "{\"ok\":true,\"signed_download_url\":\"" + baseUrl() + "/download\"}");
        });
        server.createContext("/download", exchange -> respond(exchange, 200, "cached bytes"));
        server.start();

        Optional<byte[]> result = store().fetch("slice-key");

        assertTrue(result.isPresent());
        assertArrayEquals("cached bytes".getBytes(StandardCharsets.UTF_8), result.get());
    }

    @Test
    void fetch_returnsEmptyWhenTheCacheServiceDoesNotFindAnEntry() throws IOException {
        server = server();
        server.createContext("/twirp/github.actions.results.api.v1.CacheService/GetCacheEntryDownloadURL",
                exchange -> respond(exchange, 200, "{\"ok\":false}"));
        server.start();

        assertEquals(Optional.empty(), store().fetch("missing-key"));
    }

    @Test
    void put_reservesUploadsAndFinalizesTheEntry() throws IOException {
        List<String> operations = new ArrayList<>();
        server = server();
        server.createContext("/twirp/github.actions.results.api.v1.CacheService/CreateCacheEntry", exchange -> {
            operations.add("reserve:" + readRequest(exchange));
            respond(exchange, 200, "{\"ok\":true,\"signed_upload_url\":\"" + baseUrl() + "/upload\"}");
        });
        server.createContext("/upload", exchange -> {
            assertEquals("BlockBlob", exchange.getRequestHeaders().getFirst("x-ms-blob-type"));
            operations.add(exchange.getRequestMethod() + ":" + readRequest(exchange));
            respond(exchange, 201, "");
        });
        server.createContext("/twirp/github.actions.results.api.v1.CacheService/FinalizeCacheEntryUpload", exchange -> {
            operations.add("finalize:" + readRequest(exchange));
            respond(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        store().put("slice-key", "payload".getBytes(StandardCharsets.UTF_8));

        assertEquals(3, operations.size());
        assertTrue(operations.get(0).contains("\"key\":\"slice-key\""));
        assertEquals("PUT:payload", operations.get(1));
        assertTrue(operations.get(2).contains("\"size_bytes\":\"7\""));
    }

    @Test
    void put_throwsWhenTheCacheEntryCannotBeReserved() throws IOException {
        server = server();
        server.createContext("/twirp/github.actions.results.api.v1.CacheService/CreateCacheEntry",
                exchange -> respond(exchange, 200, "{\"ok\":false}"));
        server.start();

        SliceCacheException thrown =
                assertThrows(SliceCacheException.class, () -> store().put("slice-key", new byte[0]));

        assertTrue(thrown.getMessage().contains("slice-key"));
    }

    @Test
    void constructorFromEnvironment_requiresTheRunnerSettings() {
        assertThrows(IllegalArgumentException.class,
                () -> GitHubActionsSliceStore.fromEnvironment(HttpClient.newHttpClient(), Map.of()));
    }

    @Test
    void cacheBackend_defaultsToGitHubActionsAndRequiresAnExplicitS3Choice() {
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("blastradius.cache.backend", "s3");

        assertEquals(CacheBackend.GITHUB_ACTIONS, CacheBackend.fromConfiguredValue(null));
        assertEquals(CacheBackend.GITHUB_ACTIONS, CacheBackend.fromConfiguredValue("github-actions"));
        assertEquals(CacheBackend.S3, CacheBackend.fromConfiguredValue("s3"));
        assertEquals(CacheBackend.S3, CacheBackend.fromSystemProperties(properties));
    }

    private GitHubActionsSliceStore store() {
        return GitHubActionsSliceStore.fromEnvironment(
                HttpClient.newHttpClient(),
                Map.of("ACTIONS_RESULTS_URL", baseUrl(), "ACTIONS_RUNTIME_TOKEN", "runtime-token"));
    }

    private HttpServer server() throws IOException {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String readRequest(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
