package audio.sprocket.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ArchivePayload(String json, String message) implements CustomPacketPayload {
    public static final Type<ArchivePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lagsense", "archive"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArchivePayload> CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeUtf(p.json, 32000); buf.writeUtf(p.message, 512); },
        buf -> new ArchivePayload(buf.readUtf(32000), buf.readUtf(512)));
    @Override public Type<ArchivePayload> type() { return TYPE; }
}
