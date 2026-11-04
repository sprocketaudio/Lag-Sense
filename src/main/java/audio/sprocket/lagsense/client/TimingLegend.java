package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Fixed, compact legend in the map's existing footer line. No frame-time interpolation/allocation. */
final class TimingLegend {
    static final String LOW=String.format(java.util.Locale.ROOT,"%.0f µs/t",TimingSeverity.MIN_MICROS),HIGH=String.format(java.util.Locale.ROOT,"%,.0f+ µs/t",TimingSeverity.MAX_MICROS);
    private static final int[] COLORS=new int[128];
    static {for(int i=0;i<COLORS.length;i++)COLORS[i]=TimingSeverity.color(TimingSeverity.legendMicros(i/(double)(COLORS.length-1)));}
    record Bounds(int barX,int barY,int barWidth,int highX,int right) {}
    private static Bounds cached;
    private static int lastX,lastY,lastWidth,fontGeneration=-1;
    static Bounds bounds(int x,int y,int width){
        if(cached!=null&&lastX==x&&lastY==y&&lastWidth==width&&fontGeneration==UiFonts.generation())return cached;
        lastX=x;lastY=y;lastWidth=width;fontGeneration=UiFonts.generation();
        int lowWidth=UiFonts.width(UiFonts.mono(),LOW),highWidth=UiFonts.width(UiFonts.mono(),HIGH),barWidth=Math.max(1,Math.min(COLORS.length,width-lowWidth-highWidth-16));
        int barX=x+lowWidth+8,highX=barX+barWidth+8;return cached=new Bounds(barX,y+1,barWidth,highX,highX+highWidth);
    }
    static void paint(GuiGraphicsExtractor g,int x,int y,int width){
        var b=bounds(x,y,width);UiFonts.draw(g,UiFonts.mono(),LOW,x,y,width,UiTheme.SECONDARY,false);
        for(int i=0;i<b.barWidth();i++)g.fill(b.barX()+i,b.barY(),b.barX()+i+1,b.barY()+6,color(i,b.barWidth()));
        UiFonts.draw(g,UiFonts.mono(),HIGH,b.highX(),y,Math.max(0,x+width-b.highX()),UiTheme.SECONDARY,false);
    }
    static int color(int pixel,int width){return COLORS[width<=1?0:(int)Math.round(pixel*(COLORS.length-1.0)/(width-1))];}
    private TimingLegend(){}
}
