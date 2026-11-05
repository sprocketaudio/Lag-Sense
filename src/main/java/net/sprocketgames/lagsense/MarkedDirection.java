package net.sprocketgames.lagsense;

public final class MarkedDirection {
    private static final String[] ARROWS={"↑","↗","→","↘","↓","↙","←","↖"};
    public static String arrow(double dx,double dz,float yaw){
        if(dx*dx+dz*dz<.01)return "•";
        double angle=-Math.toDegrees(Math.atan2(dx,dz))-yaw;
        return ARROWS[Math.floorMod((int)Math.floor(angle/45+.5),8)];
    }
    public static String text(double dx,double dy,double dz,float yaw){
        long distance=Math.round(Math.sqrt(dx*dx+dy*dy+dz*dz));
        return arrow(dx,dz,yaw)+" "+distance+" blocks"+(dy>4?" (above)":dy < -4?" (below)":"");
    }
    public static String guidance(Report.Entry target,MarkerPosition.Live live,String dimension,double x,double y,double z,float yaw){
        return guidance(target,live,dimension,x,y,z,yaw,null);
    }
    public static String guidance(Report.Entry target,MarkerPosition.Live live,String dimension,double x,double y,double z,float yaw,String status){
        if(target.kind().equals("Entity")&&live==null)return EntityTracking.statusText(target.entityId()==null?"missing_uuid":status);
        String destination=live==null?target.dimension():live.dimension();if(!destination.equals(dimension))return destination;
        double tx=live==null?target.x()+(target.kind().equals("Chunk")?0:.5):live.x();
        double ty=live==null?target.y():live.y(),tz=live==null?target.z()+(target.kind().equals("Chunk")?0:.5):live.z();
        return text(tx-x,ty-y,tz-z,yaw);
    }
    private MarkedDirection(){}
}
