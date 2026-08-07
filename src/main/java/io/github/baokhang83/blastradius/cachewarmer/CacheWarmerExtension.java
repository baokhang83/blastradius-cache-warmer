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
import java.util.List;
import java.util.Properties;

/**
 * Entry point every later tier (T2-T7) hooks into. Maven discovers this via Sisu component
 * scanning (declared as a Core Extension in a consuming project's {@code .mvn/extensions.xml})
 * and calls {@link #afterProjectsRead} once the reactor is built, before dependency resolution
 * - the manifesto's pre-resolution phase.
 */
@Named("cache-warmer")
@Singleton
public class CacheWarmerExtension extends AbstractMavenLifecycleParticipant {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheWarmerExtension.class);

    private final BlastradiusGate gate;
    private final RuntimeCacheFactory cacheFactory;
    private RuntimeBuildContext runtimeContext;

    @Inject
    public CacheWarmerExtension(BlastradiusGate gate, RuntimeCacheFactory cacheFactory) {
        this.gate = gate;
        this.cacheFactory = cacheFactory;
    }

    CacheWarmerExtension(BlastradiusGate gate) {
        this(gate, new RuntimeCacheFactory());
    }

    @Override
    public void afterProjectsRead(MavenSession session) {
        if (!applyGate(session.getProjects())) {
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

    /**
     * The actual gate-then-warm boundary, split out from {@link #afterProjectsRead} so it's
     * testable without constructing a real {@link MavenSession}. Constitution SS3: this must
     * fail open. A build-time surprise here - in code whose entire job is shaving CI minutes -
     * must never turn into someone else's broken build, so any unexpected exception is logged
     * and swallowed rather than propagated.
     */
    boolean applyGate(List<MavenProject> projects) {
        try {
            GateResult result = gate.check(projects);
            if (result == GateResult.ABSENT) {
                LOGGER.debug("[cache-warmer] blastradius-maven-plugin not found in reactor - skipping (no-op)");
                return false;
            }
            LOGGER.info("[cache-warmer] blastradius-maven-plugin detected - gate passed");
            return true;
        } catch (RuntimeException e) {
            LOGGER.warn("[cache-warmer] gate check failed unexpectedly - continuing with a cold build", e);
            return false;
        }
    }

    private static Properties mergedProperties(MavenSession session) {
        Properties properties = new Properties();
        properties.putAll(session.getSystemProperties());
        properties.putAll(session.getUserProperties());
        return properties;
    }

    private static String baseRef(MavenSession session) {
        return mergedProperties(session).getProperty("blastradius.cache.baseRef", "origin/main");
    }
}
