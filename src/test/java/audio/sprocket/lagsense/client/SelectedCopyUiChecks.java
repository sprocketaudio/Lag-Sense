package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.util.*;
import java.util.function.Supplier;

final class SelectedCopyUiChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static UiButton button(LagScreen screen,String name){return screen.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast).filter(b->b.getMessage().getString().equals(name)).findFirst().orElseThrow();}
    @SuppressWarnings("unchecked") private static String copied(ResultSelection selection)throws Exception{return CopyTree.format(((Supplier<CopyTree.Node>)field(selection,"source")).get(),"");}
    @SuppressWarnings("unchecked") static void run()throws Exception{
        var old=LagSenseClient.report;var oldMarkers=LagSenseClient.markerIndex;boolean oldComplete=LagSenseClient.completeReport;
        var report=new Report(42,1000,100,10,true,false,List.of(new Report.Entry("Entity","a:mob","minecraft:overworld",-1,64,-1,1000000,10),new Report.Entry("Block","b:machine","minecraft:overworld",-2,64,-1,2000000,1))).view(true,List.of());
        try{
            LagSenseClient.report=report;LagSenseClient.markerIndex=new MarkerIndex(report);LagSenseClient.completeReport=true;
            for(int scale:List.of(2,3)){
                var screen=new CallTreeScreen(null,CpuInvestigation.empty("disabled"),null);screen.width=1920/scale;screen.height=1080/scale;
                var selection=screen.selection;selection.begin("capture1");var copy=new UiButton(10,screen.height-40,44,20,"Copy",()->{});selection.bind(copy);check(!copy.active,"Copy starts disabled");
                boolean[] open={false};var tree=new CopyTree.Node("parent",()->"Parent",()->List.of(CopyTree.leaf("one","One"),CopyTree.leaf("two","Two")));
                var rows=new UiResults(screen,120,76,false).selectable(selection);rows.copy(()->tree).row("parent","Parent","","1.0 µs/t",UiTheme.TEXT,0,false,"",()->open[0]=!open[0]);
                rows.lazy("leaf",row->row.copy(()->CopyTree.leaf("leaf","Only leaf")).row("leaf","Leaf","","0.0 µs/t",UiTheme.TEXT,12,false,"",null,new UiResults.Action("Mark",()->{}),new UiResults.Action("TP",()->{})));
                var scroll=new ListScroll();var list=new UiScrollList(screen.margin(),120,screen.contentWidth(),24,scroll);list.virtual(rows.entries,null);var parent=(UiRow)list.children().getFirst();
                parent.onPress(null);check(open[0]&&copy.active&&selection.is("parent"),"Parent click both selects and expands");parent.onPress(null);check(!open[0]&&selection.is("parent")&&copied(selection).equals("Parent\n  One\n  Two\n"),"Collapsing a selected parent keeps selection and copies all collapsed children");
                list.restore(24);var leaf=(UiRow)list.children().getFirst();check(leaf.active,"A leaf with no expansion is selectable");leaf.onPress(null);check(selection.is("leaf")&&!selection.is("parent")&&copied(selection).equals("Only leaf\n"),"Lazy leaf click replaces the single selected subtree");
                check(list.children().size()==3&&list.children().get(1) instanceof UiButton&&list.children().get(2) instanceof UiButton,"Selection preserves Mark/TP controls");int offset=scroll.offset();copied(selection);check(scroll.offset()==offset&&!open[0],"Copy collection leaves scrolling and expansion untouched");
                long token=selection.generation();selection.waiting(true);check(!copy.active,"Copy is disabled while preparing its subtree");selection.waiting(false);selection.begin("capture1");check(copy.active&&selection.current(token),"A rebuild in the same context preserves selection");selection.begin("filtered");check(!copy.active&&!selection.current(token),"Changing a filter clears stale selection and pending jobs");
                selection.select("parent",()->tree);screen.removed();check(!copy.active&&selection.key()==null,"Changing screen clears selection");
                var call=new CallTreeScreen(null,CpuInvestigation.empty("disabled"),null);call.width=screen.width;call.height=screen.height;call.init();check(!button(call,"Copy").active,"Call Paths cannot copy before row selection");
                var tasks=new TaskScreen(null,report,false);tasks.width=screen.width;tasks.height=screen.height;tasks.init();check(!button(tasks,"Copy").active,"Tasks cannot copy an unrelated report by default");
                var compare=new ComparisonScreen(null,ComparisonDetailChecks.fixture());compare.width=screen.width;compare.height=screen.height;compare.init();check(!button(compare,"Copy").active,"Comparisons require a selected subtree");
                var data=new MapTargets(report,null).chunk("minecraft:overworld",-1,-1);var chunk=new ChunkScreen(null,report,false,data);chunk.width=screen.width;chunk.height=screen.height;chunk.init();check(!button(chunk,"Copy").active,"Chunk detail requires a selected category or target");
                var root=new ReportScreen();root.width=screen.width;root.height=screen.height;set(root,"filter","Chunks");var branches=(ChunkBranches)field(root,"contributors");branches.seed(data);
                for(boolean mods:List.of(false,true))for(boolean types:List.of(false,true)){
                    branches.start(data.dimension(),data.x(),data.z());branches.mode(types,false,mods);for(var state:((Map<?,?>)field(branches,"chunks")).values())for(var category:((Map<?,?>)field(state,"categories")).values())set(category,"open",true);
                    var out=new UiResults(root,120,76,false).selectable(root.selection);branches.append(out,"minecraft:overworld",-1,-1,12);
                    if(mods){for(var state:((Map<?,?>)field(branches,"chunks")).values())for(var category:((Map<?,?>)field(state,"categories")).values())for(var mod:(List<ProfileResults.ModGroup>)field(category,"mods"))((Set<String>)field(category,"expandedMods")).add(mod.owner());}
                    out=new UiResults(root,120,76,false).selectable(root.selection);branches.append(out,"minecraft:overworld",-1,-1,12);
                    var text=CopyTree.format(branches.copy(report.chunks().getFirst()),"");check(text.contains("a:mob")&&text.contains("b:machine")&&text.indexOf("Ticking blocks")<text.indexOf("Entities"),"Each chunk grouping copies complete cost-sorted descendants without expansion");
                    check(branches.hasExpanded(false),"Expanded category enables Collapse All");branches.collapseAll(false);check(!branches.hasExpanded(true),"Collapse All closes all chunk/category/mod/type branches");
                }
            }
            controls(report);
        }finally{LagSenseClient.report=old;LagSenseClient.markerIndex=oldMarkers;LagSenseClient.completeReport=oldComplete;}
        System.out.println("Selected-copy UI checks passed at scales 2/3 (parent/leaf clicks, single selection, lazy rows, disabled/stale Copy, complete chunk subtrees and shared controls).");
    }
    private static void controls(Report report)throws Exception{ProfileLayoutChecks.run(report);}
}
