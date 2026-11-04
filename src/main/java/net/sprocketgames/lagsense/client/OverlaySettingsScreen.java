package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.UiMetrics;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class OverlaySettingsScreen extends SettingsPage {
    private final Screen parent;
    private boolean position;
    private int placementTop;
    private UiButton themeControl,visualsControl;
    private boolean visualsShown;
    @Override protected boolean liveHudPositioning() { return position; }
    private void move(int dx, int dy) {
        var bounds = ProfilerHud.placement(width, height);
        change(() -> { LagSenseClient.settings.x = bounds.x() + dx; LagSenseClient.settings.y = bounds.y() + dy; });
    }
    private void corner(boolean right, boolean bottom) {
        var target = ProfilerHud.placement(width, height).corner(width, height, right, bottom);
        change(() -> { LagSenseClient.settings.x = target.x(); LagSenseClient.settings.y = target.y(); });
    }
    @Override protected void extractOverlay(GuiGraphicsExtractor g, int mx, int my, float partial) {
        if (!position) return;
        g.nextStratum(); LagSenseClient.drawHud(g, true);
        var bounds = ProfilerHud.placement(width, height);
        var target = getChildAt(mx, my).orElse(getFocused());
        if(target instanceof UiScrollList list)target=list.getChildAt(mx,my).orElse(list.getFocused());
        if (target instanceof net.minecraft.client.gui.components.AbstractWidget widget &&
            bounds.overlaps(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight())) {
            // Only the control being used comes forward; the HUD never moves to avoid the form.
            g.nextStratum(); widget.extractRenderState(g, mx, my, partial);
        }
    }
    public OverlaySettingsScreen(Screen parent) { super("Display settings"); this.parent = parent; }
    private void change(Runnable run) { run.run(); LagSenseClient.settings.save(); rebuildWidgets(); }
    private void cycleTheme(){
        var s=LagSenseClient.settings;s.theme=s.theme.next();s.save();themeControl.setMessage(net.minecraft.network.chat.Component.literal("Theme: "+s.theme.label));
    }
    @Override protected void init() {
        visualsShown=VisualVisibility.visible();
        visualsControl=null;
        var s = LagSenseClient.settings; int x = margin(), col = (contentWidth() - 8) / 2, second = x + col + 8;
        var nav = new UiMetrics.Flow(x, width - x, headerControlsTop());
        tab("Display", nav.next(76), !position, () -> { position = false; rebuildWidgets(); });
        tab("Position", nav.next(76), position, () -> { position = true; rebuildWidgets(); });
        placementTop=nav.bottom()+20;
        beginList(placementTop,position?1:0);
        formSection(position?"Placement":"Visibility and filters",placementTop);
        int first=placementTop+20;
        if (!position) {
            visualsControl=toggle("Performance visuals",new UiMetrics.Rect(x,first,col,20),visualsShown,()->{VisualVisibility.toggle();syncVisuals();});
            UiHints.hint(visualsControl,"Hide performance overlays without changing their settings. A new manual profile shows them again.");
            first+=32;
            toggle("HUD", new UiMetrics.Rect(x, first, col, 20), s.enabled, () -> change(() -> s.enabled = !s.enabled));
            toggle("World markers", new UiMetrics.Rect(second, first, col, 20), s.worldMarkers, () -> change(() -> s.worldMarkers = !s.worldMarkers));
            toggle("Chunk heatmap", new UiMetrics.Rect(x, first+32, col, 20), s.chunkHeatmap, () -> change(() -> s.chunkHeatmap = !s.chunkHeatmap));
            themeControl=action("Theme: "+s.theme.label, second, first+32, col, this::cycleTheme);
            action("Range: " + s.distance + " blocks", x, first+64, col, () -> change(() -> s.distance = s.distance >= 192 ? 32 : s.distance + 32));
            action("Min: " + s.minimumMicros + " µs/t", second, first+64, col, () -> change(() -> s.minimumMicros = s.minimumMicros < 1 ? 1 : s.minimumMicros < 10 ? 10 : s.minimumMicros < 100 ? 100 : 0));
        } else {
            int arrow=(contentWidth()-12)/4, movementY=first;
            UiHints.hint(action("Left",x,movementY,arrow,()->move(-8,0)),"Move the HUD left.");
            UiHints.hint(action("Right",x+arrow+4,movementY,arrow,()->move(8,0)),"Move the HUD right.");
            UiHints.hint(action("Up",x+(arrow+4)*2,movementY,arrow,()->move(0,-8)),"Move the HUD up.");
            UiHints.hint(action("Down",x+(arrow+4)*3,movementY,arrow,()->move(0,8)),"Move the HUD down.");
            int cornerWidth=arrow*2+4, cornerRight=x+(arrow+4)*2;
            action("Top left",x,movementY+32,cornerWidth,()->corner(false,false));
            action("Top right",cornerRight,movementY+32,cornerWidth,()->corner(true,false));
            action("Bottom left",x,movementY+64,cornerWidth,()->corner(false,true));
            action("Bottom right",cornerRight,movementY+64,cornerWidth,()->corner(true,true));
        }
        int bottom=first+84;
        if(position) {
            formSection("HUD scale",placementTop+124);
            int scaleX=(width-174)/2,scaleY=placementTop+144;
            UiHints.hint(action("−", scaleX, scaleY, 28, () -> change(() -> s.scale -= .1f)), "Decrease HUD scale.");
            UiHints.hint(action(String.format(Locale.ROOT, "Scale %.0f%%", s.scale * 100), scaleX + 32, scaleY, 110, () -> change(() -> s.scale = 1)), "Return the HUD to 100% scale.");
            UiHints.hint(action("+", scaleX + 146, scaleY, 28, () -> change(() -> s.scale += .1f)), "Increase HUD scale.");
            bottom=scaleY+20;
        }
        endList(bottom);
        danger("Reset defaults", x, footer(), 96, () -> change(() -> {LagSenseClient.settings.resetDisplay();VisualVisibility.reset();}));
        action("Close", width - x - 54, footer(), 54, this::onClose);

    }
    private void syncVisuals(){
        boolean visible=VisualVisibility.visible();if(visualsControl==null||visible==visualsShown)return;
        visualsShown=visible;visualsControl.on(visible).setMessage(net.minecraft.network.chat.Component.literal("Performance visuals "+(visible?"ON":"OFF")));
    }
    @Override public void tick(){super.tick();syncVisuals();}
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        canvas(g, "DISPLAY SETTINGS");
        headerDivider(g);
        super.extractRenderState(g, mx, my, partial);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
