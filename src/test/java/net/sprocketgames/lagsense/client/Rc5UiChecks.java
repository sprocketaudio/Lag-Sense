package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;

final class Rc5UiChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static void set(Object object,String name,Object value)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private static List<?> rows(ReportScreen screen)throws Exception{var m=ReportScreen.class.getDeclaredMethod("refreshData");m.setAccessible(true);m.invoke(screen);return (List<?>)field(screen,"visible");}
    private static Report.Entry target(String kind,String type,String dimension,int x,long nanos){return new Report.Entry(kind,type,dimension,x,64,-1,nanos,1);}
    static void run()throws Exception{
        var settings=LagSenseClient.settings;var mark=LagSenseClient.waypoint;
        try{
            LagSenseClient.settings=new OverlaySettings();
            var a=target("Block","test:machine","minecraft:overworld",-1,1000000);
            var b=target("Entity","minecraft:item","minecraft:overworld",-1,1000);
            var c=target("Entity","minecraft:item","minecraft:overworld",-17,10000);
            var d=target("Entity","minecraft:item","minecraft:overworld",-18,10000);
            var e=target("Entity","minecraft:item","minecraft:the_nether",-1,5000);
            var report=new Report(99,1000,10,2,true,false,List.of(a,b,c,d,e)).view(true,List.of());
            var screen=new ReportScreen(null,report,false);set(screen,"filter","Chunks");
            var all=rows(screen);check(((Report.Entry)field(all.getFirst(),"target")).x()>>4==-1,"Unfiltered Most time includes machine work");
            set(screen,"chunkCategory","Entity");var filtered=rows(screen);
            check(((Report.Entry)field(filtered.getFirst(),"target")).x()>>4==-2&&(double)field(filtered.getFirst(),"micros")==2,"Entity-filtered Most time excludes the expensive machine");
            set(screen,"byTargets",true);check((long)field(rows(screen).getFirst(),"count")==2,"Most targets uses measured instances instead of update calls");
            set(screen,"dimension","minecraft:the_nether");check(rows(screen).size()==1,"Dimension filtering keeps identical chunk coordinates separate");
            set(screen,"dimension","All");set(screen,"query","machine");check(rows(screen).isEmpty(),"Search and category filtering intersect");
            set(screen,"filter","Entity");set(screen,"query","");set(screen,"byTypeChunk",true);rows(screen);
            var groups=(Map<?,?>)field(screen,"typeChunks");var chunks=(List<?>)groups.values().iterator().next();check(chunks.size()==3,"Type then chunk preserves negative chunks and dimensions");
            long sum=0;for(var item:chunks)sum+=((ChunkResultGroups.Group)item).nanos();check(sum==26000,"Type/chunk children sum to the exact retained contribution");
            var many=new ArrayList<ProfileResults.Target>();for(int i=0;i<10000;i++)many.add(new ProfileResults.Target(i,target("Entity","minecraft:item","minecraft:overworld",-i-1,1)));
            var large=ChunkResultGroups.groups(many,false,true);check(large.stream().mapToInt(ChunkResultGroups.Group::count).sum()==10000,"Large grouping retains every cheap target");
            check(TargetDetails.name(d).equals("minecraft:item · -18, 64, -1"),"Individual rows display captured negative coordinates");
            var action=TargetActions.markAction(b,()->LagSenseClient.waypoint=b);LagSenseClient.waypoint=null;
            var button=new UiButton(0,0,UiResults.markWidth(),16,"Mark",action.run()).markState(b);button.refreshAvailability();int width=button.getWidth();
            action.run().run();button.refreshAvailability();check(TargetActions.marked(b)&&button.getMessage().getString().equals("Unmark")&&button.getWidth()==width,"Mark toggles in place with a fixed button width");
            check((boolean)field(button,"selected"),"Marked row uses the existing selected surface");
            LagSenseClient.waypoint=c;button.refreshAvailability();check(button.getMessage().getString().equals("Mark")&&!(boolean)field(button,"selected"),"A different mark updates the old visible button without rebuilding rows");
            LagSenseClient.waypoint=b;action.run().run();button.refreshAvailability();check(LagSenseClient.waypoint==null&&button.getMessage().getString().equals("Mark"),"Unmark clears the existing shared state");
            LagSenseClient.waypoint=b;TargetActions.unmark();button.refreshAvailability();check(button.getMessage().getString().equals("Mark"),"Footer Unmark stays synchronized");
            check(UiFonts.labelWidth("Unmark")<=width-10,"Unmark fits without shifting TP or timing on a toggle");
            for(int scale:List.of(2,3)){
                var inspect=new ChunkScreen(null,report,true,new MarkerIndex(report).chunk("minecraft:overworld",-1,-1));inspect.width=1920/scale;inspect.height=1080/scale;inspect.init();
                var widgets=ProfileLayoutChecks.widgets(inspect);var dropdowns=widgets.stream().filter(UiDropdown.class::isInstance).map(UiDropdown.class::cast).toList();
                check(dropdowns.stream().map(UiDropdown::label).toList().equals(List.of("Category","Group","Sort")),"Chunk Inspect has the labelled scoped toolbar");
                check(dropdowns.get(1).choices().size()==4&&dropdowns.get(2).choices().stream().noneMatch(choice->choice.id().equals("targets")),"Chunk Inspect keeps its existing grouping and sorting options");
                set(inspect,"category","Entity");set(inspect,"query","item");var refresh=ChunkScreen.class.getDeclaredMethod("refreshRows");refresh.setAccessible(true);refresh.invoke(inspect);
                var branches=(ChunkBranches)field(inspect,"branches");check(branches.summary("minecraft:overworld",-1,-1).equals("1 matching targets | 0.10 µs/t"),"Chunk Inspect's filtered summary excludes machine work and matches its searched category");
                check(report.entries().size()==5&&report.entries().getFirst().nanos()==1000000,"Filtering and grouping leave captured measurements unchanged");
            }
        }finally{LagSenseClient.settings=settings;LagSenseClient.waypoint=mark;}
        System.out.println("RC5 checks passed: coordinates, filtered chunk costs/counts, type/chunk grouping, large captures, inspect controls and synchronized fixed-width Mark toggles.");
    }
}
