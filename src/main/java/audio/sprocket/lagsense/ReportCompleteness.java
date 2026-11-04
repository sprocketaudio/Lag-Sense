package audio.sprocket.lagsense;

public final class ReportCompleteness {
    public static String reason(Report report) {
        if(!report.truncated())return "";
        if(report.diagnostics()!=null){var reasons=report.diagnostics().notes().stream().filter(note->note.startsWith("Capture stopped:")||note.startsWith("Result presentation limited:")).toList();if(!reasons.isEmpty())return String.join("\n",reasons)+"\nTotals use the available measurements.";}
        return "Some results are missing from this older report, but it does not record why. Totals use the available measurements.";
    }
    public static String label(Report report){return !report.truncated()?"":reason(report).startsWith("Capture stopped:")?"Capture stopped: memory budget":reason(report).startsWith("Result presentation limited:")?"Results limited: client preview":"Incomplete legacy results";}
    private ReportCompleteness(){}
}
