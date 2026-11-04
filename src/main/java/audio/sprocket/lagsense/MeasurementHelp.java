package audio.sprocket.lagsense;

public final class MeasurementHelp {
    public static final String TIME="µs/t is the average time used per server tick, in microseconds.";
    public static final String MSPT="MSPT is the average server tick time in milliseconds during this capture.";
    public static final String SAMPLES="Samples count how often this code was seen running.";
    public static final String DIRECT="Direct samples count how often this method itself was seen running.";
    public static final String STACK="In stack counts samples that include this method or code it called.";
    public static final String PERCENT="The percentage shows this result’s share of the samples in this view. It does not measure CPU usage.";
    public static final String RETAINED="Totals use the saved measurements and can include work timed elsewhere.";
    public static String value(String value){return value.contains("µs/t")||value.contains("ms/t")?TIME:value.endsWith("%")?SAMPLES+"\n"+PERCENT:"";}
    public static String diagnosticLimit(Report report,boolean events){
        if(report.diagnostics()==null)return "";
        return String.join("\n",report.diagnostics().notes().stream().filter(n->events?n.startsWith("Event results limited:")||n.equals("Detailed callback limit reached."):n.startsWith("CPU results limited:")).map(n->n.equals("Detailed callback limit reached.")?"Event collection limited: only 2048 listener/event pairs were instrumented.":n).toList());
    }
    private MeasurementHelp(){}
}
