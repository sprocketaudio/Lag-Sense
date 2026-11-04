package audio.sprocket.lagsense;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AreaPayload(String json) implements CustomPacketPayload {
    public record State(List<PublicArea> areas, PublicArea.Selection selection, boolean display) {}
    public static final Type<AreaPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lagsense", "areas"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AreaPayload> CODEC = StreamCodec.of(
        (buf, p) -> buf.writeUtf(p.json, 32000), buf -> new AreaPayload(buf.readUtf(32000)));
    @Override public Type<AreaPayload> type() { return TYPE; }
}
