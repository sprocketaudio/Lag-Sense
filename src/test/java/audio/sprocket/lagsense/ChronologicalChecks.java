package audio.sprocket.lagsense;

import java.util.*;
import audio.sprocket.lagsense.client.UiTheme;

final class ChronologicalChecks {
    private static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Report report(long id,long at,double mspt,boolean limited,long first,long second){
        return new Report(id,at,10,mspt,true,limited,List.of(
            new Report.Entry("Block","fixture:faster","minecraft:overworld",0,64,0,first,10),
            new Report.Entry("Entity","fixture:slower","minecraft:overworld",32,64,0,second,10)));
    }
    static void run(com.google.gson.Gson gson){
        var earlier=report(90,1000,8,false,100000,10000);var later=report(2,2000,12,true,20000,80000);
        var forward=ProfileComparison.between(earlier,later);var reversed=ProfileComparison.between(later,earlier);
        require(forward.equals(reversed),"Either selection order produces an identical comparison, including MSPT, types and chunks");
        require(forward.beforeId()==90&&forward.afterId()==2&&forward.beforeAt()==1000&&forward.afterAt()==2000,"Timestamp order takes precedence over IDs and selected baseline");
        require(forward.afterMspt()-forward.beforeMspt()==4&&forward.incomplete(),"MSPT direction and incomplete-capture warning remain chronological");
        for(var list:List.of(forward.types(),forward.chunks())){
            require(list.stream().filter(c->ProfileComparison.matches(c,1)).count()==1&&list.stream().filter(c->ProfileComparison.matches(c,2)).count()==1,"Higher and Lower filters follow chronological signs for types and chunks");
            require(list.stream().allMatch(c->ProfileComparison.matches(c,0)),"All retains every change");
            require(list.get(0).delta()==-8&&list.get(1).delta()==7,"Sorting retains largest absolute cost changes first");
            require(UiTheme.changeColor(list.get(0).delta())==0xff69f0ae&&UiTheme.changeColor(list.get(1).delta())==UiTheme.WARNING,"Negative chronological differences are green improvements, positive are amber regressions");
        }
        require(ReportExport.csv(forward).equals(ReportExport.csv(reversed))&&gson.toJson(forward).equals(gson.toJson(reversed)),"Copied JSON and exported CSV cannot reverse direction");
        var tieA=report(3,3000,5,false,1000,2000);var tieB=report(7,3000,6,false,2000,1000);
        var tied=ProfileComparison.between(tieB,tieA);
        require(tied.equals(ProfileComparison.between(tieA,tieB))&&tied.beforeId()==3&&tied.afterId()==7,"Equal timestamps break ties deterministically by saved-profile ID");
        require(ProfileComparison.between(tieA,tieA).types().stream().allMatch(c->c.delta()==0)&&UiTheme.changeColor(0)==UiTheme.TEXT,"Self comparisons stay neutral");
        for(long invalid:new long[]{0,-1,Long.MIN_VALUE}){
            try{ProfileComparison.between(report(5,invalid,1,false,1,1),later);throw new AssertionError("Invalid timestamp accepted");}catch(IllegalArgumentException expected){}
            try{ProfileComparison.between(later,report(5,invalid,1,false,1,1));throw new AssertionError("Invalid timestamp accepted in reverse order");}catch(IllegalArgumentException expected){}
        }
        require(earlier.capturedAt()==1000&&earlier.id()==90&&later.truncated(),"Comparison never modifies saved reports");
        System.out.println("Chronological comparison checks passed (selection order, timestamp ties, invalid data, signs, filters, sorting and exports).");
    }
}
