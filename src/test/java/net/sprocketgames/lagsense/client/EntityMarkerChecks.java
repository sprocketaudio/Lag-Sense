package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;
import org.objectweb.asm.*;

final class EntityMarkerChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void run()throws Exception{
        var playerId=UUID.randomUUID();var pillagerId=UUID.randomUUID();
        var ray=new MarkerRay(new net.minecraft.world.phys.Vec3(0,1.62,0),new net.minecraft.world.phys.Vec3(0,0,1));
        var playerBox=new net.minecraft.world.phys.AABB(-.3,0,-.3,.3,1.8,.3);
        var pillagerBox=new net.minecraft.world.phys.AABB(-.3,0,3,.3,1.95,3.6);
        check(ray.intersection(playerBox)==0&&ray.intersection(pillagerBox)==3,"The crosshair starts inside the measured local player's body and also intersects a nearby pillager");
        check(Double.isInfinite(WorldMarkers.entityIntersection(ray,playerId,playerId,playerBox))&&WorldMarkers.entityIntersection(ray,pillagerId,playerId,pillagerBox)==3,"The camera entity cannot steal focus at distance zero from a captured pillager");
        check(WorldMarkers.entityIntersection(ray,playerId,pillagerId,playerBox)==0,"Spectator/camera exclusion follows UUID identity rather than excluding every player");
        check(!WorldMarkers.entityLabelVisible(playerId,playerId,true)&&WorldMarkers.entityLabelVisible(playerId,playerId,false),"The camera player's overhead timing is hidden in first person and visible in both third-person views");
        check(WorldMarkers.entityLabelVisible(pillagerId,playerId,true)&&WorldMarkers.entityLabelVisible(pillagerId,playerId,false),"Other measured living entities keep their labels in every camera mode");
        check(Double.isInfinite(WorldMarkers.entityIntersection(ray,playerId,playerId,playerBox)),"Showing the local third-person label does not make the camera player selectable again");
        var id=UUID.randomUUID();String dimension="minecraft:overworld";
        var first=new Report.Entry("Entity","minecraft:pillager",dimension,38,62,-48,3824000,100,id);
        var moved=new Report.Entry(first.kind(),first.type(),dimension,70,62,-48,1000000,10,id);
        var other=new Report.Entry(first.kind(),first.type(),dimension,38,62,-48,9000000,100,UUID.randomUUID());
        var report=new Report(1,1,100,1,true,false,List.of(first,moved,other));var index=new MarkerIndex(report);
        check(EntityMarkers.measured(index,null,dimension,id,first.type()).nanos()==4824000,"Ordinary entity timing follows UUID across its captured chunk contributions");
        check(EntityMarkers.measured(index,first,dimension,id,first.type())==first,"A selected historical or individual mark retains its own captured timing");
        check(EntityMarkers.measured(index,first,dimension,other.entityId(),first.type())==other,"Marking one entity never marks another of the same type");
        check(EntityMarkers.measured(index,first,dimension,id,"minecraft:cow")==null,"A mismatched live entity type cannot inherit a marked timing");
        check(EntityMarkers.measured(index,first,"minecraft:the_nether",id,first.type())==first,"A reliably UUID-matched mark can be resolved after crossing dimensions");
        var live=new MarkerPosition.Live(dimension,41.25,64,-42.5,1.95,false);
        var position=MarkerPosition.resolve(first,dimension,key->key.equals(id)?live:null);
        check(position.x()==40.75&&position.z()==-43&&position.y()==64&&position.labelHeight()==2.25&&first.nanos()==3824000,"Label anchor follows current pose above the head while captured timing remains unchanged");
        check(MarkerPosition.resolve(first,dimension,key->null)==null,"Unloaded entities do not leave a floating label at capture coordinates");
        check(EntityMarkers.nearbyLiving(new MarkerPosition.Live(dimension,32,0,0,1.95,false),0,0,0),"Living entity label range includes exactly thirty-two blocks");
        check(!EntityMarkers.nearbyLiving(new MarkerPosition.Live(dimension,32.001,0,0,1.95,false),0,0,0),"Ordinary living entity captions stop beyond thirty-two blocks");
        check(!EntityMarkers.nearbyLiving(new MarkerPosition.Live(dimension,0,33,0,1.95,false),0,0,0),"Nearby range uses vertical as well as horizontal distance");
        check(!EntityMarkers.nearbyLiving(null,0,0,0)&&!EntityMarkers.nearbyLiving(new MarkerPosition.Live(dimension,0,0,0,1.95,true),0,0,0),"Removed or unresolved entities cannot gain a nearby caption");
        var calls=new HashSet<String>();
        try(var in=WorldMarkers.class.getResourceAsStream("WorldMarkers.class")){
            new ClassReader(Objects.requireNonNull(in)).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                    if(!name.equals("extract"))return null;
                    return new MethodVisitor(Opcodes.ASM9){@Override public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean itf){if(owner.endsWith("/EntityMarkers"))calls.add(method);}};
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        check(calls.containsAll(List.of("bind","entries","poseNear","pose","bounds")),"The production extraction path binds client entities and submits current-pose labels, preventing the disconnected entity-rendering regression");
        System.out.println("Entity marker checks passed: UUID/type matching, selected timing, moved anchors, unloaded hiding and production renderer wiring. Actual entity loading requires in-game verification.");
    }
}
