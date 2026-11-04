package net.sprocketgames.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerSettingsRequest(String section, String json) implements CustomPacketPayload {
    public static final Type<ServerSettingsRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lagsense", "server_settings_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerSettingsRequest> CODEC = StreamCodec.of(
        (b,p) -> { b.writeUtf(p.section,16); b.writeUtf(p.json,12000); }, b -> new ServerSettingsRequest(b.readUtf(16),b.readUtf(12000)));
    @Override public Type<ServerSettingsRequest> type() { return TYPE; }
    @Override public String toString(){return "ServerSettingsRequest[section="+section+", json=<redacted>]";}
}
