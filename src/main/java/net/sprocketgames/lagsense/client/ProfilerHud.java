package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** One renderer for the gameplay HUD and its live positioning preview. */
final class ProfilerHud {
    private static final Report PREVIEW = new Report(0, 0, 600, 18.4, true, false,
        List.of(new Report.Entry("Block", "minecraft:hopper", "minecraft:overworld", 0, 64, 0, 15_000_000, 600)),
        false, 0, List.of(), 20);
    private static Report prepared;
    private static String title, stats, top, ageText;
    private static int topColor, remaining = -1;
    private static long age = -1;
    private static int viewportWidth, viewportHeight, savedX, savedY;
    private static float scale;
    private static boolean marked;
    private static HudPlacement placement;

    static HudPlacement placement(int width, int height) {
        var s = LagSenseClient.settings;
        boolean hasMark = LagSenseClient.waypoint != null;
        if (placement == null || width != viewportWidth || height != viewportHeight || s.x != savedX || s.y != savedY || s.scale != scale || marked != hasMark) {
            viewportWidth = width; viewportHeight = height; savedX = s.x; savedY = s.y; scale = s.scale; marked = hasMark;
            placement = HudPlacement.at(width, height, savedX, savedY, scale, marked);
        }
        return placement;
    }
    static void draw(GuiGraphicsExtractor g, boolean preview) {
        if(!preview&&!VisualVisibility.visible())return;
        var mc = Minecraft.getInstance(); var s = LagSenseClient.settings;
        if (!ClientReportPolicy.showHud(s.enabled, LagSenseClient.markerReport() != null, mc.player != null, mc.options.hideGui, mc.screen != null, preview)) return;
        Report report = LagSenseClient.markerReport() == null ? PREVIEW : LagSenseClient.markerReport();
        var waypoint = report == PREVIEW ? null : LagSenseClient.waypoint;
        if (prepared != report) {
            prepared = report; age = -1; remaining = -1;
            title = "LagSense | " + (LagSenseClient.report==null&&LagSenseClient.waypoint!=null?"Marked target":"Profiler");
            stats = report.admin() ? report.ticks() == 0 ? "MSPT - | TPS -" : String.format(Locale.ROOT, "%.2f MSPT | %s", report.mspt(), report.tps() > 0 ? String.format(Locale.ROOT, "%.1f TPS", report.tps()) : "TPS unavailable") : report.entries().size() + " permitted targets";
            var entry = report.entries().isEmpty() ? null : report.entries().getFirst();
            top = entry == null ? "" : String.format(Locale.ROOT, "Top: %.1f µs/t %s", entry.micros(report.ticks()), entry.type());
            topColor = entry == null ? UiTheme.TEXT : LagSenseClient.color(entry.micros(report.ticks()));
        }
        long seconds = report == PREVIEW ? 5 : Math.max(0, (System.currentTimeMillis() - report.capturedAt()) / 1000);
        if (age != seconds || remaining != report.secondsRemaining()) {
            age = seconds; remaining = report.secondsRemaining();
            ageText = report.recording() ? "Recording | " + remaining + "s remaining" : "Snapshot age: " + seconds + "s";
        }
        var bounds = placement(g.guiWidth(), g.guiHeight());
        g.pose().pushMatrix(); g.pose().translate(bounds.x(), bounds.y()); g.pose().scale(s.scale, s.scale);
        int panelHeight = waypoint == null ? 73 : 86;
        g.fill(0, 0, 240, panelHeight, UiTheme.HUD); g.fill(0, 0, 2, panelHeight, UiTheme.ICE);
        UiFonts.draw(g, UiFonts.hudStrong(s.scale), title, 9, 7, 222, UiTheme.ICE, false); UiFonts.draw(g, UiFonts.hudBody(s.scale), ageText, 9, 20, 222, UiTheme.SECONDARY, false);
        UiFonts.draw(g, UiFonts.hudBody(s.scale), stats, 9, 33, 222, UiTheme.TEXT, false); UiFonts.draw(g, UiFonts.hudBody(s.scale), ShortcutBindings.hudHint(), 9, 46, 222, UiTheme.SECONDARY, false);
        if (waypoint != null) {
            var live=LagSenseClient.markedPose(1);
            String distance=MarkedDirection.guidance(waypoint,live,mc.level.dimension().identifier().toString(),mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.getYRot(),LagSenseClient.trackingStatus());
            UiFonts.draw(g, UiFonts.hudBody(s.scale), "Marked: " + distance, 9, 59, 222, 0xffffe082, false);
            String location=live==null?waypoint.x()+" "+waypoint.y()+" "+waypoint.z()+" (capture position)":(int)Math.floor(live.x())+" "+(int)Math.floor(live.y())+" "+(int)Math.floor(live.z())+" (current position)";
            UiFonts.draw(g, UiFonts.hudBody(s.scale), location, 9, 72, 222, UiTheme.SECONDARY, false);
        } else if (!top.isEmpty()) UiFonts.draw(g, UiFonts.hudBody(s.scale), top, 9, 59, 222, topColor, false);
        g.pose().popMatrix();
    }
    static void clear() { prepared = null; placement = null; }
    private ProfilerHud() {}
}
