package net.sprocketgames.lagsense;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Server-side permission boundary for all history operations, including asynchronous replies. */
public final class ArchiveService implements AutoCloseable {
    private final MinecraftServer server;
    private final ProfileArchive archive;
    private volatile boolean closed;
    private final RequestGate requested = new RequestGate(250_000_000);
    private long comparisonSequence;
    public ArchiveService(MinecraftServer server, Path directory, ProfileArchive.Settings settings) {
        this.server = server;
        archive = new ProfileArchive(directory.resolve("history"), Profiler.JSON, settings, e -> LagSense.LOG.warn("Profile archive operation failed: {}",e.getMessage(), e));
    }
    public void configure(ProfileArchive.Settings settings) { archive.configure(settings, (state, error) -> { if (error != null) LagSense.LOG.warn("History retention update failed", error); }); }
    public void capture(Report report) { archive.capture(report); }
    public void enrich(Report report) { archive.enrich(report); }
    void listForUi(java.util.function.BiConsumer<ProfileArchive.State,Exception> callback){archive.list(callback);}
    void readInvestigation(long id, java.util.function.BiConsumer<Report, Exception> callback) { archive.read(id, callback); }
    public void target(ServerPlayer player,long capture,int index,boolean chunk,Consumer<Report.Entry> ready){
        if(closed||!LagSense.isAdmin(player.createCommandSourceStack())){CommandOutput.error(player,"Operator access required.");return;}
        if(!requested.allow(player.getUUID(),System.nanoTime())){CommandOutput.error(player,"Please wait before requesting history again.");return;}
        archive.read(capture,(report,error)->deliver(player.getUUID(),current->{
            if(current!=player)return;
            var entry=error==null?HistoricalTargets.archived(report,capture,index,chunk):null;
            if(entry==null&&chunk&&error==null&&report!=null&&report.id()==capture&&index>=0){var chunks=HistoricalTargets.chunks(report);if(index<chunks.size())entry=HistoricalTargets.chunk(chunks.get(index),current.getBlockY());}
            if(entry==null){CommandOutput.error(current,"Saved target unavailable.");return;}
            ready.accept(entry);
        }));
    }
    public void forget(UUID player) { requested.forget(player); }
    public void open(ServerPlayer player) { request(player, new ArchiveRequestPayload("list", 0, 0, false, 10, 32)); }
    public void command(CommandSourceStack source, ArchiveRequestPayload request) {
        if (!LagSense.isAdmin(source)) return;
        if (source.getEntity() instanceof ServerPlayer player) { request(player, request); return; }
        var callback = (java.util.function.BiConsumer<ProfileArchive.State, Exception>)(state, error) -> server.execute(() -> {
            if (error != null) { CommandOutput.fail(source, "Could not read history. Check the server log."); LagSense.LOG.warn("History console operation failed", error); return; }
            CommandOutput.send(source, historyStatus(state));
            if (request.action().equals("list")) for (var item : state.profiles().stream().limit(5).toList()) CommandOutput.send(source, summary(item));
        });
        if (request.action().equals("list")) archive.list(callback);
        else if (request.action().equals("configure")) {
            try { Profiler.editHistory(new ProfileArchive.Settings(request.enabled(), request.count(), request.mebibytes())); archive.list(callback); }
            catch (Exception error) { CommandOutput.fail(source, error instanceof IllegalArgumentException ? error.getMessage() : "Could not save history settings."); }
        } else CommandOutput.fail(source, "Unknown history command.");
    }
    public void request(ServerPlayer player, ArchiveRequestPayload request) {
        if (!LagSense.isAdmin(player.createCommandSourceStack())) {
            if(Profiler.players!=null)Profiler.players.history(player,request);
            return;
        }
        if(Profiler.players!=null)Profiler.players.renew(player);
        long now = System.nanoTime();
        if (!requested.allow(player.getUUID(), now)) { CommandOutput.error(player, "Please wait before requesting history again."); return; }
        UUID id = player.getUUID();
        switch (request.action()) {
            case "configure" -> {
                try {
                    Profiler.editHistory(new ProfileArchive.Settings(request.enabled(), request.count(), request.mebibytes()));
                    archive.list((state, error) -> deliver(id, p -> { if (error != null) problem(p, error); else show(p, state, "Settings applied."); }));
                } catch (Exception error) { problem(player, error); }
            }
            case "list" -> archive.list((state, error) -> deliver(id, p -> {
                if (error != null) problem(p, error); else show(p, state, "Saved profiles");
            }));
            case "open" -> archive.read(request.first(), (report, error) -> deliver(id, p -> {
                if (error != null) { problem(p, error); return; }
                if (Profiler.hasClient(p)&&Profiler.reportDelivery!=null) Profiler.reportDelivery.send(p,report,true,true,true);
                else CommandOutput.tell(p, String.format(Locale.ROOT, "Profile: %d\nTargets: %d\nServer work: %.2f MSPT", report.id(), report.entries().size(), report.mspt()));
            }));
            case "compare" -> {
                boolean full=NetworkRegistry.hasChannel(player.connection,ComparisonPart.TYPE.id());long serial=++comparisonSequence;
                archive.compare(request.first(),request.second(),(comparison,error)->{
                    if(error!=null){deliver(id,p->problem(p,error));return;}
                    final byte[] bytes;final String preview;
                    try{bytes=full?ComparisonWire.encode(comparison):null;preview=full?null:ComparisonWire.previewJson(comparison);}
                    catch(Exception failure){deliver(id,p->problem(p,new IllegalArgumentException("Comparison transfer exceeds its safety budget.")));return;}
                    double beforeMspt=comparison.beforeMspt(),afterMspt=comparison.afterMspt();
                    deliver(id,p->{
                        if(p!=player)return;
                        if(full){for(int offset=0;offset<bytes.length;offset+=FullReportPart.PART_BYTES)PacketDistributor.sendToPlayer(p,new ComparisonPart(new FullReportPart(serial,bytes.length,offset,Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+FullReportPart.PART_BYTES)),false,false)));}
                        else if(NetworkRegistry.hasChannel(p.connection,ComparisonPayload.TYPE.id())){CommandOutput.tell(p,"Comparison preview only. Update LagSense for all results and individual details.");PacketDistributor.sendToPlayer(p,new ComparisonPayload(preview));}
                        else CommandOutput.tell(p,String.format(Locale.ROOT,"Server work: %.2f → %.2f MSPT",beforeMspt,afterMspt));
                    });
                });
            }
            default -> CommandOutput.error(player, "Unknown history command.");
        }
    }
    private void deliver(UUID id, Consumer<ServerPlayer> action) { if (!closed) server.execute(() -> reply(id, action)); }
    private void reply(UUID id, Consumer<ServerPlayer> action) {
        var player = server.getPlayerList().getPlayer(id);
        if (!closed && player != null && LagSense.isAdmin(player.createCommandSourceStack())) action.accept(player);
    }
    private void show(ServerPlayer player, ProfileArchive.State state, String message) {
        if (NetworkRegistry.hasChannel(player.connection, ArchivePayload.TYPE.id())) PacketDistributor.sendToPlayer(player, new ArchivePayload(Profiler.JSON.toJson(state), message));
        else {
            CommandOutput.tell(player, historyStatus(state));
            for (var item : state.profiles().stream().limit(5).toList()) CommandOutput.tell(player, summary(item));
        }
    }
    private void problem(ServerPlayer player, Exception error) {
        LagSense.LOG.warn("Profile history request failed", error);
        archive.list((state, ignored) -> deliver(player.getUUID(), p -> show(p, state == null ? new ProfileArchive.State(archive.settings(), List.of()) : state,
            error instanceof IllegalArgumentException ? error.getMessage() : "Saved profile unavailable.")));
    }
    private static String historyStatus(ProfileArchive.State state) {
        return "History: " + StatusText.onOff(state.settings().enabled()) + "\nSaved profiles: " + state.profiles().size() + "\nKeep: " + state.settings().maxProfiles() + " profiles, " + state.settings().maxMiB() + " MiB";
    }
    private static String summary(ProfileArchive.Summary value) {
        String date = java.time.format.DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ROOT).withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(value.capturedAt()));
        return date + ": " + String.format(Locale.ROOT, "%.2f MSPT · %d targets", value.mspt(), value.targets());
    }
    @Override public void close() { closed = true; archive.close(); requested.clear(); }
}
