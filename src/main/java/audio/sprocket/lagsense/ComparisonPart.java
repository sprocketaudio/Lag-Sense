package audio.sprocket.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Same bounded segmentation as full reports; separate channel and assembly prevent interleaving. */
public record ComparisonPart(FullReportPart part) implements CustomPacketPayload {
    public static final Type<ComparisonPart> TYPE=new Type<>(Identifier.fromNamespaceAndPath("lagsense","comparison_full"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ComparisonPart> CODEC=StreamCodec.of((b,p)->FullReportPart.CODEC.encode(b,p.part()),b->new ComparisonPart(FullReportPart.CODEC.decode(b)));
    @Override public Type<ComparisonPart> type(){return TYPE;}
}
