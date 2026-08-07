package io.github.baokhang83.blastradius.cachewarmer.slicekey;

import io.github.baokhang83.blastradius.cachewarmer.dependency.DependencyCoordinate;

/**
 * Names a single Tier B cache object from the Maven-repository path where Maven will use it.
 * Unlike the module-output keys in {@link SliceKeyComputer}, a third-party JAR is reusable across
 * modules, so the coordinate itself is the complete cache identity.
 */
public final class DependencySliceKey {

    private DependencySliceKey() {}

    public static String keyFor(DependencyCoordinate coordinate) {
        return "dependency_slice/" + coordinate.repositoryPath().toString().replace('\\', '/');
    }
}
