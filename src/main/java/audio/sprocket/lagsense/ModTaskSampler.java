package audio.sprocket.lagsense;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import jdk.jfr.*;
import jdk.jfr.consumer.*;
import net.neoforged.fml.ModList;

/** Capture-only CPU sampling. Samples are never presented as exact wall-clock timings. */
public final class ModTaskSampler implements AutoCloseable {
    public static final class Session {
        final long thread;
        final String threadName;
        final ProfilingLimits limits;final ProfilingLimits.Effective effective;
        final Instant start = Instant.now();
        Instant end,jfrStarted;
        volatile Recording recording;
        volatile boolean cancelled;
        String error;
        Session(Thread thread,ProfilingLimits limits,ProfilingLimits.Effective effective) { this.thread = thread.threadId(); threadName = thread.getName();this.limits=limits;this.effective=effective; }
    }
    private record Analysis(List<Report.Task> tasks,CpuInvestigation cpu,boolean retainedLimit,boolean collectionLimit,BackgroundCpu background,boolean recordingLimited,String limitation) {
        Analysis(List<Report.Task> tasks,CpuInvestigation cpu){this(tasks,cpu,false,false,BackgroundCpu.empty("unavailable"),false,"");}
    }
    private record ModJar(String filename, String ids) {}
    private final List<ModJar> jars;
    private final Map<String, String> owners = new HashMap<>(); // worker-owned
    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor worker;
    private volatile boolean closed;
    public ModTaskSampler() {
        jars = ModList.get().getModFiles().stream().map(file -> new ModJar(file.getFile().getFilePath().getFileName().toString().toLowerCase(Locale.ROOT),
            String.join("/", file.getMods().stream().map(info -> info.getModId()).toList()))).toList();
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4), r -> Thread.ofPlatform().daemon().name("LagSense CPU sample reader").unstarted(r));
    }
    public Session begin(Thread serverThread) {var limits=ProfilingLimits.defaults();return begin(serverThread,limits,limits.effective());}
    public Session begin(Thread serverThread,ProfilingLimits limits,ProfilingLimits.Effective effective) {
        var session = new Session(serverThread,limits,effective); sessions.add(session);
        try { worker.execute(() -> {
            try {
                if (closed || session.cancelled) return;
                var recording = new Recording(); session.recording = recording;
                recording.setName("LagSense mod tasks"); recording.setMaxAge(Duration.ofSeconds(125)); recording.setMaxSize(session.effective.jfr());
                recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10)); recording.enable("jdk.DataLoss");recording.start();session.jfrStarted=recording.getStartTime();
                if (closed || session.cancelled) closeRecording(session);
            } catch (RuntimeException error) { session.error = "CPU sampling unavailable on this JVM."; }
        }); } catch (RejectedExecutionException error) { session.error = "The CPU sampler was busy, so this capture has no CPU samples."; }
        return session;
    }
    public void finish(Session session, Report report, Consumer<Report> complete) {
        if (session == null) return;
        session.end = Instant.now();
        try { worker.execute(() -> {
            Path file = null;
            Analysis analysis = new Analysis(List.of(), CpuInvestigation.empty("unavailable")); String note = session.error;
            try {
                if (session.recording != null) {
                    session.recording.stop();
                    file = Files.createTempFile("lagsense-cpu-", ".jfr"); session.recording.dump(file);
                    if (Files.size(file) > session.effective.jfr()+32L*1024*1024) throw new IllegalStateException("CPU samples exceeded read limit");
                    analysis = read(file, session);
                    note = "CPU samples show Java code seen running on each thread. Short calls and time spent waiting may be missed.";
                    if(analysis.recordingLimited())note+=" JFR rolling storage ceiling: "+session.effective.jfr()/(1<<20)+" MiB ("+session.limits.mode()+").";
                }
            } catch (Exception error) { note = "CPU sample analysis unavailable for this capture."; LagSense.LOG.warn("LagSense CPU sample analysis failed: {}", error.getClass().getSimpleName()); }
            finally {
                closeRecording(session);
                if (file != null) try { Files.deleteIfExists(file); } catch (java.io.IOException ignored) { }
            }
            if (!closed && !session.cancelled) complete.accept(enrich(report, analysis, note));
        }); } catch (RejectedExecutionException error) {
            session.cancelled = true; closeRecording(session);
            if (!closed) complete.accept(enrich(report, new Analysis(List.of(), CpuInvestigation.empty("unavailable")), "The CPU analysis queue was full, so these samples could not be processed."));
        }
    }
    private Analysis read(Path file, Session session) throws java.io.IOException {
        var available=session.limits.effective();
        var effective=new ProfilingLimits.Effective(Math.min(available.events(),session.effective.events()),Math.min(available.cpu(),session.effective.cpu()),session.effective.jfr(),session.effective.depth());
        var samples=new CpuSamples(session.thread,session.threadName,session.limits.cpu(effective),session.limits.cpu(effective),effective.depth());
        boolean recordingLimited=recordingWindowLimited(file,session.jfrStarted);
        try(var recording=new RecordingFile(file)){
            while(recording.hasMoreEvents()){
                if(closed||session.cancelled||Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
                var event=recording.readEvent();
                if(event.getEventType().getName().equals("jdk.DataLoss")){recordingLimited=true;continue;}
                if(!event.getEventType().getName().equals("jdk.ExecutionSample")||event.getStartTime().isBefore(session.start)||event.getStartTime().isAfter(session.end))continue;
                var thread=event.getThread("sampledThread");if(thread==null||event.getStackTrace()==null)continue;
                String name=thread.getJavaName();if(name==null)name="Java thread";
                if(name.startsWith("LagSense ")||name.equals("Render thread"))continue;
                var stack=new ArrayList<CpuInvestigation.Frame>();
                for(var frame:event.getStackTrace().getFrames()){
                    var method=frame.getMethod();var type=method==null?null:method.getType();String typeName=type==null?"unknown.Frame":type.getName();
                    String methodName=method==null?frame.isJavaFrame()?"<unknown>":"<native>":method.getName();
                    stack.add(new CpuInvestigation.Frame(owner(typeName),typeName,methodName,method==null?"":method.getDescriptor()));
                }
                samples.add(thread.getJavaThreadId(),name,stack,event.getStackTrace().isTruncated());
            }
        }
        var result=samples.finish(recordingLimited);
        return new Analysis(result.server(),result.cpu(),result.serverRetentionLimited(),result.serverCollectionLimited(),result.background(),recordingLimited,result.serverReason());
    }
    // JFR chunks start with a fixed big-endian header. Detect a rolled-away prefix rather than
    // letting a bounded recording silently look like the entire requested capture window.
    static boolean recordingWindowLimited(Path file,Instant start)throws java.io.IOException{
        if(start==null)return true;
        byte[] header;try(var in=Files.newInputStream(file)){header=in.readNBytes(40);}
        if(header.length<40||header[0]!='F'||header[1]!='L'||header[2]!='R')throw new java.io.IOException("Unsupported JFR header");
        long firstChunk=java.nio.ByteBuffer.wrap(header).getLong(32);
        long started=start.getEpochSecond()*1_000_000_000L+start.getNano();
        return firstChunk-started>250_000_000L;
    }
    private String owner(String name) {
        if (name.startsWith("net.minecraft.")) return "minecraft";
        if (name.startsWith("java.") || name.startsWith("jdk.") || name.startsWith("sun.") || name.startsWith("net.neoforged.") || name.startsWith("com.mojang.")) return "platform";
        if (owners.size() >= 8192) owners.clear();
        return owners.computeIfAbsent(name, type -> {
            try {
                var clazz = Class.forName(ModOwnership.resolvableClass(type), false, LagSense.class.getClassLoader()); var code = clazz.getProtectionDomain().getCodeSource();
                String location = code == null ? "" : code.getLocation().toString().toLowerCase(Locale.ROOT);
                for (ModJar jar : jars) if (!jar.filename().isBlank() && location.contains(jar.filename())) return jar.ids();
            } catch (LinkageError | RuntimeException | ClassNotFoundException ignored) { }
            return "unattributed";
        });
    }
    private Report enrich(Report report, Analysis analysis, String note) {
        var old = report.diagnostics(); List<String> notes = new ArrayList<>(old.notes()); notes.remove("CPU sample analysis pending."); if (note != null) notes.add(note);

        if(analysis.recordingLimited())notes.add("CPU results limited: some of the sampling period is missing because recording data was lost or reached its storage limit.");
        if(!analysis.limitation().isBlank())notes.add("CPU results limited: "+analysis.limitation());
        var diagnostics = new Report.Diagnostics(old.trigger(), old.bookkeepingNanos(), old.snapshotNanos(), analysis.tasks(), notes, old.callbacks(), old.callbackState(), analysis.cpu(),old.coverage(),analysis.background(),old.performance());
        return new Report(report.id(), report.capturedAt(), report.ticks(), report.mspt(), report.admin(), report.truncated(), report.entries(), report.recording(), report.secondsRemaining(), report.chunks(), report.tps(), diagnostics);
    }
    private void closeRecording(Session session) {
        var recording = session.recording;
        if (recording != null) try { recording.close(); } catch (RuntimeException ignored) { }
        sessions.remove(session);
    }
    @Override public void close() {
        closed = true; worker.shutdownNow();
        for (var session : sessions) { session.cancelled = true; closeRecording(session); }
        sessions.clear();
    }
}
