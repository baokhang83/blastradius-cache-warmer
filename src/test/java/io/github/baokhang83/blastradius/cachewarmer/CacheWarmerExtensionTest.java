package io.github.baokhang83.blastradius.cachewarmer;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheWarmerExtensionTest {

    @Test
    void constructorRequiresOnlyTheSisuManagedCacheFactory() {
        assertArrayEquals(
                new Class<?>[] {io.github.baokhang83.blastradius.cachewarmer.cache.RuntimeCacheFactory.class},
                java.util.Arrays.stream(CacheWarmerExtension.class.getConstructors())
                        .filter(constructor -> constructor.isAnnotationPresent(javax.inject.Inject.class))
                        .findFirst()
                        .orElseThrow()
                        .getParameterTypes());
    }

    @Test
    void identifiesTheNestedBlastradiusTrackingProcess() {
        Properties properties = new Properties();
        properties.setProperty("blastradius.trackChild", "true");

        assertTrue(CacheWarmerExtension.isBlastradiusTrackChild(properties));
    }

    @Test
    void doesNotTreatAnOuterBuildAsANestedBlastradiusTrackingProcess() {
        assertFalse(CacheWarmerExtension.isBlastradiusTrackChild(new Properties()));
    }
}
