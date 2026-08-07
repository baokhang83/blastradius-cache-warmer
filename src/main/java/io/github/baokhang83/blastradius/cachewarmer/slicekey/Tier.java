package io.github.baokhang83.blastradius.cachewarmer.slicekey;

/**
 * The manifesto's three warming tiers, named explicitly rather than passed around as "A"/"B"/"C"
 * codes - a name states what a thing is, not a code you'd have to look up (constitution's naming
 * standard).
 */
public enum Tier {
    SIBLING_BYTECODE,
    DEPENDENCY_SLICE,
    COMPILER_STATE
}
