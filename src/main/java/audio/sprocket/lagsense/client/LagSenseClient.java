package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.lwjgl.glfw.GLFW;

@Mod(value = "lagsense", dist = Dist.CLIENT)
public final class LagSenseClient {
    public static Report report;
    public static boolean archived;
    public static boolean completeReport;
    public static OverlaySettings settings = OverlaySettings.load();
    public static int selected = -1;
    public static Report.Entry waypoint;
    public static int waypointTicks;
    private static Report waypointSource;
    private static MarkerIndex waypointIndex;
    public static MarkerIndex markerIndex;
    private final WorldMarkers worldMarkers=new WorldMarkers();
    public LagSenseClient(IEventBus bus) {
        ClientAccess.enable();
        bus.addListener((RegisterClientPayloadHandlersEvent e)->e.register(ProfilerStatePayload.TYPE,(p,ctx)->ClientAccess.accept(p)));
        bus.addListener((RegisterRenderPipelinesEvent e)->{e.registerPipeline(ChunkOverlay.PIPELINE);e.registerPipeline(WorldMarkers.PIPELINE);e.registerPipeline(WorldText.INTENSITY);e.registerPipeline(WorldText.COLOR);});
        bus.addListener((AddClientReloadListenersEvent e) -> {
            var id = Identifier.fromNamespaceAndPath("lagsense", "typography");
            e.addListener(id, (net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> {UiFonts.reloaded();WorldText.clear();});
            e.addDependency(net.neoforged.neoforge.client.resources.VanillaClientListeners.FONTS, id);
        });
        bus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(SettingsPayload.TYPE, (p, ctx) -> {
            var mc=Minecraft.getInstance();
            if(mc.screen instanceof SettingsScreen screen)screen.accept(p);
            else {var parent=mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen?null:mc.screen;mc.setScreen(new SettingsScreen(parent,p));}
        }));
        bus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(ServerSettingsPayload.TYPE, (p, ctx) -> ServerSettingsScreen.accept(p)));
        bus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(InvestigationPayload.TYPE, (p, ctx) -> Investigations.accept(p)));
        bus.addListener((RegisterClientPayloadHandlersEvent e)->e.register(InvestigationPart.TYPE,(p,ctx)->Investigations.accept(p)));
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseButton.Pre e)->ShortcutBindings.input(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(e.getButton()),e.getAction()));
        new AreaOverlay();
        new ChunkOverlay();
        bus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(CaptureSettingsPayload.TYPE, (p, ctx) -> {
            var mc = Minecraft.getInstance();
            if (p.json().isEmpty()) { if (mc.screen instanceof CaptureSettingsScreen) mc.setScreen(null); return; }
            var state = Profiler.JSON.fromJson(p.json(), CaptureSettings.class);
            if (mc.screen instanceof CaptureSettingsScreen screen) screen.accept(state, p.message());
            else mc.setScreen(new CaptureSettingsScreen(mc.screen, state, p.message()));
        }));
        bus.addListener((RegisterKeyMappingsEvent e) -> { e.registerCategory(ShortcutBindings.CATEGORY);ShortcutBindings.all().forEach(e::register); });
        bus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(ReportPayload.TYPE, (p, ctx) -> {
            var value=Profiler.JSON.fromJson(p.json(),Report.class);
            acceptReport(value,p.open(),p.archived(),new MarkerIndex(value),false);
        }));
        bus.addListener((RegisterClientPayloadHandlersEvent e)->e.register(FullReportPart.TYPE,(p,ctx)->FullReports.accept(p)));
        EntityMarkers.register();
        bus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(AreaPayload.TYPE, (p, ctx) ->
            AreaOverlay.accept(Profiler.JSON.fromJson(p.json(), AreaPayload.State.class))));
        bus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(ArchivePayload.TYPE, (p, ctx) -> {
            var mc = Minecraft.getInstance();
            if (p.json().isEmpty()) { if (mc.screen instanceof HistoryScreen || mc.screen instanceof ComparisonScreen) mc.setScreen(null); return; }
            var state = Profiler.JSON.fromJson(p.json(), ProfileArchive.State.class);
            if (mc.screen instanceof HistoryScreen screen) screen.accept(state, p.message());
            else mc.setScreen(new HistoryScreen(mc.screen, state, p.message()));
        }));
        bus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(ComparisonPayload.TYPE, (p, ctx) -> {
            var mc = Minecraft.getInstance(); mc.setScreen(new ComparisonScreen(mc.screen, Profiler.JSON.fromJson(p.json(), ProfileComparison.class)));
        }));
        bus.addListener((RegisterClientPayloadHandlersEvent e)->e.register(ComparisonPart.TYPE,(p,ctx)->Comparisons.accept(p)));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> { ShortcutBindings.clear();ClientAccess.reset();report = null; archived = false; completeReport=false; selected = -1; waypoint = null;waypointTicks=0;waypointSource=null;waypointIndex=null; worldMarkers.clear();markerIndex=null;EntityMarkers.clear();FullReports.clear(); Comparisons.clear(); ProfilerHud.clear(); AreaOverlay.clear(); Investigations.clear(); });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            ClientAccess.tick();Investigations.tick();
            if(Minecraft.getInstance().screen instanceof LagScreen screen)UiButton.refreshAvailability(screen);
            ShortcutBindings.tick();
        });
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre e) -> ClientFrames.frame());
        NeoForge.EVENT_BUS.addListener(this::hud);
        NeoForge.EVENT_BUS.addListener(worldMarkers::extract);
        NeoForge.EVENT_BUS.addListener(worldMarkers::capture);
        NeoForge.EVENT_BUS.addListener(worldMarkers::render);
    }
    public static int color(double micros) { return TimingSeverity.color(micros); }
    public static void openSettings() {
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof SettingsScreen)return;
        if(mc.player!=null&&mc.getConnection()!=null&&net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(mc.getConnection(),SettingsPayload.TYPE.id()))
            ClientAccess.request("settings");
        else mc.setScreen(new SettingsScreen(mc.screen,null));
    }
    public static String age() { return report == null ? "No snapshot" : "Snapshot age: " + Math.max(0, (System.currentTimeMillis() - report.capturedAt()) / 1000) + "s"; }
    private void hud(RenderGuiEvent.Post e) {
        drawHud(e.getGuiGraphics(), false);
        if (VisualVisibility.visible()&&ClientFrames.active) UiFonts.draw(e.getGuiGraphics(), UiFonts.body(), "LagSense frames: " + ClientFrames.remaining() + "s"+(ShortcutBindings.hint(ShortcutBindings.FPS).isEmpty()?"":" | "+ShortcutBindings.hint(ShortcutBindings.FPS)+" stop"), 8, e.getGuiGraphics().guiHeight() - 28, e.getGuiGraphics().guiWidth() - 16, UiTheme.ICE, true);
    }
    public static void mark(Report.Entry entry) { waypoint = entry;waypointTicks=0;waypointSource=null;waypointIndex=null; settings.enabled = true; settings.worldMarkers = true; settings.save(); }
    public static void mark(ComparisonTargets.Destination source){
        waypoint=source.target();waypointTicks=source.ticks();
        waypointSource=new Report(source.capture(),source.capturedAt(),source.ticks(),0,false,false,List.of(waypoint));waypointIndex=new MarkerIndex(waypointSource);
        settings.enabled=true;settings.worldMarkers=true;settings.save();
    }
    static Report markerReport(){return !ClientAccess.admin()?ClientAccess.scopedReport():report!=null?report:waypoint==null?null:waypointSource;}
    static MarkerIndex markerData(){return !ClientAccess.admin()?ClientAccess.scopedIndex():report!=null?markerIndex:waypoint==null?null:waypointIndex;}
    static void serverMark(ProfilerStatePayload.State value){
        waypoint=value.marker();waypointTicks=value.ticks();waypointSource=waypoint==null?null:new Report(value.report(),0,value.ticks(),0,false,false,java.util.List.of(waypoint));waypointIndex=waypointSource==null?null:new MarkerIndex(waypointSource);
        if(waypoint!=null){settings.enabled=true;settings.worldMarkers=true;settings.save();}
    }
    public static void drawHud(net.minecraft.client.gui.GuiGraphicsExtractor g, boolean preview) { ProfilerHud.draw(g, preview); }
    static void clearView(){
        report=null;markerIndex=null;archived=false;completeReport=false;waypoint=null;waypointIndex=null;waypointSource=null;waypointTicks=0;selected=-1;
        EntityMarkers.clear();FullReports.clear();Investigations.clear();Comparisons.clear();ProfilerHud.clear();
    }
    static void mark(Report report,Report.Entry entry){if(report.admin())mark(entry);else ClientAccess.mark(report,entry);}
    // Browsed archives belong to their screen; HUD and F9 retain the latest normal capture.
    static void storeReport(Report value,boolean saved,MarkerIndex markers,boolean complete) {
        if(saved)return;
        var previous=report;report=value;archived=false;selected=-1;waypoint=null;waypointTicks=0;waypointSource=null;waypointIndex=null;markerIndex=markers;completeReport=complete;
        if(VisualVisibility.visible()&&ClientReportPolicy.beganCapture(previous,value,false)&&!settings.enabled){settings.enabled=true;settings.save();}
        if(ClientReportPolicy.manualCaptureStarted(previous,value,false))VisualVisibility.reset();
    }
    static ReportScreen latestReportScreen(){return new ReportScreen(null,report,false);}
    public static void openLatestReport(){ClientAccess.open();}
    public static void acceptReport(Report value,boolean open,boolean saved,MarkerIndex markers,boolean complete) {
        if(!ClientAccess.allowed(value))return;
        ClientAccess.received(value,markers);storeReport(value,saved,markers,complete);
        if(VisualVisibility.visible()&&ClientReportPolicy.restoreHud(open,saved)&&!settings.enabled){settings.enabled=true;settings.save();}
        if(open){var mc=Minecraft.getInstance();var parent=mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen?null:mc.screen;mc.setScreen(new ReportScreen(parent,value,saved));}
    }
}
