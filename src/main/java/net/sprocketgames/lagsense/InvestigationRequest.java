package net.sprocketgames.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record InvestigationRequest(long token, long report, boolean archived, boolean cpu, boolean global, String dimension, int x, int z) implements CustomPacketPayload {
    public static final Type<InvestigationRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lagsense", "investigate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InvestigationRequest> CODEC = StreamCodec.of(
        (b, p) -> { b.writeLong(p.token); b.writeLong(p.report); b.writeBoolean(p.archived); b.writeBoolean(p.cpu); b.writeBoolean(p.global); b.writeUtf(p.dimension, 512); b.writeInt(p.x); b.writeInt(p.z); },
        b -> new InvestigationRequest(b.readLong(), b.readLong(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readUtf(512), b.readInt(), b.readInt()));
    @Override public Type<InvestigationRequest> type() { return TYPE; }
}
