package net.sprocketgames.lagsense;

import java.util.*;

/** Chronological normalized aggregates and retained sources for identity-aware investigation. */
public record ProfileComparison(long beforeId, long afterId, long beforeAt, long afterAt, int beforeTicks, int afterTicks,
                                double beforeMspt, double afterMspt, boolean incomplete, List<Change> types, List<Change> chunks, Details details, boolean presentationLimited) {
    public record Details(List<Report.Entry> earlier,List<Report.Entry> later,boolean earlierIncomplete,boolean laterIncomplete,String earlierLimit,String laterLimit) {}
    public ProfileComparison(long beforeId,long afterId,long beforeAt,long afterAt,int beforeTicks,int afterTicks,double beforeMspt,double afterMspt,boolean incomplete,List<Change> types,List<Change> chunks){this(beforeId,afterId,beforeAt,afterAt,beforeTicks,afterTicks,beforeMspt,afterMspt,incomplete,types,chunks,null,false);}
    public ProfileComparison preview(){return new ProfileComparison(beforeId,afterId,beforeAt,afterAt,beforeTicks,afterTicks,beforeMspt,afterMspt,incomplete,types.stream().limit(128).toList(),chunks.stream().limit(128).toList(),null,types.size()>128||chunks.size()>128);}
    public record Change(String dimension, String kind, String type, int x, int z, double beforeMicros, double afterMicros,
                         int beforeTargets, int afterTargets) {
        public double delta() { return afterMicros - beforeMicros; }
        public Double percentChange(){return beforeTargets>0&&afterTargets>0?percentage(beforeMicros,afterMicros):null;}
    }
    public static Double percentage(double before,double after){
        if(!Double.isFinite(before)||!Double.isFinite(after)||before<=0||after<0)return null;
        double percent=(after/before-1)*100;return Double.isFinite(percent)?percent:null;
    }
    public static String percentLabel(Double percent){return percent==null?"":String.format(Locale.ROOT,"%+.1f%%",percent==0?0:percent);}
    private record Key(String dimension, String kind, String type, int x, int z) {}
    private record Cost(long nanos, int targets) {}
    public static ProfileComparison between(Report before, Report after) {
        ProfileArchive.validate(before); ProfileArchive.validate(after);
        int order = Long.compare(before.capturedAt(), after.capturedAt());
        if (order == 0) order = Long.compare(before.id(), after.id());
        if (order > 0) { var swap = before; before = after; after = swap; }
        return new ProfileComparison(before.id(), after.id(), before.capturedAt(), after.capturedAt(), before.ticks(), after.ticks(), before.mspt(), after.mspt(),
            before.truncated() || after.truncated(), changes(before, after, false), changes(before, after, true),new Details(before.entries(),after.entries(),before.truncated(),after.truncated(),ReportCompleteness.reason(before),ReportCompleteness.reason(after)),false);
    }
    public static boolean matches(Change change, int direction) { return direction == 0 || (direction == 1 ? change.delta() > 0 : change.delta() < 0); }
    private static Map<Key, Cost> group(Report report, boolean chunks) {
        Map<Key, Cost> result = new HashMap<>();
        for (var e : report.entries()) {
            Key key = chunks ? new Key(e.dimension(), "Chunk", "", e.x() >> 4, e.z() >> 4) : new Key(e.dimension(), e.kind(), e.type(), 0, 0);
            result.merge(key, new Cost(e.nanos(), 1), (a, b) -> new Cost(a.nanos() + b.nanos(), a.targets() + b.targets()));
        }
        return result;
    }
    private static List<Change> changes(Report before, Report after, boolean chunks) {
        var first = group(before, chunks); var second = group(after, chunks);
        Set<Key> keys = new HashSet<>(first.keySet()); keys.addAll(second.keySet());
        var result = new ArrayList<Change>();
        for (Key key : keys) {
            Cost a = first.getOrDefault(key, new Cost(0, 0)), b = second.getOrDefault(key, new Cost(0, 0));
            result.add(new Change(key.dimension(), key.kind(), key.type(), key.x(), key.z(), a.nanos() / 1000.0 / before.ticks(),
                b.nanos() / 1000.0 / after.ticks(), a.targets(), b.targets()));
        }
        return result.stream().sorted(Comparator.<Change>comparingDouble(c -> Math.abs(c.delta())).reversed()
            .thenComparing(Change::dimension).thenComparing(Change::kind).thenComparing(Change::type).thenComparingInt(Change::x).thenComparingInt(Change::z)).toList();
    }
}
