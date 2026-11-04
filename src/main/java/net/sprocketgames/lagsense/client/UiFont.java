package net.sprocketgames.lagsense.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.EmptyArea;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4fc;

/** Keeps native glyph geometry and metrics; marks this mod's GUI glyphs for filtered sampling. */
public final class UiFont extends Font {
    public UiFont(Provider provider) { super(provider); }

    @Override public PreparedText prepareText(String value, float x, float y, int color, boolean shadow, int background) {
        return filtered(super.prepareText(value, x, y, color, shadow, background));
    }
    @Override public PreparedText prepareText(FormattedCharSequence value, float x, float y, int color, boolean shadow, boolean includeEmpty, int background) {
        return filtered(super.prepareText(value, x, y, color, shadow, includeEmpty, background));
    }
    private static PreparedText filtered(PreparedText nativeText) {
        // Scale 1 uses a native-resolution atlas; scales 2+ retain the existing 3x atlas.
        // Scale 3 remains one-to-one. Linear sampling also handles fractional HUD transforms.
        if (Minecraft.getInstance().getWindow().getGuiScale() == 3) return nativeText;
        return new PreparedText() {
            @Override public ScreenRectangle bounds() { return nativeText.bounds(); }
            @Override public void visit(GlyphVisitor visitor) {
                nativeText.visit(new GlyphVisitor() {
                    @Override public void acceptGlyph(TextRenderable.Styled glyph) { visitor.acceptGlyph(new SmoothGlyph(glyph)); }
                    @Override public void acceptEffect(TextRenderable effect) { visitor.acceptEffect(effect); }
                    @Override public void acceptEmptyArea(EmptyArea area) { visitor.acceptEmptyArea(area); }
                });
            }
        };
    }

    /** Only the sampler changes. Minecraft still creates, positions and draws every glyph. */
    @SuppressWarnings("deprecation")
    public record SmoothGlyph(TextRenderable.Styled nativeGlyph) implements TextRenderable.Styled {
        @Override public net.minecraft.network.chat.Style style() { return nativeGlyph.style(); }
        @Override public void render(Matrix4fc pose, VertexConsumer buffer, int light, boolean flat) { nativeGlyph.render(pose, buffer, light, flat); }
        @Override public RenderType renderType(DisplayMode mode) { return nativeGlyph.renderType(mode); }
        @Override public RenderType renderType(DisplayMode mode, boolean blur) { return nativeGlyph.renderType(mode, blur); }
        @Override public GpuTextureView textureView() { return nativeGlyph.textureView(); }
        @Override public RenderPipeline guiPipeline() { return nativeGlyph.guiPipeline(); }
        @Override public float left() { return nativeGlyph.left(); }
        @Override public float top() { return nativeGlyph.top(); }
        @Override public float right() { return nativeGlyph.right(); }
        @Override public float bottom() { return nativeGlyph.bottom(); }
        @Override public float activeLeft() { return nativeGlyph.activeLeft(); }
        @Override public float activeTop() { return nativeGlyph.activeTop(); }
        @Override public float activeRight() { return nativeGlyph.activeRight(); }
        @Override public float activeBottom() { return nativeGlyph.activeBottom(); }
    }
}
