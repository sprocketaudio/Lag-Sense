package net.sprocketgames.lagsense;

import java.util.*;

final class PresentationChecks {
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void run() {
        require(PageNumbers.label(0, 0, 5).equals("Page 1 of 1"), "Empty result pages remain readable");
        require(PageNumbers.label(2, 11, 5).equals("Page 3 of 3") && PageNumbers.count(10, 5) == 2, "Page count handles full and partial last pages");
        require(PageNumbers.clamp(5, 2, 5) == 0, "Switching to a smaller result set cannot leave the page out of range");
        var summary = StatusText.summary(ServerSettings.defaults(), "No results", "Idle", "Off");
        require(summary.size() == 6 && summary.stream().allMatch(r -> r.label().length() + r.value().length() < 48), "Status is a compact set of named values");
        require(summary.stream().filter(r -> r.label().equals("Timer")).findFirst().orElseThrow().value().equals("Off"), "Disabled features do not print unused configuration");
        String plain = CommandOutput.table("LagSense", summary).getString();
        require(plain.contains("\nTimer: Off\n") && !plain.contains("false") && !plain.contains("credential") && !plain.contains("config/") && !plain.contains("defer_automatic"), "Chat status contains no internal state or configuration narration");
        require(CommandOutput.format("Timer: Off\nNext run: In 2m").getString().equals("Timer: Off\nNext run: In 2m"), "Shared formatting preserves separate labelled lines");
        require(StatusText.duration(61).equals("1m 1s") && StatusText.duration(-1).equals("0s"), "Player-facing durations are short and non-negative");
        var backup = new BackupMonitor(); backup.update(Set.of("FTB Backups 3"), 1);
        require(backup.status(BackupMonitor.Settings.defaults(), 2).equals("Running (FTB Backups 3)"), "Backup status uses a readable value");
    }
}
