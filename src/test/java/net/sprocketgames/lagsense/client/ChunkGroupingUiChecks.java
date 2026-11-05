package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;

/** Exercise the actual chunk/category/type row factories, rather than a global grouping surrogate. */
final class ChunkGroupingUiChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static UiResults rows(ReportScreen screen)throws Exception{
        var append=ReportScreen.class.getDeclaredMethod("appendResults",UiResults.class,boolean.class);append.setAccessible(true);
        var out=new UiResults(screen,144,76,true).diagnosticHints();append.invoke(screen,out,true);return out;
    }
    private static Report fixture(){
        var entries=new ArrayList<Report.Entry>(List.of(
            new Report.Entry("Entity","minecraft:creeper","minecraft:overworld",-1,64,-1,6603000,101),
            new Report.Entry("Entity","minecraft:creeper","minecraft:overworld",-2,64,-1,1277000,11),
            new Report.Entry("Entity","minecraft:zombie","minecraft:overworld",-3,64,-1,1,1000),
            new Report.Entry("Block","minecraft:hopper","minecraft:overworld",-4,64,-1,8000000,1),
            new Report.Entry("Entity","minecraft:creeper","minecraft:overworld",-17,64,-1,20000,2),
            new Report.Entry("Entity","minecraft:creeper","minecraft:overworld",-18,64,-1,10000,2),
            new Report.Entry("Entity","minecraft:creeper","minecraft:the_nether",-1,64,-1,500000,3)));
        for(int i=0;i<500;i++)entries.add(new Report.Entry("Entity","minecraft:creeper","minecraft:overworld",-5,65+i,-1,1,1));
        return new Report(33,1000,101,10,true,false,List.copyOf(entries)).view(true,List.of());
    }
    @SuppressWarnings("unchecked") static void run()throws Exception{
        var previous=LagSenseClient.report;var markerIndex=LagSenseClient.markerIndex;boolean complete=LagSenseClient.completeReport;
        try{
            var report=fixture();LagSenseClient.report=report;LagSenseClient.markerIndex=new MarkerIndex(report);LagSenseClient.completeReport=true;
            for(int scale:List.of(2,3)){
                var screen=new ReportScreen();screen.width=1920/scale;screen.height=1080/scale;set(screen,"filter","Chunks");
                var rowType=Arrays.stream(ReportScreen.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Row")).findFirst().orElseThrow();
                var ctor=rowType.getDeclaredConstructor(Report.Entry.class,int.class,boolean.class,double.class,long.class);ctor.setAccessible(true);
                var rootRows=new ArrayList<Object>();for(int i=0;i<report.chunks().size();i++){var c=report.chunks().get(i);rootRows.add(ctor.newInstance(c.hotspot(),i,true,c.micros(report.ticks()),(long)c.targets()));}set(screen,"visible",rootRows);
                for(boolean types:List.of(false,true)){set(screen,"byType",types);check(rows(screen).entries.size()==report.chunks().size(),"Chunks stay the only top-level rows in both modes");}
                var branches=(ChunkBranches)field(screen,"contributors");for(var c:report.chunks())branches.start(c.dimension(),c.x(),c.z());
                var states=((Map<?,?>)field(branches,"chunks")).values();int categoryCount=0;
                for(var state:states){var categories=(Map<?,?>)field(state,"categories");categoryCount+=categories.size();for(var category:categories.values())set(category,"open",true);}
                set(screen,"byType",false);check(rows(screen).entries.size()==report.chunks().size()+categoryCount+report.entries().size(),"By Instance preserves Chunk → Category → Target, with every measured target");
                set(screen,"byType",true);var collapsed=rows(screen);int typeCount=0;
                for(var state:states){var data=(ChunkInvestigation)field(state,"data");long sum=0;
                    for(var category:((Map<?,?>)field(state,"categories")).values()){
                        var groups=(List<ProfileResults.Group>)field(category,"groups");typeCount+=groups.size();
                        for(var group:groups){sum+=group.nanos();check(group.instances()==group.targets().size(),"Type count uses measured instances, not update calls");
                            for(var t:group.targets())check(t.entry().dimension().equals(data.dimension())&&t.entry().x()>>4==data.x()&&t.entry().z()>>4==data.z(),"Types never combine different chunks or dimensions");
                        }
                    }check(sum==data.nanos(),"Type sums equal each chunk's measured cost exactly");
                }
                check(collapsed.entries.size()==report.chunks().size()+categoryCount+typeCount,"By Type inserts types beneath existing chunk categories");
                var firstState=states.iterator().next();var firstCategories=(Map<?,?>)field(firstState,"categories");var firstCategory=firstCategories.values().iterator().next();
                var savedGroups=field(firstCategory,"groups");rows(screen);check(field(firstCategory,"groups")==savedGroups,"Unchanged expansion rebuilds reuse cached type aggregates");
                for(var state:states)for(var category:((Map<?,?>)field(state,"categories")).values())for(var group:(List<ProfileResults.Group>)field(category,"groups"))((Set<String>)field(category,"expandedTypes")).add(group.type().registry());
                var expanded=rows(screen);check(expanded.entries.size()==report.chunks().size()+categoryCount+typeCount+report.entries().size(),"Expanded hierarchy includes each target exactly once");
                var list=new UiScrollList(screen.margin(),144,screen.contentWidth(),24,new ListScroll());list.virtual(expanded.entries,null);var root=(UiRow)list.children().getFirst();
                check(root.getMessage().getString().contains("Chunk -1, -1 (")&&!root.getMessage().getString().contains(" · "),"Chunk roots retain coordinates and measured target count");
                int leaf=-1;for(int i=0;i<expanded.entries.size();i++)if(expanded.entries.get(i).key() instanceof List<?> key&&key.getLast() instanceof ProfileResults.Target){leaf=i;break;}
                check(leaf>=0,"Nested target row exists");var key=(List<?>)expanded.entries.get(leaf).key();var measured=(ProfileResults.Target)key.getLast();
                check(report.entries().get(measured.index())==measured.entry(),"Nested target retains its original teleport index");
                list.restore(leaf*24);var target=(UiRow)list.children().getFirst();var mark=(UiButton)list.children().get(1);var tp=(UiButton)list.children().get(2);
                check(target.getX()==screen.margin()+36&&target.columns().valueRight()==root.columns().valueRight(),"Chunk/category/type/target indentation preserves timing alignment at scales 2/3");
                check(mark.getMessage().getString().equals("Mark")&&tp.getMessage().getString().equals("TP")&&list.getChildAt(mark.getX()+1,mark.getY()+1).orElseThrow()==mark,"Nested actions remain visible and independently clickable");
                check(TooltipCleanupChecks.hint(target).equals(TargetDetails.hint(measured.entry(),report.ticks())),"Chunk targets use the same concise diagnostic tooltip");
                list.restore(Integer.MAX_VALUE);check(list.visible(expanded.entries.getLast().key()),"Continuous scrolling reaches the final nested contribution");
                for(boolean updates:List.of(false,true)){
                    set(screen,"byCount",updates);var sorted=rows(screen);Object firstSummary=sorted.entries.stream().map(UiScrollList.Entry::key).filter(k->k.getClass().getSimpleName().equals("More")).findFirst().orElseThrow();
                    check(field(firstSummary,"category").equals(updates?"Entity":"Block"),"Category sorting follows aggregate time or updates");
                    for(var state:states)for(var category:((Map<?,?>)field(state,"categories")).values()){
                        var groups=(List<ProfileResults.Group>)field(category,"groups");
                        for(int i=1;i<groups.size();i++)check(updates?groups.get(i-1).updates()>=groups.get(i).updates():groups.get(i-1).nanos()>=groups.get(i).nanos(),"Type sorting follows selected criterion inside each category");
                        for(var group:groups)for(int i=1;i<group.targets().size();i++){var a=group.targets().get(i-1).entry();var b=group.targets().get(i).entry();check(updates?a.calls()>=b.calls():a.nanos()>=b.nanos(),"Individual targets follow selected sorting inside each type");}
                    }
                }
                var firstTypes=(Set<String>)field(firstCategory,"expandedTypes");firstTypes.clear();var partial=rows(screen);check(partial.entries.size()<expanded.entries.size(),"Collapsing one type/category subtree removes only its children");
                check(states.stream().skip(1).anyMatch(state->{try{return ((Map<?,?>)field(state,"categories")).values().stream().anyMatch(c->{try{return !((Set<?>)field(c,"expandedTypes")).isEmpty();}catch(Exception e){throw new RuntimeException(e);}});}catch(Exception e){throw new RuntimeException(e);}}),"Other chunks retain their own independent type expansions");
                set(firstState,"open",false);check(rows(screen).entries.size()<partial.entries.size(),"Chunk collapse retains the root and hides its entire subtree");
                check((boolean)field(firstCategory,"open"),"Parent collapse does not erase independent category expansion");
                check(new ChunkBranches(screen,report,true).actions(measured.entry(),measured.index()).length==2,"Saved captures retain Mark and permission-checked TP");
            }
        }finally{LagSenseClient.report=previous;LagSenseClient.markerIndex=markerIndex;LagSenseClient.completeReport=complete;}
        System.out.println("Chunk grouping UI checks passed (per-chunk types, categories, totals, independent nesting, sorting, virtual scrolling and original actions).");
    }
}
