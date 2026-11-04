package net.sprocketgames.lagsense;

/** Descriptive whole-capture workload classes, independent of spike trigger configuration. */
public enum ReportSeverity {
    HEALTHY("Healthy",0x57a773),WARNING("Warning",0xd8b34e),DEGRADED("Degraded",0xe38b45),CRITICAL("Critical",0xd95656),UNKNOWN("Insufficient Data",0xd8b76a);
    public final String label;public final int color;ReportSeverity(String label,int color){this.label=label;this.color=color;}
    public static ReportSeverity evaluate(Report report){
        var p=report.diagnostics()==null?null:report.diagnostics().performance();
        if(report.truncated()||p==null||!p.valid()||p.measuredTicks()!=report.ticks()||p.elapsedNanos()<1_000_000_000L||p.measuredTicks()<20||!Double.isFinite(report.mspt()))return UNKNOWN;
        double tps=p.averageTps(),share=(double)p.overBudgetTicks()/p.measuredTicks();
        if(p.elapsedNanos()>=5_000_000_000L&&(tps<10&&p.slowWindows()>=2||report.mspt()>=100&&share>=.5))return CRITICAL;
        if(tps<18||report.mspt()>=50&&share>=.25)return DEGRADED;
        if(tps<19.5||report.mspt()>=40||share>=.1||p.windows()>0&&p.lowestTps()<18)return WARNING;
        return HEALTHY;
    }
}
