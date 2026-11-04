package net.sprocketgames.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ArchiveRequestPayload(String action, long first, long second, boolean enabled, int count, int mebibytes) implements CustomPacketPayload {
    public static final Type<ArchiveRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lagsense", "archive_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArchiveRequestPayload> CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeUtf(p.action, 16); buf.writeLong(p.first); buf.writeLong(p.second); buf.writeBoolean(p.enabled); buf.writeVarInt(p.count); buf.writeVarInt(p.mebibytes); },
        buf -> new ArchiveRequestPayload(buf.readUtf(16), buf.readLong(), buf.readLong(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt()));
    @Override public Type<ArchiveRequestPayload> type() { return TYPE; }
}
