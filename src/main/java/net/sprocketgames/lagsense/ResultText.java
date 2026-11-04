package net.sprocketgames.lagsense;

import java.util.*;
import java.util.function.Function;

/** Plain-text clipboard trees built from retained measurements, independently of visible widgets. */
public final class ResultText {
    private final Function<String,String> names;
    public ResultText(Function<String,String> names){this.names=names;}
    public static String cost(double micros){return String.format(Locale.ROOT,micros>=1000?"%.3f ms/t":"%.3f µs/t",micros>=1000?micros/1000:micros);}
    private static String share(long samples,long total){return String.format(Locale.ROOT,"%.1f%%",samples*100.0/Math.max(1,total));}
    public static String category(String kind){return switch(kind){case "Entity"->"Entities";case "Block"->"Ticking blocks";default->kind;};}
    public CopyTree.Node target(ProfileResults.Target target,int ticks){var e=target.entry();return new CopyTree.Node(target,()->e.type()+" | "+cost(e.micros(ticks))+" | "+e.calls()+" updates\n"+e.kind()+" | "+e.dimension()+" | "+e.x()+" "+e.y()+" "+e.z()+(e.entityId()==null?"":" | UUID: "+e.entityId()),List::of);}
    public CopyTree.Node type(ProfileResults.Group group,int ticks){return new CopyTree.Node(group.type(),()->group.type().registry()+" | "+cost(group.micros(ticks))+" | "+group.instances()+" measured instances | "+group.updates()+" updates | "+group.type().kind(),()->group.targets().stream().map(t->target(t,ticks)).toList());}
    public CopyTree.Node mod(ProfileResults.ModGroup group,int ticks,boolean types){return new CopyTree.Node(group.owner(),()->names.apply(group.owner())+" | "+cost(group.micros(ticks))+" | "+group.instances()+" measured instances | "+group.updates()+" updates",()->types?group.types().stream().map(g->type(g,ticks)).toList():group.targets().stream().map(t->target(t,ticks)).toList());}
    public List<CopyTree.Node> targets(List<ProfileResults.Target> targets,int ticks,boolean count,boolean types,boolean mods){
        return mods?ProfileResults.mods(targets,count,types).stream().map(g->mod(g,ticks,types)).toList():types?ProfileResults.groups(targets,count).stream().map(g->type(g,ticks)).toList():targets.stream().map(t->target(t,ticks)).toList();
    }
    public CopyTree.Node chunk(ChunkInvestigation data,boolean count,boolean types,boolean mods){
        return new CopyTree.Node(List.of(data.dimension(),data.x(),data.z()),()->"Chunk "+data.x()+", "+data.z()+" | "+cost(data.nanos()/1000.0/Math.max(1,data.ticks()))+" | "+data.targets()+" measured targets | "+data.dimension(),()->{
            Comparator<ChunkInvestigation.Category> order=Comparator.comparingLong(ChunkInvestigation.Category::nanos).reversed();if(count)order=Comparator.comparingLong(ChunkInvestigation.Category::calls).reversed().thenComparing(order);
            return data.categories().stream().sorted(order.thenComparing(ChunkInvestigation.Category::kind)).map(c->category(data,c.kind(),count,types,mods)).toList();
        });
    }
    public CopyTree.Node category(ChunkInvestigation data,String kind,boolean count,boolean types,boolean mods){
        var summary=data.categories().stream().filter(c->c.kind().equals(kind)).findFirst().orElseThrow();
        return new CopyTree.Node(kind,()->category(kind)+" | "+cost(summary.nanos()/1000.0/Math.max(1,data.ticks()))+" | "+summary.targets()+" measured targets | "+summary.calls()+" updates",()->targets(chunkTargets(data,kind,count),data.ticks(),count,types,mods));
    }
    public static List<ProfileResults.Target> chunkTargets(ChunkInvestigation data,String kind,boolean count){
        Comparator<ProfileResults.Target> order=Comparator.comparingLong((ProfileResults.Target t)->t.entry().nanos()).reversed();if(count)order=Comparator.comparingLong((ProfileResults.Target t)->t.entry().calls()).reversed().thenComparing(order);
        return data.hotspots().stream().filter(h->h.entry().kind().equals(kind)).map(h->new ProfileResults.Target(h.index(),h.entry())).sorted(order.thenComparing(t->t.entry().type()).thenComparingInt(ProfileResults.Target::index)).toList();
    }
    public CopyTree.Node event(Report.Callback event,int ticks){return new CopyTree.Node(event,()->event.method()+" | "+cost(event.nanos()/1000.0/Math.max(1,ticks))+" | "+event.calls()+" calls\n"+event.event()+String.format(Locale.ROOT," | Total %.3f ms | Average %.3f ms/call | Longest %.3f ms",event.nanos()/1_000_000.0,event.nanos()/1_000_000.0/Math.max(1,event.calls()),event.maxNanos()/1_000_000.0),List::of);}
    public CopyTree.Node eventMod(Report.Callback total,List<Report.Callback> children,int ticks){return new CopyTree.Node(total.owner(),()->names.apply(total.owner())+" | "+cost(total.nanos()/1000.0/Math.max(1,ticks))+" | "+total.calls()+" calls",()->children.stream().map(c->event(c,ticks)).toList());}
    public CopyTree.Node comparison(ComparisonIndex.Node node,ComparisonIndex index,boolean laterSort){
        var c=index.comparison();return new CopyTree.Node(node.key(),()->{
            String percent=ProfileComparison.percentLabel(node.percent(c));var k=node.key();
            String line=node.name()+" | "+String.format(Locale.ROOT,"%+.3f µs/t",node.delta(c))+"\nEarlier: "+(node.earlier().targets()==0?"-":cost(node.before(c)))+" → Later: "+(node.later().targets()==0?"-":cost(node.after(c)))+" | "+(percent.isBlank()?"-%":percent);
            if(node.earlier().countKnown()||node.later().countKnown())line+=" | Measured targets: "+(node.earlier().countKnown()?node.earlier().targets():"?")+" → "+(node.later().countKnown()?node.later().targets():"?");
            if(!k.kind().isBlank())line+=" | "+k.kind();if(!k.dimension().equals("All"))line+=" | "+k.dimension();if(!node.status().isBlank())line+="\n"+node.status();if(node.uuid()!=null)line+=" | UUID: "+node.uuid();
            line+=location("Earlier",node.earlier(),c.beforeTicks())+location("Later",node.later(),c.afterTicks());return line;
        },()->(laterSort?index.sort(node.children(),true):node.children()).stream().map(n->comparison(n,index,laterSort)).toList());
    }
    private static String location(String side,ComparisonIndex.Stats stats,int ticks){var e=stats.location();return e==null?"":"\n"+side+" location: "+e.dimension()+" | "+e.x()+" "+e.y()+" "+e.z()+" | "+stats.calls()+" updates / "+ticks+" ticks"+(stats.locations()>1?" | "+stats.locations()+" measured locations":"");}
    public static final class Cpu {
        private final CpuInvestigation data;
        private final CallTreeIndex index;
        private final Map<Integer,List<CpuInvestigation.Edge>> callers=new HashMap<>(),calls=new HashMap<>();
        private final Map<String,List<CpuInvestigation.Method>> methods=new HashMap<>();
        public Cpu(CpuInvestigation data){this.data=data;index=new CallTreeIndex(data);for(var m:data.methods())methods.computeIfAbsent(m.type()+"#"+m.name(),k->new ArrayList<>()).add(m);for(var e:data.edges()){callers.computeIfAbsent(e.callee(),k->new ArrayList<>()).add(e);calls.computeIfAbsent(e.caller(),k->new ArrayList<>()).add(e);}for(var values:List.of(callers,calls))values.values().forEach(v->v.sort(Comparator.comparingLong(CpuInvestigation.Edge::samples).reversed()));}
        public CopyTree.Node method(CpuInvestigation.Method method){return new CopyTree.Node(method.id(),()->method.signature()+" | "+method.self()+" direct samples | "+method.inclusive()+" in stack | "+share(method.self(),data.treeSamples())+" of "+data.treeSamples()+" retained samples\nMod: "+method.owner(),()->{
            var result=new ArrayList<CopyTree.Node>();for(var e:callers.getOrDefault(method.id(),List.of()))result.add(CopyTree.leaf(e,"Caller: "+data.methods().get(e.caller()).signature()+" | "+e.samples()+" samples"));for(var e:calls.getOrDefault(method.id(),List.of()))result.add(CopyTree.leaf(e,"Calls: "+data.methods().get(e.callee()).signature()+" | "+e.samples()+" samples"));return result;
        });}
        public CopyTree.Node task(Report.Task task,long total){return new CopyTree.Node(task,()->task.method()+" | "+task.samples()+" samples | "+share(task.samples(),total)+" of "+total+" listed samples"+(task.threadId()>0?"\nThread: "+task.threadName()+" (#"+task.threadId()+")":""),()->methods.getOrDefault(task.method().replace("Event callback · ",""),List.of()).stream().map(this::method).toList());}
        public CopyTree.Node hierarchy(int id,String owner){return hierarchy(id,index.relevant(owner));}
        private CopyTree.Node hierarchy(int id,boolean[] relevant){var node=data.nodes().get(id);var method=data.methods().get(node.method());return new CopyTree.Node(id,()->method.signature()+" | "+node.inclusive()+" in-stack samples | "+node.self()+" direct | "+share(node.inclusive(),data.treeSamples())+" of "+data.treeSamples()+" retained samples\nMod: "+method.owner(),()->index.children(id).stream().filter(child->relevant[child]).map(child->hierarchy(child,relevant)).toList());}
    }
    public CopyTree.Node task(Report.Task task,long total,Cpu cpu){return cpu==null?CopyTree.leaf(task,task.method()+" | "+task.samples()+" samples | "+share(task.samples(),total)+" of "+total+" listed samples\nCall details unavailable"):cpu.task(task,total);}
    public CopyTree.Node taskMod(Report.Task task,List<Report.Task> children,long total,Cpu cpu){return new CopyTree.Node(task.owner(),()->names.apply(task.owner())+" | "+task.samples()+" samples | "+share(task.samples(),total)+" of "+total+" listed samples",()->children.stream().map(t->task(t,total,cpu)).toList());}
    public CopyTree.Node taskThreads(Report.Task task,List<Report.Task> children,long total,java.util.function.Function<Report.Task,Cpu> cpu){return new CopyTree.Node(task.owner(),()->names.apply(task.owner())+" | "+task.samples()+" Java execution samples | "+share(task.samples(),total),()->children.stream().map(t->task(t,total,cpu.apply(t))).toList());}

}
