package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.util.*;

final class HtmlExportUiChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Object field(Object value,String name)throws Exception{var f=ExportScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    static void run()throws Exception{
        var entry=new Report.Entry("Block","fixture:machine","minecraft:the_nether",-17,-40,-33,10,1);
        var selected=new Report(9876,1000,10,1,true,false,List.of(entry));
        for(int scale:List.of(2,3)){
            var screen=new ExportScreen(null,selected,true);screen.width=1920/scale;screen.height=1080/scale;screen.init();
            var buttons=screen.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast).toList();
            for(String label:List.of("Save JSON","Save CSV","Export HTML"))check(buttons.stream().anyMatch(b->b.getMessage().getString().equals(label)),"Existing formats and HTML remain directly available");
            var formats=buttons.stream().filter(b->b.getY()==84).sorted(Comparator.comparingInt(UiButton::getX)).toList();
            for(int i=0;i<formats.size();i++){var b=formats.get(i);check(b.getX()>=screen.margin()&&b.getX()+b.getWidth()<=screen.width-screen.margin(),"Export controls fit the panel at scale "+scale);if(i>0)check(formats.get(i-1).getX()+formats.get(i-1).getWidth()<b.getX(),"Export buttons do not overlap");}
            check(field(screen,"data")==selected&&Boolean.TRUE.equals(field(screen,"archived")),"HTML binds to the selected saved capture rather than latest");
            var chunk=new ChunkInvestigation(entry.dimension(),-2,-3,10,10,1,List.of(),List.of(new ChunkInvestigation.Hotspot(entry,0)));
            var chunkScreen=new ExportScreen(screen,chunk,selected,true);
            check(field(chunkScreen,"data")==chunk&&field(chunkScreen,"htmlProfile")==selected&&Boolean.TRUE.equals(field(chunkScreen,"archived")),"Chunk JSON/CSV binding remains while HTML can retrieve the complete selected profile");
        }
        System.out.println("HTML export UI checks passed at GUI scales 2/3 (formats fit, saved capture identity and full chunk-parent export binding).");
    }
}
