package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.util.*;
import java.nio.file.*;
import net.neoforged.fml.loading.FMLPaths;

/** Exercises the same storage and F9 screen factory as payload/key handlers, without a game window. */
final class ProfileStateChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static void run()throws Exception{
        var saved=new HashMap<java.lang.reflect.Field,Object>();
        for(String name:List.of("report","archived","completeReport","selected","waypoint","waypointTicks","waypointSource","waypointIndex","markerIndex","settings")){var f=LagSenseClient.class.getDeclaredField(name);f.setAccessible(true);saved.put(f,f.get(null));}
        var oldTheme=UiTheme.current();var config=FMLPaths.class.getDeclaredField("absolutePath");config.setAccessible(true);var oldConfig=config.get(FMLPaths.CONFIGDIR);var directory=Files.createTempDirectory("lagsense-profile-state-");config.set(FMLPaths.CONFIGDIR,directory);
        try{
            LagSenseClient.settings=new OverlaySettings();LagSenseClient.settings.enabled=true;
            LagSenseClient.report=null;check(field(LagSenseClient.latestReportScreen(),"report")==null,"The profiler opens without inventing a capture when no report exists");
            var a=new Report(9,100,100,2,true,false,List.of());var b=new Report(8,90,200,3,true,false,List.of());var c=new Report(7,80,150,4,true,false,List.of());
            var marker=new MarkerIndex(a);LagSenseClient.storeReport(a,false,marker,true);
            var normal=LagSenseClient.latestReportScreen();var history=new HistoryScreen(normal,new ProfileArchive.State(ProfileArchive.Settings.defaults(),List.of()),"");
            for(var archive:List.of(b,c,b)){
                LagSenseClient.storeReport(archive,true,new MarkerIndex(archive),false);var screen=new ReportScreen(history,archive,true);
                check(field(screen,"report")==archive&&(boolean)field(screen,"archived"),"History opens the requested archive in its own screen context");
                check(field(screen,"parent")==history&&field(history,"parent")==normal,"Saved Close/Back still returns through History to the original profile");
                check(LagSenseClient.report==a&&LagSenseClient.markerIndex==marker&&!LagSenseClient.archived&&LagSenseClient.completeReport,"Browsing multiple archives never replaces normal report, marker or completeness state");
                for(int repeat=0;repeat<3;repeat++){var reopened=LagSenseClient.latestReportScreen();check(field(reopened,"report")==a&&!(boolean)field(reopened,"archived")&&field(reopened,"parent")==null,"F9 always opens a fresh normal profile after direct close or returning through History");}
            }
            LagSenseClient.storeReport(c,false,new MarkerIndex(c),true);check(field(LagSenseClient.latestReportScreen(),"report")==c,"A new normal capture replaces the F9 report normally");
            LagSenseClient.report=null;LagSenseClient.storeReport(b,true,new MarkerIndex(b),true);check(field(LagSenseClient.latestReportScreen(),"report")==null,"An archive cannot replace the empty normal-profile state");
            titleColours();comparisonMarks(a);ShortcutChecks.run();PlayerAccessChecks.run();
        }finally{
            for(var entry:saved.entrySet())entry.getKey().set(null,entry.getValue());UiTheme.select(oldTheme);config.set(FMLPaths.CONFIGDIR,oldConfig);
            Files.deleteIfExists(directory.resolve("lagsense-client.json"));Files.deleteIfExists(directory.resolve("lagsense-client.json.tmp"));Files.delete(directory);
        }
        System.out.println("Profile state checks passed (History contexts, repeated F9, multiple archives, empty/latest captures, all-theme saved titles and comparison Mark source).");
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
