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
    static void enable(){enabled=true;reset();}
    static void reset(){state=null;deadline=0;scoped=null;scopedMarkers=null;lastRequest=0;openQueued=false;epoch++;}
    static boolean valid(){return !enabled||state!=null&&System.nanoTime()<deadline;}
    public static boolean admin(){return !enabled||valid()&&state.admin();}
    static boolean allowed(Report report){return !enabled||valid()&&report!=null&&(admin()||!report.admin()&&state.report()==report.id());}
    public static long epoch(){return epoch;}
    public static boolean current(long token){return token==epoch&&(!enabled||state==null||valid());}
    static boolean request(String action){return request(new ProfilerRequest(action));}
    static boolean request(ProfilerRequest request){
        var mc=Minecraft.getInstance();if(mc==null||mc.player==null||mc.getConnection()==null||!NetworkRegistry.hasChannel(mc.getConnection(),ProfilerRequest.TYPE.id()))return false;
        long now=System.nanoTime();if(request.action().equals("open")&&now-lastRequest<350_000_000L){openQueued=true;return true;}
        ClientPacketDistributor.sendToServer(request);lastRequest=now;if(request.action().equals("open"))openQueued=false;return true;
    }
    static void open(){
        var mc=Minecraft.getInstance();if(mc==null)return;
        mc.setScreen(new ReportScreen(null,allowed(LagSenseClient.report)?LagSenseClient.report:null,false));request("open");
    }
    static void accept(ProfilerStatePayload payload){
        final ProfilerStatePayload.State value;try{value=Profiler.JSON.fromJson(payload.json(),ProfilerStatePayload.State.class);}catch(RuntimeException error){state=null;deadline=0;invalidate(true);return;}
        if(value==null||value.leaseTicks()<1||value.leaseTicks()>100){state=null;deadline=0;invalidate(true);return;}
        boolean before=admin();long previous=state==null?0:state.report();enabled=true;state=value;deadline=System.nanoTime()+value.leaseTicks()*50_000_000L;
        if(value.clear()||before&&!value.admin())invalidate(!value.open());
        else if(!value.admin()&&previous!=value.report())invalidate(false);
        var mc=Minecraft.getInstance();
        if(value.markerChanged()&&!value.clear()){
            LagSenseClient.serverMark(value);
        }
        if(mc!=null){if(value.open())mc.setScreen(LagSenseClient.latestReportScreen());else if(before!=admin()&&mc.screen instanceof LagScreen screen)screen.rebuildResults();}
        if(value.message()!=null&&!value.message().isBlank())ShortcutBindings.feedback(value.message());
    }
    static void received(Report report,MarkerIndex markers){if(!report.admin()){scoped=report;scopedMarkers=markers;}}
    static MarkerIndex markers(Report report){return allowed(report)&&scoped!=null&&scoped.id()==report.id()?scopedMarkers:null;}
    static Report scopedReport(){return valid()?scoped:null;}
    static MarkerIndex scopedIndex(){return valid()?scopedMarkers:null;}
    static void tick(){
        if(openQueued&&System.nanoTime()-lastRequest>=350_000_000L)request("open");
        if(enabled&&state!=null&&System.nanoTime()>=deadline){state=null;invalidate(true);return;}
        var mc=Minecraft.getInstance();if(enabled&&mc!=null&&mc.screen instanceof LagScreen screen&&screen.profileNavigation!=null&&!screen.profileNavigation.permitted()){mc.setScreen(new ReportScreen(null,null,false));request("open");}
    }
    private static void invalidate(boolean navigate){
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
