package net.sprocketgames.lagsense;

import java.util.*;
import java.util.function.Predicate;

/** Surface candidates are checked before enclosed floors, always inside the chosen chunk. */
public final class ChunkTeleport {
    public interface Terrain {
        int minimumY();
        int maximumY();
        int surfaceY(int x,int z);
    }
    private record Column(int x,int z) {}
    private static final List<Column> COLUMNS;
    static {
        var columns=new ArrayList<Column>(256);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)columns.add(new Column(x,z));
        columns.sort(Comparator.comparingDouble((Column c)->Math.pow(c.x()+.5-8,2)+Math.pow(c.z()+.5-8,2)).thenComparingInt(Column::x).thenComparingInt(Column::z));
        COLUMNS=List.copyOf(columns);
    }
    public static LocalTeleport.Position choose(int chunkX,int chunkZ,Terrain terrain,Predicate<LocalTeleport.Position> safe) {
        int left=Math.multiplyExact(chunkX,16),top=Math.multiplyExact(chunkZ,16);
        for(var column:COLUMNS){
            int x=left+column.x(),z=top+column.z(),y=terrain.surfaceY(x,z);
            if(y>terrain.minimumY()&&y<terrain.maximumY()){
                var position=new LocalTeleport.Position(x+.5,y,z+.5);
                if(safe.test(position))return position;
            }
        }
        for(var column:COLUMNS)for(int y=terrain.maximumY()-2;y>terrain.minimumY();y--){
            var position=new LocalTeleport.Position(left+column.x()+.5,y,top+column.z()+.5);
            if(safe.test(position))return position;
        }
        return null;
    }
    private ChunkTeleport(){}
}
