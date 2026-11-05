package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Honest-client caches expire unless the server renews their current permissions. */
public final class ClientAccess {
    private static volatile boolean enabled;
    private static volatile ProfilerStatePayload.State state;
    private static volatile long deadline,epoch;
    private static Report scoped;
    private static MarkerIndex scopedMarkers;
    private static long lastRequest;
    private static boolean openQueued;
    private static boolean reportLoading;
    private static long reportProgress;
    private static String reportError="";
    private static long recoveryReport,recoveryAttempt;
    private static int recoveryAttempts;
    private static LagSenseClient.MarkReference recoveryMark;
    private static boolean recoveryMarkCleared;
    static void clearRecoveryMark(){if(recovering())recoveryMarkCleared=true;}
    static boolean recovering(){return recoveryReport!=0;}
    private static void clearRecovery(){recoveryReport=0;recoveryAttempt=0;recoveryAttempts=0;recoveryMark=null;recoveryMarkCleared=false;}
    static void enable(){enabled=true;reset();}
    static void reset(){clearRecovery();state=null;deadline=0;scoped=null;scopedMarkers=null;lastRequest=0;openQueued=false;completeReportLoad();epoch++;}
    static void beginReportLoad(){reportLoading=true;reportError="";reportProgress=System.nanoTime();}
    static void completeReportLoad(){reportLoading=false;reportError="";reportProgress=0;}
    static void failReportLoad(){if(reportLoading){reportLoading=false;reportError="Could not load profile.";}}
    static void reportProgress(){if(reportLoading)reportProgress=System.nanoTime();}
    static boolean reportLoading(){return reportLoading;}
    static String reportError(){return reportError;}
    static boolean valid(){return !enabled||state!=null&&System.nanoTime()<deadline;}
    static boolean canReturn(){return valid()&&state!=null&&state.admin()&&state.canReturn();}
    public static boolean admin(){return !enabled||valid()&&state.admin();}
    static boolean allowed(Report report){return !enabled||valid()&&report!=null&&(admin()||!report.admin()&&state.report()==report.id());}
    public static long epoch(){return epoch;}
    public static boolean current(long token){return token==epoch&&(!enabled||state==null||valid());}
    static boolean request(String action){return request(new ProfilerRequest(action));}
    static boolean request(ProfilerRequest request){
        var mc=Minecraft.getInstance();if(mc==null||mc.player==null||mc.getConnection()==null||!NetworkRegistry.hasChannel(mc.getConnection(),ProfilerRequest.TYPE.id()))return false;
        if(request.action().equals("open")){clearRecovery();beginReportLoad();}
        long now=System.nanoTime();if(request.action().equals("open")&&now-lastRequest<350_000_000L){openQueued=true;return true;}
        ClientPacketDistributor.sendToServer(request);lastRequest=now;if(request.action().equals("open"))openQueued=false;return true;
    }
    static void open(){
        var mc=Minecraft.getInstance();if(mc==null)return;
        request("open");mc.setScreen(allowed(LagSenseClient.report)?LagSenseClient.latestReportScreen():new ReportScreen(null,null,false));
    }
    static void accept(ProfilerStatePayload payload){
        final ProfilerStatePayload.State value;try{value=Profiler.JSON.fromJson(payload.json(),ProfilerStatePayload.State.class);}catch(RuntimeException error){failReportLoad();state=null;deadline=0;invalidate(true);return;}
        if(value==null||value.leaseTicks()<1||value.leaseTicks()>100){failReportLoad();state=null;deadline=0;invalidate(true);return;}
        boolean before=admin();long previous=state==null?0:state.report();enabled=true;state=value;deadline=System.nanoTime()+value.leaseTicks()*50_000_000L;
        if(value.open()&&value.report()==0)completeReportLoad();
        else if(reportLoading&&value.message()!=null&&!value.message().isBlank())failReportLoad();
        if(value.clear()||before&&!value.admin())invalidate(!value.open());
        else if(!value.admin()&&previous!=value.report()&&recoveryReport!=value.report())invalidate(false);
        var mc=Minecraft.getInstance();
        if(value.markerChanged()&&!value.clear()){
            LagSenseClient.serverMark(value);
        }
        LagSenseClient.tracked(value.clear()?null:value.trackedEntity());
        if(!value.clear())LagSenseClient.trackingStatus(value);
        if(mc!=null){if(value.open())mc.setScreen(LagSenseClient.latestReportScreen());else if(before!=admin()&&mc.screen instanceof LagScreen screen)screen.rebuildResults();}
        if(value.message()!=null&&!value.message().isBlank())ShortcutBindings.feedback(value.message());
    }
    static void received(Report report,MarkerIndex markers){completeReportLoad();if(!report.admin()){scoped=report;scopedMarkers=markers;}}
    static void recovered(Report report){if(recoveryReport==0)return;if(recoveryReport==report.id()&&allowed(report)&&!recoveryMarkCleared)LagSenseClient.restoreMark(report,recoveryMark,state);clearRecovery();}
    static MarkerIndex markers(Report report){return allowed(report)&&scoped!=null&&scoped.id()==report.id()?scopedMarkers:null;}
    static Report scopedReport(){return valid()?scoped:null;}
    static MarkerIndex scopedIndex(){return valid()?scopedMarkers:null;}
    static void tick(){
        if(reportLoading&&System.nanoTime()-reportProgress>60_000_000_000L)failReportLoad();
        if(openQueued&&System.nanoTime()-lastRequest>=350_000_000L)request("open");
        if(enabled&&state!=null&&System.nanoTime()>=deadline){
            long capture=LagSenseClient.report==null?0:LagSenseClient.report.id();var mark=LagSenseClient.markReference();
            failReportLoad();state=null;invalidate(true);recoveryReport=capture;recoveryMark=mark;return;
        }
        if(recoveryReport!=0&&valid()&&recoveryAttempts<3&&(recoveryAttempts==0||System.nanoTime()-recoveryAttempt>=30_000_000_000L)){
            if(request(new ProfilerRequest("refresh",recoveryReport,-1))){recoveryAttempt=System.nanoTime();recoveryAttempts++;}
        }
        var mc=Minecraft.getInstance();if(enabled&&mc!=null&&mc.screen instanceof LagScreen screen&&screen.profileNavigation!=null&&!screen.profileNavigation.permitted()){mc.setScreen(new ReportScreen(null,null,false));request("open");}
    }
    private static void invalidate(boolean navigate){
        clearRecovery();
        epoch++;scoped=null;scopedMarkers=null;LagSenseClient.clearView();
        var mc=Minecraft.getInstance();if(mc!=null&&navigate&&mc.screen instanceof LagScreen screen){screen.selection.clear();mc.setScreen(new ReportScreen(null,null,false));}
    }
    static void mark(Report report,Report.Entry entry){
        if(!allowed(report)||report==null||entry==null)return;
        int index=report.entries().indexOf(entry);if(index<0)return;request(new ProfilerRequest("mark",report.id(),index));
    }
    static void mark(long capture,Report.Entry entry){if(scoped!=null&&scoped.id()==capture)mark(scoped,entry);}
    static boolean entityMarker(java.util.UUID id){return !enabled||admin()||valid()&&state.marker()!=null&&id!=null&&id.equals(state.marker().entityId());}
    private ClientAccess(){}
}
