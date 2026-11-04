package audio.sprocket.lagsense;

import java.util.*;

final class CpuSamplesChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static List<CpuInvestigation.Frame> stack(String owner,String type){return List.of(new CpuInvestigation.Frame(owner,type,"run","()V"));}
    static void run()throws Exception{
        var capture=new CpuSamples(1,"Server thread",new DiagnosticBudget(1L<<20));
        for(int i=0;i<10;i++)capture.add(1,"Server thread",stack("modserver","server.Tick"));
        capture.add(2,"worker",stack("moda","mod.a.Work"));capture.add(3,"worker",stack("modb","mod.b.Work"));
        capture.add(4,"short-lived",stack("unattributed","unknown.Work"));capture.add(5,"platform",stack("platform","java.util.HashMap"));
        capture.add(6,"LagSense CPU sample reader",stack("own","analysis.Work"));capture.add(7,"worker",stack("own","audio.sprocket.lagsense.Reader"));
        capture.add(8,"Render thread",stack("minecraft","net.minecraft.client.Render"));
        var result=capture.finish(false);var bg=result.background();BackgroundCpu.validate(bg);
        check(result.cpu().samples()==10&&result.server().stream().mapToLong(Report.Task::samples).sum()==10,"Server aggregation is unchanged by other threads");
        check(bg.threads().size()==4&&bg.samples()==4&&!bg.limited(),"Workers, unknown and platform Java activity are retained separately");
        check(bg.thread(2).name().equals(bg.thread(3).name())&&bg.thread(2).id()!=bg.thread(3).id(),"Duplicate thread display names retain distinct Java identities");
        check(bg.thread(2).tasks().getFirst().owner().equals("moda")&&bg.thread(4).tasks().getFirst().owner().equals("unattributed")&&bg.thread(5).tasks().getFirst().owner().equals("platform"),"Reliable mod attribution does not erase unknown/platform work");
        check(result.cpu().methods().stream().noneMatch(m->m.type().contains("Work"))&&bg.thread(2).cpu().methods().stream().allMatch(m->m.type().equals("mod.a.Work")),"Call paths remain separated by thread");
        var tight=new CpuSamples(1,"Server thread",new DiagnosticBudget(0));
        for(int i=0;i<500;i++){tight.add(2+i,"worker",stack("moda","mod.a.Work"));tight.add(1,"Server thread",stack("modserver","server.Tick"));}
        var bounded=tight.finish(false);check(bounded.cpu().samples()==500&&!bounded.cpu().limited()&&bounded.background().limited()&&bounded.background().omittedSamples()==500&&tight.backgroundBytes()==0,"Background overflow cannot exhaust server-thread collection");
        check(capture.finish(true).background().limited(),"Recording loss is explicitly incomplete");
        var callbacks=new CpuSamples(1,"Server thread",new DiagnosticBudget(1L<<20));
        var eventStack=List.of(new CpuInvestigation.Frame("moda","mod.a.Handler","run","()V"),new CpuInvestigation.Frame("lagsense","audio.sprocket.lagsense.CallbackTimings$Timed","invoke","()V"),new CpuInvestigation.Frame("platform","net.neoforged.bus.EventBus","post","()V"),new CpuInvestigation.Frame("lagsense","audio.sprocket.lagsense.ProfilingEventBus","post","()V"));
        callbacks.add(1,"Server thread",eventStack);callbacks.add(2,"worker",eventStack);var eventSamples=callbacks.finish(false);
        check(eventSamples.cpu().samples()==1&&eventSamples.background().thread(2).cpu().samples()==1,"Timing adapter frames do not suppress genuine sampled event-handler work");
        check(eventSamples.cpu().methods().stream().noneMatch(m->m.type().startsWith("audio.sprocket.lagsense.")),"Adapter frames are excluded from retained paths without discarding the caller stack");
        var coverage=new TimingCoverage.Snapshot(100,30,40,10,40);
        var d=new Report.Diagnostics("Manual",0,0,result.server(),List.of(),List.of(),"ready",result.cpu(),coverage,bg);
        var report=new Report(1,1,1,.0001,true,false,List.of(),false,0,List.of(),20,d);
        ProfileArchive.validate(report);
        var transferred=FullReportWire.decode(FullReportWire.encode(report.view(true,e->true)));
        check(transferred.diagnostics().background().equals(bg)&&transferred.diagnostics().coverage().equals(coverage),"Background stacks survive archive JSON and full transport without changing timing unions");
        check(report.view(false,e->true).diagnostics()==null,"Worker identities stay admin-only");
        String csv=ReportExport.csv(report);check(csv.contains("Background CPU sample")&&csv.contains("short-lived")&&csv.contains("background listed actions"),"CSV preserves names, identities, counts and scoped percentages");
        check(InvestigationExport.csv(bg.thread(2).cpu()).contains("inclusive_sample_percent,direct_sample_percent,sample_kind")&&InvestigationExport.csv(bg.thread(2).cpu()).contains("Java execution samples"),"Per-thread call-path CSV labels sample units and percentages");
        String json=Profiler.JSON.toJson(report);check(Profiler.JSON.fromJson(json,Report.class).equals(report),"Full background archive round trip");
        check(new Report.Diagnostics("Old",0,0).background()==null,"Older captures do not fabricate background data");
        var c=new TimingCoverage();c.beginCycle(0);c.beginEvent(1);c.beginEvent(3);c.endEvent(6);c.endEvent(9);c.endCycle(10);
        check(c.snapshot().eventNanos()==8&&8+3>c.snapshot().eventNanos(),"Inclusive parent/child event rows share one union interval");
    }
}
