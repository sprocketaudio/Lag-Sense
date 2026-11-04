package net.sprocketgames.lagsense;

import java.util.UUID;
import java.util.function.Function;

/** Capture timing is immutable; only the current pose comes from the client entity cache. */
public record MarkerPosition(double x,double y,double z,double labelHeight) {
    public record Live(String dimension,double x,double y,double z,double height,boolean removed) {}
    public static MarkerPosition resolve(Report.Entry entry,String dimension,Function<UUID,Live> lookup) {
        if(!entry.dimension().equals(dimension))return null;
        if(!entry.kind().equals("Entity"))return new MarkerPosition(entry.x(),entry.y(),entry.z(),1.3);
        if(entry.entityId()==null)return null;
        var entity=lookup.apply(entry.entityId());
        return entity==null||entity.removed()||!dimension.equals(entity.dimension())?null:new MarkerPosition(entity.x()-.5,entity.y(),entity.z()-.5,Math.max(1.3,entity.height()+.3));
    }
}
