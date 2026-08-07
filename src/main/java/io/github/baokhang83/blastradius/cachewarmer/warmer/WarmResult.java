package io.github.baokhang83.blastradius.cachewarmer.warmer;

/** A Tier A/C warm attempt's observable outcome, including the reason fit for build output. */
public record WarmResult(WarmStatus status, String reason) {

    public enum WarmStatus {
        RESTORED,
        SKIPPED
    }

    public static WarmResult restored(String reason) {
        return new WarmResult(WarmStatus.RESTORED, reason);
    }

    public static WarmResult skipped(String reason) {
        return new WarmResult(WarmStatus.SKIPPED, reason);
    }
}
