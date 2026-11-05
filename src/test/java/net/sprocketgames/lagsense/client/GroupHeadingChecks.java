package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;

final class GroupHeadingChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static void run()throws Exception{
        check(GroupHeading.format("Minecraft",1245).equals("Minecraft (1,245)")&&GroupHeading.format("Empty",0).equals("Empty (0)"),"Shared headings format counts consistently");
        var oldSettings=LagSenseClient.settings;
        try{
            LagSenseClient.settings=new OverlaySettings();var entries=new ArrayList<Report.Entry>();
            for(int i=0;i<1245;i++)entries.add(new Report.Entry("Block","test:machine","minecraft:overworld",-1,64+i,-1,100,1));
            entries.add(new Report.Entry("Entity","minecraft:item","minecraft:overworld",-1,64,-1,100,1));
            var report=new Report(700,1000,20,1,true,false,entries).view(true,List.of());
            for(boolean mod:List.of(false,true))for(boolean type:List.of(false,true)){
                var screen=new ReportScreen(null,report,true);screen.width=960;screen.height=540;set(screen,"byMod",mod);set(screen,"byType",type);screen.init();
                var groups=(List<ProfileResults.Group>)field(screen,"visibleGroups");var mods=(List<ProfileResults.ModGroup>)field(screen,"visibleMods");
                check(!mod&&!type||(mod?mods.stream().mapToInt(ProfileResults.ModGroup::instances).sum()==1246:groups.stream().mapToInt(ProfileResults.Group::instances).sum()==1246),"Target aggregates count measured instances rather than child groups");
                var branches=new ChunkBranches(screen,report,true);branches.seed(new MarkerIndex(report).chunk("minecraft:overworld",-1,-1));branches.mode(type,false,mod);
                var states=(Map<?,?>)field(branches,"chunks");for(var chunk:states.values())for(var category:((Map<?,?>)field(chunk,"categories")).values())set(category,"open",true);
                var out=new UiResults(screen,140,76,true);branches.append(out,"minecraft:overworld",-1,-1,0);
                var list=new UiScrollList(screen.margin(),140,screen.contentWidth(),1000,new ListScroll());list.virtual(out.entries,null);
                for(var row:list.children())if(row instanceof UiRow widget){var name=widget.getMessage().getString();if(name.startsWith("+ ")||name.startsWith("− "))check(name.matches("(?s).*\\([0-9,]+\\).*"),"Every category/mod/type heading uses the shared count suffix: "+name);}
                branches.filter("Block","machine");out=new UiResults(screen,140,76,true);branches.append(out,"minecraft:overworld",-1,-1,0);
                check(branches.summary("minecraft:overworld",-1,-1).startsWith("1245 matching targets"),"Filtered measured counts remain unchanged by heading formatting");
            }
            var item=entries.getLast();check(!TargetDetails.name(item).contains("(1)")&&TargetDetails.name(item).endsWith("-1, 64, -1"),"Individuals retain coordinates without group counts");
        }finally{LagSenseClient.settings=oldSettings;}
        System.out.println("1.0 group heading checks passed: shared numeric formatting, four grouping combinations, mixed categories, filtered counts and unchanged individuals.");
    }
}
