package audio.sprocket.lagsense;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Request-driven encoding, paced reply segments and fresh authorization before transmission. */
public final class InvestigationService implements AutoCloseable {
    private final MinecraftServer server;
    private final RequestGate gate = new RequestGate(500_000_000);
    private final Set<UUID> pending = new HashSet<>();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4), r -> Thread.ofPlatform().daemon().name("LagSense investigation encoder").unstarted(r));
    private record Sending(ServerPlayer player,InvestigationRequest request,byte[] bytes,int offset){}
    private final Map<UUID,Sending> sending=new LinkedHashMap<>();
    private boolean closed;
    public void tick(){
        if(sending.isEmpty())return;
        int parts=0;var iterator=sending.entrySet().iterator();
        while(iterator.hasNext()&&parts<8){
            var item=iterator.next();var value=item.getValue();var player=server.getPlayerList().getPlayer(item.getKey());
            if(closed||player!=value.player()||!admin(player)){iterator.remove();pending.remove(item.getKey());continue;}
            int offset=value.offset();
            while(offset<value.bytes().length&&parts++<8){int end=Math.min(value.bytes().length,offset+FullReportPart.PART_BYTES);
                PacketDistributor.sendToPlayer(player,new InvestigationPart(new FullReportPart(value.request().token(),value.bytes().length,offset,Arrays.copyOfRange(value.bytes(),offset,end),false,value.request().archived())));offset=end;}
            if(offset==value.bytes().length){iterator.remove();pending.remove(item.getKey());}else item.setValue(new Sending(player,value.request(),value.bytes(),offset));
        }
    }
    public InvestigationService(MinecraftServer server) { this.server = server; }
    public void forget(UUID id) { gate.forget(id);pending.remove(id);sending.remove(id); }
    public void request(ServerPlayer player, InvestigationRequest request) { request(player,request,"",0); }
    public void request(ServerPlayer player, InvestigationRequest request,String category,int offset) {
        UUID id = player.getUUID();
        if (closed || !NetworkRegistry.hasChannel(player.connection, InvestigationPayload.TYPE.id())) return;
        if (!gate.allow(id, System.nanoTime())) return;
        if (pending.contains(id)) { sendError(id, request, "Please wait before opening more details."); return; }
        if (request.cpu() && !admin(player)) { error(id, request, "Operator access required."); return; }
        if(pending.size()>=4){sendError(id,request,"Details are busy. Try again shortly.");return;}
        pending.add(id);
        if(!admin(player)&&(Profiler.players==null||Profiler.players.view(player,request.report())==null)){error(id,request,"Profile access changed. Open it again.");return;}
        if (request.archived()&&admin(player)) {
            if (Profiler.archives == null) { error(id, request, "Saved profile unavailable."); return; }
            Profiler.archives.readInvestigation(request.report(), (report, failure) -> server.execute(() -> {
                if (failure != null) { error(id, request, "Saved profile unavailable."); return; }
                prepare(id, request, report,category,offset);
            }));
        } else prepare(id, request, admin(player)?Profiler.latest:Profiler.players.view(player,request.report()),category,offset);
    }
    private static boolean admin(ServerPlayer p) { return LagSense.isAdmin(p.createCommandSourceStack()); }
    private InvestigationWire.Data data(ServerPlayer player, InvestigationRequest request, Report report,String category,int offset) {
        if (request.cpu()) return new InvestigationWire.Data(report.diagnostics() == null ? CpuInvestigation.empty("legacy") : report.diagnostics().cpu(), null, "");
        var access = audio.sprocket.lagsense.compat.ClaimCompatibility.forPlayer(player, Profiler.areas.areas());
        return new InvestigationWire.Data(null, ChunkInvestigation.create(report, request.dimension(), request.x(), request.z(), request.global() && admin(player), access,category,offset), "");
    }
    private void prepare(UUID id, InvestigationRequest request, Report report,String category,int offset) {
        var player = server.getPlayerList().getPlayer(id);
        if (closed || player == null) { pending.remove(id); return; }
        if (request.cpu() && !admin(player)) { error(id, request, "Operator access required."); return; }
        if (report == null || report.id() != request.report()) { error(id, request, "Profile expired. Open the latest results."); return; }
        final InvestigationWire.Data data;
        try { data=data(player,request,report,category,offset); }
        catch(IllegalArgumentException e){error(id,request,"Contributors changed. Reopen this chunk.");return;}
        try { worker.execute(() -> {
            try {
                var bytes = InvestigationWire.encode(data);
                server.execute(() -> {
                    var current = server.getPlayerList().getPlayer(id);
                    if (closed || current != player) return;
                    if (request.cpu() && !admin(current)) { error(id, request, "Operator access required."); return; }
                    if(!admin(current)&&(Profiler.players==null||Profiler.players.view(current,request.report())==null)){error(id,request,"Profile access changed. Open it again.");return;}
                    // A claim/public-area edit or op revocation must not release an older, broader view.
                    try {
                        if (!request.cpu() && !data.equals(data(current, request, report,category,offset))) { error(id, request, "Access changed. Open the results again."); return; }
                    } catch (IllegalArgumentException e) { error(id,request,"Access changed. Open the results again."); return; }
                    if(request.cpu()&&NetworkRegistry.hasChannel(current.connection,InvestigationPart.TYPE.id()))sending.put(id,new Sending(current,request,bytes,0));
                    else if(bytes.length<=512*1024){pending.remove(id);PacketDistributor.sendToPlayer(current, new InvestigationPayload(request.token(), bytes));}
                    else error(id,request,"Update LagSense on the client to receive this complete call tree.");
                });
            } catch (Exception failure) { server.execute(() -> error(id, request, "Complete details could not be transferred. The transfer limit is 16 MiB compressed and 128 MiB expanded.")); }
        }); } catch (RejectedExecutionException failure) { error(id, request, "Details are busy. Try again shortly."); }
    }
    private void error(UUID id, InvestigationRequest request, String message) {
        pending.remove(id); sendError(id, request, message);
    }
    private void sendError(UUID id, InvestigationRequest request, String message) {
        var p = server.getPlayerList().getPlayer(id);
        if (closed || p == null || !NetworkRegistry.hasChannel(p.connection, InvestigationPayload.TYPE.id())) return;
        try { PacketDistributor.sendToPlayer(p, new InvestigationPayload(request.token(), InvestigationWire.encode(new InvestigationWire.Data(null, null, message)))); }
        catch (java.io.IOException ignored) { }
    }
    @Override public void close() { closed = true; worker.shutdownNow(); pending.clear();sending.clear(); gate.clear(); }
}
