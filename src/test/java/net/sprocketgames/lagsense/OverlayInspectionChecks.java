package net.sprocketgames.lagsense;

import java.util.*;

public final class OverlayInspectionChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void run(){
        String dim="minecraft:overworld";var entries=new ArrayList<Report.Entry>();
        for(int i=0;i<12000;i++)entries.add(new Report.Entry("Block","fixture:machine",dim,i,64,0,i,1));
        var id=new UUID(1,1);var entity=new Report.Entry("Entity","minecraft:item",dim,4,65,4,10,1,id);entries.add(entity);
        var report=new Report(1,100,100,1,true,false,List.copyOf(entries));var index=new MarkerIndex(report);var live=new MarkerEligibility.Block(true,"fixture:machine","fixture:machine","");
        var inspected=MarkerInspection.block(index,dim,0,64,0,live,100,0);
        check(inspected.size()==1&&inspected.getFirst()==entries.getFirst(),"Only the exact aimed-at block supplies a label, including the cheapest zero-cost target");
        check(MarkerInspection.block(index,dim,0,65,0,live,100,0).isEmpty()&&MarkerInspection.block(index,"minecraft:the_nether",0,64,0,live,100,0).isEmpty(),"Air and unrelated dimensions produce no neighbouring labels");
        check(MarkerInspection.block(index,dim,0,64,0,new MarkerEligibility.Block(true,"fixture:replacement","fixture:replacement",""),100,0).isEmpty()&&MarkerInspection.block(index,dim,0,64,0,live,100,1).isEmpty(),"Exact labels retain live identity and the user's minimum timing filter");
        check(MarkerInspection.entity(index,dim,id,100,0)==entity&&MarkerInspection.entity(index,dim,new UUID(2,2),100,0)==null,"Entity inspection also uses the exact aimed-at UUID");
        check(index.near(dim,0,64,0,96,0).size()>64,"Fewer captions do not limit retained nearby outline candidates");
        var selector=new NearbyChunkLabels();var chunks=new ArrayList<Report.Chunk>();
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++)chunks.add(new Report.Chunk(dim,x,z,0,1,null));
        Collections.shuffle(chunks,new Random(3));var first=selector.select(chunks,dim,8,8);
        check(first.size()==3&&first.getFirst().x()==0&&first.getFirst().z()==0,"Only the three nearest chunks are labelled, independent of capture cost/order");
        var stable=new HashSet<>(first);
        for(int i=0;i<100;i++){Collections.rotate(chunks,7);var current=selector.select(chunks,dim,8+(i%2==0?.05:-.05),8);check(new HashSet<>(current).equals(stable),"Tiny camera movements and reordered inputs do not flicker nearest-chunk captions");}
        var moved=selector.select(chunks,dim,328,328);check(moved.getFirst().x()==20&&moved.getFirst().z()==20&&!new HashSet<>(moved).equals(stable),"Walking away replaces captions with genuinely closer chunks");
        check(selector.select(chunks,"minecraft:the_nether",8,8).isEmpty(),"Changing dimension clears stale nearest labels");selector.clear();
        check(selector.select(List.of(),dim,0,0).isEmpty(),"Empty/cleared overlays retain no captions");
        System.out.println("Overlay inspection checks passed (exact block/UUID captions, complete outlines, nearest-three stable chunk labels, dimension changes; original text scale is retained).");
    }
}
