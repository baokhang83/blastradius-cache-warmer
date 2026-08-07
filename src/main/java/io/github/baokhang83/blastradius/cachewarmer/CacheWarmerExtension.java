package io.github.baokhang83.blastradius.cachewarmer;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;

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

    @Inject
    public CacheWarmerExtension(BlastradiusGate gate) {
        this.gate = gate;
    }

    @Override
    public void afterProjectsRead(MavenSession session) {
        applyGate(session.getProjects());
    }

    /**
     * The actual gate-then-warm boundary, split out from {@link #afterProjectsRead} so it's
     * testable without constructing a real {@link MavenSession}. Constitution SS3: this must
     * fail open. A build-time surprise here - in code whose entire job is shaving CI minutes -
     * must never turn into someone else's broken build, so any unexpected exception is logged
     * and swallowed rather than propagated.
     */
    void applyGate(List<MavenProject> projects) {
        try {
            GateResult result = gate.check(projects);
            if (result == GateResult.ABSENT) {
                LOGGER.debug("[cache-warmer] blastradius-maven-plugin not found in reactor - skipping (no-op)");
                return;
            }
            LOGGER.info("[cache-warmer] blastradius-maven-plugin detected - gate passed");
            // Tier A/B/C warmers hook in here (T2+); nothing to warm yet in this slice.
        } catch (RuntimeException e) {
            LOGGER.warn("[cache-warmer] gate check failed unexpectedly - continuing with a cold build", e);
        }
    }
}
