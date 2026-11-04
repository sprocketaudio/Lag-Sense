package audio.sprocket.lagsense;

import net.minecraft.world.phys.*;

final class MarkerRayChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void run(){
        var ray=new MarkerRay(Vec3.ZERO,new Vec3(0,0,1));
        check(ray.intersection(new AABB(-.5,-.5,3,.5,.5,4))==3,"Unobstructed cube intersects at its front face");
        check(ray.intersection(new AABB(-.5,-.5,7.99,.5,.5,8.99))==7.99,"Intersection distance, not centre distance, sets the eight-block limit");
        check(ray.intersection(new AABB(-.5,-.5,8,.5,.5,9))==8,"Exactly eight blocks is included");
        check(!Double.isFinite(ray.intersection(new AABB(-.5,-.5,8.000001,.5,.5,9))),"Anything beyond eight blocks is excluded");
        check(!Double.isFinite(ray.intersection(new AABB(.000001,-.5,3,1,.5,4))),"A narrow miss is not expanded into a hit");
        check(!Double.isFinite(ray.intersection(new AABB(-.5,.000001,3,.5,1,4))),"Parallel ray slabs reject vertical misses");
        check(!Double.isFinite(ray.intersection(new AABB(-.5,-.5,-4,.5,.5,-3))),"Targets behind the viewpoint never focus");
        check(ray.intersection(new AABB(0,0,3,1,1,4))==3,"Exact edges and corners remain valid intersections");
        check(ray.intersection(new AABB(-1,-1,-1,1,1,1))==0,"A viewpoint inside a displayed cube starts at distance zero");
        check(new MarkerRay(Vec3.ZERO,new Vec3(0,0,100)).intersection(new AABB(-1,-1,7,1,1,8))==7,"Direction is normalised so distance remains in blocks");
        check(new MarkerRay(Vec3.ZERO,new Vec3(0,0,-1)).intersection(new AABB(-1,-1,-4,1,1,-3))==3,"Negative ray direction uses the near face correctly");
        check(!Double.isFinite(new MarkerRay(Vec3.ZERO,Vec3.ZERO).intersection(new AABB(-1,-1,1,1,1,2))),"Zero direction safely selects nothing");
        check(!Double.isFinite(new MarkerRay(new Vec3(Double.NaN,0,0),new Vec3(0,0,1)).intersection(new AABB(-1,-1,1,1,1,2))),"Invalid viewpoints safely select nothing");
        for(int i=0;i<1000;i++){
            double x=(i%2==0?1:-1)*.00001;var moving=new MarkerRay(new Vec3(x,0,0),new Vec3(-x,0,1));
            check(Double.isFinite(moving.intersection(new AABB(-.5,-.5,3,.5,.5,4))),"Small viewpoint movement and turning keep the same intersected cube");
        }
        System.out.println("Marker ray checks passed (eight-block intersection range, exact faces/corners, parallel/negative rays, narrow misses, normalisation and stable movement).");
    }
}
