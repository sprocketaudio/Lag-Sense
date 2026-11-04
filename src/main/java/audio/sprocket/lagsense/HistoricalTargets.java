package audio.sprocket.lagsense;

/** Recorded destinations remain useful even when the measured object no longer exists. */
public final class HistoricalTargets {
    public static boolean positioned(Report.Entry e){
        return e!=null&&e.type()!=null&&net.minecraft.resources.Identifier.tryParse(e.type())!=null&&!Boolean.FALSE.equals(e.locationKnown())&&e.dimension()!=null&&net.minecraft.resources.Identifier.tryParse(e.dimension())!=null
            &&e.kind()!=null&&(e.kind().equals("Entity")||e.kind().equals("Block")||e.kind().equals("Chunk")||e.kind().startsWith("Random ")||e.kind().startsWith("Scheduled "))
            &&e.x()>=-30_000_000&&e.x()<=30_000_000&&e.z()>=-30_000_000&&e.z()<=30_000_000;
    }
    public static Report.Entry chunk(Report.Chunk chunk){
        return chunk(chunk,null);
    }
    public static Report.Entry chunk(Report.Chunk chunk,Integer fallbackY){
        if(chunk==null||Math.abs((long)chunk.x()*16+8)>30_000_000||Math.abs((long)chunk.z()*16+8)>30_000_000)return null;
        var e=chunk.hotspot();boolean located=positioned(e);
        if(e!=null&&!located)return null;
        if(!located&&fallbackY==null)return null;
        var target=new Report.Entry("Chunk",located?e.type():"minecraft:chunk",chunk.dimension(),chunk.x()*16+8,located?e.y():fallbackY,chunk.z()*16+8,chunk.nanos(),located?e.calls():0);
        return positioned(target)?target:null;
    }
    public static java.util.List<Report.Chunk> chunks(Report report){
        return report.entries().isEmpty()&&report.chunks()!=null?report.chunks():Report.aggregate(report.entries());
    }
    public static Report.Entry archived(Report report,long id,int index,boolean chunk){
        if(report==null||report.id()!=id||index<0)return null;
        if(chunk){var chunks=chunks(report);return index<chunks.size()?chunk(chunks.get(index)):null;}
        var entry=ComparisonTargets.archived(report,id,index);return positioned(entry)?entry:null;
    }
    public static MarkerPosition marker(Report.Entry recorded,MarkerPosition.Live live,String dimension){
        if(!positioned(recorded))return null;
        if(recorded.kind().equals("Entity")&&recorded.entityId()!=null&&live!=null&&!live.removed())
            return dimension.equals(live.dimension())?new MarkerPosition(live.x()-.5,live.y(),live.z()-.5,Math.max(1.3,live.height()+.3)):null;
        return dimension.equals(recorded.dimension())?new MarkerPosition(recorded.x(),recorded.y(),recorded.z(),1.3):null;
    }
    private HistoricalTargets(){}
}
