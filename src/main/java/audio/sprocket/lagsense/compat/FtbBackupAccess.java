package audio.sprocket.lagsense.compat;

import dev.ftb.mods.ftbbackups.Backups;

final class FtbBackupAccess {
    static boolean running() { return Backups.getServerInstance().backupStatus.isRunningOrDone(); }
}
