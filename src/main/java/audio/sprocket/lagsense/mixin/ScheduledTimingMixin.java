package audio.sprocket.lagsense.mixin;

import audio.sprocket.lagsense.Profiler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
public class ScheduledTimingMixin {
    @WrapOperation(method = "tickBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void lagsense$block(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, Operation<Void> original) {
        if (!Profiler.active) { original.call(state, level, pos, random); return; }
        long start = System.nanoTime();boolean measured=Profiler.beginTarget(start);
        try { original.call(state, level, pos, random); }
        finally { long end=System.nanoTime();Profiler.endTarget(measured,end);Profiler.recordScheduled(level, pos, state.getBlock(), false, end - start); }
    }
    @WrapOperation(method = "tickFluid", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void lagsense$fluid(FluidState state, ServerLevel level, BlockPos pos, BlockState block, Operation<Void> original) {
        if (!Profiler.active) { original.call(state, level, pos, block); return; }
        long start = System.nanoTime();boolean measured=Profiler.beginTarget(start);
        try { original.call(state, level, pos, block); }
        finally { long end=System.nanoTime();Profiler.endTarget(measured,end);Profiler.recordScheduled(level, pos, state.getType(), true, end - start); }
    }
}
