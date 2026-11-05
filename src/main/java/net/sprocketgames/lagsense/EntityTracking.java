package net.sprocketgames.lagsense;

import java.util.UUID;

/** Current server location, separate from the immutable captured measurement. */
public record EntityTracking(UUID entityId,String dimension,double x,double y,double z,double width,double height) {
    public boolean valid(){return entityId!=null&&dimension!=null&&net.minecraft.resources.Identifier.tryParse(dimension)!=null&&Double.isFinite(x)&&Double.isFinite(y)&&Math.abs(y)<=20000000&&Double.isFinite(z)&&Math.abs(x)<=30000000&&Math.abs(z)<=30000000&&Double.isFinite(width)&&Double.isFinite(height)&&width>0&&width<=128&&height>0&&height<=128;}
    public MarkerPosition.Live pose(){return new MarkerPosition.Live(dimension,x,y,z,height,false);}
    public static String statusText(String status){
        return switch(status==null?"":status){
            case "locating"->"Locating entity…";
            case "unloaded"->"Entity unloaded";
            case "dead"->"Entity died";
            case "removed"->"Despawned or removed";
            case "changed_dimension"->"Entity changed dimension";
            case "missing_uuid"->"No recorded entity UUID";
            case "identity_changed"->"Entity identity changed";
            default->"Not loaded or no longer exists";
        };
    }
    public static String removalStatus(net.minecraft.world.entity.Entity.RemovalReason reason){
        if(reason==null)return "not_found";
        return switch(reason){
            case KILLED->"dead";
            case DISCARDED->"removed";
            case UNLOADED_TO_CHUNK,UNLOADED_WITH_PLAYER->"unloaded";
            case CHANGED_DIMENSION->"changed_dimension";
        };
    }
}
