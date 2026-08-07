package io.github.baokhang83.blastradius.cachewarmer;

import io.github.baokhang83.blastradius.cachewarmer.reactor.ModuleId;

/**
 * One impacted module and why - a plain, human-readable sentence (SS4 Explainability), not a
 * code a reader would have to look up.
 */
public record ModuleImpact(ModuleId module, String reason) {
}
