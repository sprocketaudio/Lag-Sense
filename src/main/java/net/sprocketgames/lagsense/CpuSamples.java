package net.sprocketgames.lagsense;

import java.util.*;

/** Worker-owned aggregation. Background allocations cannot consume the server-thread collector. */
public final class CpuSamples {
    private record Key(String owner,String method){}
    private static final class Activity {
        final long id;final String name;final DiagnosticBudget budget;final CpuInvestigation.Builder tree;final Map<Key,Long> counts=new HashMap<>();boolean limited;long omitted;
        Activity(long id,String name,DiagnosticBudget budget,int depth){this.id=id;this.name=name;this.budget=budget;tree=new CpuInvestigation.Builder(name+" (#"+id+")",budget,depth);}
    }
    public record Result(List<Report.Task> server,CpuInvestigation cpu,BackgroundCpu background,boolean serverRetentionLimited,boolean serverCollectionLimited,String serverReason){}
    private final Activity server;
    private final Map<Long,Activity> background=new LinkedHashMap<>();
    private final DiagnosticBudget budget;
    private final int depth;
    private long omitted;
    private boolean limited;
    public CpuSamples(long serverId,String serverName,DiagnosticBudget budget){this(serverId,serverName,DiagnosticBudget.background(),budget,ProfilingLimits.defaults().stackDepth());}
    public CpuSamples(long serverId,String serverName,DiagnosticBudget serverBudget,DiagnosticBudget backgroundBudget,int depth){server=new Activity(serverId,serverName,serverBudget,depth);this.budget=backgroundBudget;this.depth=depth;}
    public void add(long id,String name,List<CpuInvestigation.Frame> frames){add(id,name,frames,false);}
    public void add(long id,String name,List<CpuInvestigation.Frame> frames,boolean jvmTruncated){
        if(id<=0||frames.isEmpty()||name.startsWith("LagSense ")||name.equals("Render thread")||frames.stream().anyMatch(f->f.type().startsWith("net.sprocketgames.lagsense.")&&!CpuHotPaths.adapterFrame(f.type())||f.type().startsWith("net.minecraft.client.")||f.type().startsWith("com.mojang.blaze3d.")))return;
        var path=CpuHotPaths.chooseOwned(frames);
        if(path==null&&id!=server.id){var leaf=frames.getFirst();path=new CpuHotPaths.Path(leaf.owner(),leaf.type()+"#"+leaf.name());}
        if(path==null)return;
        Activity a=server;
        if(id!=server.id){
            a=background.get(id);if(a==null){if(!budget.reserve(2048L+2L*name.length())){omitted++;limited=true;return;}a=new Activity(id,name,budget,depth);background.put(id,a);}
        }
        var key=new Key(path.owner(),path.method());
        if(a.counts.containsKey(key))a.counts.merge(key,1L,Long::sum);
        else if(a.budget.reserve(320L+2L*(key.owner().length()+key.method().length())))a.counts.put(key,1L);
        else{a.limited=true;a.omitted++;}
        // Injected wrapper frames are not useful call-path nodes; keep genuine library ownership.
        a.tree.add(frames.stream().filter(f->!f.name().contains("lagsense$")&&!CpuHotPaths.adapterFrame(f.type())).toList(),jvmTruncated);
    }
    private static List<Report.Task> tasks(Activity a,boolean main){
        var sorted=a.counts.entrySet().stream().sorted(Map.Entry.<Key,Long>comparingByValue().reversed().thenComparing(e->e.getKey().method()));
        return sorted.map(e->new Report.Task(e.getKey().owner(),e.getKey().method(),e.getValue(),main?0:a.id,main?"":a.name)).toList();
    }
    public Result finish(boolean recordingLimited){
        var workers=new ArrayList<BackgroundCpu.Worker>();long samples=0,lost=omitted;boolean incomplete=limited||recordingLimited;
        for(var a:background.values()){var cpu=a.tree.finish();samples+=cpu.samples();lost+=a.omitted;incomplete|=a.limited||cpu.limited();workers.add(new BackgroundCpu.Worker(a.id,a.name,tasks(a,false),cpu));}
        workers.sort(Comparator.comparingLong((BackgroundCpu.Worker w)->w.cpu().samples()).reversed().thenComparingLong(BackgroundCpu.Worker::id));
        var reasons=new StringBuilder();
        if(incomplete){
            if(recordingLimited)reasons.append("JFR storage or data loss shortened the sample window. ");
            reasons.append("Background CPU: ").append(workers.size()).append(" threads and ").append(workers.stream().mapToInt(w->w.tasks().size()).sum()).append(" actions retained. Budget ").append(budget.description()).append(". ").append(lost).append(" action observations omitted. ");
            long missingPaths=0,missingEdges=0,shortened=0,jvm=0;
            for(var worker:workers){var l=worker.cpu().limits();if(l!=null){missingPaths+=l.omittedPaths();missingEdges+=l.omittedEdges();shortened+=l.shortenedStacks();jvm+=l.jvmTruncatedStacks();}}
            reasons.append(missingPaths).append(" path observations and ").append(missingEdges).append(" relationship observations omitted. ").append(shortened).append(" stacks exceeded ").append(depth).append(" retained frames. ").append(jvm).append(" stacks were shortened by the JVM.");
        }
        String reason=reasons.toString();
        var cpu=server.tree.finish();
        String serverReason=(server.limited?"Server CPU: "+server.counts.size()+" actions retained, "+server.omitted+" action observations omitted. Budget "+server.budget.description()+". ":"")+cpu.limitation();
        return new Result(tasks(server,true),cpu,new BackgroundCpu("ready",workers,samples,lost,incomplete,reason),false,server.limited||cpu.limited(),serverReason);
    }

    public long backgroundBytes(){return budget.used();}
}
