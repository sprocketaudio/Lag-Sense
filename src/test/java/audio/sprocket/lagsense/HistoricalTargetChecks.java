package audio.sprocket.lagsense;

import java.util.*;

public final class HistoricalTargetChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void run(){
        for(var kind:List.of("Entity","Block","Random block","Random fluid","Scheduled block","Scheduled fluid")){
            var e=new Report.Entry(kind,"fixture:machine","minecraft:the_nether",-20,64,80,3000,2);
            check(HistoricalTargets.positioned(e),"Older positioned "+kind+" remains actionable without UUIDs");
            var decoded=Profiler.JSON.fromJson(Profiler.JSON.toJson(e),Report.Entry.class);check(decoded.equals(e),"Existing entry measurements/coordinates round-trip without schema changes");
            var position=HistoricalTargets.marker(e,null,e.dimension());check(position.x()==e.x()&&position.z()==e.z(),"Removed historical objects retain recorded marker position");
            check(HistoricalTargets.marker(e,null,"minecraft:overworld")==null,"Recorded fallback never appears in the player's unrelated dimension");
        }
        var e=new Report.Entry("Entity","minecraft:creeper","minecraft:the_nether",8,64,4,1000,10,new UUID(0,1));
        var live=new MarkerPosition.Live("minecraft:overworld",70,80,90,2,false);
        check(HistoricalTargets.marker(e,live,live.dimension()).x()==69.5&&HistoricalTargets.marker(e,live,e.dimension())==null,"Verified moving UUID uses its actual position and dimension");
        check(HistoricalTargets.marker(e,new MarkerPosition.Live(e.dimension(),0,0,0,2,true),e.dimension()).x()==8,"Unavailable or removed UUID falls back to its recorded location");
        var report=new Report(1,100,100,1,true,false,List.of(e));var chunk=HistoricalTargets.archived(report,1,0,true);
        check(chunk!=null&&chunk.kind().equals("Chunk")&&chunk.entityId()==null&&chunk.dimension().equals(e.dimension())&&chunk.x()==8&&chunk.z()==8,"Historical chunk destination stays in the recorded chunk rather than following its hotspot entity");
        check(HistoricalTargets.archived(report,1,1,true)==null&&HistoricalTargets.archived(report,2,0,false)==null,"Invalid archive IDs and chunk indices are rejected");
        var json=Profiler.JSON.toJsonTree(e).getAsJsonObject();json.remove("x");var missing=Profiler.JSON.fromJson(json,Report.Entry.class);
        check(!HistoricalTargets.positioned(missing)&&missing.nanos()==e.nanos()&&missing.entityId().equals(e.entityId())&&missing.dimension().equals(e.dimension()),"Missing coordinates disable actions while preserving measurements and identity");
        check(!HistoricalTargets.positioned(Profiler.JSON.fromJson(Profiler.JSON.toJson(missing),Report.Entry.class)),"Missing-location metadata survives archive and network round-trips");
        for(var bad:List.of(new Report.Entry("Block","fixture:machine","bad dimension",0,64,0,0,0),new Report.Entry("Block","fixture:machine","minecraft:overworld",Integer.MAX_VALUE,64,0,0,0),new Report.Entry("CPU","fixture:tick","minecraft:overworld",0,64,0,0,0)))check(!HistoricalTargets.positioned(bad),"Invalid/non-positional data cannot offer world actions");
        var comparison=ComparisonDetailChecks.fixture();var index=new ComparisonIndex(comparison);
        for(var n:index.all.chunks()){
            var d=index.targets.destination(n);check(d!=null&&d.chunk()&&d.target().kind().equals("Chunk")&&d.target().dimension().equals(n.key().dimension()),"Comparison chunks expose recorded archive destinations");
            for(var category:n.children())check(index.targets.destination(category)==null,"Comparison categories remain non-positional aggregates");
        }
        System.out.println("Historical target checks passed (legacy positions, removed targets, UUID/current-versus-recorded markers, dimensions, chunks, invalid/missing coordinates and unchanged measurements).");
    }
}
