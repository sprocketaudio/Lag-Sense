package net.sprocketgames.lagsense.mixin;

import net.sprocketgames.lagsense.Profiler;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
public class RandomTimingMixin {
    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void lagsense$block(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, Operation<Void> original) {
        if (!Profiler.active) { original.call(state, level, pos, random); return; }
        long start = System.nanoTime();boolean measured=Profiler.beginTarget(start);
        try { original.call(state, level, pos, random); }
        finally { long end=System.nanoTime();Profiler.endTarget(measured,end);Profiler.recordLocated(level, pos, state.getBlock(), "Random block", end - start); }
    }
    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void lagsense$fluid(FluidState state, ServerLevel level, BlockPos pos, RandomSource random, Operation<Void> original) {
        if (!Profiler.active) { original.call(state, level, pos, random); return; }
        long start = System.nanoTime();boolean measured=Profiler.beginTarget(start);
        try { original.call(state, level, pos, random); }
        finally { long end=System.nanoTime();Profiler.endTarget(measured,end);Profiler.recordLocated(level, pos, state.getType(), "Random fluid", end - start); }
    }
}
