package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.Profiler;
import java.nio.file.*;
import net.neoforged.fml.loading.FMLPaths;

public final class OverlaySettings {
    public int shortcutVersion;
    public boolean enabled = true;
    public boolean worldMarkers = true;
    public boolean chunkHeatmap;
    public boolean gpuTiming = true;
    public float scale = 1;
    public int x = 8, y = 8;
    public int distance = 96;
    public double minimumMicros = 1;
    public UiTheme.Theme theme = UiTheme.DEFAULT;
    public ResultViewSettings results = ResultViewSettings.defaults();
    public void resetDisplay(){
        var defaults=new OverlaySettings();enabled=defaults.enabled;worldMarkers=defaults.worldMarkers;chunkHeatmap=defaults.chunkHeatmap;
        scale=defaults.scale;x=defaults.x;y=defaults.y;distance=defaults.distance;minimumMicros=defaults.minimumMicros;theme=defaults.theme;
    }
    private static Path path() { return FMLPaths.CONFIGDIR.get().resolve("lagsense-client.json"); }
    public static OverlaySettings load() {
        try {
            if (Files.exists(path())) {
                OverlaySettings value = Profiler.JSON.fromJson(Files.readString(path()), OverlaySettings.class);
                if (value != null) { value.clamp(); UiTheme.select(value.theme); return value; }
            }
        } catch (Exception e) { net.sprocketgames.lagsense.LagSense.LOG.warn("Could not load LagSense overlay settings", e); }
        var value=new OverlaySettings();UiTheme.select(value.theme);return value;
    }
    public void clamp() {
        if(theme==null)theme=UiTheme.DEFAULT;
        if(results==null)results=ResultViewSettings.defaults();
        scale = Float.isFinite(scale) ? Math.max(0.5f, Math.min(2, scale)) : 1;
        x = Math.max(0, Math.min(4096, x)); y = Math.max(0, Math.min(4096, y));
        distance = Math.max(16, Math.min(192, distance));
        minimumMicros = Double.isFinite(minimumMicros) ? Math.max(0, Math.min(10000, minimumMicros)) : 1;
    }
    public void save() {
        clamp();
        UiTheme.select(theme);
        try {
            Files.createDirectories(path().getParent());
            Path temp = path().resolveSibling("lagsense-client.json.tmp");
            Files.writeString(temp, Profiler.JSON.toJson(this));
            Files.move(temp, path(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) { net.sprocketgames.lagsense.LagSense.LOG.warn("Could not save LagSense overlay settings", e); }
    }
}
