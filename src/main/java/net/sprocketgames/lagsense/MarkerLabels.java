package net.sprocketgames.lagsense;

import java.util.*;

/** Screen-space occupancy only limits ordinary text; it never removes measurements or block outlines. */
public final class MarkerLabels {
    private static final int CELL=8;
    private int width,height,columns,rows;
    private final BitSet occupied=new BitSet();
    public void begin(int width,int height){this.width=width;this.height=height;columns=(width+CELL-1)/CELL;rows=(height+CELL-1)/CELL;occupied.clear();}
    public boolean offer(double x,double y,double w,double h,boolean inspected){
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(w)||!Double.isFinite(h)||w<=0||h<=0||x+w<0||y+h<0||x>=width||y>=height)return false;
        int left=Math.max(0,(int)Math.floor((x-2)/CELL)),right=Math.min(columns-1,(int)Math.floor((x+w+2)/CELL)),top=Math.max(0,(int)Math.floor((y-2)/CELL)),bottom=Math.min(rows-1,(int)Math.floor((y+h+2)/CELL));
        if(!inspected)for(int row=top;row<=bottom;row++){int first=occupied.nextSetBit(row*columns+left);if(first>=0&&first<=row*columns+right)return false;}
        for(int row=top;row<=bottom;row++)occupied.set(row*columns+left,row*columns+right+1);return true;
    }
}
