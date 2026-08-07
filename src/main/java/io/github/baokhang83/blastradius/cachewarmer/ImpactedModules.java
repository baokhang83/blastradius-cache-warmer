package io.github.baokhang83.blastradius.cachewarmer;

import java.util.Optional;
import java.util.Set;

/**
 * The result of {@link BlastRadiusResolver#resolve}. Two distinct kinds of "nothing specific to
 * report" must not collapse into one: {@link #isEmpty()} means nothing changed - safe to trust
 * the cache fully - while {@link #isReactorWide()} means the opposite, the module graph itself
 * couldn't be attributed, so every module must be treated as impacted even though none can be
 * named individually. A caller that only checked {@code impacts().isEmpty()} would silently
 * confuse the two and warm nothing when it should warm nothing *because* everything's unsafe.
 */
public final class ImpactedModules {

    private final Set<ModuleImpact> impacts;
    private final boolean reactorWide;
    private final String reactorWideReason;

    private ImpactedModules(Set<ModuleImpact> impacts, boolean reactorWide, String reactorWideReason) {
        this.impacts = impacts;
        this.reactorWide = reactorWide;
        this.reactorWideReason = reactorWideReason;
    }

    public static ImpactedModules of(Set<ModuleImpact> impacts) {
        return new ImpactedModules(Set.copyOf(impacts), false, null);
    }

    public static ImpactedModules reactorWide(String reason) {
        return new ImpactedModules(Set.of(), true, reason);
    }

    public Set<ModuleImpact> impacts() {
        return impacts;
    }

    public boolean isReactorWide() {
        return reactorWide;
    }

    public Optional<String> reactorWideReason() {
        return Optional.ofNullable(reactorWideReason);
    }

    public boolean isEmpty() {
        return !reactorWide && impacts.isEmpty();
    }
}
