package audio.sprocket.lagsense;

import java.util.*;

/** Archive-backed action sources, without changing comparison identities, timings or matching. */
public final class ComparisonTargets {
    public record Destination(long capture,long capturedAt,int index,Report.Entry target,int ticks,boolean chunk) {
        public Destination(long capture,long capturedAt,int index,Report.Entry target,int ticks){this(capture,capturedAt,index,target,ticks,false);}
    }
    public record Live(String dimension,String type,int x,int y,int z,boolean removed) {}
    private final ProfileComparison comparison;
    private final Map<Report.Entry,Integer> earlier=new IdentityHashMap<>(),later=new IdentityHashMap<>();
    private record ChunkKey(String dimension,int x,int z){}
    private final Map<ChunkKey,Destination> earlierChunks=new HashMap<>(),laterChunks=new HashMap<>();
    public ComparisonTargets(ProfileComparison comparison){
        this.comparison=comparison;if(comparison.details()!=null){index(comparison.details().earlier(),earlier);index(comparison.details().later(),later);chunks(comparison.details().earlier(),earlierChunks,false);chunks(comparison.details().later(),laterChunks,true);}
    }
    private void chunks(List<Report.Entry> entries,Map<ChunkKey,Destination> out,boolean after){
        var chunks=Report.aggregate(entries);for(int i=0;i<chunks.size();i++){var c=chunks.get(i);var target=HistoricalTargets.chunk(c);if(target!=null)out.put(new ChunkKey(c.dimension(),c.x(),c.z()),new Destination(after?comparison.afterId():comparison.beforeId(),after?comparison.afterAt():comparison.beforeAt(),i,target,after?comparison.afterTicks():comparison.beforeTicks(),true));}
    }
    public static boolean actionable(ComparisonIndex.Node node){return individual(node)||node.key().scope().equals("chunk");}
    private static void index(List<Report.Entry> entries,Map<Report.Entry,Integer> indices){for(int i=0;i<entries.size();i++)indices.putIfAbsent(entries.get(i),i);}
    public static boolean individual(ComparisonIndex.Node node){return node.key().scope().equals("target")||node.key().scope().equals("chunkTarget");}
    public static ComparisonIndex.Stats recorded(ComparisonIndex.Node node){return node.later().location()!=null?node.later():node.earlier();}
    public static String title(ComparisonIndex.Node node){
        var stats=recorded(node);var e=stats.location();String title=node.name();
        if(individual(node)&&HistoricalTargets.positioned(e)&&(stats.locations()==1||node.uuid()!=null))title+=" · "+e.x()+", "+e.y()+", "+e.z();
        if(individual(node)&&!node.status().isBlank())title+=" · "+node.status();return title;
    }
    public Destination destination(ComparisonIndex.Node node){
        if(comparison.details()==null)return null;
        if(node.key().scope().equals("chunk")){var key=new ChunkKey(node.key().dimension(),node.key().x(),node.key().z());return (node.later().targets()>0?laterChunks:earlierChunks).get(key);}
        if(!individual(node))return null;
        var stats=recorded(node);var entry=stats.location();if(!HistoricalTargets.positioned(entry)||stats.targets()!=1)return null;
        if(node.uuid()==null&&stats.locations()!=1)return null;
        boolean after=stats==node.later();var source=after?later:earlier;Integer index=source.get(entry);if(index==null)return null;
        // An entity may have contributed in several chunks. Mark its actual selected-side aggregate.
        var measured=new Report.Entry(entry.kind(),entry.type(),entry.dimension(),entry.x(),entry.y(),entry.z(),stats.nanos(),stats.calls(),node.uuid(),entry.locationKnown());
        return new Destination(after?comparison.afterId():comparison.beforeId(),after?comparison.afterAt():comparison.beforeAt(),index,measured,after?comparison.afterTicks():comparison.beforeTicks());
    }
    /** Resolve against the actual archived source; client coordinates are never authoritative for travel. */
    public static Report.Entry archived(Report report,long id,int index){return report==null||report.id()!=id||index<0||index>=report.entries().size()?null:report.entries().get(index);}
    public static Report.Entry current(Report.Entry measured,Live live){
        if(measured==null||!measured.kind().equals("Entity")||measured.entityId()==null||live==null||live.removed()||!measured.type().equals(live.type()))return null;
        return new Report.Entry(measured.kind(),measured.type(),live.dimension(),live.x(),live.y(),live.z(),measured.nanos(),measured.calls(),measured.entityId(),measured.locationKnown());
    }
}
