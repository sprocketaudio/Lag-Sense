package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;
import java.nio.file.*;
import net.neoforged.fml.loading.FMLPaths;

/** Exercises the same storage and F9 screen factory as payload/key handlers, without a game window. */
final class ProfileStateChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    static void run()throws Exception{
        var saved=new HashMap<java.lang.reflect.Field,Object>();
        for(String name:List.of("report","archived","completeReport","selected","waypoint","waypointTicks","waypointSource","waypointIndex","trackedEntity","trackingStatus","markerIndex","settings","markReference")){var f=LagSenseClient.class.getDeclaredField(name);f.setAccessible(true);saved.put(f,f.get(null));}
        var oldTheme=UiTheme.current();var config=FMLPaths.class.getDeclaredField("absolutePath");config.setAccessible(true);var oldConfig=config.get(FMLPaths.CONFIGDIR);var directory=Files.createTempDirectory("lagsense-profile-state-");config.set(FMLPaths.CONFIGDIR,directory);
        try{
            LagSenseClient.settings=new OverlaySettings();LagSenseClient.settings.enabled=true;
            LagSenseClient.report=null;check(field(LagSenseClient.latestReportScreen(),"report")==null,"The profiler opens without inventing a capture when no report exists");
            var a=new Report(9,100,100,2,true,false,List.of());var b=new Report(8,90,200,3,true,false,List.of());var c=new Report(7,80,150,4,true,false,List.of());
            var marker=new MarkerIndex(a);LagSenseClient.storeReport(a,false,marker,true);
            var marked=new Report.Entry("Block","minecraft:hopper","minecraft:overworld",2,64,3,1000,100);
            LagSenseClient.mark(new ComparisonTargets.Destination(a.id(),a.capturedAt(),0,marked,a.ticks(),false));
            LagSenseClient.storeReport(a,false,new MarkerIndex(a),true);
            check(LagSenseClient.waypoint==marked&&LagSenseClient.waypointTicks==a.ticks(),"Reopening the same capture before teleporting preserves its block mark and timing denominator");
            var entityMark=new Report.Entry("Entity","minecraft:zombie","minecraft:overworld",2,64,3,1000,100,UUID.randomUUID());
            LagSenseClient.mark(new ComparisonTargets.Destination(a.id(),a.capturedAt(),0,entityMark,a.ticks(),false));
            var location=new EntityTracking(entityMark.entityId(),"minecraft:overworld",8,64,3,.6,1.9);LagSenseClient.tracked(location);
            LagSenseClient.storeReport(a,false,new MarkerIndex(a),true);
            check(LagSenseClient.waypoint==entityMark&&LagSenseClient.tracked()==location,"Same-capture refresh retains entity identity and live tracking for Mark then TP");
            marker=LagSenseClient.markerIndex;
            var normal=LagSenseClient.latestReportScreen();var history=new HistoryScreen(normal,new ProfileArchive.State(ProfileArchive.Settings.defaults(),List.of()),"");
            for(var archive:List.of(b,c,b)){
                LagSenseClient.storeReport(archive,true,new MarkerIndex(archive),false);var screen=new ReportScreen(history,archive,true);
                check(field(screen,"report")==archive&&(boolean)field(screen,"archived"),"History opens the requested archive in its own screen context");
                check(field(screen,"parent")==history&&field(history,"parent")==normal,"Saved Close/Back still returns through History to the original profile");
                check(LagSenseClient.report==a&&LagSenseClient.markerIndex==marker&&!LagSenseClient.archived&&LagSenseClient.completeReport,"Browsing multiple archives never replaces normal report, marker or completeness state");
                for(int repeat=0;repeat<3;repeat++){var reopened=LagSenseClient.latestReportScreen();check(field(reopened,"report")==a&&!(boolean)field(reopened,"archived")&&field(reopened,"parent")==null,"F9 always opens a fresh normal profile after direct close or returning through History");}
            }
            LagSenseClient.storeReport(c,false,new MarkerIndex(c),true);check(field(LagSenseClient.latestReportScreen(),"report")==c&&LagSenseClient.waypoint==null,"A different capture replaces the F9 report and clears its previous mark normally");
            LagSenseClient.report=null;LagSenseClient.storeReport(b,true,new MarkerIndex(b),true);check(field(LagSenseClient.latestReportScreen(),"report")==null,"An archive cannot replace the empty normal-profile state");
            progressHeader();titleColours();comparisonMarks(a);ShortcutChecks.run();PlayerAccessChecks.run();resultPreferences();
        }finally{
            for(var entry:saved.entrySet())entry.getKey().set(null,entry.getValue());UiTheme.select(oldTheme);config.set(FMLPaths.CONFIGDIR,oldConfig);
            Files.deleteIfExists(directory.resolve("lagsense-client.json"));Files.deleteIfExists(directory.resolve("lagsense-client.json.tmp"));Files.delete(directory);
        }
        System.out.println("Profile state checks passed (History contexts, repeated F9, multiple archives, empty/latest captures, all-theme saved titles and comparison Mark source).");
    }
    private static void resultPreferences()throws Exception{
        var settings=LagSenseClient.settings;var latest=LagSenseClient.report;
        try{
            LagSenseClient.settings=new OverlaySettings();
            var block=new Report.Entry("Block","minecraft:hopper","minecraft:the_nether",2,64,3,1000,20);
            var other=new Report.Entry("Entity","minecraft:pig","minecraft:overworld",4,65,3,2000,20);
            var capture=new Report(501,1000,20,2,true,false,List.of(block,other));LagSenseClient.report=capture;
            var screen=new ReportScreen(null,capture,false);
            set(screen,"filter","Block");set(screen,"dimension","minecraft:the_nether");set(screen,"byMod",true);set(screen,"byType",true);set(screen,"byCount",true);set(screen,"query","hopper");
            screen.removed();
            var expected=new ResultViewSettings("Block","minecraft:the_nether","mod-type","count","hopper");
            check(LagSenseClient.settings.results.equals(expected),"Closing for Mark saves the selected category, dimension, grouping, sort and search without retaining report data");
            var reopened=LagSenseClient.latestReportScreen();
            for(String name:List.of("filter","dimension","byMod","byType","byCount","query"))check(field(reopened,name).equals(field(screen,name)),"F9 restores result control "+name+" after Mark closes the screen");
            LagSenseClient.storeReport(capture,false,new MarkerIndex(capture),true);
            var delivered=LagSenseClient.latestReportScreen();check(field(delivered,"filter").equals("Block")&&(boolean)field(delivered,"byMod")&&(boolean)field(delivered,"byType"),"Fresh server report delivery does not reset the preferred controls");
            var loaded=OverlaySettings.load();check(loaded.results.equals(expected),"Result preferences survive reloading the local client settings file");
            LagSenseClient.settings=loaded;var saved=new ReportScreen(null,capture,true);check(field(saved,"query").equals("hopper")&&field(saved,"dimension").equals("minecraft:the_nether"),"Saved captures reuse display preferences while preserving their own report context");
            var different=new ReportScreen(null,new Report(502,2000,20,2,false,false,List.of(other)),false);
            check(field(different,"dimension").equals("All")&&(boolean)field(different,"byMod")&&field(different,"query").equals("hopper"),"Unavailable dimensions fall back to All without resetting other preferences or using an old report");
            var chunks=new ReportScreen(null,capture,false);set(chunks,"filter","Chunks");chunks.removed();
            var chunkReturn=LagSenseClient.latestReportScreen();check(field(chunkReturn,"filter").equals("Chunks")&&chunkReturn.targetCategory().equals("Block"),"Marking from Chunks retains that view and the previous Targets category");
            var legacy=Profiler.JSON.fromJson("{\"results\":null}",OverlaySettings.class);legacy.clamp();check(legacy.results.equals(ResultViewSettings.defaults()),"Legacy or null result settings retain the original defaults");
            check(new ResultViewSettings("invalid","",null,"invalid",null).equals(ResultViewSettings.defaults()),"Invalid saved control choices fall back safely");
        }finally{LagSenseClient.settings=settings;LagSenseClient.report=latest;}
    }
    private static void progressHeader(){
        var first=new Report(91,10000,80,1.84,true,false,List.of(),true,26,List.of(),19.5);
        LagSenseClient.report=first;var navigation=new ProfileNavigation(new ReportScreen(null,first,false),null,first,false);
        String initial=navigation.header(10000);check(initial.contains("1.84 MSPT")&&initial.contains("19.5 TPS")&&initial.contains("80 ticks")&&initial.endsWith("26s remaining"),"Recording header shows the current progress snapshot");
        check(navigation.header(10001)==initial,"Header formatting stays cached between updates");
        var next=new Report(91,10900,100,2.5,true,false,List.of(),true,25,List.of(),19.8);LagSenseClient.report=next;
        String updated=navigation.header(10900);check(updated.contains("2.50 MSPT")&&updated.contains("19.8 TPS")&&updated.contains("100 ticks")&&updated.endsWith("25s remaining"),"A new same-capture packet updates every header value even within the same second");
        check(navigation.header(12900).endsWith("23s remaining")&&navigation.header(999999).endsWith("0s remaining"),"Countdown advances between progress packets without becoming negative");
        var saved=new ProfileNavigation(new ReportScreen(null,first,true),null,first,true);check(saved.header(10900).contains("80 ticks"),"Saved profile headers never follow live capture packets");
        LagSenseClient.report=new Report(92,10900,300,9,true,false,List.of(),true,10);
        String newer=navigation.header(10900);check(newer.contains("100 ticks")&&newer.endsWith("New profile: 10s remaining"),"A new capture announces its countdown without relabelling the older displayed measurements");
        check(navigation.header(12900).endsWith("New profile: 8s remaining"),"A new capture counts down while the previous results stay open");
        check(navigation.header(12901)==navigation.header(12900),"New-capture countdown formatting remains cached within the same second");
        check(!saved.header(12900).contains("New profile"),"An explicitly opened archive stays tied to its saved capture");
        var previous=new Report(90,1000,601,1.36,true,false,List.of(),false,0,List.of(),20);
        var previousView=new ProfileNavigation(new ReportScreen(null,previous,false),null,previous,false);
        check(previousView.header(13000).startsWith("1.36 MSPT | 20.0 TPS | 601 ticks")&&previousView.header(14000).endsWith("New profile: 9s remaining"),"A completed profile open before a new run replaces its old age with the new countdown");
        LagSenseClient.report=new Report(92,14000,320,8,true,false,List.of(),true,7);
        check(previousView.header(14001).endsWith("New profile: 7s remaining"),"Incoming new-capture progress corrects the countdown immediately");
        LagSenseClient.report=new Report(92,15000,400,7,true,false,List.of(),false,0);
        check(previousView.header(16000).endsWith("15s ago")&&!previousView.header(16000).contains("New profile"),"Completion removes the active countdown when the older results remain displayed");
        var complete=new Report(91,15000,600,2.7,true,false,List.of(),false,0,List.of(),20);LagSenseClient.report=complete;
        check(navigation.header(16000).contains("600 ticks")&&navigation.header(16000).endsWith("1s ago"),"Completion replaces recording status with capture age");
        var empty=new Report(93,10000,0,0,true,false,List.of(),true,30);LagSenseClient.report=empty;
        check(new ProfileNavigation(null,null,empty,false).header(10000).contains("TPS unavailable"),"Unmeasured TPS is not shown as zero TPS");
        check(new ProfileNavigation(null,null,empty,false).header(999999).endsWith("30s remaining"),"Server/client clock differences cannot consume the initial countdown");
        var privateView=new Report(91,10000,80,0,false,false,List.of(),true,26);var privateNavigation=new ProfileNavigation(null,null,privateView,false);LagSenseClient.report=complete;
        check(privateNavigation.header(10000).startsWith("80 ticks | Permitted areas")&&!privateNavigation.header(10000).contains("MSPT"),"Player-scoped headers cannot adopt administrator measurements");
    }
    private static void titleColours(){
        int[] colours={0xffe9b86c,0xff6ddbc4,0xff91d7ef};int i=0;
        for(var theme:UiTheme.Theme.values()){
            UiTheme.select(theme);check(UiTheme.titleColor("SAVED PROFILE")==colours[i++],"Saved title responds immediately to the current theme");
            check(UiTheme.ICE==theme.palette().accent()&&UiTheme.titleColor("SERVER PROFILE")==UiTheme.ICE&&UiTheme.titleColor("BASE HEALTH")==UiTheme.ICE,"Prefix and normal titles preserve the existing accent");
            check(TimingSeverity.color(1000)==LagSenseClient.color(1000),"Title accents do not modify timing colours");
        }
    }
    private static void comparisonMarks(Report normal)throws Exception{
        var index=new ComparisonIndex(ComparisonDetailChecks.fixture());var n=ComparisonDetailChecks.leaves(index.all.types()).stream().filter(t->t.key().kind().equals("Block")).findFirst().orElseThrow();var d=index.targets.destination(n);
        LagSenseClient.report=normal;LagSenseClient.markerIndex=new MarkerIndex(normal);
        var actions=TargetActions.actions(new ComparisonScreen(null,index),d);check(actions[0].enabled().getAsBoolean()&&!actions[1].enabled().getAsBoolean(),"Known historical positions support Mark while disconnected TP is disabled");
        actions[0].run().run();check(LagSenseClient.report==normal&&LagSenseClient.waypoint==d.target()&&LagSenseClient.waypointTicks==d.ticks(),"Comparison Mark preserves the latest report and its own captured tick denominator");
        check(LagSenseClient.markerReport()==normal,"Mark does not overwrite normal HUD data");LagSenseClient.report=null;
        check(LagSenseClient.markerReport().id()==d.capture()&&LagSenseClient.markerReport().ticks()==d.ticks()&&LagSenseClient.markerData()!=null,"Mark also works without a normal report using only its actual archive context");
        var unavailable=TargetActions.actions(new ComparisonScreen(null,index),null);check(Arrays.stream(unavailable).noneMatch(a->a.enabled().getAsBoolean()),"Missing identity disables both historical actions");
        boolean[] enabled={true};int[] pressed={0};var guard=new UiButton(0,0,40,20,"TP",()->pressed[0]++).enabledWhen(()->enabled[0]);enabled[0]=false;guard.onPress(null);check(pressed[0]==0&&!guard.active,"Permission/availability loss blocks an already mounted action");
    }
}
