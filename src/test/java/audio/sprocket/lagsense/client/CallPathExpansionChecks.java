package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.CpuInvestigation;
import java.util.*;
import net.minecraft.client.gui.Font;

/** Builds real native rows with metrics-only fonts; no game window or GPU context. */
public final class CallPathExpansionChecks {
    public static void run(Font font) throws Exception {
        var saved=new HashMap<java.lang.reflect.Field,Object>();
        try {
            for(String name:List.of("body","strong","heading","mono")) {
                var field=UiFonts.class.getDeclaredField(name);field.setAccessible(true);saved.put(field,field.get(null));field.set(null,font);
            }
            var tree=new CpuInvestigation.Builder("Server thread");
            tree.add(List.of(new CpuInvestigation.Frame("fixture","test.Machine","tick","()V")));
            var data=tree.finish();
            check(data,false,"node:1",1);
            check(data,false,"method:1",0);
            check(data,true,"method:0",1);
            check(data,true,"node:0",0);
            compactRows(data,font);
            alignedSamples(data,font);
            ProfilingUsabilityUiChecks.run(font);
            BackgroundCpuUiChecks.run();
            lazyResults(data);
            nativeMapRaster();
            SettingsLayoutChecks.run();
            ButtonStyleChecks.run();
            ThemeChecks.run();
            GradientUiChecks.run();
            WorldMarkerRenderChecks.run();
            ProfileStateChecks.run();
            ChunkOverlayChecks.run();
            HistoricalUiChecks.run();
            HtmlExportUiChecks.run();
        } finally { for(var entry:saved.entrySet())entry.getKey().set(null,entry.getValue()); }
        System.out.println("Call-path expansion checks passed (real hierarchy/running rows reveal inline details without mixing node and method IDs).");
    }
    private static void nativeMapRaster() {
        var plan=new audio.sprocket.lagsense.HeatmapPlan(7,9,9,9,2,0,0,List.of(new audio.sprocket.lagsense.HeatmapPlan.Tile(0,0,0xff69f0ae)),UiTheme.PANEL,UiTheme.BACKGROUND);
        try(var image=new com.mojang.blaze3d.platform.NativeImage(18,18,false)) {
            plan.paint((l,t,r,b,c)->image.fillRect(l-7,t-9,r-l,b-t,c));
            if(image.getPixel(8,8)!=0xff69f0ae||image.getPixel(9,8)!=UiTheme.BACKGROUND||image.getPixel(0,0)!=UiTheme.PANEL)throw new AssertionError("Cached map texture raster must preserve ARGB severity colours, grid gaps and empty cells");
        }
    }
    private static void lazyResults(CpuInvestigation data) {
        var screen=new CallTreeScreen(null,data,null);screen.width=640;screen.height=360;
        var out=new UiResults(screen,100,0,false);int[] formatted={0};
        for(int i=0;i<26000;i++){int id=i;out.lazy(id,row->{formatted[0]++;row.row(id,"fixture:target_"+id,"Coordinates and retained metadata","0.00 µs/t",UiTheme.ICE,0,false,"Full retained target",null);});}
        if(formatted[0]!=0)throw new AssertionError("Large reports must not format off-screen rows while building their virtual index");
        var list=new UiScrollList(screen.margin(),100,screen.contentWidth(),240,new audio.sprocket.lagsense.ListScroll());list.virtual(out.entries,null);
        if(formatted[0]!=10||list.widgetCount()!=10)throw new AssertionError("Only ten visible rows should format and create widgets");
        list.restore(Integer.MAX_VALUE);
        if(formatted[0]!=20||!list.visible(25999))throw new AssertionError("The last low-cost retained result must remain reachable without formatting the intervening rows");
    }
    private static void alignedSamples(CpuInvestigation data,Font font) {
        for(int scale:List.of(2,3)) {
            var screen=new CallTreeScreen(null,data,null);screen.width=1920/scale;screen.height=1080/scale;
            int inspect=Math.max(52,UiFonts.labelWidth("Inspect")+12);
            var rows=new UiResults(screen,100,inspect+12,false).samples(1_000_000).nameOnlyIndent();
            for(int depth=0;depth<100;depth++)rows.row(depth,"+ extremely.long.mod.namespace.Machine#veryLongTickMethod",depth+" direct | 1000000 in stack","1000000 samples","100.0%",UiTheme.ICE,depth*7,false,"Full method identity and retained diagnostics.",()->{},new UiResults.Action("Inspect",()->{}));
            var list=new UiScrollList(screen.margin(),100,screen.contentWidth(),24,new audio.sprocket.lagsense.ListScroll());list.virtual(rows.entries,null);
            UiRow.Columns baseline=null;int actionX=-1;
            for(int depth=0;depth<100;depth++) {
                list.restore(depth*24);var row=(UiRow)list.children().getFirst();var columns=row.columns();
                var action=(UiButton)list.children().get(1);
                if(baseline==null){baseline=columns;actionX=action.getX();}
                if(columns.countRight()!=baseline.countRight()||columns.countWidth()!=baseline.countWidth()||columns.valueRight()!=baseline.valueRight()||columns.valueWidth()!=baseline.valueWidth())throw new AssertionError("Nesting must not move sample or percentage columns");
                if(row.getX()!=screen.margin()||columns.nameX()+columns.nameWidth()>columns.countRight()-columns.countWidth()-12||columns.nameWidth()<48)throw new AssertionError("Only the name area may indent; long/deep names cannot overlap measurements");
                if(action.getX()!=actionX||action.getWidth()!=inspect||font.width(UiFonts.rich("Inspect"))>inspect-10)throw new AssertionError("Inspect must keep its complete fixed-width label at every depth");
                if(UiFonts.width(UiFonts.mono(),"1000000 samples")>columns.countWidth()||UiFonts.width(UiFonts.mono(),"100.0%")>columns.valueWidth())throw new AssertionError("Sample columns must preserve complete values");
                if(depth==1&&columns.nameX()!=baseline.nameX()+7)throw new AssertionError("Hierarchy indentation uses seven logical pixels per level");
            }
        }
        for(String kind:List.of("Entity","Block","Scheduled block","Random block","Scheduled fluid","Random fluid")) {
            var target=new audio.sprocket.lagsense.Report.Entry(kind,"minecraft:creeper","minecraft:overworld",1,64,2,1,1);
            if(!TargetDetails.name(target).equals(target.type()))throw new AssertionError("Target names must retain their complete registry IDs");
        }
    }
    private static void compactRows(CpuInvestigation data,Font font) {
        for(int scale:List.of(2,3)) {
            var screen=new CallTreeScreen(null,data,null);screen.width=1920/scale;screen.height=1080/scale;
            var rows=new UiResults(screen,100,76,false);int[] pressed={0,0};
            for(int i=0;i<100;i++)rows.row(i,"Machine "+i,"minecraft:overworld | 123 64 -456 | 20 updates","12.34 µs/t",UiTheme.ICE,12,false,"Actual retained measurement.",null,
                new UiResults.Action("Mark",()->pressed[0]++),new UiResults.Action("TP",()->pressed[1]++));
            var old=new UiResults(screen,100,134,false,false);old.row("history","Capture date","Performance metadata","1.20 MSPT",UiTheme.TEXT,0,false,"",null);
            if(rows.entries.getFirst().height()!=24||old.entries.getFirst().height()!=34)throw new AssertionError("Compact profiling rows must preserve two-line history/comparison sizing");
            var scroll=new audio.sprocket.lagsense.ListScroll();var list=new UiScrollList(screen.margin(),100,screen.contentWidth(),240,scroll);list.virtual(rows.entries,null);
            if(!list.visible(9)||list.visible(10))throw new AssertionError("A 240-pixel viewport must fit ten compact rows");
            list.restore(24*40+3);
            var buttons=list.children().stream().filter(w->w instanceof UiButton).map(w->(UiButton)w).toList();
            for(var button:buttons.subList(0,2)) {
                if(button.getHeight()!=16||font.width(UiFonts.rich(button.getMessage().getString()))>button.getWidth()-10)throw new AssertionError("Compact action label must fit: "+button.getMessage().getString()+" needs "+font.width(UiFonts.rich(button.getMessage().getString()))+", available "+(button.getWidth()-10));
                var hit=list.getChildAt(button.getX()+button.getWidth()/2,button.getY()+button.getHeight()/2).orElseThrow();
                if(hit!=button)throw new AssertionError("Compact action hitboxes must follow nested, scrolled rows at scale "+scale);
                button.onPress(null);
            }
            if(!Arrays.equals(pressed,new int[]{1,1}))throw new AssertionError("Mark and TP must remain independently clickable");
        }
    }
    @SuppressWarnings("unchecked")
    private static void check(CpuInvestigation data,boolean running,String selection,int expected) throws Exception {
        var screen=new CallTreeScreen(null,data,null);screen.width=720;screen.height=480;
        var mode=CallTreeScreen.class.getDeclaredField("running");mode.setAccessible(true);mode.setBoolean(screen,running);
        var field=CallTreeScreen.class.getDeclaredField("inspected");field.setAccessible(true);((Set<String>)field.get(screen)).add(selection);
        screen.init();
        var list=LagScreen.class.getDeclaredField("currentList");list.setAccessible(true);
        var children=((UiScrollList)list.get(screen)).children();
        long details=children.stream().filter(w->w instanceof UiDetails).count();
        if(details!=expected)throw new AssertionError("Inline inspection failed for "+selection+" in "+(running?"Running code":"Call hierarchy"));
        long actions=children.stream().filter(w->w instanceof UiButton).count();
        if(actions!=(running?0:1))throw new AssertionError("Only Call hierarchy retains an Inspect button");
        if(running&&children.stream().filter(w->w instanceof UiRow).anyMatch(w->!((UiRow)w).active))throw new AssertionError("Running code rows remain clickable for inline inspection");
    }
}
