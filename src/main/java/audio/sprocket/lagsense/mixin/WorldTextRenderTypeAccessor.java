package audio.sprocket.lagsense.mixin;
import net.minecraft.client.renderer.rendertype.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Read the native atlas binding without changing Minecraft's render types. */
@Mixin(RenderType.class)
public interface WorldTextRenderTypeAccessor {@Accessor("state") RenderSetup lagsense$setup();}
