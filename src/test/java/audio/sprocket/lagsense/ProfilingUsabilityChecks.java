package audio.sprocket.lagsense;

import java.util.*;

final class ProfilingUsabilityChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static Report fixture(){
        UUID moving=UUID.fromString("00000000-0000-0000-0000-000000000001");
        return new Report(101,1000,10,10,true,false,List.of(
            new Report.Entry("Entity","minecraft:zombie","minecraft:overworld",0,64,0,100000,5,moving),
            new Report.Entry("Entity","minecraft:zombie","minecraft:overworld",32,64,0,300000,6,moving),
            new Report.Entry("Entity","minecraft:zombie","minecraft:the_nether",0,64,0,500000,100,UUID.fromString("00000000-0000-0000-0000-000000000002")),
            new Report.Entry("Block","minecraft:zombie","minecraft:overworld",400,64,0,900000,2),
            new Report.Entry("Random block","minecraft:zombie","minecraft:overworld",48,64,0,700000,50),
            new Report.Entry("Scheduled block","minecraft:zombie","minecraft:overworld",256,64,0,600000,7),
            new Report.Entry("Random fluid","minecraft:flowing_water","minecraft:overworld",64,64,0,0,10)));
    }
    static void run(){
        var report=fixture();
        for(String tab:List.of("All","Entity","Block","Ticks","Chunks")){
            for(boolean updates:List.of(false,true)){
                var targets=ProfileResults.targets(report,new ProfileResults.Filter(tab,"All",false,"",0,0,0),updates);
                var groups=ProfileResults.groups(targets,updates);
                check(targets.size()==switch(tab){case "Entity"->3;case "Block"->1;case "Ticks"->3;default->7;},"All five instance views include the correct activity categories");
                check(groups.stream().flatMap(g->g.targets().stream()).mapToLong(t->t.entry().nanos()).sum()==targets.stream().mapToLong(t->t.entry().nanos()).sum(),"Grouping cannot duplicate or omit retained contributions");
                check(groups.stream().mapToLong(ProfileResults.Group::nanos).sum()==targets.stream().mapToLong(t->t.entry().nanos()).sum(),"Aggregate costs equal included individual costs");
                check(groups.stream().mapToLong(ProfileResults.Group::updates).sum()==targets.stream().mapToLong(t->t.entry().calls()).sum(),"Updates stay distinct from instance counts");
                for(int i=1;i<targets.size();i++)check((updates?targets.get(i-1).entry().calls()>=targets.get(i).entry().calls():targets.get(i-1).entry().nanos()>=targets.get(i).entry().nanos()),"Instance sorting follows selected criterion");
                for(int i=1;i<groups.size();i++)check((updates?groups.get(i-1).updates()>=groups.get(i).updates():groups.get(i-1).nanos()>=groups.get(i).nanos()),"Group sorting follows aggregate updates/time");
                for(var group:groups){
                    check(group.targets().stream().allMatch(t->t.entry().kind().equals(group.type().kind())&&t.entry().type().equals(group.type().registry())),"Registry and category identities cannot merge");
                    check(Math.abs(group.micros(report.ticks())-group.targets().stream().mapToDouble(t->t.entry().micros(report.ticks())).sum())<1e-8,"Per-tick group and individual costs agree");
                    for(var target:group.targets())check(report.entries().get(target.index())==target.entry(),"Expanded targets retain original Mark/TP identities and chunk positions");
                }
                var again=ProfileResults.targets(report,new ProfileResults.Filter(tab,"All",false,"",0,0,0),updates);
                check(targets.equals(again),"Switching back to By Instance preserves the original sorted measurements");
            }
        }
        var groups=ProfileResults.groups(ProfileResults.targets(report,new ProfileResults.Filter("All","All",false,"",0,0,0),false),false);
        var entities=groups.stream().filter(g->g.type().kind().equals("Entity")).findFirst().orElseThrow();
        check(entities.instances()==2&&entities.targets().size()==3&&entities.updates()==111&&entities.nanos()==900000,"A moving UUID counts as one instance while every chunk contribution is retained");
        for(String tab:List.of("All","Entity","Block","Ticks","Chunks")){
            var near=ProfileResults.targets(report,new ProfileResults.Filter(tab,"All",true,"minecraft:overworld",0,64,0),false);
            check(near.stream().allMatch(t->t.entry().dimension().equals("minecraft:overworld")&&t.entry().x()<=128),"Near filter precedes aggregation in every tab");
            var nether=ProfileResults.targets(report,new ProfileResults.Filter(tab,"minecraft:the_nether",false,"",0,0,0),false);
            check(nether.stream().allMatch(t->t.entry().dimension().equals("minecraft:the_nether")),"Dimension filter precedes aggregation in every tab");
            check(ProfileResults.targets(report,new ProfileResults.Filter(tab,"minecraft:the_nether",true,"minecraft:overworld",0,64,0),false).isEmpty(),"Near and dimension filters intersect safely");
        }
        var near=ProfileResults.groups(ProfileResults.targets(report,new ProfileResults.Filter("Chunks","All",true,"minecraft:overworld",0,64,0),false),false);
        var moving=near.stream().filter(g->g.type().kind().equals("Entity")).findFirst().orElseThrow();
        check(moving.instances()==1&&moving.nanos()==400000&&moving.updates()==11,"Filtered cumulative totals include only matching chunk contributions");
        var first=report.entries().getFirst();var duplicates=List.of(first,first,new Report.Entry("Random block",first.type(),first.dimension(),0,64,0,100,500));
        check(ProfileResults.chunkCounts(duplicates).get(new ProfileResults.Chunk("minecraft:overworld",0,0))==2,"Chunk counts distinct measured category/target identities, never update calls");
        var large=new ArrayList<Report.Entry>();for(int i=0;i<26000;i++)large.add(new Report.Entry("Block","fixture:machine","minecraft:overworld",i,64,0,i%2,1));
        var largeReport=new Report(102,2000,100,1,true,false,List.copyOf(large));
        var largeGroup=ProfileResults.groups(ProfileResults.targets(largeReport,new ProfileResults.Filter("Chunks","All",false,"",0,0,0),false),false).getFirst();
        check(largeGroup.instances()==26000&&largeGroup.targets().size()==26000&&largeGroup.nanos()==13000&&largeGroup.updates()==26000,"Grouping retains all cheap/zero targets beyond old caps");
        percentages();tooltips();
        System.out.println("Profiling usability checks passed (all five grouping views, filtered totals, instance/update counts, sorting, original action indices, percentages and measurement help).");
    }
    private static void percentages(){
        var change=new ProfileComparison.Change("minecraft:overworld","Entity","minecraft:zombie",0,0,56.86,224.40,1,1);
        check(Math.abs(change.percentChange()-294.653534998)<1e-6&&ProfileComparison.percentLabel(change.percentChange()).equals("+294.7%"),"Percentage uses earlier measured value with correct rounding");
        check(new ProfileComparison.Change("d","k","t",0,0,10,0,1,1).percentChange()==-100,"Measured zero later value is a 100% decrease");
        check(ProfileComparison.percentage(10,5)==-50&&ProfileComparison.percentage(10,10)==0,"Improvements and unchanged measurements preserve the percentage sign");
        for(double before:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})check(ProfileComparison.percentage(before,10)==null,"Invalid or zero baseline never produces infinity");
        check(ProfileComparison.percentage(0,0)==null&&ProfileComparison.percentage(10,Double.POSITIVE_INFINITY)==null,"Undefined percentages remain absent");
        check(new ProfileComparison.Change("d","k","t",0,0,10,0,1,0).percentChange()==null,"Unobserved targets are not presented as measured percentage changes");
        var a=fixture();var b=new Report(102,2000,10,20,true,false,a.entries());var comparison=ProfileComparison.between(b,a);
        check(ReportExport.json(comparison).equals(ReportExport.json(ProfileComparison.between(a,b))),"Exported percentages preserve chronological ordering");
        check(ReportExport.csv(comparison).contains("change_percent")&&ReportExport.json(comparison).contains("changePercent"),"CSV and JSON preserve percentages alongside absolute measurements");
    }
    private static void tooltips(){
        check(MeasurementHelp.TIME.contains("per server tick")&&MeasurementHelp.MSPT.contains("during this capture"),"Time help identifies the denominator and capture scope");
        check(MeasurementHelp.value("12.0%").contains("does not measure CPU usage")&&MeasurementHelp.DIRECT.contains("method itself")&&MeasurementHelp.STACK.contains("code it called"),"CPU help distinguishes sample semantics from CPU utilisation");
        check(TimingSeverity.position(50)<TimingSeverity.position(100),"Increasing absolute time advances along the shared timing gradient");
        check(TimingSeverity.color(0)==0xff45e69b&&TimingSeverity.color(10000)==0xffe94d58,"Continuous timing gradient keeps its explicit low/high endpoints");
        var report=fixture();var incomplete=new Report(report.id(),report.capturedAt(),report.ticks(),report.mspt(),true,true,report.entries(),false,0,List.of(),20,new Report.Diagnostics("Manual",0,0,List.of(),List.of("Capture stopped: target memory budget reached.")));
        check(ReportCompleteness.reason(incomplete).contains("Totals use the available measurements.")&&ReportCompleteness.label(incomplete).contains("memory budget"),"Capture collection limits explain partial retained aggregates");
        var preview=Profiler.JSON.fromJson(ReportWire.json(new Report(1,2,1,1,true,false,Collections.nCopies(129,report.entries().getFirst()),false,0,List.of(),20,new Report.Diagnostics("Manual",0,0)),Profiler.JSON),Report.class);
        check(ReportCompleteness.reason(preview).contains("presentation limited")&&ReportCompleteness.reason(preview).contains("on the server"),"Known legacy presentation limits are distinct from collection loss");
        check(ReportCompleteness.reason(new Report(1,2,1,1,true,true,List.of())).contains("does not record why"),"Unknown older limits never claim completeness or invent a cause");
        var diagnosticLimits=new Report(1,2,10,1,true,false,List.of(),false,0,List.of(),20,new Report.Diagnostics("Manual",0,0,List.of(),List.of("CPU results limited: overview retention.","Event results limited: event retention.","Detailed callback limit reached.")));
        check(MeasurementHelp.diagnosticLimit(diagnosticLimits,true).contains("2048")&&!MeasurementHelp.diagnosticLimit(diagnosticLimits,true).contains("CPU results"),"Event limit help identifies collection/retention causes without mixing CPU data");
        check(MeasurementHelp.diagnosticLimit(diagnosticLimits,false).contains("CPU results")&&!MeasurementHelp.diagnosticLimit(diagnosticLimits,false).contains("Event results"),"CPU limit help stays specific to its own data");
    }
}
