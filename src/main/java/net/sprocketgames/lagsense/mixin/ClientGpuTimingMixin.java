package net.sprocketgames.lagsense.mixin;

import net.sprocketgames.lagsense.client.ClientFrames;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.systems.TimerQuery;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuses Minecraft's query lifecycle; never starts a competing query or waits for a result. */
@Mixin(Minecraft.class)
abstract class ClientGpuTimingMixin {
    @Shadow private TimerQuery.FrameProfile currentFrameProfile;
    @ModifyExpressionValue(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/debug/DebugScreenEntryList;isCurrentlyEnabled(Lnet/minecraft/resources/Identifier;)Z"))
    private boolean lagsense$gpuRequested(boolean original) { ClientFrames.gpuFrame(currentFrameProfile); return original || ClientFrames.wantsGpu(); }
    @WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/TimerQuery;endProfile()Lcom/mojang/blaze3d/systems/TimerQuery$FrameProfile;"))
    private TimerQuery.FrameProfile lagsense$gpuSubmitted(TimerQuery timer, Operation<TimerQuery.FrameProfile> original) {
        var profile = original.call(timer); ClientFrames.submitted(profile); return profile;
    }
    @Inject(method = "renderFrame", at = @At("TAIL"))
    private void lagsense$gpuResult(boolean advance, CallbackInfo ci) { ClientFrames.gpuFrame(currentFrameProfile); }
}
