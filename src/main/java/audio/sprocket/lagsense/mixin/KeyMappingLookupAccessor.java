package audio.sprocket.lagsense.mixin;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyMappingLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Use NeoForge's real modifier/conflict lookup, including other mods and remapped keys. */
@Mixin(KeyMapping.class)
public interface KeyMappingLookupAccessor {
    @Accessor("MAP") static KeyMappingLookup lagsense$lookup(){throw new AssertionError("Mixin accessor not applied");}
}
