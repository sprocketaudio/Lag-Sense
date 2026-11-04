package audio.sprocket.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ComparisonPayload(String json) implements CustomPacketPayload {
    public static final Type<ComparisonPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lagsense", "comparison"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ComparisonPayload> CODEC = StreamCodec.of(
        (buf, p) -> buf.writeUtf(p.json, 200000), buf -> new ComparisonPayload(buf.readUtf(200000)));
    @Override public Type<ComparisonPayload> type() { return TYPE; }
}
