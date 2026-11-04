package net.sprocketgames.lagsense.mixin;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reuse the live Minecraft font resources without replacing the game's default font. */
@Mixin(Font.class)
public interface FontProviderAccessor {
    @Accessor("provider") Font.Provider lagsense$provider();
}
