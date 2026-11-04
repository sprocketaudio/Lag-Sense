package net.sprocketgames.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Optional segmented reply for complete call trees, using the existing bounded assembly. */
public record InvestigationPart(FullReportPart part) implements CustomPacketPayload {
    public static final Type<InvestigationPart> TYPE=new Type<>(Identifier.fromNamespaceAndPath("lagsense","investigation_part"));
    public static final StreamCodec<RegistryFriendlyByteBuf,InvestigationPart> CODEC=StreamCodec.of((b,p)->FullReportPart.CODEC.encode(b,p.part()),b->new InvestigationPart(FullReportPart.CODEC.decode(b)));
    @Override public Type<InvestigationPart> type(){return TYPE;}
}
