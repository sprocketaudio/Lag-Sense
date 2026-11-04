package net.sprocketgames.lagsense;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

public final class Profiler {
    public static final Gson JSON = new GsonBuilder().registerTypeAdapter(Report.Entry.class,new EntryJson()).create();
    private static final CaptureBudget budget=new CaptureBudget(Runtime.getRuntime().maxMemory());
    private static boolean finishing, insideCycle,stopRequested;
    private static final TimingCoverage coverage = new TimingCoverage();
    private static final CapturePerformance.Counter performance=new CapturePerformance.Counter();
    private static long captureEnded;private static String captureReason;
    private static Mutable hottest;
    private static final Map<Level,Map<UUID,Long2ObjectOpenHashMap<Mutable>>> entityMeasurements=new IdentityHashMap<>();
    // Primitive location keys and UUIDs avoid retaining ticking objects or allocating per-tick entries.
    private static final Map<Level, Map<String, Map<Object, Long2ObjectOpenHashMap<Mutable>>>> scheduled = new IdentityHashMap<>();
    private static final List<Mutable> allTargets = new ArrayList<>();
    private static long deadline, tickStart, tickNanos, started, bookkeepingNanos, snapshotNanos;
    private static int ticks;
    private static UUID initiator;
    private static boolean truncated;
    private static String trigger;
    private static Thread serverThread;
    private static JsonStore store;
    public static ArchiveService archives;
    public static ProfilerUi players;
    public static ServerSettings settings = ServerSettings.defaults();
    public static DiscordDelivery delivery;
    public static Path configPath;
    public static final SpikeDetector detector = new SpikeDetector();
    public static final ProfileSchedule schedule = new ProfileSchedule();
    public static boolean active;
    public static Report latest;
    private static final RequestGate requests = new RequestGate(500_000_000);
    public static Path directory;
    public static final AreaService areas = new AreaService();
    public static final BackupMonitor backups = new BackupMonitor();
    private static ModTaskSampler sampler;
    private static ModTaskSampler.Session sampling;
    private static CallbackTimings callbacks;
    private static String callbackError;
    private static boolean captureDetailed;
    public static InvestigationService investigations;
    public static ReportDelivery reportDelivery;
    private static final Set<String> captureBackups = new LinkedHashSet<>();
    private static boolean deferredTimer;
    private static boolean captureGrace;
    private static final class Mutable {
        final String kind, type;
        Level level;
        String dimension;
        int x, y, z;
        long nanos, calls;
        UUID entityId;
        Mutable(String kind, String type) { this.kind = kind; this.type = type; }
        void add(Level level, BlockPos pos, long elapsed) {
            if (this.level != level) { this.level = level; dimension = level.dimension().identifier().toString(); }
            x = pos.getX(); y = pos.getY(); z = pos.getZ(); nanos += Math.max(0, elapsed); calls++;
        }
        Report.Entry entry() { return new Report.Entry(kind, type, dimension, x, y, z, nanos, calls,entityId); }
    }
    public static void load(MinecraftServer server) {
        reset(); serverThread = Thread.currentThread();
        players=new ProfilerUi(server);
        directory = server.getWorldPath(LevelResource.ROOT).resolve("lagsense");
        try {
            Path path = directory.resolve("latest.json");
            if (Files.exists(path) && Files.size(path) <= CaptureBudget.MAX_FILE_BYTES) {
                try(var reader=Files.newBufferedReader(path)){Report report=JSON.fromJson(reader,Report.class);ProfileArchive.validate(report);latest=report;}
            }
        } catch (IOException | RuntimeException e) { LagSense.LOG.warn("Could not read latest LagSense profile", e); }
        areas.load(directory);
        configPath = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("lagsense-server.toml");
        try { settings = ServerSettings.load(configPath); }
        catch (IOException | RuntimeException error) { settings = ServerSettings.defaults(); LagSense.LOG.warn("LagSense server config rejected; automation and external delivery remain disabled. {}", safeConfigError(error)); }
        detector.configure(settings.spikes(), System.nanoTime()); schedule.configure(settings.timer(), System.nanoTime());
        store = new JsonStore(directory, JSON, e -> LagSense.LOG.error("LagSense data save failed", e));
        archives = new ArchiveService(server, directory, settings.history());
        delivery = new DiscordDelivery(settings.discord(), message -> LagSense.LOG.warn("LagSense Discord: {}", message));
        sampler = new ModTaskSampler(); investigations = new InvestigationService(server);reportDelivery=new ReportDelivery(server); refreshBackups();
    }
    public static void reset() {
        active = false;finishing=false;stopRequested=false;insideCycle=false;coverage.reset();
        if(reportDelivery!=null){reportDelivery.close();reportDelivery=null;}
        if (investigations != null) { investigations.close(); investigations = null; }
        if (callbacks != null) { callbacks.close(); callbacks = null; } callbackError = null;
        if (sampler != null) { sampler.close(); sampler = null; } sampling = null;
        captureBackups.clear(); backups.clear(); deferredTimer = false;
        if (delivery != null) { delivery.close(); delivery = null; }
        if(players!=null){players.close();players=null;}
        if (archives != null) { archives.close(); archives = null; }
        if (store != null) { store.close(); store = null; }
        latest = null; clearTargets(); areas.clear(); requests.clear(); ServerSettingsForms.clear(); initiator = null; serverThread = null;
        detector.configure(SpikeDetector.Settings.defaults(), System.nanoTime());
        schedule.configure(ProfileSchedule.Settings.defaults(), System.nanoTime());
        settings = ServerSettings.defaults();
    }
    public static void save(String name, Object data) {
        Object snapshot = data instanceof List<?> list ? List.copyOf(list) : data;
        if (store != null) store.save(name, snapshot);
    }
    public static String spikeStatus() {
        var s = detector.settings();
        if (!s.enabled()) return "Spike capture: Off";
        var lines = new ArrayList<String>(); lines.add("Spike capture: On");
        if (s.minimumTps() > 0) lines.add("TPS: Below " + StatusText.number(s.minimumTps()) + " for " + s.sustainSeconds() + "s");
        if (s.thresholdMs() > 0) lines.add("Tick work: " + StatusText.number(s.thresholdMs()) + "ms for " + s.consecutive() + " ticks");
        lines.add("Capture: " + s.captureSeconds() + "s"); lines.add("Cooldown: " + StatusText.duration(s.cooldownSeconds()));
        return String.join("\n", lines);
    }
    public static String reload() {
        ServerSettings next;
        try { next = ServerSettings.load(configPath); }
        catch (IOException | RuntimeException error) { return "Reload failed: " + safeConfigError(error) + "\nPrevious settings kept."; }
        apply(next);
        return "Configuration reloaded.";
    }
    private static void apply(ServerSettings next) {
        long now = System.nanoTime();
        if (!next.timer().equals(settings.timer())) { schedule.configure(next.timer(), now); deferredTimer = false; }
        if (!next.spikes().equals(settings.spikes())) detector.configure(next.spikes(), now);
        if (!next.history().equals(settings.history()) && archives != null) archives.configure(next.history());
        if (delivery != null) delivery.configure(next.discord());
        settings = next;
    }
    public static void editCaptures(CaptureSettings value) throws IOException {
        Map<String, Object> edits = new LinkedHashMap<>();
        edits.put("capture.timer.enabled", value.timer().enabled()); edits.put("capture.timer.interval_minutes", value.timer().intervalMinutes()); edits.put("capture.timer.duration_seconds", value.timer().captureSeconds());
        var s = value.spikes(); edits.put("capture.spikes.enabled", s.enabled()); edits.put("capture.spikes.tps_alert_threshold", s.minimumTps()); edits.put("capture.spikes.sustain_seconds", s.sustainSeconds());
        edits.put("capture.spikes.tick_work_alert_ms", s.thresholdMs()); edits.put("capture.spikes.consecutive_ticks", s.consecutive()); edits.put("capture.spikes.duration_seconds", s.captureSeconds()); edits.put("capture.spikes.cooldown_seconds", s.cooldownSeconds());
        apply(ServerSettings.update(configPath, settings, edits));
    }
    public static void setTimer(boolean enabled, int minutes, int seconds) throws IOException { editCaptures(new CaptureSettings(new ProfileSchedule.Settings(enabled, minutes, seconds), settings.spikes())); }
    public static void setSpikes(boolean enabled, Double workThreshold, Integer cooldown) throws IOException {
        var s = settings.spikes();
        editCaptures(new CaptureSettings(settings.timer(), new SpikeDetector.Settings(enabled, workThreshold == null ? s.thresholdMs() : workThreshold, s.consecutive(), s.captureSeconds(),
            cooldown == null ? s.cooldownSeconds() : cooldown, workThreshold == null ? s.minimumTps() : 0, s.sustainSeconds())));
    }
    public static void editHistory(ProfileArchive.Settings value) throws IOException {
        apply(ServerSettings.update(configPath, settings, Map.of("history.enabled", value.enabled(), "history.max_profiles", value.maxProfiles(), "history.max_mib", value.maxMiB())));
    }
    public static void editServerSettings(String section,ServerSettingsForms.Edit edit) throws IOException {
        apply(ServerSettings.update(configPath,settings,ServerSettingsForms.changes(section,settings,edit)));
    }
    public static void openSettings(ServerPlayer player, String message) {
        if (!LagSense.isAdmin(player.createCommandSourceStack())) return;
        if (NetworkRegistry.hasChannel(player.connection, CaptureSettingsPayload.TYPE.id())) PacketDistributor.sendToPlayer(player, new CaptureSettingsPayload(JSON.toJson(new CaptureSettings(settings.timer(), settings.spikes())), message));
        else CommandOutput.tell(player, scheduleStatus() + "\n" + spikeStatus());
    }
    private static String safeConfigError(Exception error) {
        return error instanceof IllegalArgumentException ? error.getMessage() : "Could not read the configuration file. Check file permissions and disk space.";
    }
    public static String scheduleStatus() {
        var s = schedule.settings();
        return "Timer: " + StatusText.timer(s) + (s.enabled() ? "\nNext run: " + (deferredTimer ? "After backup" : "In " + StatusText.duration(schedule.remainingSeconds(System.nanoTime()))) : "");
    }
    public static void start(ServerPlayer player, int seconds, String reason) {
        clearTargets(); budget.reset();finishing=false;stopRequested=false;ticks = 0; tickNanos = 0; bookkeepingNanos = 0; snapshotNanos = 0; truncated = false;
        initiator = player == null ? null : player.getUUID(); trigger = reason;
        serverThread = Thread.currentThread(); started = System.nanoTime(); tickStart = started;
        deadline = started + seconds * 1_000_000_000L; active = true;performance.start(started);captureEnded=started;captureReason=reason.equals("Spike follow-up")?detector.reason():"";
        coverage.reset();if(insideCycle)coverage.beginCycle(started);
        if (player != null && LagSense.isAdmin(player.createCommandSourceStack()) && hasClient(player))
            sendPacket(player, new Report(deadline, System.currentTimeMillis(), 0, 0, true, false, List.of(), true, seconds), false);
        refreshBackups(); captureBackups.clear(); if (!settings.backups().mode().equals("ignore")) captureBackups.addAll(backups.active());
        captureGrace = !settings.backups().mode().equals("ignore") && backups.active().isEmpty() && backups.blocked(new BackupMonitor.Settings("defer_automatic", settings.backups().graceSeconds()), started);
        var limits=settings.limits();var effective=limits.effective();
        sampling = settings.modTasks() && sampler != null ? sampler.begin(serverThread,limits,effective) : null;
        callbackError = null; captureDetailed = settings.detailedCallbacks();
        if (settings.detailedCallbacks()) try { callbacks = new CallbackTimings(net.neoforged.neoforge.common.NeoForge.EVENT_BUS, serverThread, new ModOwnership()::owner,limits.events(effective)); }
        catch (ReflectiveOperationException | RuntimeException error) { callbackError = "Event handlers could not be timed on this event bus."; LagSense.LOG.warn("LagSense callback timing adapter unavailable: {}", error.getClass().getSimpleName()); }
    }
    public static boolean stopEarly(){if(!active||finishing||stopRequested||deadline<=System.nanoTime())return false;stopRequested=true;return true;}
    public static boolean busy(){return active||finishing;}
    public static void pre() {
        insideCycle=true;
        if (active || finishing || detector.settings().enabled()) tickStart = System.nanoTime();
        if(active||finishing)coverage.beginCycle(tickStart);
    }
    public static boolean beginTarget(long now){return active&&Thread.currentThread()==serverThread&&coverage.beginTarget(now);}
    public static boolean beginEvent(long now){return active&&Thread.currentThread()==serverThread&&coverage.beginEvent(now);}
    public static void endTarget(boolean measured,long now){if(measured)coverage.endTarget(now);}
    public static void endEvent(boolean measured,long now){if(measured)coverage.endEvent(now);}
    public static void post(MinecraftServer server) {
        long ended=active||finishing||detector.settings().enabled()?System.nanoTime():0;
        if(active||finishing)coverage.endCycle(ended);insideCycle=false;
        if(players!=null)players.tick();
        areas.tick(server);if(investigations!=null)investigations.tick();if(reportDelivery!=null)reportDelivery.tick();
        if (!active && !finishing && !detector.settings().enabled() && !schedule.settings().enabled()) return;
        refreshBackups();
        if (active && !settings.backups().mode().equals("ignore")) captureBackups.addAll(backups.active());
        boolean backupBlocked = backups.blocked(settings.backups(), System.nanoTime());
        boolean monitoring = !finishing && (settings.monitorEmptyServer() || !server.getPlayerList().getPlayers().isEmpty());
        boolean spike = false;
        if (detector.settings().enabled() && monitoring && !backupBlocked) spike = detector.observe(Math.max(0,ended-tickStart), ended, active);
        else detector.resetWindow();
        // Spike trigger wins if a timer deadline falls on the same tick; its delivery stays a spike result.
        if (!active && !finishing && spike) { start(null, detector.settings().captureSeconds(), "Spike follow-up"); LagSense.LOG.info("LagSense spike threshold reached; starting follow-up capture: {}", detector.reason()); return; }
        if (!schedule.settings().enabled()) deferredTimer = false;
        if (schedule.settings().enabled() && schedule.due(System.nanoTime(), active||finishing)) {
            if (backupBlocked) { deferredTimer = true; return; }
            deferredTimer = false;
            start(null, schedule.settings().captureSeconds(), "Scheduled");
            LagSense.LOG.info("LagSense scheduled capture started");
            return;
        }
        if (deferredTimer && !active && !finishing && !backupBlocked) { deferredTimer = false; schedule.configure(schedule.settings(), System.nanoTime()); start(null, schedule.settings().captureSeconds(), "Scheduled"); return; }
        if (!active && !finishing) return;
                tickNanos += Math.max(0,ended-tickStart); ticks++;captureEnded=ended;performance.tick(ended,Math.max(0,ended-tickStart));
        if (finishing || stopRequested || System.nanoTime() >= deadline) {
            latest = snapshot(); active = false;finishing=false;stopRequested=false;
            if (callbacks != null) { callbacks.close(); callbacks = null; }
            detector.captureFinished(System.nanoTime()); save("latest.json", latest);
            if (archives != null) archives.capture(latest);
            UUID captureInitiator = initiator;
            var captureSampler = sampler;var captureDelivery=delivery;String capturedReason=captureReason;
            if (sampler != null && sampling != null) sampler.finish(sampling, latest, enriched -> server.execute(() -> {
                if (sampler != captureSampler) return;
                if (archives != null) archives.enrich(enriched);
                if(captureDelivery!=null&&delivery==captureDelivery)captureDelivery.capture(enriched,capturedReason);
                if (latest == null || latest.id() != enriched.id()) return;
                latest = enriched; save("latest.json", enriched);
                var viewer = captureInitiator == null ? null : server.getPlayerList().getPlayer(captureInitiator);
                if (!active && viewer != null && LagSense.isAdmin(viewer.createCommandSourceStack())) sendPacket(viewer, enriched, false);
            }));
            else if(captureDelivery!=null)captureDelivery.capture(latest,capturedReason);
            sampling = null;
            ServerPlayer player = initiator == null ? null : server.getPlayerList().getPlayer(initiator);
            if (player != null && LagSense.isAdmin(player.createCommandSourceStack())) send(player, true, true);
            else {
                LagSense.LOG.info("LagSense profile saved: {} targets, {} ticks, {} MSPT", latest.entries().size(), ticks, latest.mspt());
                for (ServerPlayer op : server.getPlayerList().getPlayers()) if (LagSense.isAdmin(op.createCommandSourceStack())) {
                    CommandOutput.ready(op);
                }
            }
            clearTargets();
        } else if (ticks % 20 == 0 && initiator != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(initiator);
            if (player != null && LagSense.isAdmin(player.createCommandSourceStack()) && hasClient(player)) {
                sendPacket(player, new Report(deadline,System.currentTimeMillis(),ticks,tickNanos/1_000_000.0/Math.max(1,ticks),true,false,hottest==null?List.of():List.of(hottest.entry()),true,(int)Math.max(0,(deadline-System.nanoTime())/1_000_000_000L)), false);
            }
        }
    }
    private static Report snapshot() {
        long assemblyStart = System.nanoTime();
        List<Report.Entry> entries = allTargets.stream().map(Mutable::entry)
            .sorted(Comparator.comparingLong(Report.Entry::nanos).reversed().thenComparing(Report.Entry::dimension)
                .thenComparingInt(Report.Entry::x).thenComparingInt(Report.Entry::y).thenComparingInt(Report.Entry::z).thenComparing(Report.Entry::type)).toList();
        snapshotNanos += System.nanoTime() - assemblyStart;
        int remaining = (int)Math.max(0, Math.ceil((deadline - System.nanoTime()) / 1_000_000_000.0));
        double tps = performance.finish(captureEnded,captureReason).averageTps();
        var notes = new ArrayList<String>();
        if (sampling != null) notes.add("CPU sample analysis pending.");
        if(truncated)notes.add("Capture stopped: target memory budget reached ("+budget.maximum()/1024/1024+" MiB). Recording ended partway through the final tick.");
        if (!captureBackups.isEmpty()) notes.add("Backup ran during profile: " + String.join(", ", captureBackups));
        if (captureGrace) notes.add("Backup finished shortly before this profile.");
        if (callbackError != null) notes.add(callbackError);
        if (callbacks != null && callbacks.incomplete()) notes.add(callbacks.limitation());
        return new Report(deadline, System.currentTimeMillis(), ticks, tickNanos / 1_000_000.0 / Math.max(1, ticks), true, truncated, entries,
            !finishing && !stopRequested && remaining > 0, finishing||stopRequested?0:remaining, List.of(), tps, new Report.Diagnostics(trigger, bookkeepingNanos, snapshotNanos, List.of(), notes, callbacks == null ? List.of() : callbacks.snapshot(), callbackError != null ? "unavailable" : !captureDetailed ? "disabled" : "enabled", CpuInvestigation.empty(sampling == null ? "disabled" : "pending"),coverage.snapshot(),BackgroundCpu.empty(sampling==null?"disabled":"pending"),performance.finish(captureEnded,captureReason)));
    }
    public static void refreshBackups() { backups.update(net.sprocketgames.lagsense.compat.BackupCompatibility.active(), System.nanoTime()); }
    public static String backupStatus() { refreshBackups(); return "Backups: " + backups.status(settings.backups(), System.nanoTime()); }
    public static String captureStatus() {
        if (active) return "Recording (" + StatusText.duration((deadline - System.nanoTime() + 999_999_999L) / 1_000_000_000L) + " left)";
        return latest == null ? "No results" : "Last run " + StatusText.duration((System.currentTimeMillis() - latest.capturedAt()) / 1000) + " ago";
    }
    public static void record(Entity entity, long nanos) {
        if((!active&&!finishing)||Thread.currentThread()!=serverThread)return;
        record(entity,entity.level(),entity.blockPosition(),nanos);
    }
    public static void record(Entity entity,Level level,BlockPos position,long nanos) {
        if ((!active && !finishing) || Thread.currentThread() != serverThread || level.isClientSide()) return;
        long before = System.nanoTime();
        var positions=entityMeasurements.computeIfAbsent(level,k->new HashMap<>()).computeIfAbsent(entity.getUUID(),k->new Long2ObjectOpenHashMap<>());
        long chunk=(long)(position.getX()>>4)<<32|((position.getZ()>>4)&0xffffffffL);
        Mutable m = positions.get(chunk);
        if (m == null) {
            String type=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            reserve(type,level,true);
            m = new Mutable("Entity",type);m.entityId=entity.getUUID();positions.put(chunk,m);allTargets.add(m);
        }
        m.add(level, position, nanos);
        if(hottest==null||m.nanos>hottest.nanos)hottest=m;
        bookkeepingNanos += System.nanoTime() - before;
    }
    public static void recordTicker(Level level,BlockPos pos,String registry,long nanos){
        if((!active&&!finishing)||Thread.currentThread()!=serverThread||level.isClientSide())return;
        recordLocated(level,pos,registry,"Block",registry,nanos);
    }
    public static void record(BlockEntity block, Level level, long nanos) {
        if(!active&&!finishing)return;
        recordLocated(level,block.getBlockPos(),block.getType(),"Block",nanos);
    }
    private static void reserve(String type,Level level,boolean entity) {
        if(finishing)return;
        if(budget.add(type,level.dimension().identifier().toString(),entity))return;
        // Retain this already-timed call too; later hooks stop before doing any timing work.
        truncated=true;active=false;finishing=true;LagSense.LOG.warn("LagSense capture stopped at its {} MiB target memory budget; final tick is incomplete",budget.maximum()/1024/1024);
    }
    private static void clearTargets() { entityMeasurements.clear();scheduled.clear(); allTargets.clear();hottest=null; }
    public static void recordScheduled(Level level, BlockPos pos, Object type, boolean fluid, long nanos) {
        recordLocated(level, pos, type, fluid ? "Scheduled fluid" : "Scheduled block", nanos);
    }
    public static void recordLocated(Level level, BlockPos pos, Object type, String kind, long nanos) {
        if ((!active && !finishing) || Thread.currentThread() != serverThread || level.isClientSide()) return;
        recordLocated(level,pos,type,kind,null,nanos);
    }
    private static void recordLocated(Level level,BlockPos pos,Object type,String kind,String registry,long nanos){
        long before=System.nanoTime();
        var byKind = scheduled.computeIfAbsent(level, k -> new HashMap<>());
        var byType = byKind.computeIfAbsent(kind, k -> new HashMap<>());
        var byPosition = byType == null ? null : byType.get(type);
        Mutable m = byPosition == null ? null : byPosition.get(pos.asLong());
        if (m == null) {
            String id = registry!=null?registry:type instanceof Fluid fluid ? BuiltInRegistries.FLUID.getKey(fluid).toString() : type instanceof net.minecraft.world.level.block.entity.BlockEntityType<?> block ? BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(block).toString() : BuiltInRegistries.BLOCK.getKey((Block)type).toString();
            reserve(id,level,false);
            if (byPosition == null) { byPosition = new Long2ObjectOpenHashMap<>(); byType.put(type, byPosition); }
            m = new Mutable(kind, id);
            byPosition.put(pos.asLong(), m); allTargets.add(m);
        }
        m.add(level, pos, nanos);
        if(hottest==null||m.nanos>hottest.nanos)hottest=m;
        bookkeepingNanos += System.nanoTime() - before;
    }
    public static void send(ServerPlayer player, boolean global, boolean open) {
        if (latest == null) { CommandOutput.error(player, "No profile available yet."); return; }
        if(hasClient(player)&&reportDelivery!=null){reportDelivery.send(player,latest,global,open,false);return;}
        boolean admin = global && LagSense.isAdmin(player.createCommandSourceStack());
        Report report = admin ? latest.view(true, e -> true)
            : latest.view(false, net.sprocketgames.lagsense.compat.ClaimCompatibility.forPlayer(player, areas.areas()));
        if (!hasClient(player)) {
            CommandOutput.tell(player, "Targets: " + report.entries().size());
            for (Report.Entry e : report.entries().stream().limit(5).toList()) CommandOutput.tell(player, String.format(Locale.ROOT,
                "%s: %.1f µs/t\nPosition: %d, %d, %d (%s)", e.type(), e.micros(report.ticks()), e.x(), e.y(), e.z(), e.dimension()));
            return;
        }
        sendPacket(player, report, open);
    }
    public static void request(ServerPlayer player, boolean global) {
        if (!requests.allow(player.getUUID(), System.nanoTime())) { CommandOutput.error(player, "Please wait before requesting another report."); return; }
        send(player, global, true);
    }
    public static void forget(UUID player) { requests.forget(player); ServerSettingsForms.forget(player);if(reportDelivery!=null)reportDelivery.forget(player); }
    public static boolean hasClient(ServerPlayer player) { return player.connection!=null&&player.connection.getConnection().channel()!=null&&NetworkRegistry.hasChannel(player.connection, ReportPayload.TYPE.id()); }
    private static void sendPacket(ServerPlayer player, Report report, boolean open) {
        if(hasClient(player)&&reportDelivery!=null)reportDelivery.send(player,report,report.admin(),open,false);
    }
}
