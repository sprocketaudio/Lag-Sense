package net.sprocketgames.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ReportPayload(String json, boolean open, boolean archived) implements CustomPacketPayload {
    public ReportPayload(String json, boolean open) { this(json, open, false); }
    public static final Type<ReportPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lagsense", "report"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReportPayload> CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeUtf(p.json, 200000); buf.writeBoolean(p.open); buf.writeBoolean(p.archived); },
        buf -> new ReportPayload(buf.readUtf(200000), buf.readBoolean(), buf.readBoolean()));
    @Override public Type<ReportPayload> type() { return TYPE; }
}
