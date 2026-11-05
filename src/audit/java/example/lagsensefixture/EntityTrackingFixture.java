package example.lagsensefixture;

import net.sprocketgames.lagsense.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;

final class EntityTrackingFixture {
    private static int warmup;
    static boolean ready(MinecraftServer server){
        if(warmup++==0){
            server.overworld().setChunkForced(8,0,true);server.overworld().getChunk(8,0);
            var nether=server.getLevel(net.minecraft.world.level.Level.NETHER);nether.setChunkForced(0,0,true);nether.getChunk(0,0);
        }
        return warmup>=12;
    }
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static void run(MinecraftServer server)throws Exception{
        class Viewer extends net.neoforged.neoforge.common.util.FakePlayer {
            boolean operator;
            Viewer(boolean operator){super(server.overworld(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"TrackingAudit"));this.operator=operator;}
            @Override public net.minecraft.server.permissions.PermissionSet permissions(){return operator?net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER:net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS;}
        }
        var viewer=new Viewer(true);var reader=new Viewer(false);var states=new HashMap<UUID,ProfilerStatePayload.State>();
        var constructor=ProfilerUi.class.getDeclaredConstructor(MinecraftServer.class,java.util.function.Function.class,java.util.function.BiConsumer.class,java.util.function.BiFunction.class);constructor.setAccessible(true);
        java.util.function.Function<UUID,ServerPlayer> lookup=id->id.equals(viewer.getUUID())?viewer:id.equals(reader.getUUID())?reader:null;
        java.util.function.BiConsumer<ServerPlayer,net.minecraft.network.protocol.common.custom.CustomPacketPayload> send=(p,payload)->{if(payload instanceof ProfilerStatePayload value)states.put(p.getUUID(),Profiler.JSON.fromJson(value.json(),ProfilerStatePayload.State.class));};
        java.util.function.BiFunction<ServerPlayer,List<PublicArea>,BaseAccess> access=(p,areas)->new BaseAccess(List.of(),List.of(k->k.dimension().equals("minecraft:overworld")&&k.x()==0&&k.z()==0?BaseAccess.Decision.ALLOW:BaseAccess.Decision.DENY));
        var ui=constructor.newInstance(server,lookup,send,access);
        var level=server.overworld();var nether=server.getLevel(net.minecraft.world.level.Level.NETHER);ArmorStand mob=null;
        try{
            level.getChunk(0,0);level.getChunk(8,0);nether.getChunk(0,0);
            mob=new ArmorStand(level,4,81,4);mob.setNoGravity(true);level.addFreshEntity(mob);UUID id=mob.getUUID();
            var entry=new Report.Entry("Entity","minecraft:armor_stand","minecraft:overworld",4,81,4,1234567,200,id);
            var source=new Report(92341,1000,200,2,true,false,List.of(entry));
            ui.grant(viewer,source);ui.grant(reader,source.view(false,access.apply(reader,List.of())));
            var track=ProfilerUi.class.getDeclaredMethod("track",ServerPlayer.class,long.class,int.class);track.setAccessible(true);
            check(level.getEntity(id)==mob,"Fixture entity is UUID-resolvable: added="+!mob.isRemoved()+", alive="+mob.isAlive()+", server levels="+server.getAllLevels());
            track.invoke(ui,viewer,source.id(),0);
            check(states.get(viewer.getUUID()).marker()!=null,"Tracking request accepts the actual report/index identity");
            check(states.get(viewer.getUUID()).trackedEntity()!=null,"Marked entity is alive and present on server: alive="+mob.isAlive()+", removed="+mob.isRemoved()+", source="+states.get(viewer.getUUID()).marker());
            check(states.get(viewer.getUUID()).trackedEntity().x()==4,"Mark starts tracking the recorded UUID's actual server position");
            ui.grant(viewer,source);
            check(!states.get(viewer.getUUID()).markerChanged()&&states.get(viewer.getUUID()).marker().equals(entry)&&states.get(viewer.getUUID()).trackedEntity()!=null,"Reopening the marked capture preserves server tracking instead of sending an Unmark");
            check(viewer.teleportTo(level,6,81,4,Set.of(),0,0,true),"Marked viewer can teleport near the entity");
            for(int i=0;i<20;i++)ui.tick();
            check(states.get(viewer.getUUID()).marker().equals(entry)&&states.get(viewer.getUUID()).trackedEntity()!=null,"Teleport and lease renewal preserve the marked entity");
            mob.snapTo(134.75,86.5,8.25,0,0);for(int i=0;i<20;i++)ui.tick();
            var moved=states.get(viewer.getUUID());
            check(moved.trackedEntity().x()==134.75&&moved.trackedEntity().y()==86.5&&moved.marker().equals(entry),"Server guidance follows a mob across chunks beyond client tracking distance while captured coordinates and timing remain unchanged");
            mob.snapTo(16000,86.5,16000,0,0);for(int i=0;i<20;i++)ui.tick();
            check(states.get(viewer.getUUID()).trackedEntity()==null&&states.get(viewer.getUUID()).markerStatus().equals("unloaded"),"An entity outside server-loaded sections is unavailable rather than replaced by capture coordinates");
            mob.snapTo(134.75,86.5,8.25,0,0);for(int i=0;i<20;i++)ui.tick();
            check(states.get(viewer.getUUID()).trackedEntity()!=null,"Tracking resumes for the same UUID when its section becomes available again");
            mob.setHealth(0);for(int i=0;i<20;i++)ui.tick();
            check(states.get(viewer.getUUID()).trackedEntity()==null&&states.get(viewer.getUUID()).markerStatus().equals("dead"),"A dead entity stops producing tracking coordinates before its removal packet");
            mob.setHealth(20);
            int packets=states.size();track.invoke(ui,reader,source.id(),0);
            check(states.size()==packets&&states.get(reader.getUUID()).trackedEntity()==null,"An ordinary player cannot use the operator archive-tracking request");
            check(mob.teleportTo(nether,10.25,82,10.75,Set.of(),0,0,true),"The fixture entity can change dimension");
            mob=(ArmorStand)nether.getEntity(id);check(mob!=null,"The same entity UUID resolves after a real dimension transfer");
            for(int i=0;i<20;i++)ui.tick();
            check(states.get(viewer.getUUID()).trackedEntity().dimension().equals("minecraft:the_nether"),"Marked server guidance follows the same UUID into another dimension");
            mob.discard();for(int i=0;i<20;i++)ui.tick();
            check(states.get(viewer.getUUID()).trackedEntity()==null&&states.get(viewer.getUUID()).markerStatus().equals("removed")&&states.get(viewer.getUUID()).marker().equals(entry),"Removed or unloaded entities have no live location and never substitute their capture position");
            var unrelated=new ArmorStand(nether,10.25,82,10.75);unrelated.setNoGravity(true);nether.addFreshEntity(unrelated);
            try{for(int i=0;i<20;i++)ui.tick();check(states.get(viewer.getUUID()).trackedEntity()==null,"A different mob of the same type at the old location cannot inherit a marked UUID");}finally{unrelated.discard();}
            reader.operator=true;ui.grant(reader,source);track.invoke(ui,reader,source.id(),0);
            check(states.get(reader.getUUID()).trackedEntity()==null&&states.get(reader.getUUID()).markerStatus().equals("not_found"),"Marking an already missing UUID reports an unknown cause rather than claiming an observed death or unload");
            ui.request(viewer,new ProfilerRequest("unmark"));check(states.get(viewer.getUUID()).marker()==null&&states.get(viewer.getUUID()).trackedEntity()==null,"Unmark clears both the selected target and remote tracking");
            track.invoke(ui,viewer,source.id(),0);
            viewer.operator=false;for(int i=0;i<20;i++)ui.tick();
            check(states.get(viewer.getUUID()).clear()&&states.get(viewer.getUUID()).marker()==null&&states.get(viewer.getUUID()).trackedEntity()==null,"Permission revocation removes the mark and all server-tracked coordinates");
            ui.forget(viewer.getUUID());
            System.out.println("Entity tracking server fixture passed: UUID identity, moved chunks, remote coordinates, real dimension transfer, immutable capture data, unavailable entities, unrelated replacement rejection and fresh permissions.");
        }finally{if(mob!=null&&!mob.isRemoved())mob.discard();ui.close();viewer.discard();reader.discard();}
    }
}
