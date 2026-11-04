package net.sprocketgames.lagsense;

import java.util.*;

/** State only: no process scanning, file scanning, or required backup dependency. */
public final class BackupMonitor {
    public record Settings(String mode, int graceSeconds) {
        public Settings { if (!Set.of("defer_automatic", "warn", "ignore").contains(mode) || graceSeconds < 0 || graceSeconds > 3600) throw new IllegalArgumentException("Invalid backup mode or grace period (0–3600 seconds)."); }
        public static Settings defaults() { return new Settings("defer_automatic", 60); }
    }
    private Set<String> active = Set.of();
    private long ended;
    private boolean seen;
    public Set<String> active() { return active; }
    public void update(Set<String> values, long now) {
        if (!active.isEmpty() && values.isEmpty()) { ended = now; seen = true; }
        active = Set.copyOf(values);
    }
    public boolean blocked(Settings settings, long now) {
        return settings.mode().equals("defer_automatic") && (!active.isEmpty() || seen && now - ended < settings.graceSeconds() * 1_000_000_000L);
    }
    public String status(Settings settings, long now) {
        return !active.isEmpty() ? "Running (" + String.join(", ", active) + ")" : remainingSeconds(settings, now) > 0 ? "Finished recently" : "Idle";
    }
    public long remainingSeconds(Settings settings, long now) { return !seen ? 0 : Math.max(0, (settings.graceSeconds() * 1_000_000_000L - (now - ended) + 999_999_999L) / 1_000_000_000L); }
    public void clear() { active = Set.of(); seen = false; ended = 0; }
}
