package net.sprocketgames.lagsense;

/** Conservative accounting includes collector maps, identities and snapshot headroom. */
public final class DiagnosticBudget {
    private final long maximum,configured;
    private final String mode;
    private long used;
    private boolean exhausted;
    public DiagnosticBudget(long maximum){this(maximum,maximum,"custom");}
    public DiagnosticBudget(long maximum,long configured,String mode){this.maximum=Math.max(0,maximum);this.configured=configured;this.mode=mode;}
    public static DiagnosticBudget events(){var l=ProfilingLimits.defaults();return l.events(l.effective());}
    public static DiagnosticBudget background(){var l=ProfilingLimits.defaults();return l.cpu(l.effective());}
    public boolean reserve(long bytes){if(bytes<0||bytes>maximum-used){exhausted=true;return false;}used+=bytes;return true;}
    public long maximum(){return maximum;}
    public boolean exhausted(){return exhausted;}
    public long used(){return used;}
    public long configured(){return configured;}
    public String mode(){return mode;}
    public String description(){return String.format(java.util.Locale.ROOT,"%.2f MiB effective (%s ceiling %.2f MiB)",maximum/(double)(1<<20),mode,configured/(double)(1<<20));}
}
