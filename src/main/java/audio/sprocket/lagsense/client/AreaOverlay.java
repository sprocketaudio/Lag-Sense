package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;

/** Admin-only boundary state, independent of profiler visibility and captured results. */
public final class AreaOverlay {
    private static final int RANGE = 96;
    private static AreaPayload.State state = new AreaPayload.State(List.of(), null, false);
    private record Line(double x1, double y1, double z1, double x2, double y2, double z2, int color) {}
    private record Caption(Vec3 pos, String text, int color) {}
    private record Frame(List<Line> lines, List<Caption> captions) {}
    private static final ContextKey<Frame> FRAME = new ContextKey<>(Identifier.fromNamespaceAndPath("lagsense", "area_boundaries"));
    public AreaOverlay() {
        NeoForge.EVENT_BUS.addListener(this::extract);
        NeoForge.EVENT_BUS.addListener(this::submit);
        NeoForge.EVENT_BUS.addListener(this::hud);
    }
    public static void accept(AreaPayload.State value) { state = value; }
    public static void clear() { state = new AreaPayload.State(List.of(), null, false); }
    private static double distance(PublicArea area, Vec3 camera) {
        double x = Math.clamp(camera.x, area.minX(), area.maxX() + 1.0), z = Math.clamp(camera.z, area.minZ(), area.maxZ() + 1.0);
        return (x - camera.x) * (x - camera.x) + (z - camera.z) * (z - camera.z);
    }
    private void extract(ExtractLevelRenderStateEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null || (!state.display() || state.areas().isEmpty()) && state.selection() == null) { event.getRenderState().setRenderData(FRAME, null); return; }
        String dimension = event.getLevel().dimension().identifier().toString(); Vec3 camera = event.getCamera().position();
        List<Line> lines = new ArrayList<>(); List<Caption> captions = new ArrayList<>();
        double y = Math.clamp(Math.floor(mc.player.getY()) + .03, event.getLevel().getMinY() + 1.0, event.getLevel().getMaxY() - 1.0);
        if (state.display()) {
            state.areas().stream().filter(a -> a.dimension().equals(dimension) && distance(a, camera) <= RANGE * RANGE)
                .sorted(Comparator.comparingDouble(a -> distance(a, camera))).limit(16)
                .forEach(area -> outline(area, camera, y, UiTheme.ICE, "PUBLIC / " + area.name(), lines, captions));
        }
        var selected = state.selection();
        if (selected != null && selected.dimension().equals(dimension)) {
            var feet = mc.player.blockPosition();
            var end = selected.second() == null ? new PublicArea.Point(feet.getX(), feet.getZ()) : selected.second();
            PublicArea preview = PublicArea.between("preview", dimension, selected.first(), end);
            outline(preview, camera, y, UiTheme.TEXT, (selected.second() == null ? "LIVE PREVIEW" : "LOCKED PREVIEW") + " / " + preview.width() + " x " + preview.depth(), lines, captions);
            corner(selected.first(), camera, y, "Corner 1", lines, captions);
            if (selected.second() != null) corner(selected.second(), camera, y, "Corner 2", lines, captions);
        }
        event.getRenderState().setRenderData(FRAME, new Frame(List.copyOf(lines), List.copyOf(captions)));
    }
    private static void outline(PublicArea area, Vec3 camera, double y, int color, String text, List<Line> lines, List<Caption> captions) {
        double x1 = area.minX(), x2 = area.maxX() + 1.0, z1 = area.minZ(), z2 = area.maxZ() + 1.0;
        // Draw only nearby stretches; this never loads chunks or scales work with area size.
        for (int level = -2; level <= 2; level++) {
            horizontal(x1, x2, z1, camera, y + level * 4, color, lines);
            horizontal(x1, x2, z2, camera, y + level * 4, color, lines);
            vertical(z1, z2, x1, camera, y + level * 4, color, lines);
            vertical(z1, z2, x2, camera, y + level * 4, color, lines);
        }
        double labelX = Math.clamp(camera.x, x1, x2), labelZ = Math.clamp(camera.z, z1, z2);
        // If inside, place the label on the nearest actual edge rather than under the player.
        if (camera.x >= x1 && camera.x <= x2 && camera.z >= z1 && camera.z <= z2) {
            double[] edges = {camera.x - x1, x2 - camera.x, camera.z - z1, z2 - camera.z};
            int nearest = 0; for (int i = 1; i < 4; i++) if (edges[i] < edges[nearest]) nearest = i;
            if (nearest == 0) labelX = x1; else if (nearest == 1) labelX = x2; else if (nearest == 2) labelZ = z1; else labelZ = z2;
        }
        if (Math.abs(labelX - camera.x) <= RANGE && Math.abs(labelZ - camera.z) <= RANGE)
            captions.add(new Caption(new Vec3(labelX, y + 3, labelZ), text + " | full height", color));
    }
    private static void horizontal(double low, double high, double z, Vec3 camera, double y, int color, List<Line> lines) {
        if (Math.abs(z - camera.z) > RANGE) return;
        low = Math.max(low, camera.x - RANGE); high = Math.min(high, camera.x + RANGE); if (low > high) return;
        lines.add(new Line(low, y, z, high, y, z, color));
        for (double x = Math.ceil(low / 8) * 8; x <= high; x += 8) lines.add(new Line(x, y, z, x, y + 4, z, color));
        lines.add(new Line(low, y, z, low, y + 4, z, color)); lines.add(new Line(high, y, z, high, y + 4, z, color));
    }
    private static void vertical(double low, double high, double x, Vec3 camera, double y, int color, List<Line> lines) {
        if (Math.abs(x - camera.x) > RANGE) return;
        low = Math.max(low, camera.z - RANGE); high = Math.min(high, camera.z + RANGE); if (low > high) return;
        lines.add(new Line(x, y, low, x, y, high, color));
        for (double z = Math.ceil(low / 8) * 8; z <= high; z += 8) lines.add(new Line(x, y, z, x, y + 4, z, color));
        lines.add(new Line(x, y, low, x, y + 4, low, color)); lines.add(new Line(x, y, high, x, y + 4, high, color));
    }
    private static void corner(PublicArea.Point point, Vec3 camera, double y, String title, List<Line> lines, List<Caption> captions) {
        if (Math.abs(point.x() - camera.x) > RANGE || Math.abs(point.z() - camera.z) > RANGE) return;
        double x = point.x() + .5, z = point.z() + .5;
        lines.add(new Line(x - .5, y + .1, z, x + .5, y + .1, z, UiTheme.TEXT));
        lines.add(new Line(x, y + .1, z - .5, x, y + .1, z + .5, UiTheme.TEXT));
        lines.add(new Line(x, y, z, x, y + 2, z, UiTheme.TEXT));
        captions.add(new Caption(new Vec3(x, y + 2.3, z), title + " / " + point.x() + ", " + point.z(), UiTheme.TEXT));
    }
    private void submit(SubmitCustomGeometryEvent event) {
        Frame frame = event.getLevelRenderState().getRenderData(FRAME); if (frame == null) return;
        var camera = event.getLevelRenderState().cameraRenderState; var stack = event.getPoseStack(); var collector = event.getSubmitNodeCollector();
        if (!frame.lines().isEmpty()) collector.submitCustomGeometry(stack, RenderTypes.lines(), (pose, vertices) -> {
            for (Line line : frame.lines()) {
                double dx = line.x2() - line.x1(), dy = line.y2() - line.y1(), dz = line.z2() - line.z1();
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz); if (length == 0) continue;
                float nx = (float)(dx / length), ny = (float)(dy / length), nz = (float)(dz / length);
                vertices.addVertex(pose, (float)(line.x1() - camera.pos.x), (float)(line.y1() - camera.pos.y), (float)(line.z1() - camera.pos.z))
                    .setColor(line.color()).setNormal(pose, nx, ny, nz).setLineWidth(2);
                vertices.addVertex(pose, (float)(line.x2() - camera.pos.x), (float)(line.y2() - camera.pos.y), (float)(line.z2() - camera.pos.z))
                    .setColor(line.color()).setNormal(pose, nx, ny, nz).setLineWidth(2);
            }
        });
        for (Caption caption : frame.captions()) {
            stack.pushPose(); stack.translate(caption.pos().x - camera.pos.x, caption.pos().y - camera.pos.y, caption.pos().z - camera.pos.z);
            stack.mulPose(camera.orientation); stack.scale(.025f, -.025f, .025f);
            int labelWidth = Minecraft.getInstance().font.width(UiFonts.worldText(caption.text()));
            collector.submitText(stack, -labelWidth / 2f, 0,
                // World text is rendered by Minecraft’s default Font, so supply the UI face explicitly.
                UiFonts.worldText(caption.text()).getVisualOrderText(), false, Font.DisplayMode.NORMAL, 15728880, caption.color(), UiTheme.LABEL, 0);
            stack.popPose();
        }
    }
    private void hud(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance(); if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        var selected = state.selection(); if (selected == null && (!state.display() || state.areas().isEmpty())) return;
        var g = event.getGuiGraphics(); int x = Math.max(4, g.guiWidth() - 264), y = g.guiHeight() - 92;
        g.fill(x, y, g.guiWidth() - 4, y + (selected == null ? 17 : 29), UiTheme.HUD);
        String first = selected == null ? "Public areas | /lagsense area hide" : "Area preview";
        String second = selected == null ? "" : selected.dimension().equals(mc.level.dimension().identifier().toString())
            ? selected.second() == null ? "Set corner 2 with /lagsense area pos2" : "/lagsense area create <name> or area cancel" : "Selection is in " + selected.dimension();
        UiFonts.draw(g, UiFonts.body(), first, x + 4, y + 5, 252, selected == null ? UiTheme.ICE : UiTheme.TEXT, false);
        if (selected != null) UiFonts.draw(g, UiFonts.body(), second, x + 4, y + 17, 252, UiTheme.SECONDARY, false);
    }
}
