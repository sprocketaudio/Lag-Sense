package net.sprocketgames.lagsense;

import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Short-lived server authorization for the displayed report and optional measured-target marker. */
public record ProfilerStatePayload(String json) implements CustomPacketPayload {
    public record State(boolean admin,boolean clear,boolean open,long report,int leaseTicks,boolean markerChanged,Report.Entry marker,int ticks,String message,boolean canReturn,EntityTracking trackedEntity,String markerStatus){
        public State(boolean admin,boolean clear,boolean open,long report,int leaseTicks,boolean markerChanged,Report.Entry marker,int ticks,String message,boolean canReturn,EntityTracking trackedEntity){this(admin,clear,open,report,leaseTicks,markerChanged,marker,ticks,message,canReturn,trackedEntity,null);}
        public State(boolean admin,boolean clear,boolean open,long report,int leaseTicks,boolean markerChanged,Report.Entry marker,int ticks,String message,boolean canReturn){this(admin,clear,open,report,leaseTicks,markerChanged,marker,ticks,message,canReturn,null);}
        public State(boolean admin,boolean clear,boolean open,long report,int leaseTicks,boolean markerChanged,Report.Entry marker,int ticks,String message){this(admin,clear,open,report,leaseTicks,markerChanged,marker,ticks,message,false);}
    }
    public static final Type<ProfilerStatePayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("lagsense","profiler_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ProfilerStatePayload> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,16384),b->new ProfilerStatePayload(b.readUtf(16384)));
    public ProfilerStatePayload(State state){this(Profiler.JSON.toJson(state));}
    @Override public Type<ProfilerStatePayload> type(){return TYPE;}
}
