package audio.sprocket.lagsense;

import java.util.*;

/** Shared grouping and navigation state; changing detail views preserves the overview page. */
public final class TaskNavigation {
    public static final List<String> SECTIONS=List.of("Event timings","CPU activity","Call paths");
    public static final boolean DEFAULT_EVENTS=true, DEFAULT_EVENT_BY_MOD=true,DEFAULT_CPU_BY_MOD=true;
    public String owner;
    public int page;
    private int overviewPage;
    public void select(String mod) { overviewPage = page; owner = mod; page = 0; }
    public boolean back() { if (owner == null) return false; owner = null; page = overviewPage; return true; }
    public void reset() { owner = null; page = overviewPage = 0; }
    public static List<Report.Task> tasks(List<Report.Task> source, boolean grouped, String owner) {
        if (owner != null) return source.stream().filter(t -> t.owner().equals(owner)).toList();
        if (!grouped) return source;
        Map<String, Long> sums = new HashMap<>(); source.forEach(t -> sums.merge(t.owner(), t.samples(), Long::sum));
        return sums.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey)).map(e -> new Report.Task(e.getKey(), "", e.getValue())).toList();
    }
    public static String simple(String method) {
        String value = method.replace("Event callback · ", ""); int hash = value.indexOf('#'); int end = hash < 0 ? value.length() : hash;
        return value.substring(value.lastIndexOf('.', end) + 1);
    }
    public static String callbackState(Report.Diagnostics d) {
        if (d == null) return "No event timing data.";
        if (!d.callbacks().isEmpty()) return "";
        return switch (d.callbackState()) {
            case "disabled" -> "Event timing was off for this profile.";
            case "unavailable" -> "Event timing was unavailable.";
            case "enabled", "ready" -> "No supported events were measured.";
            default -> "No event timing data in this profile.";
        };
    }
}
