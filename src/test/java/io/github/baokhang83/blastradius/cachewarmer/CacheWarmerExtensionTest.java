package io.github.baokhang83.blastradius.cachewarmer;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link CacheWarmerExtension#applyGate} directly rather than
 * {@code afterProjectsRead(MavenSession)} - constructing a real {@code MavenSession} needs a
 * repository session, execution request, and execution result this unit doesn't otherwise
 * touch. {@code afterProjectsRead} itself is a one-line delegation to {@code applyGate}; Maven
 * actually invoking it at the right lifecycle point is an integration concern, not this slice's.
 */
class CacheWarmerExtensionTest {

    @Test
    void constructorRequiresOnlyTheSisuManagedGate() {
        assertArrayEquals(
                new Class<?>[] {BlastradiusGate.class, io.github.baokhang83.blastradius.cachewarmer.cache.RuntimeCacheFactory.class},
                java.util.Arrays.stream(CacheWarmerExtension.class.getConstructors())
                        .filter(constructor -> constructor.isAnnotationPresent(javax.inject.Inject.class))
                        .findFirst()
                        .orElseThrow()
                        .getParameterTypes());
    }

    @Test
    void doesNotThrow_whenPluginIsAbsent() {
        CacheWarmerExtension extension = new CacheWarmerExtension(new BlastradiusGate());

        assertDoesNotThrow(() -> extension.applyGate(List.of(projectWithPlugins())));
    }

    @Test
    void doesNotThrow_whenPluginIsPresent() {
        CacheWarmerExtension extension = new CacheWarmerExtension(new BlastradiusGate());
        MavenProject project = projectWithPlugins(
                plugin("io.github.baokhang83.blastradius", "blastradius-maven-plugin"));

        assertDoesNotThrow(() -> extension.applyGate(List.of(project)));
    }

    @Test
    void failsOpen_whenTheGateItselfThrows() {
        BlastradiusGate throwingGate = new BlastradiusGate() {
            @Override
            public GateResult check(List<MavenProject> projects) {
                throw new IllegalStateException("simulated gate failure");
            }
        };
        CacheWarmerExtension extension = new CacheWarmerExtension(throwingGate);

        assertDoesNotThrow(() -> extension.applyGate(List.of(projectWithPlugins())));
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

    private static MavenProject projectWithPlugins(Plugin... plugins) {
        Build build = new Build();
        for (Plugin plugin : plugins) {
            build.addPlugin(plugin);
        }
        Model model = new Model();
        model.setBuild(build);
        return new MavenProject(model);
    }

    private static Plugin plugin(String groupId, String artifactId) {
        Plugin plugin = new Plugin();
        plugin.setGroupId(groupId);
        plugin.setArtifactId(artifactId);
        return plugin;
    }
}
