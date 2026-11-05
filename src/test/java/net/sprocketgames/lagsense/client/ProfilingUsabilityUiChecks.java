package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;
import net.minecraft.client.gui.Font;

/** Native widgets and font metrics at the logical sizes of a 1080p window, without GUI control. */
final class ProfilingUsabilityUiChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Object field(Object instance,String name)throws Exception{var f=instance.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(instance);}
    private static void field(Object instance,String name,Object value)throws Exception{var f=instance.getClass().getDeclaredField(name);f.setAccessible(true);f.set(instance,value);}
    @SuppressWarnings("unchecked") static void run(Font font)throws Exception{
        var configField=net.neoforged.fml.loading.FMLPaths.class.getDeclaredField("absolutePath");configField.setAccessible(true);
        var oldConfig=configField.get(net.neoforged.fml.loading.FMLPaths.CONFIGDIR);
        var temporaryConfig=java.nio.file.Files.createTempDirectory("lagsense-ui-check-");configField.set(net.neoforged.fml.loading.FMLPaths.CONFIGDIR,temporaryConfig);
        var previous=LagSenseClient.report;boolean archived=LagSenseClient.archived;
        try{
            var entries=new ArrayList<Report.Entry>();
            for(int i=0;i<2000;i++)entries.add(new Report.Entry(switch(i%3){case 0->"Entity";case 1->"Block";default->"Random block";},"fixture:very_long_registry_identifier_for_a_measured_target","minecraft:overworld",i,64,0,i*1000L,1));
            LagSenseClient.report=new Report(1,1000,100,10,true,false,List.copyOf(entries));LagSenseClient.archived=false;
            var append=ReportScreen.class.getDeclaredMethod("appendResults",UiResults.class,boolean.class);append.setAccessible(true);
            for(int scale:List.of(2,3))for(String tab:List.of("All","Entity","Block","Ticks")){
                var screen=new ReportScreen();screen.width=1920/scale;screen.height=1080/scale;
                check(!(boolean)field(screen,"byType"),"By Instance remains the default");
                field(screen,"filter",tab);field(screen,"byType",true);
                var targets=ProfileResults.targets(LagSenseClient.report,new ProfileResults.Filter(tab,"All",false,"",0,0,0),false);
                var groups=ProfileResults.groups(targets,false);field(screen,"visibleGroups",groups);
                var collapsed=new UiResults(screen,144,76,true).diagnosticHints();append.invoke(screen,collapsed,true);
                check(collapsed.entries.size()==groups.size(),"Collapsed type grouping has one compact row per category/ID in every tab");
                var group=groups.getFirst();((Set<ProfileResults.Type>)field(screen,"expandedTypes")).add(group.type());
                var expanded=new UiResults(screen,144,76,true).diagnosticHints();append.invoke(screen,expanded,true);
                check(expanded.entries.size()==groups.size()+group.targets().size(),"Expansion retains every measured instance in the same continuous list");
                var list=new UiScrollList(screen.margin(),144,screen.contentWidth(),24,new ListScroll());list.virtual(expanded.entries,null);
                var groupRow=(UiRow)list.children().getFirst();check(groupRow.getHeight()==22&&groupRow.columns().nameWidth()>0,"Group row preserves compact height and readable name space");
                list.restore(24);var row=(UiRow)list.children().getFirst();
                check(list.children().size()==3&&row.getHeight()==22,"Expanded targets retain Mark and TP without changing row height");
                var mark=(UiButton)list.children().get(1);var tp=(UiButton)list.children().get(2);
                check(mark.getMessage().getString().equals("Mark")&&tp.getMessage().getString().equals("TP"),"Expanded targets retain original action buttons");
                check(row.columns().nameX()>groupRow.columns().nameX()&&row.columns().valueRight()==groupRow.columns().valueRight(),"Only the name indents; timings retain their aligned column");
                check(row.columns().nameX()+row.columns().nameWidth()<mark.getX()&&tp.getX()+tp.getWidth()<=screen.width-screen.margin(),"Long IDs cannot overlap measurements or compact actions at scales 2 and 3");
                check(!row.isMouseOver(mark.getX()+1,mark.getY()+1)&&list.getChildAt(mark.getX()+1,mark.getY()+1).orElseThrow()==mark,"Mark retains an independent clickable hitbox after expansion/scrolling");
                var hints=UiHints.class.getDeclaredField("widgets");hints.setAccessible(true);var hint=((Map<?,?>)hints.get(null)).get(row);var text=hint.getClass().getDeclaredField("text");text.setAccessible(true);String help=(String)text.get(hint);
                check(help.equals(TargetDetails.hint(group.targets().getFirst().entry(),LagSenseClient.report.ticks()))&&help.lines().count()==4,"Individual tooltips contain exactly four diagnostic lines, without automatic prefixes or explanations");
                list.restore(Integer.MAX_VALUE);check(list.visible(expanded.entries.getLast().key()),"The final nested result remains reachable through continuous scrolling");
                ((Set<ProfileResults.Type>)field(screen,"expandedTypes")).clear();var closed=new UiResults(screen,144,76,true).diagnosticHints();append.invoke(screen,closed,true);
                check(closed.entries.size()==collapsed.entries.size(),"Collapsing preserves all group rows");
            }
            var tasks=new TaskScreen(null,LagSenseClient.report,false);
            check((boolean)field(tasks,"callbackView")&&(boolean)field(tasks,"byMod")&&(boolean)field(tasks,"cpuByMod"),"Tasks defaults to Event timings / By mod with CPU activity also defaulting to By mod");
            tasks.width=640;tasks.height=360;tasks.init();
            check(ProfileLayoutChecks.dropdown(tasks,"Group").value().equals("mod"),"Events retains its By mod default in the Group dropdown");
            ChunkGroupingUiChecks.run();
            Rc5UiChecks.run();
            TooltipCleanupChecks.run();
            MapInteractionChecks.run();
            HistoryComparisonUiChecks.run();
            SelectedCopyUiChecks.run();
        }finally{LagSenseClient.report=previous;LagSenseClient.archived=archived;configField.set(net.neoforged.fml.loading.FMLPaths.CONFIGDIR,oldConfig);java.nio.file.Files.delete(temporaryConfig);}
        System.out.println("Profiling UI checks passed at GUI scales 2/3 (native grouped rows, nesting/scrolling, aligned timing/action columns, tooltips and Tasks defaults).");
    }
}
