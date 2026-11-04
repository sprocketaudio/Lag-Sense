package net.sprocketgames.lagsense;

import java.util.*;

/** Three nearby captions, independently of complete floor-grid coverage. */
public final class NearbyChunkLabels {
    public static final int COUNT=3;
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet previous=new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private String previousDimension;
    private static long key(int x,int z){return (long)x<<32|z&0xffffffffL;}
    public void clear(){previous.clear();previousDimension=null;}
    public List<Report.Chunk> select(List<Report.Chunk> chunks,String dimension,double x,double z){
        if(!dimension.equals(previousDimension)){previous.clear();previousDimension=dimension;}
        var nearest=new ArrayList<Report.Chunk>(COUNT);var distances=new double[COUNT];
        for(var chunk:chunks){
            if(!dimension.equals(chunk.dimension()))continue;
            double dx=chunk.x()*16.0+8-x,dz=chunk.z()*16.0+8-z;
            double distance=Math.hypot(dx,dz)-(previous.contains(key(chunk.x(),chunk.z()))?2:0);
            int at=0;while(at<nearest.size()&&(distances[at]<distance||distances[at]==distance&&order(nearest.get(at),chunk)<=0))at++;
            if(at>=COUNT)continue;
            if(nearest.size()<COUNT)nearest.add(chunk);
            for(int i=nearest.size()-1;i>at;i--){nearest.set(i,nearest.get(i-1));distances[i]=distances[i-1];}
            nearest.set(at,chunk);distances[at]=distance;
        }
        previous.clear();for(var chunk:nearest)previous.add(key(chunk.x(),chunk.z()));return List.copyOf(nearest);
    }
    private static int order(Report.Chunk a,Report.Chunk b){int n=Integer.compare(a.x(),b.x());return n==0?Integer.compare(a.z(),b.z()):n;}
}
