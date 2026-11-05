package net.sprocketgames.lagsense;

import java.util.*;

/** Groups already filtered retained targets without changing their source indices. */
public final class ChunkResultGroups {
    public record Group(ProfileResults.Chunk chunk,long nanos,long updates,int count,List<ProfileResults.Target> targets) {
        public double micros(int ticks){return nanos/1000.0/Math.max(1,ticks);}
    }
    public static List<Group> groups(List<ProfileResults.Target> targets,boolean updates,boolean counts){
        var buckets=new HashMap<ProfileResults.Chunk,List<ProfileResults.Target>>();
        for(var target:targets){var e=target.entry();if(!HistoricalTargets.positioned(e))continue;buckets.computeIfAbsent(new ProfileResults.Chunk(e.dimension(),e.x()>>4,e.z()>>4),k->new ArrayList<>()).add(target);}
        var result=new ArrayList<Group>();
        for(var pair:buckets.entrySet()){
            long nanos=0,calls=0;var rows=List.copyOf(pair.getValue());for(var target:rows){nanos+=target.entry().nanos();calls+=target.entry().calls();}
            int count=ProfileResults.chunkCounts(rows.stream().map(ProfileResults.Target::entry).toList()).get(pair.getKey());
            result.add(new Group(pair.getKey(),nanos,calls,count,rows));
        }
        Comparator<Group> cost=Comparator.comparingLong(Group::nanos).reversed();
        result.sort((counts?Comparator.comparingInt(Group::count).reversed().thenComparing(cost):updates?Comparator.comparingLong(Group::updates).reversed().thenComparing(cost):cost).thenComparing(g->g.chunk().dimension()).thenComparingInt(g->g.chunk().x()).thenComparingInt(g->g.chunk().z()));
        return List.copyOf(result);
    }
    private ChunkResultGroups(){}
}
