package io.github.baokhang83.blastradius.cachewarmer;

import org.apache.maven.plugin.MojoExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/** Configures one Maven Compiler Plugin execution to reuse a verified restored output directory. */
final class MavenCompilerSkipper {

    boolean skip(MojoExecution execution) {
        Xpp3Dom configuration = execution.getConfiguration();
        if (configuration == null) {
            configuration = new Xpp3Dom("configuration");
            execution.setConfiguration(configuration);
        }
        Xpp3Dom skip = configuration.getChild("skipMain");
        if (skip == null) {
            skip = new Xpp3Dom("skipMain");
            configuration.addChild(skip);
        }
        skip.setValue("true");
        return true;
    }
}
