package audio.sprocket.lagsense;

import java.util.*;

public record Report(long id, long capturedAt, int ticks, double mspt, boolean admin,
                     boolean truncated, List<Entry> entries, boolean recording, int secondsRemaining, List<Chunk> chunks, double tps, Diagnostics diagnostics) {
    public record Task(String owner, String method, long samples,long threadId,String threadName) {
        public Task {threadName=threadName==null?"":threadName;}
        public Task(String owner,String method,long samples){this(owner,method,samples,0,"");}
    }
    public record Callback(String owner, String event, String method, long nanos, long calls, long maxNanos,long handlerId) {
        public Callback(String owner,String event,String method,long nanos,long calls,long maxNanos){this(owner,event,method,nanos,calls,maxNanos,0);}
        public Callback(String owner, String event, String method, long nanos, long calls) { this(owner, event, method, nanos, calls, 0); }
    }
    public record Diagnostics(String trigger, long bookkeepingNanos, long snapshotNanos, List<Task> tasks, List<String> notes, List<Callback> callbacks, String callbackState, CpuInvestigation cpu, TimingCoverage.Snapshot coverage, BackgroundCpu background,CapturePerformance performance) {
        public Diagnostics(String trigger,long bookkeepingNanos,long snapshotNanos,List<Task> tasks,List<String> notes,List<Callback> callbacks,String callbackState,CpuInvestigation cpu,TimingCoverage.Snapshot coverage,BackgroundCpu background){this(trigger,bookkeepingNanos,snapshotNanos,tasks,notes,callbacks,callbackState,cpu,coverage,background,null);}
        public Diagnostics {
            tasks = tasks == null ? List.of() : List.copyOf(tasks); notes = notes == null ? List.of() : List.copyOf(notes); callbacks = callbacks == null ? List.of() : List.copyOf(callbacks);
            callbackState = callbackState == null ? (callbacks.isEmpty() ? "legacy" : "ready") : callbackState;
            if (callbackState.equals("enabled") && !callbacks.isEmpty()) callbackState = "ready";
            cpu = cpu == null ? CpuInvestigation.empty("legacy") : cpu;
        }
        public Diagnostics(String trigger, long bookkeepingNanos, long snapshotNanos, List<Task> tasks, List<String> notes, List<Callback> callbacks) { this(trigger, bookkeepingNanos, snapshotNanos, tasks, notes, callbacks, null, null); }
        public Diagnostics(String trigger,long bookkeepingNanos,long snapshotNanos,List<Task> tasks,List<String> notes,List<Callback> callbacks,String callbackState,CpuInvestigation cpu,TimingCoverage.Snapshot coverage){this(trigger,bookkeepingNanos,snapshotNanos,tasks,notes,callbacks,callbackState,cpu,coverage,null);}
        public Diagnostics(String trigger,long bookkeepingNanos,long snapshotNanos,List<Task> tasks,List<String> notes,List<Callback> callbacks,String callbackState,CpuInvestigation cpu){this(trigger,bookkeepingNanos,snapshotNanos,tasks,notes,callbacks,callbackState,cpu,null);}
        public Diagnostics summary() { return new Diagnostics(trigger, bookkeepingNanos, snapshotNanos, tasks, notes, callbacks, callbackState, cpu.summary(),coverage,background,performance); }
        public Diagnostics(String trigger, long bookkeepingNanos, long snapshotNanos, List<Task> tasks, List<String> notes) { this(trigger, bookkeepingNanos, snapshotNanos, tasks, notes, List.of()); }
        public Diagnostics(String trigger, long bookkeepingNanos, long snapshotNanos) { this(trigger, bookkeepingNanos, snapshotNanos, List.of(), List.of()); }
    }
    public Report(long id, long capturedAt, int ticks, double mspt, boolean admin, boolean truncated, List<Entry> entries, boolean recording, int secondsRemaining, List<Chunk> chunks, double tps) {
        this(id, capturedAt, ticks, mspt, admin, truncated, entries, recording, secondsRemaining, chunks, tps, null);
    }
    public Report(long id, long capturedAt, int ticks, double mspt, boolean admin, boolean truncated, List<Entry> entries, boolean recording, int secondsRemaining, List<Chunk> chunks) {
        this(id, capturedAt, ticks, mspt, admin, truncated, entries, recording, secondsRemaining, chunks, 0);
    }
    public Report(long id, long capturedAt, int ticks, double mspt, boolean admin, boolean truncated, List<Entry> entries, boolean recording, int secondsRemaining) {
        this(id, capturedAt, ticks, mspt, admin, truncated, entries, recording, secondsRemaining, List.of());
    }
    public Report(long id, long capturedAt, int ticks, double mspt, boolean admin, boolean truncated, List<Entry> entries) {
        this(id, capturedAt, ticks, mspt, admin, truncated, entries, false, 0);
    }
    public Report view(boolean admin, List<PublicArea> areas) {
        return view(admin, new BaseAccess(areas, List.of()));
    }
    public Report view(boolean admin, java.util.function.Predicate<Entry> access) {
        List<Entry> allowed = admin ? entries : entries.stream().filter(HistoricalTargets::positioned).filter(access).toList();
        return new Report(id, capturedAt, ticks, admin ? mspt : 0, admin, admin && truncated, allowed, recording, secondsRemaining, aggregate(allowed), admin ? tps : 0, admin && diagnostics != null ? diagnostics.summary() : null);
    }
    public record Chunk(String dimension, int x, int z, long nanos, int targets, Entry hotspot) {
        public double micros(int ticks) { return nanos / 1000.0 / Math.max(1, ticks); }
    }
    public static List<Chunk> aggregate(List<Entry> entries) {
        record Key(String dimension, int x, int z) {}
        final class Total { long nanos; int targets; Entry hotspot; void add(Entry e) { nanos += e.nanos(); targets++; if (hotspot == null || hotspot.nanos() < e.nanos()) hotspot = e; } }
        Map<Key, Total> chunks = new HashMap<>();
        for (Entry e : entries) {
            Key key = new Key(e.dimension(), e.x() >> 4, e.z() >> 4);
            chunks.computeIfAbsent(key, k -> new Total()).add(e);
        }
        return chunks.entrySet().stream().map(e -> new Chunk(e.getKey().dimension(), e.getKey().x(), e.getKey().z(), e.getValue().nanos, e.getValue().targets, e.getValue().hotspot)).sorted(Comparator.comparingLong(Chunk::nanos).reversed()
            .thenComparing(Chunk::dimension).thenComparingInt(Chunk::x).thenComparingInt(Chunk::z)).toList();
    }
    public record Entry(String kind, String type, String dimension, int x, int y, int z,
                        long nanos, long calls, UUID entityId,Boolean locationKnown) {
        public Entry(String kind,String type,String dimension,int x,int y,int z,long nanos,long calls,UUID entityId){this(kind,type,dimension,x,y,z,nanos,calls,entityId,null);}
        public Entry(String kind,String type,String dimension,int x,int y,int z,long nanos,long calls){this(kind,type,dimension,x,y,z,nanos,calls,null);}
        public double micros(int ticks) { return nanos / 1000.0 / Math.max(1, ticks); }
    }
}
