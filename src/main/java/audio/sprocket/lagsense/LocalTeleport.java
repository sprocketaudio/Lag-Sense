package audio.sprocket.lagsense;

import java.util.*;
import java.util.function.Predicate;

/** Fixed, local standing-space search. No heightmap or surface lookup. */
public final class LocalTeleport {
    public record Position(double x,double y,double z){}
    private record Offset(int x,int y,int z){}
    private static final List<Offset> OFFSETS;
    static {
        var offsets=new ArrayList<Offset>();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-1;y<=1;y++)offsets.add(new Offset(x,y,z));
        offsets.sort(Comparator.<Offset>comparingInt(o->o.x()*o.x()+o.z()*o.z()+o.y()*o.y()*2).thenComparingInt(o->Math.abs(o.y())).thenComparingInt(o->-o.y()).thenComparingInt(Offset::x).thenComparingInt(Offset::z));
        OFFSETS=List.copyOf(offsets);
    }
    public static Position choose(Report.Entry target,Predicate<Position> canStand){
        double x=target.x()+(target.kind().equals("Chunk")?0:.5),z=target.z()+(target.kind().equals("Chunk")?0:.5);
        for(var offset:OFFSETS){var p=new Position(x+offset.x(),target.y()+offset.y(),z+offset.z());if(canStand.test(p))return p;}
        return new Position(x,target.y(),z);
    }
    private LocalTeleport(){}
}
