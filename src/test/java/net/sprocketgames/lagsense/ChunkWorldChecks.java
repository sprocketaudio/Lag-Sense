package net.sprocketgames.lagsense;

import java.util.*;

final class ChunkWorldChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void run(){
        class Terrain implements ChunkTeleport.Terrain {
            int min=-64,max=320,surface=80;
            public int minimumY(){return min;}
            public int maximumY(){return max;}
            public int surfaceY(int x,int z){return surface;}
        }
        var terrain=new Terrain();
        var centre=ChunkTeleport.choose(-2,-3,terrain,p->true);
        check(centre.x()>=-32&&centre.x() < -16&&centre.z()>=-48&&centre.z() < -32&&centre.y()==80,"Negative-coordinate chunks prefer a surface near their centre");
        var alternate=ChunkTeleport.choose(-2,-3,terrain,p->p.x()==-31.5&&p.z()==-47.5&&p.y()==80);
        check(alternate!=null&&alternate.y()==80,"A safe surface elsewhere in the selected chunk is preferred to any enclosed floor");
        var attempts=new ArrayList<LocalTeleport.Position>();
        check(ChunkTeleport.choose(0,0,terrain,p->{attempts.add(p);return false;})==null,"A chunk with no safe standing space fails without an unsafe fallback");
        check(attempts.stream().allMatch(p->p.x()>0&&p.x()<16&&p.z()>0&&p.z()<16)&&attempts.size()<=256*(terrain.max-terrain.min),"The bounded search never leaves the chosen chunk");
        terrain.min=0;terrain.max=128;terrain.surface=128;
        var nether=ChunkTeleport.choose(3,4,terrain,p->p.y()==70);
        check(nether!=null&&nether.y()==70,"Nether ceiling surfaces are excluded while interior standing spaces remain reachable");
        terrain.max=256;terrain.surface=0;
        check(ChunkTeleport.choose(0,0,terrain,p->false)==null,"End void chunks have no invented standing surface");
        check(ChunkDisplay.floorY(64)==52.04&&Math.abs(ChunkDisplay.floorY(-35.25)+47.21)<.0001,"Heatmap follows feet twelve blocks below in the sky or underground");
        check(ChunkDisplay.labelY(64)==61&&ChunkDisplay.labelY(-35.25)==-38.25,"Heatmap labels follow player feet three blocks below, independently of floor height");
        int base=TimingSeverity.color(35),previous=255;
        for(int distance=0;distance<=96;distance++){
            int color=ChunkDisplay.color(base,distance,96),alpha=color>>>24;
            check((color&0xffffff)==(base&0xffffff)&&alpha<=previous&&alpha>0,"Distance fading is gradual, monotonic and preserves timing RGB");previous=alpha;
        }
        String[] arrows={"↑","↗","→","↘","↓","↙","←","↖"};
        for(int yaw=-360;yaw<=360;yaw+=45)for(int direction=0;direction<8;direction++){
            double angle=Math.toRadians(yaw+direction*45);
            check(MarkedDirection.arrow(-Math.sin(angle)*10,Math.cos(angle)*10,yaw).equals(arrows[direction]),"All eight directions follow the player's yaw across angle wrapping");
        }
        check(MarkedDirection.text(0,12,5,0).equals("↑ 13 blocks (above)")&&MarkedDirection.text(0,-12,-5,0).equals("↓ 13 blocks (below)"),"Direction keeps rounded three-dimensional distance and compact vertical context");
        var zombie=new Report.Entry("Entity","minecraft:zombie","minecraft:overworld",0,64,0,1200000,100,UUID.randomUUID());
        check(MarkedDirection.guidance(zombie,new MarkerPosition.Live("minecraft:overworld",30,64,40,1.95,false),"minecraft:overworld",0,64,0,0).contains("50 blocks"),"Marked entity guidance uses its current position, not its captured origin");
        check(MarkedDirection.guidance(zombie,null,"minecraft:overworld",0,64,0,0).equals("Not loaded or no longer exists"),"An unresolved marked entity has no fabricated distance or direction");
        check(MarkedDirection.guidance(zombie,new MarkerPosition.Live("minecraft:the_nether",30,64,40,1.95,false),"minecraft:overworld",0,64,0,0).equals("minecraft:the_nether"),"Cross-dimension guidance names the current dimension without a misleading arrow");
        check(EntityTracking.statusText(EntityTracking.removalStatus(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK)).equals("Entity unloaded"),"Known chunk unloads have an explicit status");
        check(EntityTracking.statusText(EntityTracking.removalStatus(net.minecraft.world.entity.Entity.RemovalReason.KILLED)).equals("Entity died"),"Confirmed entity deaths are distinguished from unloads");
        check(EntityTracking.statusText(EntityTracking.removalStatus(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED)).equals("Despawned or removed"),"A generic removal does not falsely claim a particular despawn cause");
        check(EntityTracking.statusText(null).equals("Not loaded or no longer exists"),"Missing historical UUIDs do not invent removal evidence");
        var noId=new Report.Entry("Entity","minecraft:zombie","minecraft:overworld",0,64,0,0,1);
        check(MarkedDirection.guidance(noId,null,"minecraft:overworld",0,64,0,0).equals("No recorded entity UUID"),"Older captures without entity identity explain why tracking is impossible");
        System.out.println("Chunk world checks passed: bounded surface/interior destinations, negative chunks, Nether ceiling/End void, grid height/fade and eight-way direction.");
    }
}
