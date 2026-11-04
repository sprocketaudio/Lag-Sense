package audio.sprocket.lagsense.compat;

import audio.sprocket.lagsense.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

public final class ClaimCompatibility {
    private static final Set<String> warned = new HashSet<>();
    private ClaimCompatibility() {}

    public static BaseAccess forPlayer(ServerPlayer player, List<PublicArea> areas) {
        var providers = new ArrayList<BaseAccess.Provider>();
        if (ModList.get().isLoaded("ftbchunks")) providers.add(guard("FTB Chunks",
            () -> FtbClaimAccess.forPlayer(player.level().getServer(), player.getUUID())));
        if (ModList.get().isLoaded("openpartiesandclaims")) providers.add(guard("Open Parties and Claims",
            () -> OpacClaimAccess.forPlayer(player.level().getServer(), player.getUUID())));
        return new BaseAccess(areas, providers);
    }

    private static BaseAccess.Provider guard(String name, Supplier<BaseAccess.Provider> factory) {
        try {
            BaseAccess.Provider provider = factory.get();
            return new BaseAccess.Provider() {
                private boolean failed;
                @Override public BaseAccess.Decision check(BaseAccess.ChunkKey chunk) {
                    if (failed) return BaseAccess.Decision.DENY;
                    try { return provider.check(chunk); }
                    catch (RuntimeException | LinkageError error) {
                        failed = true; failure(name, error); return BaseAccess.Decision.DENY;
                    }
                }
            };
        } catch (RuntimeException | LinkageError error) {
            failure(name, error); return chunk -> BaseAccess.Decision.DENY;
        }
    }

    private static void failure(String name, Throwable error) {
        if (warned.add(name)) LagSense.LOG.warn("{} claim ownership checks failed; claim access denied. Explicit public areas still apply.", name, error);
    }
}
