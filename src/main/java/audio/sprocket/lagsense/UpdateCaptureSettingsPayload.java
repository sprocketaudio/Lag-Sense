package audio.sprocket.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record UpdateCaptureSettingsPayload(String json) implements CustomPacketPayload {
    public static final Type<UpdateCaptureSettingsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lagsense", "update_capture_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateCaptureSettingsPayload> CODEC = StreamCodec.of(
        (buf, p) -> buf.writeUtf(p.json, 8000), buf -> new UpdateCaptureSettingsPayload(buf.readUtf(8000)));
    @Override public Type<UpdateCaptureSettingsPayload> type() { return TYPE; }
}
