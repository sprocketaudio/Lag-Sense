package net.sprocketgames.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record InvestigationPayload(long token, byte[] bytes) implements CustomPacketPayload {
    public static final Type<InvestigationPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lagsense", "investigation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InvestigationPayload> CODEC = StreamCodec.of(
        (b, p) -> { b.writeLong(p.token); b.writeByteArray(p.bytes); }, b -> new InvestigationPayload(b.readLong(), b.readByteArray(512*1024)));
    @Override public Type<InvestigationPayload> type() { return TYPE; }
}
