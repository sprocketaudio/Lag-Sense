package audio.sprocket.lagsense;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Bounded local history. All archive reads, serialization, writes and pruning run off the tick thread. */
public final class ProfileArchive implements AutoCloseable {
    public record Settings(boolean enabled, int maxProfiles, int maxMiB) {
        public Settings {
            if (maxProfiles < 1 || maxProfiles > 50) throw new IllegalArgumentException("Keep between 1 and 50 profiles.");
            if (maxMiB < 8 || maxMiB > 256) throw new IllegalArgumentException("Disk limit must be 8–256 MiB.");
        }
        public static Settings defaults() { return new Settings(false, 10, 32); }
    }
    public record Summary(long id, long capturedAt, int ticks, double mspt, double tps, int targets, boolean truncated, String trigger, long bytes) {}
    public record State(Settings settings, List<Summary> profiles) {}
    private static final long MAX_FILE_BYTES = CaptureBudget.MAX_FILE_BYTES;
    private final Path directory;
    private final Gson gson;
    private final Consumer<Exception> failure;
    private final ThreadPoolExecutor worker;
    private final List<Summary> index = new ArrayList<>(); // worker-owned
    private volatile Settings settings;
    @FunctionalInterface private interface Job<T> { T run() throws Exception; }

    public ProfileArchive(Path directory, Gson gson, Settings settings, Consumer<Exception> failure) {
        this.directory = directory; this.gson = gson; this.settings = settings; this.failure = failure;
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16),
            r -> Thread.ofPlatform().daemon().name("LagSense profile archive").unstarted(r), new ThreadPoolExecutor.AbortPolicy());
        submit(() -> { scan(); prune(); return null; }, (ignored, error) -> { if (error != null) failure.accept(error); });
    }
    public Settings settings() { return settings; }
    private Path path(long id) { return directory.resolve("profile-" + Long.toUnsignedString(id) + ".json"); }
    private void scan() throws IOException {
        Files.createDirectories(directory);
        try (var files = Files.newDirectoryStream(directory, "profile-*.json")) {
            for (Path file : files) {
                if (!file.getFileName().toString().matches("profile-[0-9]+\\.json")) continue;
                try {
                    Report report = readFile(file);
                    if (file.getFileName().equals(path(report.id()).getFileName())) index.add(summary(report, Files.size(file)));
                } catch (IOException | RuntimeException error) { failure.accept(new IOException("Skipped unreadable archive " + file.getFileName(), error)); }
            }
        }
        sort();
    }
    private Summary summary(Report report, long bytes) {
        return new Summary(report.id(), report.capturedAt(), report.ticks(), report.mspt(), report.tps(), report.entries().size(), report.truncated(),
            report.diagnostics() == null ? "Saved" : report.diagnostics().trigger(), bytes);
    }
    private void sort() { index.sort(Comparator.comparingLong(Summary::capturedAt).reversed().thenComparingLong(Summary::id)); }
    private void prune() throws IOException {
        sort(); long bytes = index.stream().mapToLong(Summary::bytes).sum();
        while (!index.isEmpty() && (index.size() > settings.maxProfiles() || bytes > settings.maxMiB() * 1024L * 1024L)) {
            Summary oldest = index.getLast();
            Files.deleteIfExists(path(oldest.id())); index.removeLast(); bytes -= oldest.bytes();
        }
    }
    public void configure(Settings value, BiConsumer<State, Exception> callback) {
        // Commit in queue order so captures before/after Apply use the corresponding settings.
        submit(() -> { settings = value; prune(); return state(); }, callback);
    }
    public void capture(Report report) {
        write(report, false);
    }
    public void enrich(Report report) { write(report, true); }
    private void write(Report report, boolean existingOnly) {
        if (!settings.enabled()) return;
        submit(() -> {
            if (!settings.enabled()) return null;
            if (existingOnly && index.stream().noneMatch(s -> s.id() == report.id())) return null;
            validate(report);
            Files.createDirectories(directory);
            Path target = path(report.id()), temp = directory.resolve(target.getFileName() + ".tmp");
            ReportFiles.json(temp,report,gson,Math.min(MAX_FILE_BYTES,settings.maxMiB()*1024L*1024L));
            long bytes=Files.size(temp);
            if(bytes>MAX_FILE_BYTES||bytes>settings.maxMiB()*1024L*1024L){Files.deleteIfExists(temp);throw new IOException("This profile exceeds the history size limit and was saved only to latest.json.");}
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
            index.removeIf(s -> s.id() == report.id()); index.add(summary(report, bytes)); prune(); return null;
        }, (ignored, error) -> { if (error != null) failure.accept(error); });
    }
    public void list(BiConsumer<State, Exception> callback) { submit(this::state, callback); }
    private State state() { return new State(settings, List.copyOf(index)); }
    public void read(long id, BiConsumer<Report, Exception> callback) { submit(() -> readKnown(id), callback); }
    public void compare(long older, long newer, BiConsumer<ProfileComparison, Exception> callback) {
        submit(() -> ProfileComparison.between(readKnown(older), readKnown(newer)), callback);
    }
    private Report readKnown(long id) throws IOException {
        if (index.stream().noneMatch(s -> s.id() == id)) throw new IOException("Saved profile is unavailable or was removed by retention.");
        return readFile(path(id));
    }
    private Report readFile(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_FILE_BYTES) throw new IOException("Invalid archive file.");
        try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){Report report=gson.fromJson(reader,Report.class);validate(report);return report;}
    }
    static void validate(Report report) {
        if (report == null || report.entries() == null || report.entries().size() > CaptureBudget.MAX_ENTRIES || report.ticks() < 1 || report.capturedAt() < 1
            || report.recording() || !Double.isFinite(report.mspt()) || !Double.isFinite(report.tps()) || report.mspt() < 0 || report.tps() < 0
            || report.diagnostics() != null && (report.diagnostics().trigger() == null || report.diagnostics().trigger().length() > 128)) throw new IllegalArgumentException("Invalid completed profile.");
        for (var e : report.entries()) {
            if (e == null || e.kind() == null || e.type() == null || e.dimension() == null || e.kind().length() > 512 || e.type().length() > 512
                || e.dimension().length() > 512 || e.nanos() < 0 || e.calls() < 0 || e.entityId()!=null&&!e.kind().equals("Entity")) throw new IllegalArgumentException("Invalid saved target.");
        }
        if (report.diagnostics() != null) {
            var d = report.diagnostics();
            if(d.performance()!=null&&(!d.performance().valid()||d.performance().measuredTicks()!=report.ticks()))throw new IllegalArgumentException("Invalid capture performance measurements");
            CpuInvestigation.validate(d.cpu());
            BackgroundCpu.validate(d.background());
            if (!Set.of("disabled", "enabled", "ready", "unavailable", "legacy").contains(d.callbackState())) throw new IllegalArgumentException("Invalid callback state");
            if (d.notes().size() > 32) throw new IllegalArgumentException("Invalid saved diagnostics.");
            for (var task : d.tasks()) if (task == null || task.owner() == null || task.method() == null || task.owner().length() > 65536 || task.method().length() > 131072 || task.samples() < 0) throw new IllegalArgumentException("Invalid saved CPU samples.");
            for (var note : d.notes()) if (note == null || note.length() > 4096) throw new IllegalArgumentException("Invalid saved capture note.");
            long eventBytes=0;
            for(var c:d.callbacks()){if(c!=null)eventBytes+=256L+2L*(c.owner()==null?0:c.owner().length())+2L*(c.event()==null?0:c.event().length())+2L*(c.method()==null?0:c.method().length());}
            if(eventBytes>512L*1024*1024)throw new IllegalArgumentException("Saved event data exceeds safety budget");
            for (var c : d.callbacks()) if (c == null || c.owner() == null || c.event() == null || c.method() == null || c.owner().length() > 65536 || c.event().length() > 65536 || c.method().length() > 131072 || c.handlerId()<0 || c.nanos() < 0 || c.calls() < 0 || c.maxNanos() < 0 || c.maxNanos() > c.nanos()) throw new IllegalArgumentException("Invalid saved callbacks.");
        }
    }
    private <T> void submit(Job<T> job, BiConsumer<T, Exception> callback) {
        try { worker.execute(() -> {
            T result;
            try { result = job.run(); }
            catch (Exception error) { callback.accept(null, error); return; }
            callback.accept(result, null);
        }); } catch (RejectedExecutionException error) { callback.accept(null, new IOException("History is unavailable right now. Try again shortly.", error)); }
    }
    @Override public void close() {
        worker.shutdown();
        try { if (!worker.awaitTermination(5, TimeUnit.SECONDS)) failure.accept(new IOException("Archive shutdown timed out; queued saves may still be pending.")); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); failure.accept(error); }
    }
}
