package net.sprocketgames.lagsense;

import java.util.*;

public final class ComparisonActionChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void run(){
        var c=ComparisonDetailChecks.fixture();var index=new ComparisonIndex(c);
        for(var roots:List.of(index.all.types(),index.all.chunks()))for(var n:ComparisonDetailChecks.leaves(roots)){
            var d=index.targets.destination(n);var s=ComparisonTargets.recorded(n);
            check(d!=null,"Identified individual contributions have archive-backed actions");
            boolean later=s==n.later();var source=later?c.details().later():c.details().earlier();var original=source.get(d.index());
            check(d.capture()==(later?c.afterId():c.beforeId())&&d.ticks()==(later?c.afterTicks():c.beforeTicks()),"Actions retain their selected source capture and tick denominator");
            check(original==s.location()&&original.type().equals(d.target().type())&&d.target().nanos()==s.nanos()&&d.target().calls()==s.calls(),"Archive index and aggregated measured work come from actual retained data");
            check(ComparisonTargets.title(n).equals(n.name()+" · "+original.x()+", "+original.y()+", "+original.z()+" · "+n.status()),"Leaf title includes actual recorded coordinates and matching status");
        }
        for(var n:index.all.types())check(index.targets.destination(n)==null,"Aggregate rows have no travel destination");
        var moved=ComparisonDetailChecks.leaves(index.all.types()).stream().filter(n->new UUID(0,1).equals(n.uuid())).findFirst().orElseThrow();
        var d=index.targets.destination(moved);check(d.target().dimension().equals("minecraft:the_nether")&&d.target().x()==32&&d.ticks()==200,"Types actions prefer the later actual contribution after chunk/dimension movement");
        var live=new ComparisonTargets.Live("minecraft:overworld",d.target().type(),301,80,-40,false);var current=ComparisonTargets.current(d.target(),live);
        check(current.x()==301&&current.z()==-40&&current.dimension().equals(live.dimension())&&current.nanos()==d.target().nanos()&&current.entityId().equals(d.target().entityId()),"Live entity travel changes location only; captured timing and UUID remain fixed");
        check(ComparisonTargets.current(d.target(),null)==null&&ComparisonTargets.current(d.target(),new ComparisonTargets.Live(live.dimension(),"fixture:other",0,0,0,false))==null&&ComparisonTargets.current(d.target(),new ComparisonTargets.Live(live.dimension(),live.type(),0,0,0,true))==null,"Missing, replaced and removed entities cannot use stale positions");
        var report=new Report(c.afterId(),c.afterAt(),c.afterTicks(),1,true,false,c.details().later());
        check(ComparisonTargets.archived(report,d.capture(),d.index())==report.entries().get(d.index()),"Server resolves the original archive entry");
        check(ComparisonTargets.archived(report,-1,0)==null&&ComparisonTargets.archived(report,report.id(),-1)==null&&ComparisonTargets.archived(report,report.id(),report.entries().size())==null&&ComparisonTargets.archived(null,1,0)==null,"Malformed or unavailable archive destinations are rejected");
        var old=new ProfileComparison(c.beforeId(),c.afterId(),c.beforeAt(),c.afterAt(),c.beforeTicks(),c.afterTicks(),c.beforeMspt(),c.afterMspt(),false,c.types(),c.chunks());
        var legacy=new ComparisonIndex(old);for(var n:legacy.all.types())check(legacy.targets.destination(n)==null,"Aggregate-only archives retain data without inventing actions");
        order(index,index.all.types());order(index,index.all.chunks());order(legacy,legacy.all.types());order(legacy,legacy.all.chunks());
        System.out.println("Comparison action checks passed (recorded locations, archive indices, captured denominators, live entity destinations and unavailable identity).");
    }
    private static void order(ComparisonIndex index,List<ComparisonIndex.Node> nodes){
        for(int i=1;i<nodes.size();i++)check(Math.abs(nodes.get(i-1).delta(index.comparison()))>=Math.abs(nodes.get(i).delta(index.comparison())),"Every comparison level sorts by absolute change");
        for(var n:nodes)order(index,n.children());
    }
}
