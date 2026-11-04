package net.sprocketgames.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Optional extension: older clients keep using the initial 128-target investigation. */
public record ChunkBatchRequest(InvestigationRequest context, String category, int offset) implements CustomPacketPayload {
    public static final Type<ChunkBatchRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lagsense","chunk_batch"));
    public ChunkBatchRequest {
        if(context == null || context.cpu() || category == null || category.length()>512 || offset<0 || offset>CaptureBudget.MAX_ENTRIES) throw new IllegalArgumentException("Invalid chunk batch");
    }
    public static final StreamCodec<RegistryFriendlyByteBuf,ChunkBatchRequest> CODEC=StreamCodec.of(
        (b,p)->{InvestigationRequest.CODEC.encode(b,p.context());b.writeUtf(p.category(),512);b.writeVarInt(p.offset());},
        b->new ChunkBatchRequest(InvestigationRequest.CODEC.decode(b),b.readUtf(512),b.readVarInt()));
    @Override public Type<ChunkBatchRequest> type(){return TYPE;}
}
