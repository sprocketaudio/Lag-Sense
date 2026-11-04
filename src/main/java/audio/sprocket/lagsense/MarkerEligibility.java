package audio.sprocket.lagsense;

/** Separate measurement, world identity, range and label-density decisions. No fabricated measurements. */
public final class MarkerEligibility {
    public record Block(boolean loaded,String block,String blockEntity,String fluid) {}
    public static boolean timing(Report.Entry e,int ticks,double minimum){return e.nanos()>=0&&e.calls()>=0&&ticks>0&&e.micros(ticks)>=Math.max(0,minimum);}
    public static boolean matches(Report.Entry e,Block live){
        if(!live.loaded()||e.kind().equals("Entity"))return false;
        return switch(e.kind()){
            case "Block"->e.type().equals(live.blockEntity());
            case "Random block","Scheduled block"->e.type().equals(live.block());
            case "Random fluid","Scheduled fluid"->e.type().equals(live.fluid());
            default->e.type().equals(live.block())||e.type().equals(live.blockEntity())||e.type().equals(live.fluid());
        };
    }
    public static boolean inRange(MarkerPosition p,double x,double y,double z,int distance){double dx=p.x()+.5-x,dy=p.y()-y,dz=p.z()+.5-z;return dx*dx+dy*dy+dz*dz<=(double)distance*distance;}
    private MarkerEligibility(){}
}
