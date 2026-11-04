package audio.sprocket.lagsense.mixin;
import audio.sprocket.lagsense.client.ShortcutBindings;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.client.*;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Vanilla fullscreen/screenshot checks run before normal modified keybinding dispatch. */
@Mixin(KeyboardHandler.class)
abstract class ShortcutKeyboardMixin {
    @Inject(method="keyPress",at=@At("HEAD"))
    private void lagsense$press(long window,int action,KeyEvent event,CallbackInfo callback){ShortcutBindings.key(window,action,event);}
    @WrapOperation(method="keyPress",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;matches(Lnet/minecraft/client/input/KeyEvent;)Z"))
    private boolean lagsense$globalKey(KeyMapping mapping,KeyEvent event,Operation<Boolean> original){
        boolean matches=original.call(mapping,event);var mc=Minecraft.getInstance();
        return matches&&!(mc.options!=null&&(mapping==mc.options.keyFullscreen||mapping==mc.options.keyScreenshot)&&ShortcutBindings.claimsModified(event));
    }
}
