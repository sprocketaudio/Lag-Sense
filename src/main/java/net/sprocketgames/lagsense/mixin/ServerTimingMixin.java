package net.sprocketgames.lagsense.mixin;

import net.sprocketgames.lagsense.Profiler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;

/** Encloses packet processing and the full server tick, including every Pre/Post listener. */
@Mixin(MinecraftServer.class)
public abstract class ServerTimingMixin {
    @WrapMethod(method="processPacketsAndTick")
    private void lagsense$cycle(boolean sprinting,Operation<Void> original){
        Profiler.pre();
        try{original.call(sprinting);}finally{Profiler.post((MinecraftServer)(Object)this);}
    }
}
