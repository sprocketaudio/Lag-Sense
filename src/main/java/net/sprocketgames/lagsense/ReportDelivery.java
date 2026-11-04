package net.sprocketgames.lagsense;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Bounded asynchronous complete-report delivery; authorization is rechecked before transmission. */
public final class ReportDelivery implements AutoCloseable {
    private record Ticket(long serial,long report,boolean open,boolean archived) {}
    private final MinecraftServer server;
    private final Map<UUID,Ticket> latest=new HashMap<>();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),r->Thread.ofPlatform().daemon().name("LagSense report encoder").unstarted(r));
    private record Sending(ServerPlayer player,Ticket ticket,Report source,Report view,boolean admin,byte[] bytes,int offset){}
    private final Map<UUID,Sending> sending=new LinkedHashMap<>();
    private long sequence;
    public void tick(){
        if(sending.isEmpty())return;
        int parts=0;var iterator=sending.entrySet().iterator();
        while(iterator.hasNext()&&parts<8){
            var item=iterator.next();var value=item.getValue();UUID id=item.getKey();var current=server.getPlayerList().getPlayer(id);
            if(closed||latest.get(id)!=value.ticket()||current!=value.player()||value.admin()&&!LagSense.isAdmin(current.createCommandSourceStack())){iterator.remove();if(latest.get(id)==value.ticket())latest.remove(id);continue;}
            if(!value.admin()&&!permitted(current,value.view())){iterator.remove();latest.remove(id);CommandOutput.error(current,"Access changed. Open the report again.");continue;}
            int offset=value.offset();while(offset<value.bytes().length&&parts++<8){int end=Math.min(value.bytes().length,offset+FullReportPart.PART_BYTES);
                PacketDistributor.sendToPlayer(current,new FullReportPart(value.ticket().serial(),value.bytes().length,offset,Arrays.copyOfRange(value.bytes(),offset,end),value.ticket().open(),value.ticket().archived()));offset=end;}
            if(offset==value.bytes().length){iterator.remove();latest.remove(id);}else item.setValue(new Sending(current,value.ticket(),value.source(),value.view(),value.admin(),value.bytes(),offset));
        }
    }
    private static boolean permitted(ServerPlayer player,Report view){
        if(Profiler.players!=null)return Profiler.players.permitted(player,view);
        var access= net.sprocketgames.lagsense.compat.ClaimCompatibility.forPlayer(player,Profiler.areas.areas());
        for(var entry:view.entries())if(!access.test(entry))return false;
        return true;
    }
    private boolean closed;
    public ReportDelivery(MinecraftServer server){this.server=server;}
    public void forget(UUID player){latest.remove(player);sending.remove(player);}
    public void send(ServerPlayer player,Report source,boolean global,boolean open,boolean archived) {
        if(!latest.containsKey(player.getUUID())&&latest.size()>=4){CommandOutput.error(player,"Report transfer is busy. Try again shortly.");return;}
        boolean admin=global&&LagSense.isAdmin(player.createCommandSourceStack());
        var access= net.sprocketgames.lagsense.compat.ClaimCompatibility.forPlayer(player,Profiler.areas.areas());
        var view=source.view(admin,access);
        if(Profiler.players!=null)Profiler.players.grant(player,view);
        UUID id=player.getUUID();var old=latest.get(id);sending.remove(id);
        var ticket=new Ticket(++sequence,source.id(),open||old!=null&&old.report()==source.id()&&old.archived()==archived&&old.open(),archived);latest.put(id,ticket);
        if(source.recording()||!NetworkRegistry.hasChannel(player.connection,FullReportPart.TYPE.id())) {
            if(!source.recording()&&(view.entries().size()>128||view.chunks().size()>128))CommandOutput.tell(player,"Client preview only. Update LagSense to receive all retained targets and chunks.");
            PacketDistributor.sendToPlayer(player,new ReportPayload(ReportWire.json(view,Profiler.JSON),open,archived));latest.remove(id);return;
        }
        try{worker.execute(()->{
            try{
                byte[] bytes=FullReportWire.encode(view);
                server.execute(()->{
                    if(closed||latest.get(id)!=ticket)return;
                    var current=server.getPlayerList().getPlayer(id);if(current!=player){latest.remove(id);return;}
                    if(admin&&!LagSense.isAdmin(current.createCommandSourceStack())){latest.remove(id);return;}
                    if(!admin&&!permitted(current,view)){latest.remove(id);CommandOutput.error(current,"Access changed. Open the report again.");return;}
                    sending.put(id,new Sending(current,ticket,source,view,admin,bytes,0));
                });
            }catch(Exception e){server.execute(()->{if(!closed&&latest.get(id)==ticket){latest.remove(id);CommandOutput.error(player,"Could not transfer the complete report. Results remain on the server.");LagSense.LOG.warn("Full report delivery failed",e);}});}
        });}catch(RejectedExecutionException e){latest.remove(id);CommandOutput.error(player,"Report transfer is busy. Open the report again shortly.");}
    }
    @Override public void close(){closed=true;latest.clear();sending.clear();worker.shutdownNow();}
}
