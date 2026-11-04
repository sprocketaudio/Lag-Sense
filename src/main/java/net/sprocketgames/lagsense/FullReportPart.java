package net.sprocketgames.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FullReportPart(long transfer,int total,int offset,byte[] bytes,boolean open,boolean archived) implements CustomPacketPayload {
    public static final int PART_BYTES=64*1024,MAX_BYTES=16*1024*1024;
    public static final Type<FullReportPart> TYPE=new Type<>(Identifier.fromNamespaceAndPath("lagsense","full_report"));
    public FullReportPart {if(transfer<1||total<1||total>MAX_BYTES||offset<0||bytes==null||bytes.length<1||bytes.length>PART_BYTES||offset>(long)total-bytes.length)throw new IllegalArgumentException("Invalid report segment");}
    public static final StreamCodec<RegistryFriendlyByteBuf,FullReportPart> CODEC=StreamCodec.of(
        (b,p)->{b.writeLong(p.transfer());b.writeVarInt(p.total());b.writeVarInt(p.offset());b.writeByteArray(p.bytes());b.writeBoolean(p.open());b.writeBoolean(p.archived());},
        b->new FullReportPart(b.readLong(),b.readVarInt(),b.readVarInt(),b.readByteArray(PART_BYTES),b.readBoolean(),b.readBoolean()));
    @Override public Type<FullReportPart> type(){return TYPE;}
}
