package io.github.baokhang83.blastradius.cachewarmer;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenCompilerSkipperTest {

    @Test
    void addsSkipWithoutDiscardingExistingCompilerConfiguration() {
        MojoExecution execution = execution();
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        Xpp3Dom release = new Xpp3Dom("release");
        release.setValue("21");
        configuration.addChild(release);
        execution.setConfiguration(configuration);

        assertTrue(new MavenCompilerSkipper().skip(execution));

        assertEquals("21", execution.getConfiguration().getChild("release").getValue());
        assertEquals("true", execution.getConfiguration().getChild("skipMain").getValue());
    }

    @Test
    void createsConfigurationWhenTheExecutionHasNone() {
        MojoExecution execution = execution();

        assertTrue(new MavenCompilerSkipper().skip(execution));

        assertEquals("true", execution.getConfiguration().getChild("skipMain").getValue());
    }

    private static MojoExecution execution() {
        Plugin compiler = new Plugin();
        compiler.setGroupId("org.apache.maven.plugins");
        compiler.setArtifactId("maven-compiler-plugin");
        return new MojoExecution(compiler, "compile", "default-compile");
    }
}
