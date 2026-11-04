package audio.sprocket.lagsense;

import java.util.*;

/** Filter retained contributions before grouping; source indices remain valid for travel. */
public final class ProfileResults {
    public record Filter(String tab,String dimension,boolean near,String playerDimension,double x,double y,double z) {
        public boolean includes(Report.Entry e) {
            if(!dimension.equals("All")&&!dimension.equals(e.dimension()))return false;
            if(!matches(tab,e.kind()))return false;
            double dx=e.x()-x,dy=e.y()-y,dz=e.z()-z;
            return !near||e.dimension().equals(playerDimension)&&dx*dx+dy*dy+dz*dz<=128*128;
        }
    }
    public record Target(int index,Report.Entry entry) {}
    public record Type(String kind,String registry) {}
    public record Group(Type type,long nanos,long updates,int instances,List<Target> targets) {
        public double micros(int ticks){return nanos/1000.0/Math.max(1,ticks);}
    }
    public record ModGroup(String owner,long nanos,long updates,int instances,List<Target> targets,List<Group> types){public double micros(int ticks){return nanos/1000.0/Math.max(1,ticks);}}
    public static String owner(Report.Entry entry){int colon=entry.type().indexOf(':');return colon<0?"unattributed":entry.type().substring(0,colon);}
    public static List<ModGroup> mods(List<Target> targets,boolean updates,boolean byType){
        var owners=new HashMap<String,List<Target>>();for(var t:targets)owners.computeIfAbsent(owner(t.entry()),k->new ArrayList<>()).add(t);
        var result=new ArrayList<ModGroup>();for(var entry:owners.entrySet()){var sorted=entry.getValue();sorted.sort(order(updates));long nanos=0,calls=0;var ids=new HashSet<Identity>();for(var t:sorted){nanos+=t.entry().nanos();calls+=t.entry().calls();ids.add(identity(t.entry()));}var rows=List.copyOf(sorted);result.add(new ModGroup(entry.getKey(),nanos,calls,ids.size(),rows,byType?groups(rows,updates):List.of()));}
        Comparator<ModGroup> cost=Comparator.comparingLong(ModGroup::nanos).reversed();result.sort((updates?Comparator.comparingLong(ModGroup::updates).reversed().thenComparing(cost):cost).thenComparing(ModGroup::owner));return List.copyOf(result);
    }
    public record Chunk(String dimension,int x,int z) {}
    private record Identity(String kind,String type,String dimension,int x,int y,int z,UUID uuid) {}
    private static Identity identity(Report.Entry e){
        return e.kind().equals("Entity")&&e.entityId()!=null?new Identity(e.kind(),e.type(),"",0,0,0,e.entityId()):new Identity(e.kind(),e.type(),e.dimension(),e.x(),e.y(),e.z(),null);
    }
    public static boolean matches(String tab,String kind){return tab.equals("All")||tab.equals("Chunks")||tab.equals(kind)||tab.equals("Ticks")&&(kind.startsWith("Scheduled")||kind.startsWith("Random"));}
    public static List<Target> targets(Report report,Filter filter,boolean updates){
        var result=new ArrayList<Target>();
        for(int i=0;i<report.entries().size();i++){var e=report.entries().get(i);if(filter.includes(e))result.add(new Target(i,e));}
        result.sort(order(updates));return List.copyOf(result);
    }
    private static Comparator<Target> order(boolean updates){
        Comparator<Target> cost=Comparator.comparingLong((Target t)->t.entry().nanos()).reversed();
        return (updates?Comparator.comparingLong((Target t)->t.entry().calls()).reversed().thenComparing(cost):cost).thenComparingInt(Target::index);
    }
    public static List<Group> groups(List<Target> targets,boolean updates){
        class Sum {long nanos,calls;final Set<Identity> instances=new HashSet<>();final List<Target> rows=new ArrayList<>();void add(Target t){nanos+=t.entry().nanos();calls+=t.entry().calls();instances.add(identity(t.entry()));rows.add(t);}}
        var sums=new LinkedHashMap<Type,Sum>();
        for(var target:targets){var e=target.entry();sums.computeIfAbsent(new Type(e.kind(),e.type()),k->new Sum()).add(target);}
        var result=new ArrayList<Group>();
        for(var pair:sums.entrySet()){var s=pair.getValue();s.rows.sort(order(updates));result.add(new Group(pair.getKey(),s.nanos,s.calls,s.instances.size(),List.copyOf(s.rows)));}
        Comparator<Group> cost=Comparator.comparingLong(Group::nanos).reversed();
        result.sort((updates?Comparator.comparingLong(Group::updates).reversed().thenComparing(cost):cost).thenComparing(g->g.type().kind()).thenComparing(g->g.type().registry()));
        return List.copyOf(result);
    }
    public static Map<Chunk,Integer> chunkCounts(List<Report.Entry> entries){
        var groups=new HashMap<Chunk,Set<Identity>>();
        for(var e:entries)groups.computeIfAbsent(new Chunk(e.dimension(),e.x()>>4,e.z()>>4),k->new HashSet<>()).add(identity(e));
        var counts=new HashMap<Chunk,Integer>();groups.forEach((k,v)->counts.put(k,v.size()));return Map.copyOf(counts);
    }
    private ProfileResults(){}
}
