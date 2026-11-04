package audio.sprocket.lagsense;

import java.util.*;

final class HeatmapChecks {
    static void run() {
        for(int cell:List.of(2,6,18,34)) {
            int columns=17,rows=13,width=columns*cell,height=rows*cell;
            var tiles=List.of(new HeatmapPlan.Tile(-3,4,31),new HeatmapPlan.Tile(-11,-2,47),new HeatmapPlan.Tile(5,10,63),new HeatmapPlan.Tile(10000,10000,99));
            var view=new HeatmapPlan(7,9,columns,rows,cell,-3,4,tiles,10,0);
            int[] painted=new int[width*height],reference=new int[painted.length];
            view.paint((left,top,right,bottom,color)->{for(int y=top;y<bottom;y++)Arrays.fill(painted,(y-9)*width+left-7,(y-9)*width+right-7,color);});
            for(int rz=0;rz<rows;rz++)for(int rx=0;rx<columns;rx++) {
                int x=-3+rx-columns/2,z=4+rz-rows/2,color=10;
                for(var tile:tiles)if(tile.x()==x&&tile.z()==z)color=tile.color();
                for(int y=rz*cell;y<(rz+1)*cell-1;y++)Arrays.fill(reference,y*width+rx*cell,y*width+(rx+1)*cell-1,color);
                var hit=view.at(7+rx*cell+cell/2.0,9+rz*cell+cell/2.0);
                if(!hit.equals(new HeatmapPlan.Cell(x,z)))throw new AssertionError("Map hover/click must retain chunk coordinates at every zoom");
            }
            if(!Arrays.equals(painted,reference))throw new AssertionError("Sparse heatmap plan must preserve the original grid and captured colours pixel-for-pixel");
            if(view.at(6,9)!=null||view.at(view.right(),9)!=null||view.at(7,view.bottom())!=null)throw new AssertionError("Map hit-testing excludes panel edges");
            if(!view.matches(7,9,columns,rows,cell,-3,4)||view.matches(7,9,columns,rows,cell+1,-3,4)||view.matches(7,9,columns,rows,cell,-2,4))throw new AssertionError("Cached map geometry invalidates on zoom and pan");
        }
        var tiles=new ArrayList<HeatmapPlan.Tile>();for(int i=0;i<128;i++)tiles.add(new HeatmapPlan.Tile(i%16-8,i/16-4,i+1));
        var zoomedOut=new HeatmapPlan(0,0,333,239,2,0,0,tiles,10,0);int[] calls={0};
        zoomedOut.paint((left,top,right,bottom,color)->{calls[0]++;if(left<0||top<0||right>666||bottom>478)throw new AssertionError("Map draws only inside the viewport");});
        if(calls[0]!=1+333+239+128||calls[0]*100L>=333L*239)throw new AssertionError("Maximum zoom-out draw work must grow by axes plus recorded chunks, not empty-cell count");
        int before=calls[0];zoomedOut.paint((l,t,r,b,c)->calls[0]++);if(calls[0]!=before*2)throw new AssertionError("Repeated map frames reuse a stable plan");
        var dense=new ArrayList<HeatmapPlan.Tile>();for(int z=-100;z<=100;z++)for(int x=-100;x<=100;x++)dense.add(new HeatmapPlan.Tile(x,z,(x+z)%2==0?0xff69f0ae:0xffffe082));
        var densePlan=new HeatmapPlan(0,0,333,239,2,0,0,dense,10,0);
        if(zoomedOut.textureRecommended()||!densePlan.textureRecommended())throw new AssertionError("Dense complete maps must use a cached texture rather than unlimited per-frame cell submissions");
        var edge=new HeatmapPlan(0,0,3,3,2,Integer.MIN_VALUE,Integer.MAX_VALUE,List.of(),10,0);
        if(edge.column(Integer.MAX_VALUE)!=-1||edge.row(Integer.MIN_VALUE)!=-1||edge.at(0,0)!=null)throw new AssertionError("Extreme coordinates must not wrap into visible chunks");
        System.out.println("Heatmap checks passed (pixel-equivalent sparse rendering, zoom/pan hitboxes, cache keys, coordinate bounds; 79,587 cell draws reduced to 701 grid/data draws in fixture).");
    }
}
