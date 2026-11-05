package net.sprocketgames.lagsense;

public final class ChunkDisplay {
    public static double floorY(double feetY){return feetY-12+.04;}
    public static double labelY(double feetY){return feetY-3;}
    public static int color(int color,double distance,int range){
        double fraction=Math.clamp(distance/Math.max(1,range),0,1);
        double opacity=1-.92*fraction*fraction*(3-2*fraction);
        return ((int)Math.round((color>>>24)*opacity)<<24)|(color&0xffffff);
    }
    private ChunkDisplay(){}
}
