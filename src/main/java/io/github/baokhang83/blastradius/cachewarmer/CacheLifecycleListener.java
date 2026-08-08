package io.github.baokhang83.blastradius.cachewarmer;

import io.github.baokhang83.blastradius.cachewarmer.warmer.WarmResult;
import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/** Restores safe module state immediately before Maven Compiler executes a production compile. */
final class CacheLifecycleListener extends AbstractExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheLifecycleListener.class);

    private final ExecutionListener delegate;
    private final RuntimeBuildContext context;
    private final Function<MavenProject, WarmResult> bytecodeWarmer;
    private final Function<MavenProject, WarmResult> compilerStateWarmer;
    private final MavenCompilerSkipper compilerSkipper;

    CacheLifecycleListener(
            ExecutionListener delegate,
            RuntimeBuildContext context,
            Function<MavenProject, WarmResult> bytecodeWarmer,
            Function<MavenProject, WarmResult> compilerStateWarmer) {
        this(delegate, context, bytecodeWarmer, compilerStateWarmer, new MavenCompilerSkipper());
    }

    CacheLifecycleListener(
            ExecutionListener delegate,
            RuntimeBuildContext context,
            Function<MavenProject, WarmResult> bytecodeWarmer,
            Function<MavenProject, WarmResult> compilerStateWarmer,
            MavenCompilerSkipper compilerSkipper) {
        this.delegate = delegate;
        this.context = context;
        this.bytecodeWarmer = bytecodeWarmer;
        this.compilerStateWarmer = compilerStateWarmer;
        this.compilerSkipper = compilerSkipper;
    }

    @Override
    public void projectDiscoveryStarted(ExecutionEvent event) {
        delegate.projectDiscoveryStarted(event);
    }

    @Override
    public void sessionStarted(ExecutionEvent event) {
        delegate.sessionStarted(event);
    }

    @Override
    public void sessionEnded(ExecutionEvent event) {
        delegate.sessionEnded(event);
    }

    @Override
    public void projectSkipped(ExecutionEvent event) {
        delegate.projectSkipped(event);
    }

    @Override
    public void projectStarted(ExecutionEvent event) {
        delegate.projectStarted(event);
    }

    @Override
    public void projectSucceeded(ExecutionEvent event) {
        delegate.projectSucceeded(event);
    }

    @Override
    public void projectFailed(ExecutionEvent event) {
        delegate.projectFailed(event);
    }

    @Override
    public void forkStarted(ExecutionEvent event) {
        delegate.forkStarted(event);
    }

    @Override
    public void forkSucceeded(ExecutionEvent event) {
        delegate.forkSucceeded(event);
    }

    @Override
    public void forkFailed(ExecutionEvent event) {
        delegate.forkFailed(event);
    }

    @Override
    public void mojoSkipped(ExecutionEvent event) {
        delegate.mojoSkipped(event);
    }

    @Override
    public void mojoStarted(ExecutionEvent event) {
        delegate.mojoStarted(event);
        if (!isProductionCompiler(event) || event.getProject() == null) {
            return;
        }

        MavenProject project = event.getProject();
        if (!context.isSafeToWarm(project)) {
            LOGGER.info("[cache-warmer] {} cold: {}", project.getArtifactId(),
                    context.coldBuildReason(project).orElse("inside the blast radius"));
            return;
        }

        WarmResult bytecode = warm(project, "sibling bytecode", bytecodeWarmer);
        WarmResult compilerState = warm(project, "compiler state", compilerStateWarmer);
        if (bytecode.status() == WarmResult.WarmStatus.RESTORED
                && compilerState.status() == WarmResult.WarmStatus.RESTORED) {
            skipCompiler(project, event.getMojoExecution());
        }
    }

    @Override
    public void mojoSucceeded(ExecutionEvent event) {
        delegate.mojoSucceeded(event);
    }

    @Override
    public void mojoFailed(ExecutionEvent event) {
        delegate.mojoFailed(event);
    }

    @Override
    public void forkedProjectStarted(ExecutionEvent event) {
        delegate.forkedProjectStarted(event);
    }

    @Override
    public void forkedProjectSucceeded(ExecutionEvent event) {
        delegate.forkedProjectSucceeded(event);
    }

    @Override
    public void forkedProjectFailed(ExecutionEvent event) {
        delegate.forkedProjectFailed(event);
    }

    private static boolean isProductionCompiler(ExecutionEvent event) {
        return event.getMojoExecution() != null
                && "org.apache.maven.plugins".equals(event.getMojoExecution().getGroupId())
                && "maven-compiler-plugin".equals(event.getMojoExecution().getArtifactId())
                && "compile".equals(event.getMojoExecution().getGoal());
    }

    private static WarmResult warm(
            MavenProject project, String tier, Function<MavenProject, WarmResult> warmer) {
        try {
            WarmResult result = warmer.apply(project);
            LOGGER.info("[cache-warmer] {} {}: {}", project.getArtifactId(), tier, result.reason());
            return result;
        } catch (RuntimeException e) {
            LOGGER.warn("[cache-warmer] {} {} failed - continuing cold", project.getArtifactId(), tier, e);
            return WarmResult.skipped("runtime cache warming failed");
        }
    }

    private void skipCompiler(MavenProject project, MojoExecution execution) {
        try {
            compilerSkipper.skip(execution);
            LOGGER.info("[cache-warmer] {} compiler: skipped after verified bytecode and compiler-state restore",
                    project.getArtifactId());
        } catch (RuntimeException e) {
            LOGGER.warn("[cache-warmer] {} compiler skip setup failed - continuing cold", project.getArtifactId(), e);
        }
    }
}
