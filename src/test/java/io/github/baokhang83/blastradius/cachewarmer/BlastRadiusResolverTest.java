package io.github.baokhang83.blastradius.cachewarmer;

import io.github.baokhang83.blastradius.cachewarmer.git.GitDiff;
import io.github.baokhang83.blastradius.cachewarmer.reactor.ModuleId;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlastRadiusResolverTest {

    @Test
    void resolve_returnsEmpty_whenNothingChanged() {
        BlastRadiusResolver resolver = new BlastRadiusResolver(fakeGitDiff(List.of()));
        MavenProject root = project("root", "", true);

        ImpactedModules result = resolver.resolve(List.of(root), "main");

        assertTrue(result.isEmpty());
        assertFalse(result.isReactorWide());
    }

    @Test
    void resolve_impactsTheOwningModule_forADirectChange() {
        MavenProject root = project("root", "", true);
        MavenProject moduleA = project("module-a", "module-a", false);
        BlastRadiusResolver resolver = new BlastRadiusResolver(fakeGitDiff(List.of("module-a/src/Foo.java")));

        ImpactedModules result = resolver.resolve(List.of(root, moduleA), "main");

        assertEquals(
                Set.of(new ModuleImpact(new ModuleId("module-a", "module-a"), "changed: module-a/src/Foo.java")),
                result.impacts());
    }

    @Test
    void resolve_impactsTransitiveDependents_withAReasonNamingTheDependency() {
        MavenProject root = project("root", "", true);
        MavenProject core = project("core", "core", false);
        MavenProject api = project("api", "api", false);
        dependOn(api, "core");
        BlastRadiusResolver resolver = new BlastRadiusResolver(fakeGitDiff(List.of("core/src/Core.java")));

        ImpactedModules result = resolver.resolve(List.of(root, core, api), "main");

        assertEquals(
                Set.of(
                        new ModuleImpact(new ModuleId("core", "core"), "changed: core/src/Core.java"),
                        new ModuleImpact(new ModuleId("api", "api"),
                                "depends on core, which changed via core/src/Core.java")),
                result.impacts());
    }

    @Test
    void resolve_isReactorWide_whenTheModuleGraphCannotBeAttributed() {
        MavenProject moduleA = project("module-a", "module-a", false); // no execution root present
        BlastRadiusResolver resolver = new BlastRadiusResolver(fakeGitDiff(List.of("module-a/Foo.java")));

        ImpactedModules result = resolver.resolve(List.of(moduleA), "main");

        assertTrue(result.isReactorWide());
        assertTrue(result.reactorWideReason().orElseThrow().contains("module-a/Foo.java"));
        assertTrue(result.impacts().isEmpty());
    }

    private static GitDiff fakeGitDiff(List<String> changedPaths) {
        return new GitDiff(new File(".")) {
            @Override
            public List<String> changedPaths(String baseRef) {
                return changedPaths;
            }
        };
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
