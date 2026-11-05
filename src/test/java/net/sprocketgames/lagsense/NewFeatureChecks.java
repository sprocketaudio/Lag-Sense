package net.sprocketgames.lagsense;

import java.util.*;

final class NewFeatureChecks {
    private static int csvColumns(String line){boolean quoted=false;int fields=1;for(char c:line.toCharArray()){if(c=='"')quoted=!quoted;else if(c==','&&!quoted)fields++;}return fields;}
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void run(com.google.gson.Gson gson) {
        var gate = new RequestGate(500); var player = UUID.randomUUID();
        require(gate.allow(player, 0) && !gate.allow(player, 100) && !gate.allow(player, 499) && gate.allow(player, 500), "Rejected requests do not extend the report cooldown");
        require(gate.allow(UUID.randomUUID(), 501), "Report request budgets are per-player");
        gate.forget(player); require(gate.allow(player, 501), "Logout clears the request budget");
        java.util.function.Function<String, String> owner = type -> type.startsWith("net.minecraft.") ? "minecraft" : type.startsWith("net.neoforged.") ? "platform" : type.startsWith("example.mod.") ? "examplemod" : "unattributed";
        var frames = List.of(new CpuHotPaths.Frame("net.minecraft.SomeWorld", "heavyWork"), new CpuHotPaths.Frame("example.mod.TickHandler", "onTick"), new CpuHotPaths.Frame("net.neoforged.bus.EventBus", "post"));
        var path = CpuHotPaths.choose(frames, owner);
        require(path.owner().equals("examplemod") && path.method().equals("Event callback · example.mod.TickHandler#onTick"), "Mod callback owns its vanilla work, with explicit event label");
        frames = List.of(new CpuHotPaths.Frame("net.sprocketgames.lagsense.Profiler", "snapshot"), new CpuHotPaths.Frame("net.minecraft.SomeWorld", "tick"));
        require(CpuHotPaths.choose(frames, owner) == null, "Profiler overhead is excluded rather than blamed on its caller");
        frames = List.of(new CpuHotPaths.Frame("net.minecraft.SomeWorld", "wrapOperation$lagsense$block"), new CpuHotPaths.Frame("net.minecraft.SomeWorld", "tick"));
        require(CpuHotPaths.choose(frames, owner).method().equals("net.minecraft.SomeWorld#tick"), "Injected wrapper frame does not claim ownership");
        frames = List.of(new CpuHotPaths.Frame("unknown.Code", "work"), new CpuHotPaths.Frame("net.minecraft.SomeWorld", "tick"));
        require(CpuHotPaths.choose(frames, owner).owner().equals("unattributed"), "Unknown library/mod work is not falsely labelled as vanilla");
        frames=List.of(new CpuHotPaths.Frame("some.unknown.Library","insert"),new CpuHotPaths.Frame("example.mod.Network","tick"),new CpuHotPaths.Frame("net.minecraft.World","tick"));
        require(CpuHotPaths.choose(frames,owner).owner().equals("examplemod"),"A known mod caller takes ownership over an unresolved library frame");
        require(ModOwnership.resolvableClass("mod.a.Network$$Lambda/0x123").equals("mod.a.Network")&&ModOwnership.resolvableClass("mod.a.Network$$Lambda$42").equals("mod.a.Network"),"Hidden lambda ownership resolves its actual defining class without initializing it");
        var c = new FrameProfile.Capture(); c.frame(0); c.frame(-5);
        for (int i = 0; i < 99; i++) c.frame(10_000_000);
        c.frame(100_000_000); c.stage("World submission", 1_000_000_000); c.gpu(1_000_000); c.gpu(3_000_000); c.gpu(0);
        var f = c.finish();
        require(f.frames() == 100 && Math.abs(f.seconds() - 1.09) < .0001, "Frame intervals accumulate elapsed time, ignoring invalid intervals");
        require(f.medianMs() == 10 && f.p95Ms() == 10 && f.p99Ms() == 10 && f.worstMs() == 100 && f.stutters() == 1, "Frame percentiles retain spikes");
        require(f.onePercentLow() == 10 && Math.abs(f.fps() - 100 / 1.09) < .0001, "1% low uses mean of slowest frames, average FPS uses total elapsed time");
        require(f.stages().getFirst().msPerFrame() == 10 && f.graphMs().contains(100.0), "CPU stages normalized by frame count; graph retains worst frame");
        require(f.gpu().samples() == 2 && f.gpu().meanMs() == 2 && f.gpu().p95Ms() == 3 && f.gpu().worstMs() == 3, "GPU sample statistics use only completed positive query results");
        require(gson.fromJson(gson.toJson(f), FrameProfile.class).equals(f) && f.csv().contains("one_percent_low,10.0,fps"), "Frame export round trip");
        c = new FrameProfile.Capture(); for (int i = 0; i < 60010; i++) c.frame(1_000_000);
        f = c.finish(); require(f.frames() == 60000 && f.limited() && f.graphMs().size() <= 120, "Frame history and graph are bounded");
        f = new FrameProfile.Capture().finish(); require(f.frames() == 0 && f.fps() == 0 && f.onePercentLow() == 0, "Empty frame capture is finite");

        var backup = new BackupMonitor(); var defer = new BackupMonitor.Settings("defer_automatic", 60);
        require(ServerSettings.defaults().backups().equals(defer) && ServerSettings.defaults().modTasks(), "Backup deferral and capture-only CPU sampling defaults");
        require(!ServerSettings.defaults().detailedCallbacks(), "Detailed callback timings require explicit opt-in");
        var configured = ServerSettings.parse("[profiling]\nmod_tasks=false\n[backups]\nmode=\"warn\"\ngrace_seconds=5", Map.of());
        require(!configured.modTasks() && configured.backups().mode().equals("warn") && configured.backups().graceSeconds() == 5, "Backup and CPU config settings load independently");
        require(!backup.blocked(defer, 0), "Absent backup mod never blocks captures");
        backup.update(Set.of("FTB Backups 3"), 1);
        require(backup.blocked(defer, 2), "Active backup defers automatic capture");
        require(!backup.blocked(new BackupMonitor.Settings("warn", 60), 2) && !backup.blocked(new BackupMonitor.Settings("ignore", 60), 2), "Warn and ignore do not suppress automatic captures");
        backup.update(Set.of(), 10); backup.update(Set.of(), 1_000_000_010L);
        require(backup.blocked(defer, 59_000_000_010L) && !backup.blocked(defer, 60_000_000_010L), "Grace starts once at backup end and expires exactly once");
        for(long second=1;second<=180;second++){
            long now=10+second*1_000_000_000L;backup.update(Set.of(),now);
            require(backup.blocked(defer,now)==(second<60)&&backup.remainingSeconds(defer,now)==Math.max(0,60-second),"Repeated idle polls cannot restart the backup grace timer");
        }
        backup.update(Set.of("FTB Backups 3"), 181_000_000_010L); require(backup.blocked(defer, 181_000_000_011L), "Repeated backups extend active suppression");
        backup.clear(); require(!backup.blocked(defer, 0), "Server shutdown clears backup state");

        var entry = new Report.Entry("Random block", "minecraft:wheat", "minecraft:overworld", -1, 64, -1, 100_000, 2);
        var diagnostics = new Report.Diagnostics("Manual", 0, 0, List.of(new Report.Task("secretmod", "private.Handler#tick", 50)), List.of("Backup overlapped capture: FTB Backups 3"), List.of(new Report.Callback("secretmod", "private.Event", "private.Handler#tick", 1_000_000, 5)));
        var report = new Report(1, 1, 10, 1, true, false, List.of(entry), false, 0, List.of(), 20, diagnostics);
        ProfileArchive.validate(report);
        require(!gson.toJson(report.view(false, e -> true)).contains("secretmod") && report.view(false, e -> true).diagnostics() == null, "CPU task/backup metadata stays admin-only even in public chunks");
        require(report.view(false, e -> true).chunks().getFirst().x() == -1, "Random ticks use floor chunk coordinates in filtered heatmap totals");
        require(ReportExport.csv(report).contains("CPU sample") && ReportExport.csv(report).contains("private.Handler#tick"), "CPU sample exports include separate sample units");
        require(gson.fromJson(gson.toJson(report), Report.class).diagnostics().callbacks().equals(diagnostics.callbacks()), "Callback diagnostics survive JSON round trip");
        String csv = ReportExport.csv(report);
        for (String row : csv.split("\\r\\n")) require(csvColumns(row)==34, "Timing/sample/callback rows align with the export header");
        require(csv.contains("private.Event") && csv.contains("100.000000"), "Callback exports carry event, count and elapsed cost per tick");
        String longName = "x".repeat(500);
        var longTarget = new Report.Entry(longName, longName, longName, 0, 64, 0, 1000, 1);
        var longDiagnostics = new Report.Diagnostics("Manual", 0, 0, Collections.nCopies(128, new Report.Task(longName, longName, 1)), List.of(), Collections.nCopies(128, new Report.Callback(longName, longName, longName, 1, 1)));
        var large = new Report(1, 1, 10, 1, true, false, Collections.nCopies(128, longTarget), false, 0, List.of(), 20, longDiagnostics);
        String wire = ReportWire.json(large, gson); var bounded = gson.fromJson(wire, Report.class);
        require(wire.length() <= 200000 && bounded.truncated() && !bounded.entries().isEmpty(), "Oversized callback metadata cannot overflow the report packet; results are explicitly limited");
        var discord = ServerSettings.defaults().discord();
        require(DiscordMessage.build(report, discord, discord.timer(), gson).json().contains("Backup overlapped capture"), "Discord summary retains backup overlap warning without sending anything");
    }
}
