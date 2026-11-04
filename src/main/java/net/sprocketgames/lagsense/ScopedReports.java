package net.sprocketgames.lagsense;

import java.util.*;
import java.util.function.Predicate;

/** Scoped history/report metadata and compact evidence for periodic ownership revalidation. */
public final class ScopedReports {
    public record Evidence(List<PublicArea> areas,Set<BaseAccess.ChunkKey> claims){
        public boolean valid(List<PublicArea> current,Predicate<BaseAccess.ChunkKey> owned){return areas.equals(current)&&claims.stream().allMatch(owned);}
    }
    public static Evidence evidence(Report view,List<PublicArea> areas){
        var claims=new HashSet<BaseAccess.ChunkKey>();
        for(var e:view.entries())if(areas.stream().noneMatch(a->a.contains(e)))claims.add(new BaseAccess.ChunkKey(e.dimension(),e.x()>>4,e.z()>>4));
        return new Evidence(List.copyOf(areas),Set.copyOf(claims));
    }
    public static Report.Entry target(Report view,long capture,int index){
        if(view==null||view.id()!=capture||index<0||index>=view.entries().size())return null;
        var entry=view.entries().get(index);return HistoricalTargets.positioned(entry)?entry:null;
    }
    public static ProfileArchive.Summary summary(Report view){return new ProfileArchive.Summary(view.id(),view.capturedAt(),view.ticks(),0,0,view.entries().size(),false,"Saved",0);}
    private ScopedReports(){}
}
