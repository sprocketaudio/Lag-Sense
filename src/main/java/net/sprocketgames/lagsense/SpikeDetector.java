package net.sprocketgames.lagsense;

/** Explicit TPS/work thresholds. No learned normal, recovery gate or overlapping captures. */
public final class SpikeDetector {
    public record Settings(boolean enabled, double thresholdMs, int consecutive, int captureSeconds, int cooldownSeconds,
                           double minimumTps, int sustainSeconds) {
        public Settings(boolean enabled, double thresholdMs, int consecutive, int captureSeconds, int cooldownSeconds) {
            this(enabled, thresholdMs, consecutive, captureSeconds, cooldownSeconds, 0, 10);
        }
        public Settings {
            if (!Double.isFinite(thresholdMs) || thresholdMs < 0 || thresholdMs > 10000) throw new IllegalArgumentException("Tick-work threshold must be 0–10000 ms, with 0 to turn it off.");
            if (!Double.isFinite(minimumTps) || minimumTps < 0 || minimumTps > 20 || minimumTps > 0 && minimumTps < 1) throw new IllegalArgumentException("TPS threshold must be 1–20, or 0 to turn it off.");
            if (enabled && thresholdMs == 0 && minimumTps == 0) throw new IllegalArgumentException("Enable at least one spike threshold.");
            if (consecutive < 1 || consecutive > 100 || captureSeconds < 5 || captureSeconds > 120 || cooldownSeconds < 30 || cooldownSeconds > 86400 || sustainSeconds < 1 || sustainSeconds > 120)
                throw new IllegalArgumentException("Check the spike duration, sustain time, consecutive ticks and cooldown against their allowed ranges.");
        }
        public static Settings defaults() { return new Settings(false, 0, 3, 10, 120, 12, 10); }
    }
    private Settings settings = Settings.defaults();
    private int streak, windowTicks;
    private long nextEligible, windowStart;
    private boolean waitingForCompletion;
    private double anomalySeconds, recentTps;
    private String reason = "";
    public Settings settings() { return settings; }
    public String reason() { return reason; }
    public double recentTps() { return recentTps; }
    public void configure(Settings value, long now) { settings = value; streak = 0; waitingForCompletion = false; nextEligible = now + 30_000_000_000L; resetWindow(); }
    public void resetWindow() { windowStart = 0; windowTicks = 0; anomalySeconds = 0; recentTps = 0; }
    public void captureFinished(long now) {
        if (waitingForCompletion) { nextEligible = now + settings.cooldownSeconds() * 1_000_000_000L; waitingForCompletion = false; }
    }
    public boolean observe(long elapsedNanos, long now, boolean profiling) {
        if (!settings.enabled()) return false;
        boolean tpsTrigger = false;
        if (settings.minimumTps() > 0) {
            if (windowStart == 0) windowStart = now;
            else {
                windowTicks++;
                double seconds = (now - windowStart) / 1_000_000_000.0;
                if (seconds >= 1) {
                    recentTps = Math.min(20, windowTicks / seconds);
                    if (recentTps < settings.minimumTps() && !profiling && !waitingForCompletion && now >= nextEligible) anomalySeconds += seconds;
                    else anomalySeconds = 0;
                    tpsTrigger = anomalySeconds >= settings.sustainSeconds();
                    windowStart = now; windowTicks = 0;
                }
            }
        }
        if (profiling || waitingForCompletion || now < nextEligible) { streak = 0; anomalySeconds = 0; return false; }
        boolean slowWork = settings.thresholdMs() > 0 && elapsedNanos / 1_000_000.0 >= settings.thresholdMs();
        streak = slowWork ? streak + 1 : 0;
        if (!tpsTrigger && streak < settings.consecutive()) return false;
        reason = tpsTrigger ? String.format(java.util.Locale.ROOT, "TPS %.1f stayed below configured %.1f for at least %ds", recentTps, settings.minimumTps(), settings.sustainSeconds())
            : "Tick work >= " + settings.thresholdMs() + " ms for " + settings.consecutive() + " consecutive ticks";
        streak = 0; anomalySeconds = 0; waitingForCompletion = true; return true;
    }
}
