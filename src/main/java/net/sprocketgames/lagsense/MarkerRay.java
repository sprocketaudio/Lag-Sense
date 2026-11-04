package net.sprocketgames.lagsense;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A crosshair ray used only for profiler selection. World terrain never participates. */
public final class MarkerRay {
    public static final double RANGE=8;
    private final double x,y,z,dx,dy,dz;
    private final boolean valid;
    public MarkerRay(Vec3 origin,Vec3 direction){
        x=origin.x;y=origin.y;z=origin.z;
        double length=direction.length();valid=Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Double.isFinite(length)&&length>0;
        dx=direction.x/length;dy=direction.y/length;dz=direction.z/length;
    }
    public double intersection(AABB box){
        if(!valid)return Double.POSITIVE_INFINITY;
        double enter=0,exit=RANGE;
        for(int axis=0;axis<3;axis++){
            double origin=axis==0?x:axis==1?y:z,direction=axis==0?dx:axis==1?dy:dz;
            double min=axis==0?box.minX:axis==1?box.minY:box.minZ,max=axis==0?box.maxX:axis==1?box.maxY:box.maxZ;
            if(!Double.isFinite(min)||!Double.isFinite(max))return Double.POSITIVE_INFINITY;
            if(direction==0){if(origin<min||origin>max)return Double.POSITIVE_INFINITY;continue;}
            double a=(min-origin)/direction,b=(max-origin)/direction;
            enter=Math.max(enter,Math.min(a,b));exit=Math.min(exit,Math.max(a,b));
            if(enter>exit)return Double.POSITIVE_INFINITY;
        }
        return enter;
    }
}
