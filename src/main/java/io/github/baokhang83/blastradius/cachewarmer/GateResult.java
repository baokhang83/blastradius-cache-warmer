package io.github.baokhang83.blastradius.cachewarmer;

/**
 * Whether this reactor is a blastradius user. A plugin declaration either exists or it doesn't
 * - there is no corrupt or expired state to model, unlike a parsed config/license file would
 * have had.
 */
public enum GateResult {
    PRESENT,
    ABSENT
}
