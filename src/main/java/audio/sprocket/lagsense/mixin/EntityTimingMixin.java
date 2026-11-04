package audio.sprocket.lagsense.mixin;

import audio.sprocket.lagsense.Profiler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
public class EntityTimingMixin {
    @WrapOperation(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void lagsense$tick(Entity entity, Operation<Void> original) {
        if (!Profiler.active) { original.call(entity); return; }
        var level=entity.level();var position=entity.blockPosition();
        long start = System.nanoTime();boolean measured=Profiler.beginTarget(start);
        try { original.call(entity); } finally { long end=System.nanoTime();Profiler.endTarget(measured,end);Profiler.record(entity,level,position,end - start); }
    }
    @WrapOperation(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
    private void lagsense$ride(Entity entity, Operation<Void> original) {
        if (!Profiler.active) { original.call(entity); return; }
        var level=entity.level();var position=entity.blockPosition();
        long start = System.nanoTime();boolean measured=Profiler.beginTarget(start);
        try { original.call(entity); } finally { long end=System.nanoTime();Profiler.endTarget(measured,end);Profiler.record(entity,level,position,end - start); }
    }
}
