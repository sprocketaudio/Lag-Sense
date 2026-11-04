package net.sprocketgames.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerSettingsPayload(String section, String json, String message, boolean saved) implements CustomPacketPayload {
    public static final Type<ServerSettingsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lagsense", "server_settings_form"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerSettingsPayload> CODEC = StreamCodec.of(
        (b,p) -> { b.writeUtf(p.section,16); b.writeUtf(p.json,6000); b.writeUtf(p.message,512); b.writeBoolean(p.saved); },
        b -> new ServerSettingsPayload(b.readUtf(16),b.readUtf(6000),b.readUtf(512),b.readBoolean()));
    @Override public Type<ServerSettingsPayload> type() { return TYPE; }
}
