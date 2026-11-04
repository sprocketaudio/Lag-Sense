package audio.sprocket.lagsense;

/** Fixed-size capture-period counters. TPS uses elapsed time, never inverse tick cost. */
public record CapturePerformance(long elapsedNanos,int measuredTicks,long peakNanos,int overBudgetTicks,long overBudgetNanos,double lowestTps,int windows,String triggerReason,int slowWindows) {
    public CapturePerformance(long elapsedNanos,int measuredTicks,long peakNanos,int overBudgetTicks,long overBudgetNanos,double lowestTps,int windows,String triggerReason){this(elapsedNanos,measuredTicks,peakNanos,overBudgetTicks,overBudgetNanos,lowestTps,windows,triggerReason,0);}
    public static final long BUDGET=50_000_000L;
    public double averageTps(){return elapsedNanos>0?Math.min(20,measuredTicks*1e9/elapsedNanos):Double.NaN;}
    public double durationSeconds(){return elapsedNanos/1e9;}
    public boolean valid(){return elapsedNanos>0&&measuredTicks>0&&peakNanos>=0&&overBudgetTicks>=0&&overBudgetTicks<=measuredTicks&&overBudgetNanos>=0&&windows>=0&&slowWindows>=0&&slowWindows<=windows&&Double.isFinite(lowestTps)&&lowestTps>=0&&lowestTps<=20&&triggerReason!=null&&triggerReason.length()<=512;}
    public static final class Counter {
        private long start,windowStart,peak,overNanos;private int ticks,windowTicks,over,windows,slow;private double lowest=20;
        public void start(long now){start=windowStart=now;peak=overNanos=0;ticks=windowTicks=over=windows=slow=0;lowest=20;}
        public void tick(long ended,long nanos){
            ticks++;windowTicks++;peak=Math.max(peak,Math.max(0,nanos));if(nanos>BUDGET){over++;overNanos+=nanos;}
            long elapsed=ended-windowStart;if(elapsed>=1_000_000_000L){double tps=Math.min(20,windowTicks*1e9/elapsed);lowest=Math.min(lowest,tps);if(tps<10)slow++;windows++;windowTicks=0;windowStart=ended;}
        }
        public CapturePerformance finish(long ended,String reason){return new CapturePerformance(Math.max(0,ended-start),ticks,peak,over,overNanos,windows==0?0:lowest,windows,reason==null?"":reason,slow);}
    }
}
