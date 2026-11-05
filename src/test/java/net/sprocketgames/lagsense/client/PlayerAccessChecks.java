package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;

/** Actual native widgets plus expiring server report/marker grants, without a game window. */
final class PlayerAccessChecks {
    private static void check(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    private static void state(boolean admin,boolean clear,long id,Report.Entry marker){ClientAccess.accept(new ProfilerStatePayload(new ProfilerStatePayload.State(admin,clear,false,id,100,true,marker,20,"")));}
    private static ReportScreen emptyScreen(){var screen=new ReportScreen(null,null,false);screen.width=854;screen.height=480;screen.init();return screen;}
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
            var entity=new Report.Entry("Entity","minecraft:zombie","minecraft:overworld",-9,-27,-109,15000000,600,UUID.randomUUID());
            LagSenseClient.mark(new ComparisonTargets.Destination(full.id(),1000,0,entity,600,false));
            var moved=new EntityTracking(entity.entityId(),"minecraft:overworld",140.25,34,-40.5,.6,1.95);
            ClientAccess.accept(new ProfilerStatePayload(new ProfilerStatePayload.State(true,false,false,full.id(),100,false,entity,20,"",false,moved)));
            check(LagSenseClient.tracked().equals(moved)&&LagSenseClient.markedPose(1).x()==140.25&&LagSenseClient.waypoint.equals(entity)&&LagSenseClient.waypointTicks==600,"Server tracking guides a client-unloaded mob at its current position without replacing captured identity, coordinates or timing");
            var crossed=new EntityTracking(entity.entityId(),"minecraft:the_nether",-14,70,20,.6,1.95);
            ClientAccess.accept(new ProfilerStatePayload(new ProfilerStatePayload.State(true,false,false,full.id(),100,false,entity,20,"",false,crossed)));
            check(LagSenseClient.markedPose(1).dimension().equals("minecraft:the_nether"),"Marked guidance can follow a server-loaded UUID into a different dimension");
            ClientAccess.accept(new ProfilerStatePayload(new ProfilerStatePayload.State(true,false,false,full.id(),100,false,entity,20,"",false,null)));
            check(LagSenseClient.markedPose(1)==null&&MarkedDirection.guidance(entity,null,"minecraft:overworld",-9,-27,-109,0).equals("Not loaded or no longer exists"),"A server-unresolved mob loses its direction instead of silently pointing back at capture coordinates");
            for(String reason:List.of("unloaded","dead","removed","changed_dimension","not_found")){
                ClientAccess.accept(new ProfilerStatePayload(new ProfilerStatePayload.State(true,false,false,full.id(),100,false,entity,20,"",false,null,reason)));
                check(LagSenseClient.trackingStatus().equals(reason)&&MarkedDirection.guidance(entity,null,"minecraft:overworld",0,64,0,0,LagSenseClient.trackingStatus()).equals(EntityTracking.statusText(reason)),"HUD receives the server-observed unavailability reason without stale direction or location");
            }
            LagSenseClient.tracked(new EntityTracking(UUID.randomUUID(),"minecraft:overworld",0,70,0,.6,1.95));check(LagSenseClient.tracked()==null,"A delayed update for a different UUID cannot move the current mark");
            LagSenseClient.tracked(new EntityTracking(entity.entityId(),"minecraft:overworld",Double.NaN,70,0,.6,1.95));check(LagSenseClient.tracked()==null,"Invalid server location values cannot enter navigation calculations");
            LagSenseClient.tracked(moved);long trackingToken=ClientAccess.epoch();state(false,true,0,null);
            check(LagSenseClient.tracked()==null&&LagSenseClient.waypoint==null&&!ClientAccess.current(trackingToken),"Permission revocation clears remote tracking and its waypoint");
            state(true,false,full.id(),null);
            check(!ClientAccess.canReturn(),"No destination or legacy state leaves Return unavailable");
            var available=new ProfilerStatePayload.State(true,false,false,full.id(),100,false,null,20,"",true);
            ClientAccess.accept(new ProfilerStatePayload(available));check(ClientAccess.canReturn(),"A current operator grant can enable Return when the server has a destination");
            var legacy=Profiler.JSON.toJsonTree(available).getAsJsonObject();legacy.remove("canReturn");ClientAccess.accept(new ProfilerStatePayload(legacy.toString()));check(!ClientAccess.canReturn(),"Older server packets without availability default to disabled");

            ClientAccess.accept(new ProfilerStatePayload(new ProfilerStatePayload.State(true,false,false,full.id(),100,false,null,20,"",false)));check(!ClientAccess.canReturn(),"Using Return disables it as soon as the server consumes the destination");
            ClientAccess.accept(new ProfilerStatePayload(new ProfilerStatePayload.State(false,false,false,view.id(),100,false,null,20,"",true)));check(!ClientAccess.canReturn(),"Non-operators cannot enable Return even with a true availability field");
            ClientAccess.accept(new ProfilerStatePayload(available));ClientAccess.reset();check(!ClientAccess.canReturn(),"Disconnect clears Return availability");
            state(true,false,full.id(),null);
            var empty=emptyScreen();check(ProfileLayoutChecks.widgets(empty).stream().anyMatch(w->w.getMessage().getString().equals("Start Profile")),"Operator empty-state capture remains available");
            ClientAccess.beginReportLoad();empty=emptyScreen();
            check(empty.emptyMessage().equals("Loading profile…")&&ProfileLayoutChecks.widgets(empty).stream().noneMatch(w->w.getMessage().getString().equals("Start Profile")),"Loading a previous capture shows progress without offering a misleading new capture");
            ClientAccess.accept(new ProfilerStatePayload(available));
            check(ClientAccess.reportLoading(),"An authorization grant does not finish the report transfer before its data arrives");
            ClientAccess.received(full,new MarkerIndex(full));empty=emptyScreen();
            check(!ClientAccess.reportLoading()&&ClientAccess.reportError().isEmpty(),"Receiving the report completes loading");
            ClientAccess.beginReportLoad();ClientAccess.failReportLoad();empty=emptyScreen();
            check(empty.emptyMessage().equals("Could not load profile.")&&ProfileLayoutChecks.widgets(empty).stream().anyMatch(w->w.getMessage().getString().equals("Retry")),"Failed transfers show Retry instead of claiming there are no captures");
            ClientAccess.beginReportLoad();ClientAccess.accept(new ProfilerStatePayload(new ProfilerStatePayload.State(true,false,true,0,100,false,null,0,"")));empty=emptyScreen();
            check(!ClientAccess.reportLoading()&&empty.emptyMessage().equals("No profile captured yet")&&ProfileLayoutChecks.widgets(empty).stream().anyMatch(w->w.getMessage().getString().equals("Start Profile")),"Only the server's confirmed empty response enables the normal empty state");
            ClientAccess.beginReportLoad();ClientAccess.reset();check(!ClientAccess.reportLoading()&&ClientAccess.reportError().isEmpty(),"Disconnect clears pending loading and errors");
            ClientAccess.accept(new ProfilerStatePayload(available));LagSenseClient.storeReport(full,false,new MarkerIndex(full),true);var deadline=ClientAccess.class.getDeclaredField("deadline");deadline.setAccessible(true);deadline.setLong(null,System.nanoTime()-1);ClientAccess.tick();
            check(!ClientAccess.admin()&&!ClientAccess.canReturn()&&LagSenseClient.report==null&&!ClientAccess.allowed(full),"Expired server authorization disables Return and fails closed even without a revocation packet");
            check(ClientAccess.recovering(),"Lease expiry schedules background recovery without displaying unauthorized data");
            ClientAccess.accept(new ProfilerStatePayload(available));
            check(ClientAccess.recovering()&&LagSenseClient.report==null,"A renewed permission grant waits for fresh report data");
            LagSenseClient.acceptReport(full,false,false,new MarkerIndex(full),true);
            check(!ClientAccess.recovering()&&LagSenseClient.report==full,"A fresh authorized report restores visual data without opening a screen");
            LagSenseClient.mark(new ComparisonTargets.Destination(full.id(),full.capturedAt(),0,target,full.ticks(),false));
            boolean visible=VisualVisibility.visible();if(visible)VisualVisibility.toggle();
            try{
                deadline.setLong(null,System.nanoTime()-1);ClientAccess.tick();
                check(LagSenseClient.waypoint==null&&LagSenseClient.report==null,"Expiry clears the rendered mark and report while retaining only an opaque recovery reference");
                ClientAccess.accept(new ProfilerStatePayload(available));LagSenseClient.acceptReport(full,false,false,new MarkerIndex(full),true);
                check(LagSenseClient.waypoint.equals(target)&&LagSenseClient.waypointTicks==full.ticks()&&!VisualVisibility.visible(),"Recovery restores the validated mark and captured timing without changing the master visibility override");
                deadline.setLong(null,System.nanoTime()-1);ClientAccess.tick();ClientAccess.accept(new ProfilerStatePayload(available));TargetActions.unmark();LagSenseClient.acceptReport(full,false,false,new MarkerIndex(full),true);
                check(LagSenseClient.waypoint==null,"An explicit Unmark during recovery cannot be undone by a late report");
                deadline.setLong(null,System.nanoTime()-1);ClientAccess.tick();ClientAccess.accept(new ProfilerStatePayload(available));
                var newer=new Report(full.id()+1,2000,20,2,true,false,full.entries());LagSenseClient.acceptReport(newer,false,false,new MarkerIndex(newer),true);
                check(!ClientAccess.recovering()&&LagSenseClient.report==newer&&LagSenseClient.waypoint==null,"A newly delivered capture cancels stale recovery instead of requesting an older report again");
                deadline.setLong(null,System.nanoTime()-1);ClientAccess.tick();state(false,true,0,null);
                check(!ClientAccess.recovering()&&LagSenseClient.report==null&&LagSenseClient.waypoint==null,"Explicit permission revocation cancels recovery and cannot resurrect a report or mark");
                ClientAccess.reset();check(!ClientAccess.recovering(),"Disconnect cancels pending recovery");
            }finally{if(VisualVisibility.visible()!=visible)VisualVisibility.toggle();}
        }finally{for(var value:saved.entrySet())value.getKey().set(null,value.getValue());LagSenseClient.settings=settings;UiTheme.select(theme);}
        System.out.println("Unified player UI checks passed (three permitted tabs, retained History/Settings, read-only empty/history views, server marker grants, role changes, cache/copy/export revocation and lease expiry in all themes).");
    }
}
