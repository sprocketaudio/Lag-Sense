package net.sprocketgames.lagsense.compat;

import net.sprocketgames.lagsense.BaseAccess;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;

/** Loaded only when FTB Chunks is installed. Allies and public build access are not ownership. */
final class FtbClaimAccess {
    static BaseAccess.Provider forPlayer(MinecraftServer server, UUID viewer) {
        var api = FTBChunksAPI.api();
        return key -> {
            if (!api.isManagerLoaded()) return BaseAccess.Decision.DENY;
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(key.dimension())));
            if (level == null) return BaseAccess.Decision.DENY;
            var team = api.getOwningTeam(level, new ChunkPos(key.x(), key.z()));
            if (team.isEmpty()) return BaseAccess.Decision.UNCLAIMED;
            var owner = team.get();
            if (owner.isServerTeam()) return BaseAccess.Decision.DENY;
            return viewer.equals(owner.getOwner()) || owner.getMembers().contains(viewer)
                ? BaseAccess.Decision.ALLOW : BaseAccess.Decision.DENY;
        };
    }
}
