package io.github.baokhang83.blastradius.cachewarmer.cache;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import java.lang.reflect.Proxy;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeCacheFactoryTest {

    @Test
    void defaultsToTheGitHubActionsCache() {
        RuntimeCacheFactory factory = factory();

        SliceCache cache = factory.create(new Properties(), runnerEnvironment());

        assertInstanceOf(GitHubActionsSliceStore.class, cache);
    }

    @Test
    void createsS3OnlyWhenExplicitlyConfigured() {
        AtomicInteger createdClients = new AtomicInteger();
        RuntimeCacheFactory factory = new RuntimeCacheFactory(
                HttpClient::newHttpClient,
                () -> {
                    createdClients.incrementAndGet();
                    return unusedS3Client();
                });
        Properties properties = new Properties();
        properties.setProperty("blastradius.cache.backend", "s3");
        properties.setProperty(RuntimeCacheFactory.S3_BUCKET_PROPERTY, "cache-bucket");
        properties.setProperty(RuntimeCacheFactory.S3_NAMESPACE_PROPERTY, "build-cache/v1");

        SliceCache cache = factory.create(properties, Map.of());

        assertInstanceOf(S3SliceStore.class, cache);
        assertEquals(1, createdClients.get());
    }

    @Test
    void rejectsAnIncompleteS3ConfigurationBeforeCreatingAClient() {
        AtomicInteger createdClients = new AtomicInteger();
        RuntimeCacheFactory factory = new RuntimeCacheFactory(
                HttpClient::newHttpClient,
                () -> {
                    createdClients.incrementAndGet();
                    return unusedS3Client();
                });
        Properties properties = new Properties();
        properties.setProperty("blastradius.cache.backend", "s3");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> factory.create(properties, Map.of()));

        assertEquals(0, createdClients.get());
        assertEquals(
                "blastradius.cache.s3.bucket is required when blastradius.cache.backend=s3",
                error.getMessage());
    }

    private static RuntimeCacheFactory factory() {
        return new RuntimeCacheFactory(HttpClient::newHttpClient, RuntimeCacheFactoryTest::unusedS3Client);
    }

    private static Map<String, String> runnerEnvironment() {
        return Map.of("ACTIONS_RESULTS_URL", "https://results.example.test", "ACTIONS_RUNTIME_TOKEN", "token");
    }

    private static S3Client unusedS3Client() {
        return (S3Client) Proxy.newProxyInstance(
                RuntimeCacheFactoryTest.class.getClassLoader(),
                new Class<?>[] {S3Client.class},
                (proxy, method, arguments) -> {
                    throw new AssertionError("The factory must not use S3 while constructing a store");
                });
    }
}
