package audio.sprocket.lagsense.mixin;

import audio.sprocket.lagsense.client.ClientFrames;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
abstract class ClientRenderTimingMixin {
    @WrapMethod(method = "extract")
    private void lagsense$extract(DeltaTracker delta, boolean advance, Operation<Void> original) {
        if (!ClientFrames.active) { original.call(delta, advance); return; }
        long start = System.nanoTime(); try { original.call(delta, advance); } finally { ClientFrames.stage("Frame preparation", System.nanoTime() - start); }
    }
    @WrapMethod(method = "render")
    private void lagsense$render(DeltaTracker delta, boolean advance, Operation<Void> original) {
        if (!ClientFrames.active) { original.call(delta, advance); return; }
        long start = System.nanoTime(); try { original.call(delta, advance); } finally { ClientFrames.stage("Render submission", System.nanoTime() - start); }
    }
    @WrapMethod(method = "renderLevel")
    private void lagsense$world(DeltaTracker delta, Operation<Void> original) {
        if (!ClientFrames.active) { original.call(delta); return; }
        long start = System.nanoTime(); try { original.call(delta); } finally { ClientFrames.stage("World submission", System.nanoTime() - start); }
    }
}
