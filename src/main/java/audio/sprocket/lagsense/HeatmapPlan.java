package audio.sprocket.lagsense;

import java.util.*;

/** Cached sparse map geometry. Empty grid work grows by axes, never by their product. */
public final class HeatmapPlan {
    public record Tile(int x,int z,int color) {}
    public record Cell(int x,int z) {}
    private record Rect(int left,int top,int right,int bottom,int color) {}
    @FunctionalInterface public interface Fill { void fill(int left,int top,int right,int bottom,int color); }
    private final int left,top,columns,rows,cell,centerX,centerZ;
    private final List<Rect> fills;
    public HeatmapPlan(int left,int top,int columns,int rows,int cell,int centerX,int centerZ,List<Tile> tiles,int empty,int gap) {
        if(columns<1||rows<1||cell<2)throw new IllegalArgumentException("Invalid map geometry");
        this.left=left;this.top=top;this.columns=columns;this.rows=rows;this.cell=cell;this.centerX=centerX;this.centerZ=centerZ;
        int right=Math.addExact(left,Math.multiplyExact(columns,cell)),bottom=Math.addExact(top,Math.multiplyExact(rows,cell));
        var rectangles=new ArrayList<Rect>(1+columns+rows+tiles.size());
        rectangles.add(new Rect(left,top,right,bottom,empty));
        for(int x=0;x<columns;x++){int px=pixelX(x)+cell-1;rectangles.add(new Rect(px,top,px+1,bottom,gap));}
        for(int z=0;z<rows;z++){int py=pixelY(z)+cell-1;rectangles.add(new Rect(left,py,right,py+1,gap));}
        for(var tile:tiles){int x=column(tile.x()),z=row(tile.z());if(x>=0&&z>=0){int px=pixelX(x),py=pixelY(z);rectangles.add(new Rect(px,py,px+cell-1,py+cell-1,tile.color()));}}
        fills=List.copyOf(rectangles);
    }
    public boolean matches(int left,int top,int columns,int rows,int cell,int centerX,int centerZ) {
        return this.left==left&&this.top==top&&this.columns==columns&&this.rows==rows&&this.cell==cell&&this.centerX==centerX&&this.centerZ==centerZ;
    }
    public int left(){return left;}
    public int top(){return top;}
    public int right(){return left+columns*cell;}
    public int bottom(){return top+rows*cell;}
    public boolean textureRecommended(){return fills.size()>2048;}
    public int pixelX(int column){return left+column*cell;}
    public int pixelY(int row){return top+row*cell;}
    public int column(int x){long value=(long)x-centerX+columns/2;return value<0||value>=columns?-1:(int)value;}
    public int row(int z){long value=(long)z-centerZ+rows/2;return value<0||value>=rows?-1:(int)value;}
    public Cell at(double x,double y) {
        if(x<left||x>=right()||y<top||y>=bottom())return null;
        long cx=(long)centerX+(int)((x-left)/cell)-columns/2,cz=(long)centerZ+(int)((y-top)/cell)-rows/2;
        return cx<Integer.MIN_VALUE||cx>Integer.MAX_VALUE||cz<Integer.MIN_VALUE||cz>Integer.MAX_VALUE?null:new Cell((int)cx,(int)cz);
    }
    public void paint(Fill target){for(var rect:fills)target.fill(rect.left(),rect.top(),rect.right(),rect.bottom(),rect.color());}
}
