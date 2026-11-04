package audio.sprocket.lagsense;

import net.minecraft.resources.Identifier;

/** Capture-independent command coordinates; validation and landing remain server-side. */
public final class TeleportLocation {
    public static boolean valid(String dimension,int x,int y,int z){return dimension!=null&&Identifier.tryParse(dimension)!=null&&Math.abs((long)x)<=30_000_000&&Math.abs((long)z)<=30_000_000&&Math.abs((long)y)<=20_000_000;}
    public static Report.Entry target(String dimension,int x,int y,int z,boolean chunk){return valid(dimension,x,y,z)?new Report.Entry(chunk?"Chunk":"Block","minecraft:air",dimension,x,y,z,0,0):null;}
    public static String command(String dimension,int x,int y,int z){return valid(dimension,x,y,z)?"/lagsense tp location "+dimension+" "+x+" "+y+" "+z:null;}
    public static String chunkCommand(String dimension,int x,int z){long cx=(long)x*16+8,cz=(long)z*16+8;return cx>=-30_000_000&&cx<=30_000_000&&cz>=-30_000_000&&cz<=30_000_000&&valid(dimension,(int)cx,0,(int)cz)?"/lagsense tp chunk "+dimension+" "+x+" "+z:null;}
    private TeleportLocation(){}
}
