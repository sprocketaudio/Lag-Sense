package audio.sprocket.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CaptureSettingsPayload(String json, String message) implements CustomPacketPayload {
    public static final Type<CaptureSettingsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lagsense", "capture_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CaptureSettingsPayload> CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeUtf(p.json, 8000); buf.writeUtf(p.message, 512); }, buf -> new CaptureSettingsPayload(buf.readUtf(8000), buf.readUtf(512)));
    @Override public Type<CaptureSettingsPayload> type() { return TYPE; }
}
