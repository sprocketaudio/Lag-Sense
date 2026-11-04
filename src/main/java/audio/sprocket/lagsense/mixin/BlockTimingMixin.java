package audio.sprocket.lagsense.mixin;

import audio.sprocket.lagsense.Profiler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Time the level's actual dispatch, including custom/delegated ticker implementations. */
@Mixin(Level.class)
public abstract class BlockTimingMixin {
    @WrapOperation(method="tickBlockEntities",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private void lagsense$dispatch(TickingBlockEntity ticker,Operation<Void> original){
        var level=(Level)(Object)this;
        if(!Profiler.active||level.isClientSide()){original.call(ticker);return;}
        var position=ticker.getPos().immutable();String type=ticker.getType();
        long start=System.nanoTime();boolean measured=Profiler.beginTarget(start);
        try{original.call(ticker);}finally{long end=System.nanoTime();Profiler.endTarget(measured,end);Profiler.recordTicker(level,position,type,end-start);}
    }
}
