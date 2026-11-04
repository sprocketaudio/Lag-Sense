package net.sprocketgames.lagsense;

public final class ProfileSchedule {
    public record Settings(boolean enabled, int intervalMinutes, int captureSeconds) {
        public Settings {
            if (intervalMinutes < 1 || intervalMinutes > 1440 || captureSeconds < 5 || captureSeconds > 120
                || captureSeconds >= intervalMinutes * 60) throw new IllegalArgumentException("Capture must be 5–120 seconds and shorter than its 1–1440 minute interval");
        }
        public static Settings defaults() { return new Settings(false, 5, 30); }
    }
    private Settings settings = Settings.defaults();
    private long nextDue;
    public Settings settings() { return settings; }
    public void configure(Settings value, long now) { settings = value; nextDue = now + value.intervalMinutes() * 60_000_000_000L; }
    public long remainingSeconds(long now) { return Math.max(0, (nextDue - now + 999_999_999L) / 1_000_000_000L); }
    public boolean due(long now, boolean profiling) {
        if (!settings.enabled() || now < nextDue) return false;
        // Advance even if busy: no overlapping captures and no catch-up queue.
        nextDue = now + settings.intervalMinutes() * 60_000_000_000L;
        return !profiling;
    }
}
