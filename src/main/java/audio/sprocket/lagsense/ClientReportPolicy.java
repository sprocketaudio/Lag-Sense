package audio.sprocket.lagsense;

/** Opening a current report restores its HUD; background updates and saved profiles do not. */
public final class ClientReportPolicy {
    public static boolean restoreHud(boolean open, boolean archived) { return open && !archived; }
    public static boolean beganCapture(Report previous, Report current, boolean archived) {
        return !archived && current != null && current.recording()
            && (previous == null || !previous.recording() || previous.id() != current.id());
    }
    /** Only the initiating administrator receives the zero-tick capture-start acknowledgement. */
    public static boolean manualCaptureStarted(Report previous,Report current,boolean archived){
        return beganCapture(previous,current,archived)&&current.admin()&&current.ticks()==0;
    }
    public static boolean showHud(boolean enabled, boolean hasReport, boolean hasPlayer, boolean hideGui, boolean screenOpen, boolean preview) {
        return preview || enabled && hasReport && hasPlayer && !hideGui && !screenOpen;
    }
    private ClientReportPolicy() {}
}
