package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** One paced request in flight, bounded queue, off-thread decoding and no navigation replacement. */
public final class Investigations {
    private static final ThreadPoolExecutor READER = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),
        r->Thread.ofPlatform().daemon().name("LagSense investigation reader").unstarted(r));
    private record Pending(long token, Screen owner, CustomPacketPayload packet, Consumer<InvestigationWire.Data> ready, Consumer<String> error) {}
    private static final Deque<Pending> queue = new ArrayDeque<>();
    private static Pending active;
    private static final ReportAssembly assembly=new ReportAssembly();
    private static boolean decoding;
    private static long sequence, lastSent, deadline, cachedId;
    private static boolean cachedArchive;
    private static CpuInvestigation cached;
    public static void clear() { queue.clear();active=null;assembly.clear();decoding=false;cached=null;lastSent=0;sequence++; }
    public static void tick() {
        if(active==null&&queue.isEmpty())return;
        long now=System.nanoTime();
        if(active!=null&&now>=deadline){var p=active;active=null;assembly.clear();p.error().accept("No reply. Reopen this branch to retry.");}
        if(active!=null||queue.isEmpty()||now-lastSent<650_000_000L)return;
        var p=queue.removeFirst();var mc=Minecraft.getInstance();
        if(mc.screen!=p.owner()){p.error().accept("Open this branch again to load it.");return;}
        if(mc.getConnection()==null||!NetworkRegistry.hasChannel(mc.getConnection(),p.packet().type().id())) {
            p.error().accept(p.packet() instanceof ChunkBatchRequest?"Update LagSense on the server to load remaining contributors.":"Update LagSense on the server to open details.");return;
        }
        active=p;assembly.clear();decoding=false;lastSent=now;deadline=now+15_000_000_000L;ClientPacketDistributor.sendToServer(p.packet());
    }
    private static void enqueue(Screen owner,CustomPacketPayload packet,long token,Consumer<InvestigationWire.Data> ready,Consumer<String> error) {
        if(queue.size()>=16){error.accept("Contributors are busy. Reopen this branch shortly.");return;}
        queue.addLast(new Pending(token,owner,packet,ready,error));
    }
    public static void chunkInline(Screen owner,Report report,boolean archived,String dim,int x,int z,Consumer<ChunkInvestigation> ready,Consumer<String> error) {
        chunkBatch(owner,report,archived,dim,x,z,"",0,false,ready,error);
    }
    public static void chunkBatch(Screen owner,Report report,boolean archived,String dim,int x,int z,String category,int offset,
                                  boolean extension,Consumer<ChunkInvestigation> ready,Consumer<String> error) {
        long token=++sequence;var context=new InvestigationRequest(token,report.id(),archived,false,report.admin(),dim,x,z);
        enqueue(owner,extension?new ChunkBatchRequest(context,category,offset):context,token,
            data->{if(data.chunk()!=null)ready.accept(data.chunk());else error.accept("Contributors unavailable.");},error);
    }
    public static void cpu(Screen owner,Report report,boolean archived,Consumer<CpuInvestigation> ready,Consumer<String> error) {
        if(!report.admin()){error.accept("Operator access required.");return;}
        if(cached!=null&&cachedId==report.id()&&cachedArchive==archived&&!cached.state().equals("pending")){ready.accept(cached);return;}
        long token=++sequence;
        enqueue(owner,new InvestigationRequest(token,report.id(),archived,true,true,"",0,0),token,data->{
            if(data.cpu()==null){error.accept("CPU activity unavailable.");return;}
            cached=data.cpu();cachedId=report.id();cachedArchive=archived;ready.accept(cached);
        },error);
    }
    public static void cpu(Screen owner,Report report,boolean archived,Consumer<CpuInvestigation> ready) {cpu(owner,report,archived,ready,error->{});}
    public static void chunk(Screen parent,Report report,boolean archived,String dim,int x,int z) {
        Minecraft.getInstance().setScreen(new ChunkScreen(parent,report,archived,dim,x,z));
    }
    public static void accept(InvestigationPart packet){
        var p=active;if(p==null||p.token()!=packet.part().transfer()||decoding)return;
        try{var complete=assembly.accept(packet.part());deadline=System.nanoTime()+30_000_000_000L;if(complete!=null)accept(new InvestigationPayload(p.token(),complete.bytes()));}
        catch(IllegalArgumentException error){active=null;assembly.clear();p.error().accept("Call-path transfer was interrupted. Reopen it to retry.");}
    }
    public static void accept(InvestigationPayload packet) {
        var p=active;if(p==null||p.token()!=packet.token()||decoding)return;
        decoding=true;deadline=System.nanoTime()+60_000_000_000L;
        try{READER.execute(()->{
            InvestigationWire.Data data;
            try{data=InvestigationWire.decode(packet.bytes());}catch(Exception e){data=new InvestigationWire.Data(null,null,"Could not read investigation.");}
            var result=data;
            Minecraft.getInstance().execute(()->{
                if(active!=p)return;active=null;
                if(result.error()!=null&&!result.error().isBlank())p.error().accept(result.error());else p.ready().accept(result);
            });
        });}catch(RejectedExecutionException e){active=null;p.error().accept("Details are busy. Reopen this branch shortly.");}
    }
    private Investigations(){}
}
