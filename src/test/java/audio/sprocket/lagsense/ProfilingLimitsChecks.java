package audio.sprocket.lagsense;

import audio.sprocket.lagsense.client.UiTheme;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Dedicated small/large-heap runs exercise the actual collectors and complete output pipeline. */
public final class ProfilingLimitsChecks {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static CpuInvestigation.Frame frame(String name){return new CpuInvestigation.Frame("fixture","fixture.Work",name,"()V");}
    public static void policy()throws Exception{
        var limits=ProfilingLimits.defaults();
        var large=limits.effective(28L<<30,8L<<30);var small=limits.effective(256L<<20,32L<<20);var pressure=limits.effective(256L<<20,250L<<20);
        check(large.events()==128L<<20&&large.cpu()==128L<<20&&large.jfr()==128L<<20&&large.depth()==128,"28 GiB heaps get the generous defaults");
        check(small.cpu()==7L<<20&&pressure.cpu()<small.cpu()&&limits.effective(1L<<30,1L<<30).cpu()==0,"Budgets follow heap headroom and can stop admission under severe pressure");
        var custom=new ProfilingLimits("custom",256,512,64,256);
        check(custom.effective(28L<<30,8L<<30).cpu()==512L<<20&&custom.effective(256L<<20,250L<<20).cpu()<=6L<<20,"Custom ceilings also respect free heap");
        var defaults=ServerSettings.defaults();var view=ServerSettingsForms.view(defaults);
        var advanced=new ServerSettingsForms.Advanced(true,true,false,defaults.backups(),custom);
        var changes=ServerSettingsForms.changes("advanced",defaults,new ServerSettingsForms.Edit(view,new ServerSettingsForms.View(advanced,view.discord()),""));
        Path directory=Files.createTempDirectory("lagsense-limits-config-");Path config=directory.resolve("server.toml");
        try{
            Files.writeString(config,ServerSettings.template());var saved=ServerSettings.update(config,defaults,changes);
            check(saved.limits().equals(custom)&&ServerSettings.load(config).limits().equals(custom),"Scoped admin edits persist all limits");
            var stale=ServerSettingsForms.view(saved);var changedEffective=new ServerSettingsForms.View(stale.advanced(),stale.discord(),new ProfilingLimits.Effective(0,0,1,128));
            check(!ServerSettingsForms.changes("advanced",saved,new ServerSettingsForms.Edit(changedEffective,changedEffective,"")).isEmpty(),"Changing free heap is not mistaken for a conflicting config edit");
            String before=Files.readString(config);
            try{ServerSettings.update(config,saved,Map.of("profiling.limits.cpu_mib",513));throw new AssertionError("Invalid limit accepted");}catch(IllegalArgumentException expected){}
            check(Files.readString(config).equals(before),"Rejected configuration edits preserve the previous file");
        }finally{Files.deleteIfExists(config);Files.deleteIfExists(directory);}
        for(int bad:new int[]{-1,0,513})try{new ProfilingLimits("custom",bad,128,128,128);throw new AssertionError("Invalid budget");}catch(IllegalArgumentException expected){}
        try{new ProfilingLimits("adaptive",128,128,128,513);throw new AssertionError("Invalid depth");}catch(IllegalArgumentException expected){}
        var budget=new DiagnosticBudget(2048);var builder=new CpuInvestigation.Builder("bounded",budget);
        builder.add(List.of(frame("known")));for(int i=0;i<100;i++)builder.add(List.of(frame("extra"+i)));builder.add(List.of(frame("known")));
        var cpu=builder.finish();CpuInvestigation.validate(cpu);
        check(cpu.limited()&&budget.used()<=2048&&cpu.limits().omittedPaths()>0&&cpu.methods().getFirst().self()==2,"Exhaustion preserves prior measurements and continues counting known paths");
        var depth=new CpuInvestigation.Builder("depth",new DiagnosticBudget(1L<<20));depth.add(Collections.nCopies(160,frame("recursive")),true);var shortened=depth.finish();
        check(shortened.nodes().size()==129&&shortened.limits().shortenedStacks()==1&&shortened.limits().jvmTruncatedStacks()==1,"Configured and JVM truncation are reported separately");
        check(Profiler.JSON.fromJson("{\"state\":\"ready\",\"thread\":\"old\",\"samples\":0,\"treeSamples\":0,\"methods\":[],\"nodes\":[],\"edges\":[]}",CpuInvestigation.class).limits()==null,"Older captures need no fabricated limit metadata");
    }
    private static void largerWire()throws Exception{
        var random=new Random(29);var methods=new ArrayList<CpuInvestigation.Method>();var nodes=new ArrayList<CpuInvestigation.Node>();
        nodes.add(new CpuInvestigation.Node(0,-1,-1,6000,0));
        for(int i=0;i<6000;i++){byte[] id=new byte[600];random.nextBytes(id);String type="fixture."+Base64.getEncoder().encodeToString(id);
            methods.add(new CpuInvestigation.Method(i,"fixture",type,"run"+i,"()V",1,1));nodes.add(new CpuInvestigation.Node(i+1,0,i,1,1));}
        var cpu=new CpuInvestigation("ready","Server thread",6000,6000,false,methods,nodes,List.of());
        long expanded=Profiler.JSON.toJson(new InvestigationWire.Data(cpu,null,"")).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        byte[] bytes=InvestigationWire.encode(new InvestigationWire.Data(cpu,null,""));
        check(expanded>4L<<20&&bytes.length>512*1024,"Fixture exceeds both former call-path transfer limits");
        check(InvestigationWire.decode(bytes).cpu().equals(cpu),"Multi-megabyte call paths survive the replacement streaming envelope");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY,net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        var assembly=new ReportAssembly();ReportAssembly.Complete complete=null;
        try{for(int offset=0;offset<bytes.length;offset+=FullReportPart.PART_BYTES){buffer.clear();var part=new InvestigationPart(new FullReportPart(1,bytes.length,offset,Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+FullReportPart.PART_BYTES)),false,false));
            InvestigationPart.CODEC.encode(buffer,part);complete=assembly.accept(InvestigationPart.CODEC.decode(buffer).part());}}
        finally{buffer.release();}
        check(complete!=null&&Arrays.equals(complete.bytes(),bytes),"Large call paths round trip through actual bounded packet codecs");
        Path temp=Files.createTempFile("lagsense-output-limit-",".tmp");
        try{try{ReportFiles.json(temp,cpu,Profiler.JSON,1024);throw new AssertionError("Oversized file accepted");}catch(java.io.IOException|com.google.gson.JsonIOException expected){}check(!Files.exists(temp),"Explicit file-limit failure removes the incomplete output");}
        finally{Files.deleteIfExists(temp);}
        System.out.printf(Locale.ROOT,"LARGE CALL PATH WIRE expanded=%.2f MiB compressed=%.2f MiB%n",expanded/(double)(1<<20),bytes.length/(double)(1<<20));
    }
    public static void main(String[] args)throws Exception{
        policy();largerWire();
        var limits=ProfilingLimits.defaults();var effective=limits.effective();
        System.gc();long before=used();
        var allocated=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();long allocationBefore=allocated.getThreadAllocatedBytes(Thread.currentThread().threadId());
        var serverBudget=limits.cpu(effective);var backgroundBudget=limits.cpu(effective);
        var samples=new CpuSamples(1,"Server thread",serverBudget,backgroundBudget,effective.depth());
        var root=frame("tick");long started=System.nanoTime();
        for(int i=0;i<5000;i++){
            var stack=List.of(frame("run"+i),root);samples.add(1,"Server thread",stack);samples.add(2+i%3,"worker-"+(i%3),stack);
        }
        var deep=new ArrayList<CpuInvestigation.Frame>();for(int i=0;i<120;i++)deep.add(frame("deep"+i));samples.add(1,"Server thread",deep);
        var result=samples.finish(false);long elapsed=System.nanoTime()-started;
        long allocatedBytes=allocated.getThreadAllocatedBytes(Thread.currentThread().threadId())-allocationBefore;
        samples=null;System.gc();long growth=Math.max(0,used()-before);
        var cpu=result.cpu();CpuInvestigation.validate(cpu);BackgroundCpu.validate(result.background());
        check(result.server().size()==5001&&!result.serverCollectionLimited()&&!result.serverRetentionLimited(),"Every server action survives the former 4096/128 limits");
        check(cpu.methods().size()>1024&&cpu.nodes().size()>2048&&cpu.edges().size()>4096&&!cpu.limited(),"No fixed method, node, edge or 48-frame cutoff remains");
        check(result.background().threads().size()==3&&!result.background().limited()&&result.background().tasks().size()==5000,"All worker actions survive in an independent shared background budget");
        check(serverBudget.used()<=serverBudget.maximum()&&backgroundBudget.used()<=backgroundBudget.maximum(),"Both collector scopes respect their effective ceilings");
        var callbacks=new ArrayList<Report.Callback>();for(int i=0;i<9001;i++)callbacks.add(new Report.Callback("fixture","fixture.Event","fixture.Handler#run"+i,1000,10,100,i+1));
        var entries=new ArrayList<Report.Entry>();for(int i=0;i<5000;i++)entries.add(new Report.Entry("Block","fixture:machine","minecraft:overworld",i,64,-i,1000,10));
        var diagnostics=new Report.Diagnostics("Manual",0,0,result.server(),List.of(),callbacks,"ready",cpu,null,result.background());
        var report=new Report(100,1000,100,2,true,false,entries,false,0,Report.aggregate(entries),20,diagnostics);
        ProfileArchive.validate(report);
        byte[] encoded=FullReportWire.encode(report);check(FullReportWire.decode(encoded).equals(report),"All targets, handlers, actions and paths survive full transfer");
        byte[] detail=InvestigationWire.encode(new InvestigationWire.Data(cpu,null,""));var assembly=new ReportAssembly();ReportAssembly.Complete complete=null;
        for(int offset=0;offset<detail.length;offset+=FullReportPart.PART_BYTES)complete=assembly.accept(new FullReportPart(1,detail.length,offset,Arrays.copyOfRange(detail,offset,Math.min(detail.length,offset+FullReportPart.PART_BYTES)),false,false));
        check(complete!=null&&InvestigationWire.decode(complete.bytes()).cpu().equals(cpu),"Segmented on-demand paths are complete");
        var failures=new ArrayList<Exception>();Path directory=Files.createTempDirectory("lagsense-limits-pipeline-");
        try{
            try(var archive=new ProfileArchive(directory,Profiler.JSON,new ProfileArchive.Settings(true,10,128),failures::add)){
                archive.capture(report);var future=new CompletableFuture<Report>();archive.read(report.id(),(r,error)->{if(error!=null)future.completeExceptionally(error);else future.complete(r);});
                check(future.get(30,TimeUnit.SECONDS).equals(report)&&failures.isEmpty(),"Complete CPU data is saved and reopened without overview truncation");
            }
            var later=new Report(101,2000,200,3,true,false,entries,false,0,report.chunks(),20,diagnostics);
            check(ProfileComparison.between(report,later).equals(ProfileComparison.between(later,report)),"Larger diagnostics do not change chronological target comparisons");
            Path csv=directory.resolve("complete.csv");try(var writer=Files.newBufferedWriter(csv)){ReportExport.writeCsv(report,writer);}
            var kinds=new HashMap<String,Long>();try(var lines=Files.lines(csv)){lines.skip(1).forEach(line->{String kind=line.substring(0,line.indexOf(','));kinds.merge(kind,1L,Long::sum);});}
            check(kinds.get("\"CPU sample\"")==5001&&kinds.get("\"Background CPU sample\"")==5000&&kinds.get("\"Callback\"")==9001,"CSV preserves every action and handler");
            long methods=cpu.methods().size(),nodes=cpu.nodes().size(),edges=cpu.edges().size();for(var w:result.background().threads()){methods+=w.cpu().methods().size();nodes+=w.cpu().nodes().size();edges+=w.cpu().edges().size();}
            check(kinds.get("\"CPU method\"")==methods&&kinds.get("\"Call-path node\"")==nodes&&kinds.get("\"Call-path relationship\"")==edges,"CSV retains all method identities, tree nodes and relationships");
            Path html=directory.resolve("complete.html");HtmlReportExport.write(html,report,UiTheme.Theme.OBSIDIAN_GOLD);
            String document=Files.readString(html);String marker="<script id=\"report-data\" type=\"application/json\">";int start=document.indexOf(marker)+marker.length();
            var data=com.google.gson.JsonParser.parseString(document.substring(start,document.indexOf("</script>",start))).getAsJsonObject().getAsJsonObject("data");
            check(Profiler.JSON.fromJson(data,Report.class).equals(report),"HTML contains the entire capture with all worker paths");
            Path output=Path.of("work/profiling-limits");Files.createDirectories(output);
            Files.copy(html,output.resolve("complete-"+Runtime.getRuntime().maxMemory()/1024/1024+"m.html"),StandardCopyOption.REPLACE_EXISTING);
            System.out.printf(Locale.ROOT,"LIMITS BENCH heap=%d MiB effectiveCPU=%.2f MiB serverCharge=%.2f MiB backgroundCharge=%.2f MiB retainedHeapGrowth=%.2f MiB allocated=%.2f MiB samples=10001 ingestAndSnapshot=%.2f ms wire=%.2f MiB csv=%.2f MiB html=%.2f MiB%n",Runtime.getRuntime().maxMemory()>>20,effective.cpu()/(double)(1<<20),serverBudget.used()/(double)(1<<20),backgroundBudget.used()/(double)(1<<20),growth/(double)(1<<20),allocatedBytes/(double)(1<<20),elapsed/1e6,encoded.length/(double)(1<<20),Files.size(csv)/(double)(1<<20),Files.size(html)/(double)(1<<20));
        }finally{try(var files=Files.list(directory)){for(var file:files.toList())Files.deleteIfExists(file);}Files.deleteIfExists(directory);}
        java.lang.ref.Reference.reachabilityFence(result);
    }
    private static long used(){var r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
}
