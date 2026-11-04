package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;

public final class TargetDetails {
    public static String owner(Report.Entry e) { return e.type().contains(":") ? e.type().substring(0,e.type().indexOf(':')) : "unattributed"; }
    public static String hint(Report.Entry e,int ticks) {
        return e.type()+"\n"+e.kind()+" | "+e.dimension()+" | "+e.x()+" "+e.y()+" "+e.z()+"\nChunk "+(e.x()>>4)+", "+(e.z()>>4)+"\n"+e.calls()+" updates"+(ticks>0?" | "+UiResults.cost(e.micros(ticks)):"");
    }
    public static String name(Report.Entry e) {
        return e.type();
    }
    public static void teleport(Screen parent, Report report, Report.Entry e, int index) {
        if(parent instanceof LagScreen screen)TargetActions.teleport(screen,TargetActions.recorded(report,e,index,false));
    }
}
