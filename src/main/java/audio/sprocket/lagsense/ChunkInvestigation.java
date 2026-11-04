package audio.sprocket.lagsense;

import java.util.*;
import java.util.function.Predicate;

/** Built from the full capture after access filtering, not from the 128-row display. */
public record ChunkInvestigation(String dimension, int x, int z, int ticks, long nanos, int targets,
                                 List<Category> categories, List<Hotspot> hotspots, String category, int offset, boolean limited) {
    public ChunkInvestigation(String dimension,int x,int z,int ticks,long nanos,int targets,List<Category> categories,List<Hotspot> hotspots) { this(dimension,x,z,ticks,nanos,targets,categories,hotspots,"",0,false); }
    public ChunkInvestigation { category = category == null ? "" : category; }
    public int pageTotal() { return category.isEmpty() ? targets : categories.stream().filter(c -> c.kind().equals(category)).mapToInt(Category::targets).sum(); }
    public record Category(String kind, long nanos, long calls, int targets) {}
    public record Hotspot(Report.Entry entry, int index) {}
    public static ChunkInvestigation create(Report report, String dimension, int x, int z, boolean admin, Predicate<Report.Entry> access) {
        return create(report,dimension,x,z,admin,access,"",0);
    }
    public static ChunkInvestigation create(Report report,String dimension,int x,int z,boolean admin,Predicate<Report.Entry> access,String category,int offset) {
        if (offset < 0 || offset > CaptureBudget.MAX_ENTRIES || category == null || category.length() > 512) throw new IllegalArgumentException("Invalid contributor batch");
        Map<String, long[]> categories = new TreeMap<>(); var items = new ArrayList<Hotspot>(); long nanos = 0; int count = 0;
        for (int i = 0; i < report.entries().size(); i++) {
            var e = report.entries().get(i);
            if (!e.dimension().equals(dimension) || e.x() >> 4 != x || e.z() >> 4 != z || !admin && !access.test(e)) continue;
            nanos += e.nanos(); count++;
            var c = categories.computeIfAbsent(e.kind(), k -> new long[3]); c[0] += e.nanos(); c[1] += e.calls(); c[2]++;
            if (category.isEmpty() || category.equals(e.kind())) items.add(new Hotspot(e, admin ? i : -1));
        }
        items.sort(Comparator.comparingLong((Hotspot h) -> h.entry().nanos()).reversed().thenComparing(h -> h.entry().type()).thenComparing(h -> h.entry().kind()).thenComparingInt(h -> h.entry().x()).thenComparingInt(h -> h.entry().y()).thenComparingInt(h -> h.entry().z()));
        if(offset>items.size())throw new IllegalArgumentException("Contributor offset changed");
        return new ChunkInvestigation(dimension, x, z, report.ticks(), nanos, count,
            categories.entrySet().stream().map(e -> new Category(e.getKey(), e.getValue()[0], e.getValue()[1], (int)e.getValue()[2])).toList(), items.stream().skip(offset).limit(128).toList(),category,offset,admin && report.truncated());
    }
}
