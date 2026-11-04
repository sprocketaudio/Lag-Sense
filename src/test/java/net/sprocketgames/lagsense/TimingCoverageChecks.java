package net.sprocketgames.lagsense;

import java.util.List;

final class TimingCoverageChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void run()throws Exception{
        var c=new TimingCoverage();check(!c.beginTarget(1)&&!c.beginEvent(1),"Between-cycle tasks do not inflate tick coverage");
        c.beginCycle(100);c.beginTarget(110);c.beginTarget(120);c.endTarget(130);
        c.beginEvent(140);c.beginEvent(150);c.endEvent(160);c.endTarget(170);c.endEvent(180);c.endCycle(200);
        check(c.snapshot().equals(new TimingCoverage.Snapshot(100,60,40,30,30)),"Nested scopes and intersecting categories count their interval unions once");
        c.beginCycle(300);c.beginEvent(310);c.endEvent(320);c.beginTarget(330);c.endTarget(340);c.endCycle(350);
        check(c.snapshot().equals(new TimingCoverage.Snapshot(150,70,50,30,60)),"Disjoint cycles exclude idle gaps and preserve overlap");
        c.reset();c.beginCycle(0);
        for(int i=0;i<4096;i++){c.beginEvent(1);c.beginTarget(2);}
        for(int i=0;i<4096;i++){c.endTarget(8);c.endEvent(9);}
        c.endCycle(10);
        check(c.snapshot().equals(new TimingCoverage.Snapshot(10,6,8,6,2)),"Deeply nested callbacks measure unions, never summed or estimated overlap");
        c.reset();c.beginCycle(-50);c.beginTarget(-40);c.endTarget(-30);c.endCycle(0);
        check(c.snapshot().equals(new TimingCoverage.Snapshot(50,10,0,0,40)),"nanoTime needs no positive epoch and manual partial cycles are supported");
        boolean invalid=false;try{new TimingCoverage.Snapshot(10,8,8,0,0);}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"Reject double-counted or inconsistent persisted accounting");
        var snapshot=c.snapshot();var d=new Report.Diagnostics("Manual",0,0,List.of(),List.of(),List.of(),"disabled",null,snapshot);
        var report=new Report(1,1,1,.00005,true,false,List.of(),false,0,List.of(),20,d);
        check(FullReportWire.decode(FullReportWire.encode(report)).equals(report),"Coverage survives segmented-transfer format and archive JSON");
        check(report.view(true,List.of()).diagnostics().coverage().equals(snapshot),"Summary retains global coverage");
        check(report.view(false,List.of()).diagnostics()==null,"Private base views never expose global coverage");
        check(ReportExport.csv(report).contains("Outside timed categories"),"CSV export includes coverage independently of summed inclusive rows");
        var json=Profiler.JSON.toJson(report);check(json.contains("outsideNanos"),"JSON exports retain unrounded accounting");
        var legacy=Profiler.JSON.fromJson(json.replace(",\"coverage\":"+Profiler.JSON.toJson(snapshot),""),Report.class);
        check(legacy.diagnostics().coverage()==null,"Older reports leave coverage unavailable rather than inventing a remainder");
        var entry=new Report.Entry("Block","minecraft:stone","minecraft:overworld",0,0,0,0,1,null,false);
        var index=new MarkerIndex(new Report(1,1,1,0,true,false,List.of(entry)));
        check(index.position("minecraft:overworld",0,0,0).isEmpty()&&index.near("minecraft:overworld",0,0,0,96,0).isEmpty(),"Missing legacy locations do not manufacture markers at the world origin");
    }
}
