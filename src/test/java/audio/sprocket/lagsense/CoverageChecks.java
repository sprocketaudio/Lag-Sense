package audio.sprocket.lagsense;

import java.util.*;
import java.nio.file.*;
import java.util.concurrent.*;

final class CoverageChecks {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void run() throws Exception {
        var entries=new ArrayList<Report.Entry>();var random=new Random(42);
        for(int i=0;i<26000;i++)entries.add(new Report.Entry(i%2==0?"Entity":"Block","fixture:cheap_"+i,new String[]{"minecraft:overworld","minecraft:the_nether","minecraft:the_end"}[i%3],(i/3)*16,64,20000,i%3,1,i%2==0?new UUID(random.nextLong(),random.nextLong()):null));
        var original=new Report(900,1000,100,10,true,false,List.copyOf(entries),false,0,List.of(),15);
        var view=original.view(true,e->false);
        require(view.entries().size()==26000&&view.chunks().size()>128&&!view.truncated(),"Complete server view retains zero/low-cost targets beyond both previous limits and across all dimensions");
        require(view.chunks().stream().mapToLong(Report.Chunk::nanos).sum()==entries.stream().mapToLong(Report.Entry::nanos).sum(),"All measured costs contribute to complete chunk totals");
        byte[] encoded=FullReportWire.encode(view);require(encoded.length>FullReportPart.PART_BYTES,"Large UUID capture exercises multiple bounded segments");
        var assembly=new ReportAssembly();ReportAssembly.Complete completed=null;
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY,net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        try{for(int off=0;off<encoded.length;off+=FullReportPart.PART_BYTES){var part=new FullReportPart(1,encoded.length,off,Arrays.copyOfRange(encoded,off,Math.min(encoded.length,off+FullReportPart.PART_BYTES)),true,false);buffer.clear();FullReportPart.CODEC.encode(buffer,part);completed=assembly.accept(FullReportPart.CODEC.decode(buffer));}}
        finally{buffer.release();}
        require(completed!=null&&FullReportWire.decode(completed.bytes()).equals(view),"Every target, chunk, UUID and timing survives complete segmented transfer");
        var base=original.view(false,e->e.dimension().equals("minecraft:overworld")&&e.x()<10000);
        var decodedBase=FullReportWire.decode(FullReportWire.encode(base));
        require(decodedBase.entries().size()>128&&decodedBase.entries().stream().allMatch(e->e.dimension().equals("minecraft:overworld")&&e.x()<10000)&&decodedBase.diagnostics()==null&&decodedBase.mspt()==0,"Full base views preserve authorization filtering without exposing server-wide diagnostics or other dimensions");
        var partial=new ReportAssembly();require(partial.accept(new FullReportPart(1,2,0,new byte[]{1},false,false))==null,"Partial reports are not published");
        try{partial.accept(new FullReportPart(1,2,0,new byte[]{2},false,false));throw new AssertionError("Repeated segment accepted");}catch(IllegalArgumentException expected){}
        require(partial.accept(new FullReportPart(2,1,0,new byte[]{3},true,true))!=null&&partial.accept(new FullReportPart(1,1,0,new byte[]{4},true,true))==null,"New reports supersede old partial/replayed transfers");
        try{new FullReportPart(3,FullReportPart.MAX_BYTES+1,0,new byte[]{1},false,false);throw new AssertionError("Transfer budget");}catch(IllegalArgumentException expected){}
        var budget=new CaptureBudget(4L*1024*1024*1024);int count=0;while(budget.add("fixture:cheap","minecraft:overworld"))count++;
        require(count>20000&&count<CaptureBudget.MAX_ENTRIES,"Memory guard replaces the fixed 20,000-target cutoff with a bounded capture budget");budget.reset();require(budget.add("fixture:cheap","minecraft:overworld"),"Next capture resets its memory budget");
        var incomplete=new Report(901,1000,1,10,true,true,List.of(),false,0,List.of(),20,new Report.Diagnostics("Manual",0,0,List.of(),List.of("Capture stopped: target memory budget reached. Final tick is partial.")));
        require(ReportCompleteness.label(incomplete).contains("memory budget")&&ReportCompleteness.reason(view).isEmpty(),"Memory stop reason is distinct from complete/legacy results");
        tracking();persistence(original);
        System.out.println("Coverage checks passed (26,000 low/zero-cost targets, three dimensions, complete chunks/private transfers, UUID persistence, live-pose markers and bounded retention).");
    }
    private static void tracking() {
        UUID id=UUID.randomUUID();String dim="minecraft:overworld";
        var a=new Report.Entry("Entity","minecraft:creeper",dim,16000,64,16000,2,1,id);
        var b=new Report.Entry("Entity","minecraft:creeper",dim,16016,64,16000,3,2,id);
        var stationary=new Report.Entry("Block","minecraft:hopper",dim,2,64,3,1,1);
        var report=new Report(2,1000,100,10,true,false,List.of(a,b,stationary)).view(true,e->true);var index=new MarkerIndex(report);
        var captured=index.entity(dim,id);require(captured.nanos()==5&&captured.calls()==3,"One entity marker combines its real captured work across visited chunks");
        require(index.chunk(dim,1000,1000).nanos()==2&&index.chunk(dim,1001,1000).nanos()==3,"Entity tick costs stay in the chunks where they were measured");
        var poses=new HashMap<UUID,MarkerPosition.Live>();poses.put(id,new MarkerPosition.Live(dim,10.5,65,20.5,1.8,false));
        var first=MarkerPosition.resolve(captured,dim,poses::get);poses.put(id,new MarkerPosition.Live(dim,40.5,66,50.5,1.8,false));var moved=MarkerPosition.resolve(captured,dim,poses::get);
        require(first.x()==10&&moved.x()==40&&moved.y()==66&&moved.labelHeight()>1.8&&captured.nanos()==5,"Marker follows current entity pose above its head without changing captured timing");
        poses.clear();require(MarkerPosition.resolve(captured,dim,poses::get)==null,"Unloaded/despawned entities leave no snapshot-position marker");
        poses.put(id,new MarkerPosition.Live("minecraft:the_nether",1,2,3,1,true));require(MarkerPosition.resolve(captured,dim,poses::get)==null,"Removed/dimension-changed entities are hidden");
        require(MarkerPosition.resolve(new Report.Entry("Entity","minecraft:pig",dim,1,2,3,1,1),dim,poses::get)==null,"Legacy entity entries without UUIDs must not pretend to track a live entity");
        require(MarkerPosition.resolve(stationary,dim,key->{throw new AssertionError("Stationary target queried entity cache");}).equals(new MarkerPosition(2,64,3,1.3)),"Stationary markers retain fixed positions with no entity lookups");
        require(index.near(dim,2,64,3,32,0).equals(List.of(stationary))&&index.near(dim,2,64,3,32,1).isEmpty(),"Spatial stationary index respects range and minimum captured timing");
        require(ReportExport.csv(report).contains("entity_uuid")&&ReportExport.csv(report).contains(id.toString()),"CSV exports preserve entity identity");
    }
    private static void persistence(Report report)throws Exception {
        var dir=Files.createTempDirectory("lagsense-full-coverage");var errors=new ArrayList<Exception>();
        try(var archive=new ProfileArchive(dir,Profiler.JSON,new ProfileArchive.Settings(true,2,64),errors::add)) {
            archive.capture(report);var listed=new CompletableFuture<ProfileArchive.State>();archive.list((s,e)->{if(e!=null)listed.completeExceptionally(e);else listed.complete(s);});require(listed.get(15,TimeUnit.SECONDS).profiles().getFirst().targets()==26000,"Archives retain targets beyond the old count limit");
            var read=new CompletableFuture<Report>();archive.read(report.id(),(r,e)->{if(e!=null)read.completeExceptionally(e);else read.complete(r);});require(read.get(15,TimeUnit.SECONDS).equals(report),"Large saved captures retain exact UUIDs and timings");
        }finally{try(var paths=Files.list(dir)){for(var p:paths.toList())Files.delete(p);}Files.delete(dir);}
        require(errors.isEmpty(),"Complete archive has no hidden truncation/failure");
    }
}
