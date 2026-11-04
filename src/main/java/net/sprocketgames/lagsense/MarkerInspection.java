package net.sprocketgames.lagsense;

import java.util.*;

/** Exact crosshair lookup, independent of the number or costs of nearby measurements. */
public final class MarkerInspection {
    public static List<Report.Entry> block(MarkerIndex index,String dimension,int x,int y,int z,MarkerEligibility.Block live,int ticks,double minimum){
        return index.position(dimension,x,y,z).stream().filter(e->MarkerEligibility.timing(e,ticks,minimum)&&MarkerEligibility.matches(e,live)).toList();
    }
    public static Report.Entry entity(MarkerIndex index,String dimension,UUID id,int ticks,double minimum){
        var entry=index.entity(dimension,id);return entry!=null&&MarkerEligibility.timing(entry,ticks,minimum)?entry:null;
    }
    private MarkerInspection(){}
}
