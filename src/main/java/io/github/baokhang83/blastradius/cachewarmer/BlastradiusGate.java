package io.github.baokhang83.blastradius.cachewarmer;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;

/**
 * Detects whether the current reactor is a blastradius user: cache-warmer is a hard-gated
 * add-on, so it only ever activates for reactors that declare {@code blastradius-maven-plugin}
 * in at least one module's build plugins. This is a presence check, not a license or config
 * validation - blastradius itself has neither.
 */
@Named
@Singleton
public class BlastradiusGate {

    static final String BLASTRADIUS_GROUP_ID = "io.github.baokhang83.blastradius";
    static final String BLASTRADIUS_PLUGIN_ARTIFACT_ID = "blastradius-maven-plugin";

    public GateResult check(List<MavenProject> projects) {
        for (MavenProject project : projects) {
            for (Plugin plugin : project.getBuildPlugins()) {
                if (BLASTRADIUS_GROUP_ID.equals(plugin.getGroupId())
                        && BLASTRADIUS_PLUGIN_ARTIFACT_ID.equals(plugin.getArtifactId())) {
                    return GateResult.PRESENT;
                }
            }
        }
        return GateResult.ABSENT;
    }
}
