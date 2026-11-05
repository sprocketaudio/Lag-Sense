package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** One capture and cached main views; switching tabs never requests another report. */
public final class ProfileNavigation {
    private final ReportScreen results;
    private final Screen parent;
    private final Report report;
    private final boolean archived;
    private HeatmapScreen map;
    private TaskScreen tasks;
    private final String title;
    private Report prepared;
    private long age=-1,observedAt;
    private Report activeCapture;
    private long activeObservedAt,activeAge=-1;
    private String status="", statusHint="";
    public ProfileNavigation(ReportScreen results, Screen parent, Report report, boolean archived) {
        this.results=results;this.parent=parent;this.report=report;this.archived=archived;
        title=report!=null&&archived?"SAVED PROFILE":"SERVER PROFILE";
    }
    public int mount(LagScreen screen,String active) {
        int y=UiMetrics.PROFILE_TABS,right=screen.profileWidth()-screen.margin(),settings=UiFonts.labelWidth("Settings")+12,history=UiFonts.labelWidth("History")+12;
        var strip=screen.beginControls(y,right-settings-(history==0?0:history+4)-12);
        var flow=new UiMetrics.Flow(screen.margin(),100000,y);UiButton selected=null;
        for(String tab:new String[]{"Targets","Chunks","Map","CPU Activity","Call Paths","Event Timings"}){
            if((report==null?!ClientAccess.admin():!report.admin())&&(tab.equals("CPU Activity")||tab.equals("Call Paths")||tab.equals("Event Timings")))continue;
            boolean on=tab.equals(active)||(tab.equals("Targets")&&!java.util.Set.of("Chunks","Map","CPU Activity","Call Paths","Event Timings").contains(active));
            var button=screen.tab(tab,screen.slot(flow,tab,24),on,()->open(tab));if(on)selected=button;
        }
        screen.endControls();if(selected!=null)strip.reveal(selected);
        if(history>0)screen.action("History",right-settings-4-history,y,history,ProfileNavigation::history);
        screen.action("Settings",right-settings,y,settings,LagSenseClient::openSettings);
        return y+20;
    }
    private static void history(){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.getConnection()!=null&&net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(mc.getConnection(),ArchivePayload.TYPE.id()))HistoryScreen.request("list",0,0);
        else ShortcutBindings.feedback("LagSense is required on the server to open history.");
    }
    void open(String tab){if(report==null){results.emptyView(tab);Minecraft.getInstance().setScreen(results);return;}switch(tab){
        case "Targets"->{if(results.currentCategory().equals("Chunks"))results.selectView(results.targetCategory());Minecraft.getInstance().setScreen(results);}
        case "Chunks"->{results.selectView("Chunks");Minecraft.getInstance().setScreen(results);}
        case "Map"->map();
        case "CPU Activity"->{var screen=tasks();if(screen!=null)screen.selectSection(false);}
        case "Event Timings"->{var screen=tasks();if(screen!=null)screen.selectSection(true);}
        case "Call Paths"->{var screen=tasks();if(screen!=null){Minecraft.getInstance().setScreen(screen);screen.openPaths();}}
        default->throw new IllegalArgumentException("Unknown profiler tab");
    }}
    private void map(){
        if(map==null){map=new HeatmapScreen(results,report,archived);map.profileNavigation=this;}
        Minecraft.getInstance().setScreen(map);
    }
    private TaskScreen tasks(){
        if(!report.admin())return null;
        if(tasks==null){tasks=new TaskScreen(results,report,archived);tasks.profileNavigation=this;}
        return tasks;
    }
    public void render(LagScreen screen,GuiGraphicsExtractor g,int mx,int my){
        screen.canvas(g,title);
        screen.headerDivider(g);
        if(report==null)return;
        header(System.currentTimeMillis());
        int summaryY=screen.panelTop()+25;
        screen.text(g,status,screen.margin(),summaryY,screen.contentWidth(),UiTheme.SECONDARY);
        UiHints.region(g,screen.profileFont(),mx,my,screen.margin(),summaryY-2,screen.contentWidth(),12,statusHint);
    }
    String header(long now){
        if(report==null)return "";
        var latest=LagSenseClient.report;
        var current=!archived&&latest!=null&&latest.id()==report.id()&&latest.admin()==report.admin()?latest:!archived&&prepared!=null?prepared:report;
        var active=!archived&&latest!=null&&latest.recording()&&latest.admin()==report.admin()?latest:null;
        boolean activeChanged=activeCapture!=active;
        if(activeChanged){activeCapture=active;activeObservedAt=now;}
        long activeSeconds=active==null?-1:Math.max(0,(now-activeObservedAt)/1000);
        boolean changed=prepared!=current;if(changed)observedAt=now;
        long seconds=Math.max(0,(now-(current.recording()?observedAt:current.capturedAt()))/1000);
        if(changed||age!=seconds||activeChanged||activeAge!=activeSeconds){
            prepared=current;age=seconds;activeAge=activeSeconds;
            String summary=current.admin()?String.format(Locale.ROOT,"%.2f MSPT | %s | %d ticks",current.mspt(),current.tps()>0?String.format(Locale.ROOT,"%.1f TPS",current.tps()):"TPS unavailable",current.ticks()):current.ticks()+" ticks | Permitted areas";
            String progress=active!=null&&active.id()!=current.id()?"New profile: "+Math.max(0,active.secondsRemaining()-activeSeconds)+"s remaining":current.recording()?Math.max(0,current.secondsRemaining()-seconds)+"s remaining":seconds+"s ago";
            status=summary+" | "+progress;
            statusHint=status;var d=current.diagnostics();if(current.admin()&&d!=null&&d.coverage()!=null)statusHint+="\n"+d.coverage().describe(current.ticks(),d.callbackState());
        }
        return status;
    }
    public void close(){Minecraft.getInstance().setScreen(parent);}
    boolean permitted(){return report==null||ClientAccess.allowed(report);}
}
