package net.sprocketgames.lagsense;

/** Absolute captured µs/t, shared by every target and aggregate. No capture-relative scaling. */
public final class TimingSeverity {
    public static final double MIN_MICROS=0,MAX_MICROS=10_000;
    private static final double LOG_RANGE=Math.log1p(MAX_MICROS);
    /** Logarithmic position keeps sub-microsecond work and millisecond aggregates readable together. */
    public static double position(double micros){
        if(!(micros>MIN_MICROS))return 0;
        if(micros>=MAX_MICROS)return 1;
        return Math.log1p(micros)/LOG_RANGE;
    }
    public static int color(double micros){return PerformanceGradient.color(position(micros));}
    /** A colourbar position maps back to the same absolute timing used by map cells. */
    public static double legendMicros(double fraction){return fraction<=0||Double.isNaN(fraction)?MIN_MICROS:fraction>=1?MAX_MICROS:Math.expm1(fraction*LOG_RANGE);}
    private TimingSeverity(){}
}
