package io.github.baokhang83.blastradius.cachewarmer.dependency;

import java.util.List;

/** Deterministic Tier B artifact list for later publishing and restoration. */
public record DependencyManifest(List<DependencyCoordinate> artifacts) {
    public DependencyManifest { artifacts = List.copyOf(artifacts); }
}
