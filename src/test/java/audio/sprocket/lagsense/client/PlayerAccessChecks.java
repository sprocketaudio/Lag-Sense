package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.util.*;

/** Actual native widgets plus expiring server report/marker grants, without a game window. */
final class PlayerAccessChecks {
    private static void check(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    private static void state(boolean admin,boolean clear,long id,Report.Entry marker){ClientAccess.accept(new ProfilerStatePayload(new ProfilerStatePayload.State(admin,clear,false,id,100,true,marker,20,"")));}
    static void run()throws Exception{
        var saved=new HashMap<java.lang.reflect.Field,Object>();for(var field:ClientAccess.class.getDeclaredFields())if(java.lang.reflect.Modifier.isStatic(field.getModifiers())){field.setAccessible(true);saved.put(field,field.get(null));}
        var settings=LagSenseClient.settings;var theme=UiTheme.current();
        try{
            var enabled=ClientAccess.class.getDeclaredField("enabled");enabled.setAccessible(true);enabled.setBoolean(null,true);
            var target=new Report.Entry("Block","fixture:machine","minecraft:overworld",2,80,2,40000,20);
            var full=new Report(44,1000,20,125,true,false,List.of(target),false,0,List.of(),8,new Report.Diagnostics("Private",1,2));var view=full.view(false,e->true);
            state(false,true,view.id(),null);ClientAccess.received(view,new MarkerIndex(view));LagSenseClient.storeReport(view,false,new MarkerIndex(view),true);
            check(ClientAccess.allowed(view)&&!ClientAccess.allowed(full),"A player cannot accept an unrestricted report with the same ID");
            check(!ClientAccess.allowed(new Report(45,1000,20,0,false,false,List.of(target))),"Old/wrong report contexts cannot bypass the grant");
            for(var palette:UiTheme.Theme.values())for(int width:List.of(640,854)){
                UiTheme.select(palette);var root=new ReportScreen(null,view,false);root.width=width;root.height=480;root.init();
                var labels=ProfileLayoutChecks.widgets(root).stream().map(w->w.getMessage().getString()).toList();
                check(labels.containsAll(List.of("Targets","Chunks","Map","History","Settings"))&&!labels.contains("CPU Activity")&&!labels.contains("Call Paths")&&!labels.contains("Event Timings")&&!labels.contains("Return")&&!labels.contains("TP"),"Scoped navigation and controls preserve the existing layout without operator actions");
                var empty=new ReportScreen(null,null,false);empty.width=width;empty.height=480;empty.init();
                check(ProfileLayoutChecks.widgets(empty).stream().noneMatch(w->w.getMessage().getString().equals("Start Profile")),"Read-only players never get an empty-state capture button");
                var summary=ScopedReports.summary(view);var history=new HistoryScreen(root,new ProfileArchive.State(ProfileArchive.Settings.defaults(),List.of(summary)),"");history.width=width;history.height=480;history.init();
                var controls=ProfileLayoutChecks.widgets(history);var names=controls.stream().map(w->w.getMessage().getString()).toList();
                check(names.contains("View")&&names.contains("Reload")&&!names.contains("Apply")&&!names.contains("Baseline")&&!names.contains("Clear baseline")&&controls.stream().noneMatch(net.minecraft.client.gui.components.EditBox.class::isInstance),"Player history permits viewing saved captures without editing server retention or comparisons");
            }
            state(false,false,view.id(),target);check(LagSenseClient.waypoint==target||LagSenseClient.waypoint.equals(target),"Only a server marker grant installs the scoped waypoint");
            long token=ClientAccess.epoch();state(false,true,0,null);
            check(LagSenseClient.report==null&&LagSenseClient.waypoint==null&&ClientAccess.scopedReport()==null&&!ClientAccess.allowed(view)&&!ClientAccess.current(token),"Revocation removes reports, markers and deferred copy/export authorization");
            state(true,false,full.id(),null);check(ClientAccess.admin()&&ClientAccess.allowed(full),"Operators retain full report authority");
            var empty=new ReportScreen(null,null,false);empty.width=854;empty.height=480;empty.init();check(ProfileLayoutChecks.widgets(empty).stream().anyMatch(w->w.getMessage().getString().equals("Start Profile")),"Operator empty-state capture remains available");
            LagSenseClient.storeReport(full,false,new MarkerIndex(full),true);var deadline=ClientAccess.class.getDeclaredField("deadline");deadline.setAccessible(true);deadline.setLong(null,System.nanoTime()-1);ClientAccess.tick();
            check(!ClientAccess.admin()&&LagSenseClient.report==null&&!ClientAccess.allowed(full),"Expired server authorization fails closed even without a revocation packet");
        }finally{for(var value:saved.entrySet())value.getKey().set(null,value.getValue());LagSenseClient.settings=settings;UiTheme.select(theme);}
        System.out.println("Unified player UI checks passed (three permitted tabs, retained History/Settings, read-only empty/history views, server marker grants, role changes, cache/copy/export revocation and lease expiry in all themes).");
    }
}
