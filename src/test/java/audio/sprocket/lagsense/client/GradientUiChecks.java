package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.util.*;

/** Real row factories and map tiles: verify colours are based on measured data at each hierarchy level. */
final class GradientUiChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static java.lang.reflect.Method method(Class<?> type,String name,Class<?>...args)throws Exception{var m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
    private static UiRow row(LagScreen screen,UiResults out,int offset){var list=new UiScrollList(screen.margin(),100,screen.contentWidth(),24,new ListScroll());list.virtual(out.entries,null);list.restore(offset);return (UiRow)list.children().getFirst();}
    static void run()throws Exception{
        var oldReport=LagSenseClient.report;var oldTheme=UiTheme.current();
        try{
            var entries=List.of(new Report.Entry("Entity","a:creeper","minecraft:overworld",1,64,1,2300000,100),new Report.Entry("Entity","a:creeper","minecraft:overworld",2,64,1,7500000,100),new Report.Entry("Block","a:machine","minecraft:overworld",3,64,1,3000000,100),new Report.Entry("Random block","b:crop","minecraft:overworld",4,64,1,3000000,100));
            var report=new Report(88,1000,100,1,true,false,entries).view(true,List.of());LagSenseClient.report=report;
            var targets=ProfileResults.targets(report,new ProfileResults.Filter("All","All",false,"",0,0,0),false);
            for(int scale:List.of(2,3)){
                var screen=new ReportScreen();screen.width=1920/scale;screen.height=1080/scale;
                var group=ProfileResults.groups(targets,false).stream().filter(g->g.type().kind().equals("Entity")).findFirst().orElseThrow();var mods=ProfileResults.mods(targets,false,true);
                var appendGroup=method(ReportScreen.class,"appendGroup",UiResults.class,ProfileResults.Group.class,int.class);var appendMod=method(ReportScreen.class,"appendMod",UiResults.class,ProfileResults.ModGroup.class);
                var out=new UiResults(screen,100,76,false);appendGroup.invoke(screen,out,group,0);check(row(screen,out,0).measurementColor()==TimingSeverity.color(98),"By Type uses 98 µs/t aggregate, without a group-specific scale");
                out=new UiResults(screen,100,76,false);appendMod.invoke(screen,out,mods.getFirst());check(row(screen,out,0).measurementColor()==TimingSeverity.color(mods.getFirst().micros(100)),"Group by Mod uses full summed timing, not averaged child colours");
                var chunkData=new MapTargets(report,null).chunk("minecraft:overworld",0,0);var branches=new ChunkBranches(screen,report,false);branches.seed(chunkData);branches.mode(false,false,false);var expanded=(Map<?,?>)field(branches,"chunks");var state=expanded.values().iterator().next();
                out=new UiResults(screen,100,76,false);branches.append(out,report.chunks().getFirst(),0);check(row(screen,out,0).measurementColor()==TimingSeverity.color(98),"Chunk category with the same 98 µs/t as a type gets the identical colour");
                for(var category:((Map<?,?>)field(state,"categories")).values())set(category,"open",true);
                out=new UiResults(screen,100,76,false);branches.append(out,report.chunks().getFirst(),0);
                int targetIndex=-1;for(int i=0;i<out.entries.size();i++)if(out.entries.get(i).key() instanceof List<?> key&&key.getLast() instanceof ProfileResults.Target t&&t.entry().nanos()==2300000){targetIndex=i;break;}
                check(targetIndex>=0&&row(screen,out,targetIndex*24).measurementColor()==TimingSeverity.color(23),"Nested individual targets use their unaveraged measured cost");
                var map=new HeatmapScreen(null,report,false);map.width=screen.width;map.height=screen.height;map.init();
                @SuppressWarnings("unchecked") var tiles=(Map<String,List<HeatmapPlan.Tile>>)field(map,"tiles");check(tiles.get("minecraft:overworld").getFirst().color()==TimingSeverity.color(158),"Actual map tiles use total measured chunk µs/t on the shared scale");
                var b=TimingLegend.bounds(screen.margin(),screen.height-60,screen.contentWidth());check(b.right()<=screen.width-screen.margin()&&b.barY()+6<screen.footer()&&b.barWidth()>32,"Compact visual legend and endpoints fit the existing footer at GUI scales 2/3");
                check(b==TimingLegend.bounds(screen.margin(),screen.height-60,screen.contentWidth()),"Unchanged legend reuses its layout without per-frame allocation");
                check(TimingLegend.LOW.equals("0 µs/t")&&TimingLegend.HIGH.equals("10,000+ µs/t")&&TimingLegend.color(0,b.barWidth())==TimingSeverity.color(0)&&TimingLegend.color(b.barWidth()-1,b.barWidth())==TimingSeverity.color(10000),"Legend endpoints describe the actual fixed timing domain");
                cpuAndEvents(report,screen.width,screen.height);
            }
            contrast();
        }finally{LagSenseClient.report=oldReport;UiTheme.select(oldTheme);}
        System.out.println("Gradient UI checks passed (actual type/mod/category/target rows, map totals, cached compact legend, events and sampled CPU/call-path rows at GUI scales 2/3, all-theme contrast).");
    }
    private static void cpuAndEvents(Report source,int width,int height)throws Exception{
        var task=new TaskScreen(null,source,false);task.width=width;task.height=height;set(task,"total",100L);
        var taskRow=method(TaskScreen.class,"taskRow",UiResults.class,String.class,Report.Task.class,boolean.class,int.class);
        var sample=new Report.Task("a","test.Work#tick",30);var out=new UiResults(task,100,0,false).samples(100);taskRow.invoke(task,out,"sample",sample,false,0);var cpu=row(task,out,0);
        check(cpu.measurementColor()==UiTheme.TEXT&&field(cpu,"sampleCount").equals("30 samples")&&field(cpu,"trailing").equals("30.0%"),"CPU rows preserve counts/percentages and use the neutral theme colour");
        var group=new Report.Task("a","",60);out=new UiResults(task,100,0,false).samples(100);taskRow.invoke(task,out,"mod",group,true,0);check(row(task,out,0).measurementColor()==UiTheme.TEXT,"CPU mod percentages use neutral text without changing the sampled share");
        var callbackRow=method(TaskScreen.class,"callbackRow",UiResults.class,String.class,Report.Callback.class,boolean.class,int.class);var event=new Report.Callback("a","Tick","test.Events#tick",9800000,100,1000000);
        for(boolean mod:List.of(false,true)){out=new UiResults(task,100,0,false);callbackRow.invoke(task,out,"event",event,mod,0);check(row(task,out,0).measurementColor()==TimingSeverity.color(98),"Event action/mod rows use the same 98 µs/t as targets/types/chunks");}
        var builder=new CpuInvestigation.Builder("Server thread");for(int i=0;i<4;i++)builder.add(List.of(new CpuInvestigation.Frame(i==0?"a":"b",i==0?"test.Leaf":"test.Other","run","()V"),new CpuInvestigation.Frame("a","test.Parent","tick","()V")));var data=builder.finish();String json=Profiler.JSON.toJson(data);
        var call=new CallTreeScreen(task,data,null);call.width=width;call.height=height;var methodRow=method(CallTreeScreen.class,"methodRow",UiResults.class,CpuInvestigation.Method.class,long.class,long.class,int.class,int.class,boolean.class);
        var leaf=data.methods().stream().filter(m->m.type().equals("test.Leaf")).findFirst().orElseThrow();
        for(boolean running:List.of(false,true)){
            set(call,"running",running);out=new UiResults(call,100,running?0:64,false).samples(data.treeSamples()).nameOnlyIndent();methodRow.invoke(call,out,leaf,leaf.inclusive(),leaf.self(),leaf.id(),7,false);var nativeRow=row(call,out,0);
            check(nativeRow.measurementColor()==UiTheme.TEXT&&field(nativeRow,"trailing").equals("25.0%"),"Call hierarchy and running code keep their retained-sample percentages in neutral text");
            check(field(nativeRow,"secondary").toString().contains("1 direct | 1 in stack"),"Direct/in-stack sample diagnostics remain unchanged");
        }
        for(String percent:List.of("0.0%","1.0%","50.0%","100.0%","+100.0%","-50.0%","-%")){
            var percentage=new UiRow(0,0,200,24,"Test","",percent,UiTheme.TEXT,0,0,false,null).valueColor(TimingSeverity.color(10000));
            check(percentage.measurementColor()==UiTheme.TEXT,"Every percentage stays neutral even if a caller supplies a severity colour");
        }
        var comparison=new ComparisonScreen(null,ProfileComparison.between(new Report(91,100,100,10,true,false,List.of()),new Report(92,200,100,20,true,false,List.of())));
        check((field(comparison,"summaryPrefix").toString()+field(comparison,"summaryPercentage")).equals(field(comparison,"summary"))&&!field(comparison,"summaryPrefix").toString().contains("%")&&field(comparison,"summaryPercentage").toString().contains("%"),"Comparison header preserves its exact text while separating the neutral percentage from coloured MSPT changes");
        check(Profiler.JSON.toJson(data).equals(json),"Contribution colouring does not alter captured sample data");
    }
    private static void contrast(){
        for(var theme:UiTheme.Theme.values()){
            UiTheme.select(theme);var p=theme.palette();
            for(int i=0;i<=1000;i++){
                int color=PerformanceGradient.color(i/1000.0);for(int bg:new int[]{p.background(),p.row(),p.selected(),p.hover()})check(ratio(color,bg)>=(bg==p.selected()||bg==p.hover()?2.45:3),"Continuous performance palette remains legible on normal, selected and hovered surfaces in every theme");
                check(UiTheme.textColor(color,UiTheme.Theme.CHARCOAL_ICE)==color,"Theme rebinding cannot replace diagnostic gradient values with an accent");
            }
            check(UiTheme.changeColor(-100)==0xff69f0ae&&UiTheme.changeColor(100)==0xffffab40&&UiTheme.changeColor(0)==UiTheme.TEXT,"Comparison improvement/regression colouring remains independent of absolute timing");
        }
    }
    private static double luminance(int c){double value=0;for(int i=0;i<3;i++){double x=(c>>>(16-i*8)&255)/255.0;value+=(x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4))*(i==0?.2126:i==1?.7152:.0722);}return value;}
    private static double ratio(int a,int b){double x=luminance(a),y=luminance(b);return(Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
}
