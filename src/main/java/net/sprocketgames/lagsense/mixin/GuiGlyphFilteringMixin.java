package net.sprocketgames.lagsense.mixin;

import net.sprocketgames.lagsense.client.UiFont;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Minecraft's deferred GUI glyph path hardcodes NEAREST, ignoring Font's filtering flag. */
@Mixin(GlyphRenderState.class)
abstract class GuiGlyphFilteringMixin {
    @Shadow @Final private TextRenderable renderable;

    @Inject(method = "textureSetup", at = @At("HEAD"), cancellable = true)
    private void lagsense$sampleGuiGlyph(CallbackInfoReturnable<TextureSetup> result) {
        if (renderable instanceof UiFont.SmoothGlyph)
            result.setReturnValue(TextureSetup.singleTextureWithLightmap(renderable.textureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)));
    }
}
