package audio.sprocket.lagsense;

/** Sample share, never inferred duration or CPU utilisation. Uses the displayed percentage's denominator. */
public final class SampleContribution {
    public static double fraction(long samples,long total){return samples<=0||total<=0?0:Math.min(1,(double)samples/total);}
    public static int color(long samples,long total){return PerformanceGradient.color(fraction(samples,total));}
    private SampleContribution(){}
}
