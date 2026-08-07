package io.github.baokhang83.blastradius.cachewarmer.reactor;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Root module basedir is "" (the reactor root); every other module's basedir is relative to it,
 * e.g. "module-a". {@link #project} sets both, matching how {@link ReactorModuleGraph#from}
 * relativizes every module against the execution-root project's basedir.
 */
class ReactorModuleGraphTest {

    @Test
    void moduleOf_returnsTheDeepestMatchingModule_forAPathUnderItsBasedir() {
        MavenProject root = project("root", "", true);
        MavenProject moduleA = project("module-a", "module-a", false);
        ReactorModuleGraph graph = ReactorModuleGraph.from(List.of(root, moduleA));

        Optional<ModuleId> found = graph.moduleOf("module-a/src/main/java/Foo.java");

        assertEquals(Optional.of(new ModuleId("module-a", "module-a")), found);
    }

    @Test
    void moduleOf_fallsBackToTheRootModule_forAPathNotUnderAnySubmodule() {
        MavenProject root = project("root", "", true);
        MavenProject moduleA = project("module-a", "module-a", false);
        ReactorModuleGraph graph = ReactorModuleGraph.from(List.of(root, moduleA));

        Optional<ModuleId> found = graph.moduleOf("pom.xml");

        assertEquals(Optional.of(new ModuleId("root", "")), found);
    }

    @Test
    void dependentsOf_includesTheModuleItself() {
        MavenProject root = project("root", "", true);
        ReactorModuleGraph graph = ReactorModuleGraph.from(List.of(root));

        assertEquals(Set.of(new ModuleId("root", "")), graph.dependentsOf(new ModuleId("root", "")));
    }

    @Test
    void dependentsOf_includesModulesThatDeclareADirectDependencyOnIt() {
        MavenProject root = project("root", "", true);
        MavenProject core = project("core", "core", false);
        MavenProject api = project("api", "api", false);
        dependOn(api, "core");
        ReactorModuleGraph graph = ReactorModuleGraph.from(List.of(root, core, api));

        Set<ModuleId> dependents = graph.dependentsOf(new ModuleId("core", "core"));

        assertEquals(Set.of(new ModuleId("core", "core"), new ModuleId("api", "api")), dependents);
    }

    @Test
    void dependentsOf_isTransitive_acrossAChainOfDependencies() {
        MavenProject root = project("root", "", true);
        MavenProject core = project("core", "core", false);
        MavenProject api = project("api", "api", false);
        MavenProject web = project("web", "web", false);
        dependOn(api, "core");
        dependOn(web, "api");
        ReactorModuleGraph graph = ReactorModuleGraph.from(List.of(root, core, api, web));

        Set<ModuleId> dependents = graph.dependentsOf(new ModuleId("core", "core"));

        assertEquals(
                Set.of(new ModuleId("core", "core"), new ModuleId("api", "api"), new ModuleId("web", "web")),
                dependents);
    }

    @Test
    void dependentsOf_includesAChildModule_whenItsParentPomChanges() {
        MavenProject root = project("root", "", true);
        MavenProject core = project("core", "core", false);
        core.setParent(root);
        ReactorModuleGraph graph = ReactorModuleGraph.from(List.of(root, core));

        Set<ModuleId> dependents = graph.dependentsOf(new ModuleId("root", ""));

        assertTrue(dependents.contains(new ModuleId("core", "core")),
                "a child module inherits from its parent POM, so a parent change reaches it too");
    }

    @Test
    void isReactorWide_isFalse_forAPathThatFallsUnderTheRootModule() {
        MavenProject root = project("root", "", true);
        ReactorModuleGraph graph = ReactorModuleGraph.from(List.of(root));

        assertEquals(false, graph.isReactorWide("pom.xml"));
    }

    @Test
    void isReactorWide_isTrue_whenNoProjectIsFlaggedAsTheExecutionRoot() {
        // Every module's basedir is relativized against the execution root's basedir; without
        // one, there's no anchor to relativize against, so nothing can be safely attributed -
        // the conservative fallback, never guess narrower.
        MavenProject moduleA = project("module-a", "module-a", false);
        ReactorModuleGraph graph = ReactorModuleGraph.from(List.of(moduleA));

        assertEquals(true, graph.isReactorWide("module-a/src/main/java/Foo.java"));
    }

    private static MavenProject project(String artifactId, String basedir, boolean executionRoot) {
        Model model = new Model();
        model.setArtifactId(artifactId);
        MavenProject project = new MavenProject(model);
        project.setFile(new File("/repo/" + basedir, "pom.xml"));
        project.setExecutionRoot(executionRoot);
        return project;
    }

    private static void dependOn(MavenProject project, String artifactId) {
        Dependency dependency = new Dependency();
        dependency.setArtifactId(artifactId);
        project.getModel().addDependency(dependency);
    }
}
