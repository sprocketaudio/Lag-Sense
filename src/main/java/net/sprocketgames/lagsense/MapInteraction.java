package net.sprocketgames.lagsense;

/** Shared pixel/chunk transform and click-versus-drag state; independent of rendering. */
public final class MapInteraction {
    public record Viewport(int left,int top,int right,int bottom){public int width(){return Math.max(1,right-left);}public int height(){return Math.max(1,bottom-top);}public boolean contains(double x,double y){return x>=left&&x<right&&y>=top&&y<bottom;}public double midX(){return(left+right)/2.0;}public double midY(){return(top+bottom)/2.0;}}
    public record Geometry(int left,int top,int columns,int rows,int anchorX,int anchorZ) {}
    public record Zoom(double x,double z,int cell) {}
    public record Shift(double x,double y) {}
    private boolean pressed,dragging;
    private double startX,startY,lastX,lastY;
    public void begin(double x,double y){pressed=true;dragging=false;startX=lastX=x;startY=lastY=y;}
    public boolean pressed(){return pressed;}
    public Shift drag(double x,double y){if(!pressed)return new Shift(0,0);if(!dragging&&(x-startX)*(x-startX)+(y-startY)*(y-startY)<9)return new Shift(0,0);dragging=true;var shift=new Shift(x-lastX,y-lastY);lastX=x;lastY=y;return shift;}
    public boolean release(){boolean click=pressed&&!dragging;cancel();return click;}
    public void cancel(){pressed=dragging=false;}
    public static Geometry geometry(Viewport v,double x,double z,int cell){int columns=odd((v.width()+cell-1)/cell+2),rows=odd((v.height()+cell-1)/cell+2),cx=(int)Math.floor(x),cz=(int)Math.floor(z);return new Geometry((int)Math.floor(v.midX()+(cx-columns/2.0+.5-x)*cell),(int)Math.floor(v.midY()+(cz-rows/2.0+.5-z)*cell),columns,rows,cx,cz);}
    private static int odd(int n){return n%2==0?n+1:n;}
    public static Zoom zoom(Viewport v,double x,double z,int cell,double mouseX,double mouseY,int steps){int next=(int)Math.max(2,Math.min(34,(long)cell+4L*steps));if(next==cell)return new Zoom(x,z,cell);double worldX=x+(mouseX-v.midX())/cell,worldZ=z+(mouseY-v.midY())/cell;return new Zoom(worldX-(mouseX-v.midX())/next,worldZ-(mouseY-v.midY())/next,next);}
}
