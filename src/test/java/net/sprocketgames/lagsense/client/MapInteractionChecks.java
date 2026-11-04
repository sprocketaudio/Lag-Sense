package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;

final class MapInteractionChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    static void run()throws Exception{
        var report=new Report(101,1000,100,10,true,false,List.of(new Report.Entry("Block","fixture:machine","minecraft:overworld",0,64,0,1000000,100))).view(true,List.of());
        for(int scale:List.of(2,3)){
            var screen=new HeatmapScreen(null,report,false);screen.width=1920/scale;screen.height=1080/scale;screen.init();
            double x=screen.width/2.0,y=(int)field(screen,"mapTop")+10;
            check(screen.mouseScrolled(x,y,0,1)&&(int)field(screen,"cell")==22,"Wheel up zooms in over the map");
            check(screen.mouseScrolled(x,y,0,-1)&&(int)field(screen,"cell")==18,"Wheel down zooms out over the map");
            for(int i=0;i<3;i++)screen.mouseScrolled(x,y,0,.25);
            check((int)field(screen,"cell")==18,"Trackpad deltas accumulate without excessive zoom jumps");
            screen.mouseScrolled(x,y,0,.25);check((int)field(screen,"cell")==22,"One accumulated notch uses the existing zoom step");
            screen.mouseScrolled(x,y,0,.5);screen.mouseScrolled(x,y,0,-.5);check((int)field(screen,"cell")==22,"Direction reversal clears fractional motion");
            screen.mouseScrolled(x,y,0,-.5);check((int)field(screen,"cell")==18,"Reversed trackpad motion accumulates normally");
            screen.mouseScrolled(x,20,0,1);screen.mouseScrolled(x,screen.height-40,0,-1);screen.mouseScrolled(0,y,0,1);
            screen.mouseScrolled(x,y,1,0);screen.mouseScrolled(x,y,0,Double.NaN);screen.mouseScrolled(x,y,0,Double.POSITIVE_INFINITY);
            check((int)field(screen,"cell")==18,"Headers, footers, horizontal and invalid deltas cannot change zoom");
            for(int i=0;i<50;i++)screen.mouseScrolled(x,y,0,1);check((int)field(screen,"cell")==34,"Existing maximum remains unchanged");
            for(int i=0;i<50;i++)screen.mouseScrolled(x,y,0,-1);check((int)field(screen,"cell")==2,"Existing minimum remains unchanged");
            check(field(screen,"plan")==null,"Wheel hit testing does not rebuild map geometry");
            var buttons=ProfileLayoutChecks.widgets(screen).stream().filter(UiButton.class::isInstance).map(UiButton.class::cast).toList();
            check(buttons.stream().noneMatch(b->List.of("+","−","Left","Right","Up","Down").contains(b.getMessage().getString())),"Wheel/drag replace duplicate zoom and direction buttons");
            for(String label:List.of("Hottest","Fit")){
                var button=buttons.stream().filter(b->b.getMessage().getString().equals(label)).findFirst().orElseThrow();button.setFocused(true);
                check(!(boolean)field(button,"selected")&&field(button,"role")==UiButton.Role.SECONDARY,"Hottest and Fit remain one-shot actions");
                check(!UiButton.highlighted(false,button.isFocused(),false)&&UiButton.highlighted(false,button.isFocused(),true),"Mouse focus never leaves a selected-looking highlight");
            }
            check(field(screen,"selected")==report.chunks().getFirst(),"Zoom preserves the selected chunk");
        }
        var gesture=new MapInteraction();gesture.begin(100,100);check(gesture.drag(101,101).equals(new MapInteraction.Shift(0,0))&&gesture.release(),"Small pointer jitter remains a click");
        gesture.begin(100,100);check(gesture.drag(120,110).equals(new MapInteraction.Shift(20,10))&&!gesture.release(),"Dragging pans and suppresses chunk activation");
        gesture.begin(10,10);gesture.cancel();check(!gesture.release(),"Leaving a screen cancels pending map clicks");
        var viewport=new MapInteraction.Viewport(10,100,900,500);
        for(int cell:List.of(2,6,18,30,34))for(int step:List.of(-1,1)){
            var zoom=MapInteraction.zoom(viewport,-123.25,75.75,cell,711,234,step);
            double beforeX=-123.25+(711-viewport.midX())/cell,beforeZ=75.75+(234-viewport.midY())/cell;
            check(Math.abs(beforeX-zoom.x()-(711-viewport.midX())/zoom.cell())<1e-9&&Math.abs(beforeZ-zoom.z()-(234-viewport.midY())/zoom.cell())<1e-9,"Wheel zoom anchors the world coordinate under the cursor, including negative chunks");
            var shape=MapInteraction.geometry(viewport,zoom.x(),zoom.z(),zoom.cell());
            check(shape.left()<=viewport.left()&&shape.top()<=viewport.top()&&shape.left()+shape.columns()*zoom.cell()>=viewport.right()&&shape.top()+shape.rows()*zoom.cell()>=viewport.bottom(),"Fractional panning always covers the full map viewport");
        }
        var state=new ProfileArchive.State(ProfileArchive.Settings.defaults(),List.of());var history=new HistoryScreen(null,state,"");history.width=640;history.height=360;history.init();
        set(history,"baseline",123L);var count=(net.minecraft.client.gui.components.EditBox)field(history,"count");count.setValue("12");
        for(String status:List.of("Loading…","Comparing…")){set(history,"message",status);history.removed();check(field(history,"message").equals("")&&field(history,"baseline").equals(123L)&&count.getValue().equals("12")&&field(history,"state")==state,"Completed history navigation clears transient loading while preserving the draft");}
        for(String status:List.of("Applying…","Saved profile unavailable.","Settings applied.")){set(history,"message",status);history.removed();check(field(history,"message").equals(status),"History retains actual setting and error messages");}
        System.out.println("Map/history checks passed (wheel/trackpad zoom, cursor anchoring, drag versus click, viewport coverage and navigation status).");
    }
}
