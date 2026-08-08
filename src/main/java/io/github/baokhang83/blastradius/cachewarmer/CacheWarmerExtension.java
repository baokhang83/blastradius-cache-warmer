package io.github.baokhang83.blastradius.cachewarmer;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import io.github.baokhang83.blastradius.cachewarmer.cache.RuntimeCacheFactory;
import io.github.baokhang83.blastradius.cachewarmer.cache.SliceCache;
import io.github.baokhang83.blastradius.cachewarmer.git.GitDiff;
import io.github.baokhang83.blastradius.cachewarmer.slicekey.SliceKeyComputer;
import io.github.baokhang83.blastradius.cachewarmer.publisher.SlicePublisher;
import io.github.baokhang83.blastradius.cachewarmer.warmer.CompilerStateWarmer;
import io.github.baokhang83.blastradius.cachewarmer.warmer.SiblingBytecodeWarmer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.util.Properties;

/**
 * Maven discovers this Core Extension through Sisu component scanning and calls
 * {@link #afterProjectsRead} once the consuming reactor is built, before dependency resolution.
 */
@Named("cache-warmer")
@Singleton
public class CacheWarmerExtension extends AbstractMavenLifecycleParticipant {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheWarmerExtension.class);

    private final RuntimeCacheFactory cacheFactory;
    private RuntimeBuildContext runtimeContext;

    @Inject
    public CacheWarmerExtension(RuntimeCacheFactory cacheFactory) {
        this.cacheFactory = cacheFactory;
    }

    CacheWarmerExtension() {
        this(new RuntimeCacheFactory());
    }

    @Override
    public void afterProjectsRead(MavenSession session) {
        if (isBlastradiusTrackChild(mergedProperties(session))) {
            // Blastradius TRACK deliberately launches `mvn clean test` in a child process.
            // Any restored outputs would be deleted immediately by `clean`, and that child
            // must not publish competing slices while the outer build is still active.
            LOGGER.info("[cache-warmer] Blastradius TRACK child detected - skipping cache runtime setup");
            return;
        }
        try {
            SliceCache cache = cacheFactory.create(mergedProperties(session), System.getenv());
            ImpactedModules impacts = new BlastRadiusResolver(
                    new GitDiff(new File(session.getExecutionRootDirectory())))
                    .resolve(session.getProjects(), baseRef(session));
            RuntimeBuildContext context = new RuntimeBuildContext(cache, impacts);
            SliceKeyComputer keys = new SliceKeyComputer();
            ExecutionListener previous = session.getRequest().getExecutionListener();
            session.getRequest().setExecutionListener(new CacheLifecycleListener(
                    previous == null ? new AbstractExecutionListener() : previous,
                    context,
                    new SiblingBytecodeWarmer(cache, keys)::warm,
                    new CompilerStateWarmer(cache, keys)::warm));
            runtimeContext = context;
            LOGGER.info("[cache-warmer] runtime restore listener registered");
        } catch (RuntimeException e) {
            LOGGER.warn("[cache-warmer] runtime setup failed - continuing with a cold build", e);
        }
    }

    @Override
    public void afterSessionEnd(MavenSession session) {
        RuntimeBuildContext context = runtimeContext;
        runtimeContext = null;
        if (context == null) {
            return;
        }
        if (session.getResult().hasExceptions()) {
            LOGGER.info("[cache-warmer] build failed - cache slices not published");
            return;
        }

        SlicePublisher publisher = new SlicePublisher(context.cache(), new SliceKeyComputer());
        new RuntimeCachePublisher(publisher::publish).publish(session.getProjects());
    }

    private static Properties mergedProperties(MavenSession session) {
        Properties properties = new Properties();
        properties.putAll(session.getSystemProperties());
        properties.putAll(session.getUserProperties());
        return properties;
    }

    static boolean isBlastradiusTrackChild(Properties properties) {
        return Boolean.parseBoolean(properties.getProperty("blastradius.trackChild", "false"));
    }

    private static String baseRef(MavenSession session) {
        return mergedProperties(session).getProperty("blastradius.cache.baseRef", "origin/main");
    }
}
