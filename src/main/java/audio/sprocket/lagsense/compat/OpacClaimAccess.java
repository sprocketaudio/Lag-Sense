package audio.sprocket.lagsense.compat;

import audio.sprocket.lagsense.BaseAccess;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import xaero.pac.common.claims.api.SpecialClaimOwners;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.config.ServerConfig;

/** OPAC's configured primary party system determines ownership of party-owned claims. */
final class OpacClaimAccess {
    static BaseAccess.Provider forPlayer(MinecraftServer server, UUID viewer) {
        if (!ServerConfig.CONFIG.claimsEnabled.get()) return key -> BaseAccess.Decision.UNCLAIMED;
        var api = OpenPACServerAPI.get(server);
        var party = ServerConfig.CONFIG.partyOwnedClaims.get() ? api.getPlayerConfigManager().getPartyOwnerConfig(viewer) : null;
        UUID partyOwner = party == null ? null : party.getPlayerId();
        return key -> {
            var claim = api.getServerClaimsManager().get(Identifier.parse(key.dimension()), key.x(), key.z());
            if (claim == null) return BaseAccess.Decision.UNCLAIMED;
            UUID owner = claim.getPlayerId();
            if (owner == null || owner.equals(SpecialClaimOwners.SERVER) || owner.equals(SpecialClaimOwners.EXPIRED)) return BaseAccess.Decision.DENY;
            return viewer.equals(owner) || owner.equals(partyOwner) ? BaseAccess.Decision.ALLOW : BaseAccess.Decision.DENY;
        };
    }
}
