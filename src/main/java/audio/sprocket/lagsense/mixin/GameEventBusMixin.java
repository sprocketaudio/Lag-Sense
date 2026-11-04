package audio.sprocket.lagsense.mixin;

import audio.sprocket.lagsense.ProfilingEventBus;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=NeoForge.class,remap=false)
public abstract class GameEventBusMixin {
    @ModifyExpressionValue(method="<clinit>",at=@At(value="INVOKE",target="Lnet/neoforged/bus/api/BusBuilder;build()Lnet/neoforged/bus/api/IEventBus;"))
    private static IEventBus lagsense$bus(IEventBus original){return ProfilingEventBus.wrap(original);}
}
