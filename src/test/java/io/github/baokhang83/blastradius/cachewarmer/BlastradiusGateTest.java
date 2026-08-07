package io.github.baokhang83.blastradius.cachewarmer;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlastradiusGateTest {

    private final BlastradiusGate gate = new BlastradiusGate();

    @Test
    void returnsAbsent_whenReactorHasNoProjects() {
        assertEquals(GateResult.ABSENT, gate.check(List.of()));
    }

    @Test
    void returnsAbsent_whenNoProjectDeclaresBlastradiusMavenPlugin() {
        MavenProject project = projectWithPlugins(plugin("some.other.group", "some-other-plugin"));

        assertEquals(GateResult.ABSENT, gate.check(List.of(project)));
    }

    @Test
    void returnsPresent_whenAProjectDeclaresBlastradiusMavenPlugin() {
        MavenProject project = projectWithPlugins(
                plugin("some.other.group", "some-other-plugin"),
                plugin("io.github.baokhang83.blastradius", "blastradius-maven-plugin"));

        assertEquals(GateResult.PRESENT, gate.check(List.of(project)));
    }

    @Test
    void returnsPresent_whenAnyModuleInAMultiModuleReactorDeclaresIt() {
        MavenProject withoutPlugin = projectWithPlugins();
        MavenProject withPlugin = projectWithPlugins(
                plugin("io.github.baokhang83.blastradius", "blastradius-maven-plugin"));

        assertEquals(GateResult.PRESENT, gate.check(List.of(withoutPlugin, withPlugin)));
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
