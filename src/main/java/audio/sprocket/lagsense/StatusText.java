package audio.sprocket.lagsense;

import java.util.*;

/** Player-facing status values, separate from configuration and delivery diagnostics. */
public final class StatusText {
    private StatusText() {}
    public record Line(String label, String value) {}
    public static String onOff(boolean value) { return value ? "On" : "Off"; }
    public static String duration(long seconds) {
        seconds = Math.max(0, seconds);
        if (seconds < 60) return seconds + "s";
        return seconds / 60 + "m" + (seconds % 60 == 0 ? "" : " " + seconds % 60 + "s");
    }
    public static String number(double value) { return String.format(Locale.ROOT, value == Math.rint(value) ? "%.0f" : "%.1f", value); }
    public static String timer(ProfileSchedule.Settings value) {
        return value.enabled() ? "Every " + value.intervalMinutes() + "m (" + value.captureSeconds() + "s)" : "Off";
    }
    public static String note(String value) {
        if (value.startsWith("Backup overlapped capture: ")) return value.replace("Backup overlapped capture: ", "Backup ran during profile: ");
        if (value.equals("Backup grace period was active when this manual capture started.")) return "Backup finished shortly before this profile.";
        if (value.equals("CPU sample analysis pending.")) return "Processing CPU activity…";
        return value;
    }
    public static List<Line> summary(ServerSettings settings, String capture, String backup, String discord) {
        return summary(settings, capture, backup, discord, false);
    }
    public static List<Line> summary(ServerSettings settings, String capture, String backup, String discord, boolean pausedForBackup) {
        return List.of(new Line("Profile", capture), new Line("Timer", pausedForBackup && settings.timer().enabled() ? "Paused for backup" : timer(settings.timer())), new Line("Spike capture", pausedForBackup && settings.spikes().enabled() ? "Paused for backup" : onOff(settings.spikes().enabled())),
            new Line("History", onOff(settings.history().enabled())), new Line("Discord", discord), new Line("Backups", backup));
    }
}
