package audio.sprocket.lagsense;

import java.util.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/** Immutable capture data indexed once off-thread; entity samples merge across visited chunks. */
public final class MarkerIndex {
    private static final Comparator<Report.Entry> ORDER=Comparator.comparingLong(Report.Entry::nanos).reversed().thenComparing(Report.Entry::kind).thenComparing(Report.Entry::type).thenComparingInt(Report.Entry::x).thenComparingInt(Report.Entry::y).thenComparingInt(Report.Entry::z);
    private final Map<String,Long2ObjectOpenHashMap<List<Report.Entry>>> stationary=new HashMap<>();
    private final Map<String,Long2ObjectOpenHashMap<List<Report.Entry>>> positions=new HashMap<>();
    private final Map<String,Map<UUID,Report.Entry>> entities=new HashMap<>();
    private final int ticks;
    private final Report report;
    private final Map<String,Long2ObjectOpenHashMap<List<ChunkInvestigation.Hotspot>>> chunks=new HashMap<>();
    private static long key(int x,int z){return (long)x<<32|z&0xffffffffL;}
    public MarkerIndex(Report report) {
        this.report=report;ticks=report.ticks();int i=0;
        for(var e:report.entries()){
            if(Boolean.FALSE.equals(e.locationKnown())){i++;continue;}
            chunks.computeIfAbsent(e.dimension(),k->new Long2ObjectOpenHashMap<>()).computeIfAbsent(key(e.x()>>4,e.z()>>4),k->new ArrayList<>()).add(new ChunkInvestigation.Hotspot(e,report.admin()?i:-1));i++;
            if(e.kind().equals("Entity")) {
            if(e.entityId()!=null)entities.computeIfAbsent(e.dimension(),k->new HashMap<>()).merge(e.entityId(),e,(a,b)->new Report.Entry(a.kind(),a.type(),a.dimension(),a.x(),a.y(),a.z(),a.nanos()+b.nanos(),a.calls()+b.calls(),a.entityId()));
        }else {
            stationary.computeIfAbsent(e.dimension(),k->new Long2ObjectOpenHashMap<>()).computeIfAbsent(key(e.x()>>4,e.z()>>4),k->new ArrayList<>()).add(e);
            positions.computeIfAbsent(e.dimension(),k->new Long2ObjectOpenHashMap<>()).computeIfAbsent(net.minecraft.core.BlockPos.asLong(e.x(),e.y(),e.z()),k->new ArrayList<>()).add(e);
        }
        }
        for(var dimension:positions.values())dimension.replaceAll((position,entries)->{if(entries.size()==1)return List.of(entries.getFirst());entries.sort(ORDER);return List.copyOf(entries);});
    }
    public Report.Entry entity(String dimension,UUID id){var values=entities.get(dimension);return values==null?null:values.get(id);}
    public List<Report.Entry> position(String dimension,int x,int y,int z){var values=positions.get(dimension);return values==null?List.of():values.getOrDefault(net.minecraft.core.BlockPos.asLong(x,y,z),List.of());}
    public ChunkInvestigation chunk(String dimension,int x,int z) {
        var values=chunks.get(dimension);var entries=values==null?List.<ChunkInvestigation.Hotspot>of():values.getOrDefault(key(x,z),List.of());
        var sorted=entries.stream().sorted(Comparator.comparingLong((ChunkInvestigation.Hotspot h)->h.entry().nanos()).reversed().thenComparing(h->h.entry().type()).thenComparingInt(h->h.entry().x()).thenComparingInt(h->h.entry().y()).thenComparingInt(h->h.entry().z())).toList();
        Map<String,long[]> totals=new TreeMap<>();long nanos=0;
        for(var h:entries){var e=h.entry();nanos+=e.nanos();var c=totals.computeIfAbsent(e.kind(),k->new long[3]);c[0]+=e.nanos();c[1]+=e.calls();c[2]++;}
        return new ChunkInvestigation(dimension,x,z,ticks,nanos,entries.size(),totals.entrySet().stream().map(e->new ChunkInvestigation.Category(e.getKey(),e.getValue()[0],e.getValue()[1],(int)e.getValue()[2])).toList(),sorted,"",0,report.admin()&&report.truncated());
    }
    public List<Report.Entry> near(String dimension,double x,double y,double z,int distance,double minimum) {
        var values=stationary.get(dimension);if(values==null)return List.of();
        var result=new ArrayList<Report.Entry>();
        int cx=(int)Math.floor(x)>>4,cz=(int)Math.floor(z)>>4,radius=(distance+15)/16+1;double range=(double)distance*distance;
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            var bucket=values.get(key(cx+dx,cz+dz));if(bucket==null)continue;
            for(var e:bucket){double ex=e.x()+.5-x,ey=e.y()-y,ez=e.z()+.5-z;if(ex*ex+ey*ey+ez*ez>range||e.micros(ticks)<minimum)continue;
                result.add(e);
            }
        }
        result.sort(ORDER);return result;
    }
}
