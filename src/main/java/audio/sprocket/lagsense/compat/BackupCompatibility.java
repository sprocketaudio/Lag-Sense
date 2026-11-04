package audio.sprocket.lagsense.compat;

import audio.sprocket.lagsense.*;
import java.util.*;
import net.neoforged.fml.ModList;

public final class BackupCompatibility {
    private static boolean warned;
    private BackupCompatibility() {}
    public static Set<String> active() {
        if (ModList.get().isLoaded("ftbbackups3") && System.getenv("FTB_BACKUPS_DISABLED") == null) {
            try { if (FtbBackupAccess.running()) return Set.of("FTB Backups 3"); }
            catch (RuntimeException | LinkageError error) {
                if (!warned) { warned = true; LagSense.LOG.warn("Could not inspect FTB Backups 3 activity; capture suppression unavailable", error); }
            }
        }
        return Set.of();
    }
}
