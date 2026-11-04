package audio.sprocket.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Permission-scoped overview, without credentials or private capture data. */
public record SettingsPayload(boolean admin, String timer, String spikes, String history, String discord) implements CustomPacketPayload {
    public static final Type<SettingsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lagsense", "settings_home"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SettingsPayload> CODEC = StreamCodec.of(
        (b,p) -> { b.writeBoolean(p.admin); b.writeUtf(p.timer,512); b.writeUtf(p.spikes,512); b.writeUtf(p.history,512); b.writeUtf(p.discord,512); },
        b -> new SettingsPayload(b.readBoolean(), b.readUtf(512), b.readUtf(512), b.readUtf(512), b.readUtf(512)));
    public static SettingsPayload view(boolean admin, ServerSettings settings, String discord) {
        return admin ? new SettingsPayload(true, StatusText.timer(settings.timer()), StatusText.onOff(settings.spikes().enabled()), StatusText.onOff(settings.history().enabled()), discord) : new SettingsPayload(false,"","","","");
    }
    @Override public Type<SettingsPayload> type() { return TYPE; }
}
