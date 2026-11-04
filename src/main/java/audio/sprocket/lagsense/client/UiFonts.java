package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.mixin.FontProviderAccessor;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/** Native Minecraft fonts and bounded layout caches; no independent rendering or coordinate system. */
public final class UiFonts {
    public static final FontDescription UI = resource("ui"), STRONG = resource("strong"), HEADING = resource("heading"), MONO = resource("mono");
    private static Font body, strong, heading, mono, enlargedHudBody, enlargedHudStrong;
    private static int generation;
    private static Boolean lowDensity;
    private static final Map<FontDescription,FontDescription> DENSITY_FACES=Map.of(UI,resource("ui_1x"),STRONG,resource("strong_1x"),HEADING,resource("heading_1x"),MONO,resource("mono_1x"));
    public static FontDescription faceForScale(FontDescription face,double guiScale){return guiScale==1?DENSITY_FACES.getOrDefault(face,face):face;}
    private static double guiScale(){var mc=Minecraft.getInstance();return mc==null?3:mc.getWindow().getGuiScale();}
    private static void checkDensity(){boolean low=guiScale()==1;if(lowDensity!=null&&lowDensity!=low){generation++;cache.clear();names.clear();UiHints.fontsReloaded();ProfilerHud.clear();}lowDensity=low;}
    private static final Map<Font, Map<String, Prepared>> cache = new IdentityHashMap<>();
    private static final Map<Font, Map<String, Prepared>> names = new IdentityHashMap<>();
    // Whole identifiers first, so their digits never split into a different face.
    private static final Pattern TECHNICAL = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_$]*(?:(?:[.:/#$])[\\p{L}\\p{N}_$<>-]+)+|(?<![\\p{L}\\p{N}_])[+−-]?\\d+(?:[.,]\\d+)*(?:%|(?:\\s?(?:µs(?:/t|/update)?|ms(?:/t|/frame)?|MSPT|TPS|FPS|MiB|s))\\b)?");
    public record Layout(FormattedCharSequence text, int width) {}
    private static final class Prepared {
        final Component text;
        Component worldText;
        final Int2ObjectOpenHashMap<Layout> widths = new Int2ObjectOpenHashMap<>();
        Prepared(String text, boolean name) { this.text = name ? Component.literal(text).withStyle(s -> s.withFont(STRONG)) : rich(text); }
    }
    private static FontDescription resource(String name) { return new FontDescription.Resource(Identifier.fromNamespaceAndPath("lagsense", name)); }
    private static Font nativeFont(FontDescription face) {return nativeFont(face,true);}
    private static Font nativeFont(FontDescription face,boolean adaptive) {
        var delegate = ((FontProviderAccessor) Minecraft.getInstance().font).lagsense$provider();
        var font = new UiFont(new Font.Provider() {
            @Override public GlyphSource glyphs(FontDescription requested) { var actual=requested.equals(FontDescription.DEFAULT)?face:requested;return delegate.glyphs(adaptive?faceForScale(actual,guiScale()):actual); }
            @Override public EffectGlyph effect() { return delegate.effect(); }
        });
        return font;
    }
    public static Font body() { checkDensity();if (body == null) body = nativeFont(UI); return body; }
    public static Font strong() { checkDensity();if (strong == null) strong = nativeFont(STRONG); return strong; }
    public static Font heading() { checkDensity();if (heading == null) heading = nativeFont(HEADING); return heading; }
    public static Font mono() { checkDensity();if (mono == null) mono = nativeFont(MONO); return mono; }
    public static boolean enlargedHudUsesOriginalAtlas(double guiScale,float hudScale){return guiScale==1&&hudScale>=1.5f;}
    public static Font hudBody(float scale){if(!enlargedHudUsesOriginalAtlas(guiScale(),scale))return body();if(enlargedHudBody==null)enlargedHudBody=nativeFont(UI,false);return enlargedHudBody;}
    public static Font hudStrong(float scale){if(!enlargedHudUsesOriginalAtlas(guiScale(),scale))return strong();if(enlargedHudStrong==null)enlargedHudStrong=nativeFont(STRONG,false);return enlargedHudStrong;}
    public static int generation() { return generation; }
    public static Component rich(String value) {
        var result = Component.empty(); var matcher = TECHNICAL.matcher(value); int end = 0;
        while (matcher.find()) {
            if (matcher.start() > end) result.append(Component.literal(value.substring(end, matcher.start())));
            result.append(Component.literal(matcher.group()).withStyle(s -> s.withFont(MONO))); end = matcher.end();
        }
        if (end < value.length()) result.append(Component.literal(value.substring(end)));
        return result;
    }
    public static Component tooltip(String value) { return rich(value).copy().withStyle(s -> UiTheme.current()==UiTheme.Theme.CHARCOAL_ICE?s.withFont(UI):s.withFont(UI).withColor(UiTheme.TEXT)); }
    private static Prepared prepared(Font font, String value) { return prepared(font,value,false); }
    private static Prepared prepared(Font font, String value, boolean name) {
        var entries = (name ? names : cache).computeIfAbsent(font, f -> new LinkedHashMap<>(128, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Prepared> entry) { return size() > 1024; }
        });
        return entries.computeIfAbsent(value, text -> new Prepared(text,name));
    }
    public static Layout layout(Font font, String value, int maxWidth) {
        return layout(font,value,maxWidth,false);
    }
    public static Layout nameLayout(Font font, String value, int maxWidth) { return layout(font,value,maxWidth,true); }
    private static Layout layout(Font font, String value, int maxWidth, boolean name) {
        checkDensity();maxWidth = Math.max(0, maxWidth); var prepared = prepared(font, value,name); var existing = prepared.widths.get(maxWidth);
        if (existing != null) return existing;
        FormattedText clipped = maxWidth == Integer.MAX_VALUE ? prepared.text : font.substrByWidth(prepared.text, maxWidth);
        var result = new Layout(Language.getInstance().getVisualOrder(clipped), font.width(clipped));
        if (prepared.widths.size() >= 8) prepared.widths.clear();
        prepared.widths.put(maxWidth, result); return result;
    }
    static Component worldText(String value) {
        var text = prepared(body(), value);
        if (text.worldText == null) text.worldText = text.text.copy().withStyle(s -> s.withFont(UI));
        return text.worldText;
    }
    public static int width(Font font, String value) { return layout(font, value, Integer.MAX_VALUE).width(); }
    public static int labelWidth(String value) { return Math.max(width(body(), value), width(strong(), value)); }
    public static void draw(GuiGraphicsExtractor g, Font font, String value, int x, int y, int maxWidth, int color, boolean shadow) {
        g.text(font, layout(font, value, maxWidth).text(), x, y, color, shadow);
    }
    public static void drawName(GuiGraphicsExtractor g, String value, int x, int y, int maxWidth, int color) {
        g.text(strong(), nameLayout(strong(),value,maxWidth).text(), x, y, color, false);
    }
    static void reloaded() {
        generation++; cache.clear(); names.clear(); UiHints.fontsReloaded(); ProfilerHud.clear();
        if (Minecraft.getInstance().screen instanceof LagScreen screen) screen.reloadTypography();
    }
    private UiFonts() {}
}
