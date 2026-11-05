package net.sprocketgames.lagsense.client;

import java.util.Set;

/** Display choices only. Captured results and permissions are never stored here. */
public record ResultViewSettings(String category,String dimension,String group,String sort,String search,String targetCategory) {
    public ResultViewSettings {
        if(category==null||!Set.of("All","Entity","Block","Ticks","Chunks").contains(category))category="All";
        if(dimension==null||dimension.isBlank()||dimension.length()>256)dimension="All";
        if(group==null||!Set.of("instance","type","mod-instance","mod-type").contains(group))group="instance";
        if(sort==null||!Set.of("time","count").contains(sort))sort="time";
        if(search==null)search="";
        if(search.length()>256)search=search.substring(0,256);
        if(targetCategory==null||!Set.of("All","Entity","Block","Ticks").contains(targetCategory))targetCategory=category.equals("Chunks")?"All":category;
    }
    public ResultViewSettings(String category,String dimension,String group,String sort,String search){this(category,dimension,group,sort,search,category);}
    public static ResultViewSettings defaults(){return new ResultViewSettings("All","All","instance","time","");}
}
