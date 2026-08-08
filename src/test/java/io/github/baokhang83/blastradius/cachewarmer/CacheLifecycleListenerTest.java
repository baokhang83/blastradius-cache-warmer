package io.github.baokhang83.blastradius.cachewarmer;

import io.github.baokhang83.blastradius.cachewarmer.cache.SliceCache;
import io.github.baokhang83.blastradius.cachewarmer.reactor.ModuleId;
import io.github.baokhang83.blastradius.cachewarmer.warmer.WarmResult;
import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheLifecycleListenerTest {

    @Test
    void restoresBothTiersBeforeACompilerExecutionOutsideTheBlastRadius() {
        AtomicInteger bytecodeWarms = new AtomicInteger();
        AtomicInteger compilerStateWarms = new AtomicInteger();
        CacheLifecycleListener listener = listener(ImpactedModules.of(Set.of()), bytecodeWarms, compilerStateWarms);

        ExecutionEvent event = compilerEvent(project("safe-module"), "compile");
        listener.mojoStarted(event);

        assertEquals(1, bytecodeWarms.get());
        assertEquals(1, compilerStateWarms.get());
        assertEquals("true", event.getMojoExecution().getConfiguration().getChild("skipMain").getValue());
    }

    @Test
    void keepsAnImpactedModuleCold() {
        AtomicInteger bytecodeWarms = new AtomicInteger();
        AtomicInteger compilerStateWarms = new AtomicInteger();
        ImpactedModules impacts = ImpactedModules.of(Set.of(
                new ModuleImpact(new ModuleId("changed-module", "changed-module"), "changed source")));
        CacheLifecycleListener listener = listener(impacts, bytecodeWarms, compilerStateWarms);

        listener.mojoStarted(compilerEvent(project("changed-module"), "compile"));

        assertEquals(0, bytecodeWarms.get());
        assertEquals(0, compilerStateWarms.get());
    }

    @Test
    void doesNotRestoreForTestCompilation() {
        AtomicInteger bytecodeWarms = new AtomicInteger();
        AtomicInteger compilerStateWarms = new AtomicInteger();
        CacheLifecycleListener listener = listener(ImpactedModules.of(Set.of()), bytecodeWarms, compilerStateWarms);

        listener.mojoStarted(compilerEvent(project("safe-module"), "testCompile"));

        assertEquals(0, bytecodeWarms.get());
        assertEquals(0, compilerStateWarms.get());
    }

    @Test
    void doesNotSkipCompilerWhenOnlyOneTierRestores() {
        AtomicInteger bytecodeWarms = new AtomicInteger();
        AtomicInteger compilerStateWarms = new AtomicInteger();
        CacheLifecycleListener listener = listener(
                ImpactedModules.of(Set.of()), bytecodeWarms, compilerStateWarms, WarmResult.restored("bytecode restored"),
                WarmResult.skipped("compiler state unavailable"));
        ExecutionEvent event = compilerEvent(project("safe-module"), "compile");

        listener.mojoStarted(event);

        assertEquals(1, bytecodeWarms.get());
        assertEquals(1, compilerStateWarms.get());
        assertEquals(null, event.getMojoExecution().getConfiguration());
    }

    private static CacheLifecycleListener listener(
            ImpactedModules impacts, AtomicInteger bytecodeWarms, AtomicInteger compilerStateWarms) {
        return listener(
                impacts, bytecodeWarms, compilerStateWarms,
                WarmResult.restored("bytecode restored"), WarmResult.restored("compiler state restored"));
    }

    private static CacheLifecycleListener listener(
            ImpactedModules impacts,
            AtomicInteger bytecodeWarms,
            AtomicInteger compilerStateWarms,
            WarmResult bytecodeResult,
            WarmResult compilerStateResult) {
        SliceCache cache = new SliceCache() {
            @Override
            public java.util.Optional<byte[]> fetch(String key) {
                return java.util.Optional.empty();
            }

            @Override
            public void put(String key, byte[] data) {
            }
        };
        RuntimeBuildContext context = new RuntimeBuildContext(cache, impacts);
        return new CacheLifecycleListener(
                new AbstractExecutionListener(),
                context,
                ignored -> {
                    bytecodeWarms.incrementAndGet();
                    return bytecodeResult;
                },
                ignored -> {
                    compilerStateWarms.incrementAndGet();
                    return compilerStateResult;
                });
    }

    private static ExecutionEvent compilerEvent(MavenProject project, String goal) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-compiler-plugin");
        MojoExecution execution = new MojoExecution(plugin, goal, "default-" + goal);
        return new ExecutionEvent() {
            @Override
            public Type getType() {
                return Type.MojoStarted;
            }

            @Override
            public org.apache.maven.execution.MavenSession getSession() {
                return null;
            }

            @Override
            public MavenProject getProject() {
                return project;
            }

            @Override
            public MojoExecution getMojoExecution() {
                return execution;
            }

            @Override
            public Exception getException() {
                return null;
            }
        };
    }

    private static MavenProject project(String artifactId) {
        MavenProject project = new MavenProject();
        project.setArtifactId(artifactId);
        return project;
    }
}
