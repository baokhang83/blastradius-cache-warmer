package io.github.baokhang83.blastradius.cachewarmer.cache;

import software.amazon.awssdk.services.s3.S3Client;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Creates the one cache store a Maven session uses. The default is the GitHub Actions service
 * because it needs no separately managed storage; S3 remains an explicit, configured alternative.
 */
@Named
@Singleton
public class RuntimeCacheFactory {

    static final String S3_BUCKET_PROPERTY = "blastradius.cache.s3.bucket";
    static final String S3_NAMESPACE_PROPERTY = "blastradius.cache.s3.namespace";

    private final Supplier<HttpClient> httpClients;
    private final Supplier<S3Client> s3Clients;

    @Inject
    public RuntimeCacheFactory() {
        this(HttpClient::newHttpClient, () -> S3Client.builder().build());
    }

    RuntimeCacheFactory(Supplier<HttpClient> httpClients, Supplier<S3Client> s3Clients) {
        this.httpClients = httpClients;
        this.s3Clients = s3Clients;
    }

    /**
     * Creates a configured cache store. Invalid configuration deliberately throws so the runtime
     * boundary can report one clear reason and continue Maven as a cold build.
     */
    public SliceCache create(Properties properties, Map<String, String> environment) {
        return switch (CacheBackend.fromSystemProperties(properties)) {
            case GITHUB_ACTIONS -> GitHubActionsSliceStore.fromEnvironment(httpClients.get(), environment);
            case S3 -> s3Store(properties);
        };
    }

    private S3SliceStore s3Store(Properties properties) {
        String bucket = requiredProperty(properties, S3_BUCKET_PROPERTY);
        String namespace = requiredProperty(properties, S3_NAMESPACE_PROPERTY);
        return new S3SliceStore(s3Clients.get(), bucket, namespace);
    }

    private static String requiredProperty(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required when blastradius.cache.backend=s3");
        }
        return value;
    }
}
