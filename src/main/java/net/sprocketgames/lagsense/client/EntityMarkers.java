package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/** Initial scan is of client-loaded entities only; joins/leaves maintain matches thereafter. */
final class EntityMarkers {
    private static ClientLevel level;
    private static MarkerIndex index;
    private static Report.Entry waypoint;
    private static String dimension;
    private static final Map<UUID,Entity> entities=new HashMap<>();
    private static List<Report.Entry> ordered=List.of();
    private static boolean dirty;
    static void register(){
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload event)->{if(event.getLevel()==level)clear();});
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e)->{if(e.getLevel()==level&&index!=null)add(e.getEntity());});
        NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent e)->{if(e.getLevel()==level&&entities.remove(e.getEntity().getUUID(),e.getEntity()))dirty=true;});
    }
    static void bind(ClientLevel current,MarkerIndex data,Report.Entry marked) {
        if(level==current&&index==data&&waypoint==marked)return;
        clear();level=current;index=data;waypoint=marked;dimension=current.dimension().identifier().toString();
        for(var entity:current.entitiesForRendering())add(entity);
    }
    private static void add(Entity entity){if(measured(index,waypoint,dimension,entity.getUUID(),net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())!=null){entities.put(entity.getUUID(),entity);dirty=true;}}
    static Report.Entry measured(MarkerIndex data,Report.Entry marked,String dimension,UUID id,String type){
        var entry=marked!=null&&id.equals(marked.entityId())?marked:data.entity(dimension,id);
        return entry!=null&&entry.type().equals(type)?entry:null;
    }
    private static Report.Entry entry(UUID id){return waypoint!=null&&id.equals(waypoint.entityId())?waypoint:index.entity(dimension,id);}
    static net.minecraft.world.phys.AABB bounds(UUID id){var entity=entities.get(id);return entity==null||entity.isRemoved()?null:entity.getBoundingBox();}
    static net.minecraft.world.phys.AABB bounds(UUID id,float partial){var entity=entities.get(id);return entity==null||entity.isRemoved()?null:entity.getBoundingBox().move(entity.getPosition(partial).subtract(entity.position()));}
    static boolean nearbyLiving(UUID id,MarkerPosition.Live pose,double x,double y,double z){
        var entity=entities.get(id);
        return entity instanceof net.minecraft.world.entity.LivingEntity&&entity.isAlive()&&nearbyLiving(pose,x,y,z);
    }
    static boolean nearbyLiving(MarkerPosition.Live pose,double x,double y,double z){
        if(pose==null||pose.removed())return false;
        double dx=pose.x()-x,dy=pose.y()-y,dz=pose.z()-z;
        return dx*dx+dy*dy+dz*dz<=32*32;
    }
    static List<Report.Entry> entries(){if(dirty){ordered=entities.keySet().stream().map(EntityMarkers::entry).filter(Objects::nonNull).sorted(Comparator.comparingLong(Report.Entry::nanos).reversed()).toList();dirty=false;}return ordered;}
    static MarkerPosition.Live pose(UUID id,float partial){if(!ClientAccess.entityMarker(id))return null;var entity=entities.get(id);if(entity==null||entity.isRemoved()||!entity.isAlive()||entity.level()!=level)return null;var pos=entity.getPosition(partial);return new MarkerPosition.Live(dimension,pos.x,pos.y,pos.z,entity.getBbHeight(),false);}
    static MarkerPosition.Live poseNear(UUID id,float partial,double x,double y,double z,int distance){var entity=entities.get(id);return entity==null||entity.distanceToSqr(x,y,z)>(double)(distance+4)*(distance+4)?null:pose(id,partial);}
    static Entity current(Report.Entry target){var mc=net.minecraft.client.Minecraft.getInstance();if(mc==null||mc.level==null||target.entityId()==null||!ClientAccess.entityMarker(target.entityId()))return null;var entity=mc.level.getEntity(target.entityId());return entity==null||entity.isRemoved()||!entity.isAlive()||!target.type().equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())?null:entity;}
    static MarkerPosition.Live currentPose(Report.Entry target,float partial){var entity=current(target);if(entity==null)return null;var position=entity.getPosition(partial);return new MarkerPosition.Live(entity.level().dimension().identifier().toString(),position.x,position.y,position.z,entity.getBbHeight(),false);}
    static void clear(){entities.clear();ordered=List.of();level=null;index=null;waypoint=null;dimension=null;dirty=false;}
    private EntityMarkers(){}
}
