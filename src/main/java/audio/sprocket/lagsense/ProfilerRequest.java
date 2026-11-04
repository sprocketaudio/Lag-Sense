package audio.sprocket.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** UI actions carry report/index identities, never client-supplied world coordinates or permissions. */
public record ProfilerRequest(String action,long report,int index) implements CustomPacketPayload {
    public static final Type<ProfilerRequest> TYPE=new Type<>(Identifier.fromNamespaceAndPath("lagsense","profiler_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ProfilerRequest> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.action,32);b.writeLong(p.report);b.writeInt(p.index);},b->new ProfilerRequest(b.readUtf(32),b.readLong(),b.readInt()));
    public ProfilerRequest(String action){this(action,0,-1);}
    @Override public Type<ProfilerRequest> type(){return TYPE;}
}
