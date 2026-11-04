package net.sprocketgames.lagsense;

import net.sprocketgames.lagsense.client.UiFonts;
import com.google.gson.*;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.font.glyphs.*;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.network.chat.*;
import net.minecraft.util.RandomSource;
import org.lwjgl.system.*;
import org.lwjgl.util.freetype.*;

/** Exercises Minecraft's real TTF advances and text splitter without creating GPU textures. */
final class TypographyChecks {
    private static void require(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    static void run() throws Exception {
        Map<FontDescription, List<TrueTypeGlyphProvider>> faces = new HashMap<>();
        try {
            for (var face : List.of("ui", "strong", "heading", "mono", "ui_1x", "strong_1x", "heading_1x", "mono_1x")) {
                var json = JsonParser.parseString(resource("/assets/lagsense/font/" + face + ".json")).getAsJsonObject().getAsJsonArray("providers");
                var providers = new ArrayList<TrueTypeGlyphProvider>();
                faces.put(new FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("lagsense", face)), providers);
                for (var element : json) { var definition = element.getAsJsonObject();
                    if (definition.get("type").getAsString().equals("reference") && definition.get("id").getAsString().startsWith("lagsense:symbols"))
                        definition = JsonParser.parseString(resource("/assets/lagsense/font/"+definition.get("id").getAsString().substring(9)+".json")).getAsJsonObject().getAsJsonArray("providers").get(0).getAsJsonObject();
                    if (!definition.get("type").getAsString().equals("ttf")) continue;
                require(json.get(json.size() - 1).getAsJsonObject().get("id").getAsString().equals("minecraft:default"), "Missing glyphs retain Minecraft's resource-pack fallback");
                var file = definition.get("file").getAsString();
                require(file.equals(file.toLowerCase(Locale.ROOT)), "Font resource paths must be valid lowercase identifiers");
                byte[] bytes;
                try (var stream = TypographyChecks.class.getResourceAsStream("/assets/lagsense/font/" + file.substring(file.indexOf(':') + 1))) { bytes = Objects.requireNonNull(stream).readAllBytes(); }
                var memory = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
                try (var stack = MemoryStack.stackPush()) {
                    var address = stack.mallocPointer(1);
                    FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), memory, 0, address), "Test font");
                    var provider = new TrueTypeGlyphProvider(memory, FT_Face.create(address.get()), definition.get("size").getAsFloat(), definition.get("oversample").getAsFloat(), 0, 0, "");
                    providers.add(provider);
                }
                }
                    for (int codepoint : "0123456789µΔ≥−→-°%/():,.ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".codePoints().toArray())
                        require(providers.stream().anyMatch(p -> p.getGlyph(codepoint) != null), face + " includes required symbol U+" + Integer.toHexString(codepoint));
            }
            for (var defaultFace : faces.keySet()) {
                var font = metrics(faces, defaultFace);
                for (var value : List.of("Timer", "Spikes", "Settings", "History", "Most updates", "Reset defaults", "World markers OFF", "Bottom right", "Page 10 of 12", "-123, 64, 456 | 12.35 µs/t", "io.github.chiselteam.chisel.api.family.VariantFamily$lambda$getAllVariants$0")) {
                    var full = UiFonts.layout(font, value, Integer.MAX_VALUE);
                    require(full.width() == font.width(UiFonts.rich(value)), "Measurement matches styled drawing");
                    for (int width : new int[]{0, 20, 64, 140, 288, 664}) {
                        var clipped = UiFonts.layout(font, value, width);
                        require(clipped.width() <= width && font.width(clipped.text()) == clipped.width(), "Clipped text remains within its hitbox at width " + width);
                        require(clipped == UiFonts.layout(font, value, width), "Repeated frames reuse cached text layouts");
                    }
                    var wrapped = font.split(UiFonts.rich(value), 140);
                    require(wrapped.stream().allMatch(line -> font.width(line) <= 140), "Long technical identifiers remain accessible through wrapped help");
                }
            }
            var help = UiFonts.tooltip("Trigger below 12.0 TPS; coordinates -123, 64, 456.");
            require(metrics(faces, UiFonts.UI).width(help) == metrics(faces, UiFonts.MONO).width(help), "Native tooltip hooks cannot reset the explicitly styled help fonts");
            var mono = faces.get(UiFonts.MONO).getFirst();
            float digit = mono.getGlyph('0').info().getAdvance(false);
            for (char c : "123456789".toCharArray()) require(mono.getGlyph(c).info().getAdvance(false) == digit, "Numerical columns use equal digit advances");
            var normal = metrics(faces, UiFonts.UI); var strong = metrics(faces, UiFonts.STRONG);
            for(var name:List.of("minecraft:grass_block","minecraft:creeper","minecraft:mob_spawner","Machine#tick","Machine lambda (unresolved)","Applied Energistics 2")) {
                var full=UiFonts.nameLayout(normal,name,Integer.MAX_VALUE);
                full.text().accept((i,style,c)->{require(style.getFont().equals(UiFonts.STRONG),"Every row-name glyph uses the same semibold sans-serif face: "+name);return true;});
                require(full.width()==normal.width(Component.literal(name).withStyle(s->s.withFont(UiFonts.STRONG))),"Row-name metrics match the semibold face without technical-font substitution");
                require(UiFonts.nameLayout(normal,name,72).width()<=72,"Long names clip before fixed measurement columns");
                require(UiFonts.nameLayout(normal,name,72)==UiFonts.nameLayout(normal,name,72),"Row-name layouts remain cached");
            }
            for(var base:List.of(UiFonts.UI,UiFonts.STRONG,UiFonts.HEADING,UiFonts.MONO)){
                require(!UiFonts.faceForScale(base,1).equals(base),"Scale 1 uses a native-resolution font face");
                for(int scale:List.of(2,3,4))require(UiFonts.faceForScale(base,scale).equals(base),"GUI scales 2/3/4 retain their existing font resources");
            }
            for(var face:List.of(UiFonts.UI,UiFonts.MONO))require(faces.get(UiFonts.faceForScale(face,1)).getFirst().getGlyph('0').info().getAdvance(false)>0,"Low-density numerical glyphs have valid native advances");
            require(!UiFonts.enlargedHudUsesOriginalAtlas(1,1.1f)&&UiFonts.enlargedHudUsesOriginalAtlas(1,2),"An enlarged HUD retains its original higher-resolution glyph atlas");
            require(!UiFonts.enlargedHudUsesOriginalAtlas(2,1),"Higher GUI scales keep their current path");
            var lowNormal=metrics(faces,UiFonts.faceForScale(UiFonts.UI,1));
            net.sprocketgames.lagsense.client.CallPathExpansionChecks.run(normal);
            net.sprocketgames.lagsense.client.CallPathExpansionChecks.run(lowNormal);
            FontDensityChecks.run();
            var navigation = new UiMetrics.Flow(16, 464, 44);
            for (var label : List.of("All", "Entities", "Blocks", "Ticks", "Chunks", "Map", "Tasks"))
                navigation.next(Math.max(24, Math.max(normal.width(UiFonts.rich(label)), strong.width(UiFonts.rich(label))) + 12));
            require(navigation.bottom() == 64, "Main navigation fits a normal desktop GUI viewport: " + List.of("All", "Entities", "Blocks", "Ticks", "Chunks", "Map", "Tasks").stream().map(label -> label + "=" + (Math.max(normal.width(UiFonts.rich(label)), strong.width(UiFonts.rich(label))) + 12)).toList());
            require(normal.width("Tick-work threshold ms") <= 138, "Capture labels fit the narrow two-column form");
            System.out.println("Typography checks passed (native TTF metrics, symbols, mixed faces, clipping, wrapping, numerical alignment and cached layouts).");
        } finally { faces.values().stream().flatMap(List::stream).forEach(TrueTypeGlyphProvider::close); }
    }
    private static String resource(String name) throws Exception { try (var in = TypographyChecks.class.getResourceAsStream(name)) { return new String(Objects.requireNonNull(in).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); } }
    private static Font metrics(Map<FontDescription, List<TrueTypeGlyphProvider>> faces, FontDescription defaultFace) {
        Map<FontDescription, GlyphSource> sources = new HashMap<>();
        return new Font(new Font.Provider() {
            @Override public GlyphSource glyphs(FontDescription face) {
                return sources.computeIfAbsent(face.equals(FontDescription.DEFAULT) ? defaultFace : UiFonts.faceForScale(face,defaultFace.toString().contains("_1x")?1:3), key -> new GlyphSource() {
                    final Map<Integer, BakedGlyph> glyphs = new HashMap<>();
                    @Override public BakedGlyph getGlyph(int codepoint) { return glyphs.computeIfAbsent(codepoint, c -> {
                        var info = Objects.requireNonNull(faces.get(key).stream().map(p -> p.getGlyph(c)).filter(Objects::nonNull).findFirst().orElse(null), "Missing test glyph " + c).info();
                        return new BakedGlyph() {
                            @Override public com.mojang.blaze3d.font.GlyphInfo info() { return info; }
                            @Override public net.minecraft.client.gui.font.TextRenderable.Styled createGlyph(float x, float y, int color, int shadow, Style style, float bold, float offset) { throw new UnsupportedOperationException("Metrics only"); }
                        };
                    }); }
                    @Override public BakedGlyph getRandomGlyph(RandomSource random, int width) { throw new UnsupportedOperationException(); }
                });
            }
            @Override public EffectGlyph effect() { throw new UnsupportedOperationException(); }
        });
    }
}
