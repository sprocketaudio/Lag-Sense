package audio.sprocket.lagsense;

import java.util.*;

/** Map attribution uses registry namespaces only; zero-match captured chunks remain known. */
public final class MapTargets {
    private record Key(String dimension,int x,int z) {}
    private final Report report;
    private final Map<Key,List<ChunkInvestigation.Hotspot>> entries=new HashMap<>();
    public final List<Report.Chunk> chunks;
    public MapTargets(Report report,String owner){
        this.report=report;for(int i=0;i<report.entries().size();i++){var e=report.entries().get(i);if(owner==null||ProfileResults.owner(e).equals(owner))entries.computeIfAbsent(new Key(e.dimension(),e.x()>>4,e.z()>>4),k->new ArrayList<>()).add(new ChunkInvestigation.Hotspot(e,report.admin()?i:-1));}
        var original=report.chunks()==null||report.chunks().isEmpty()?Report.aggregate(report.entries()):report.chunks();
        if(owner==null)chunks=original;
        else{var rows=new ArrayList<Report.Chunk>();for(var c:original){var values=entries.getOrDefault(new Key(c.dimension(),c.x(),c.z()),List.of());long nanos=0;Report.Entry hottest=null;for(var h:values){nanos+=h.entry().nanos();if(hottest==null||h.entry().nanos()>hottest.nanos())hottest=h.entry();}rows.add(new Report.Chunk(c.dimension(),c.x(),c.z(),nanos,values.size(),hottest));}rows.sort(Comparator.comparingLong(Report.Chunk::nanos).reversed().thenComparing(Report.Chunk::dimension).thenComparingInt(Report.Chunk::x).thenComparingInt(Report.Chunk::z));chunks=List.copyOf(rows);}
    }
    public ChunkInvestigation chunk(String dimension,int x,int z){
        var values=entries.getOrDefault(new Key(dimension,x,z),List.of());var totals=new TreeMap<String,long[]>();long nanos=0;
        for(var h:values){var e=h.entry();nanos+=e.nanos();var sum=totals.computeIfAbsent(e.kind(),k->new long[3]);sum[0]+=e.nanos();sum[1]+=e.calls();sum[2]++;}
        var sorted=values.stream().sorted(Comparator.comparingLong((ChunkInvestigation.Hotspot h)->h.entry().nanos()).reversed()).toList();
        return new ChunkInvestigation(dimension,x,z,report.ticks(),nanos,values.size(),totals.entrySet().stream().map(e->new ChunkInvestigation.Category(e.getKey(),e.getValue()[0],e.getValue()[1],(int)e.getValue()[2])).toList(),sorted,"",0,report.truncated());
    }
}
