package audio.sprocket.lagsense;

import java.util.*;

final class WorldMarkerChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void run()throws Exception{
        String dimension="minecraft:overworld";var entries=new ArrayList<Report.Entry>();var kinds=List.of("Block","Random block","Random fluid","Scheduled block","Scheduled fluid","Pipe update");
        for(int i=0;i<12000;i++){String kind=kinds.get(i%kinds.size()),type=kind.equals("Block")?"fixture:ticker":kind.endsWith("fluid")?"minecraft:water":"fixture:machine";entries.add(new Report.Entry(kind,type,dimension,i%50-25,64+i/2500,i/50%50-25,i%3,1));}
        var distant=new Report.Entry("Block","fixture:far",dimension,16000,64,16000,999999,1);entries.add(distant);
        var other=new Report.Entry("Block","fixture:other","minecraft:the_nether",0,64,0,1,1);entries.add(other);
        UUID id=UUID.randomUUID();var entity=new Report.Entry("Entity","minecraft:item",dimension,16000,64,16000,100,1,id);entries.add(entity);
        var captured=new Report(401,1000,100,1,true,false,List.copyOf(entries)).view(true,List.of());
        var transferred=FullReportWire.decode(FullReportWire.encode(captured));check(transferred.entries().size()==12003&&transferred.equals(captured),"Measurement retention and complete client transfer precede marker indexing without truncation");
        var index=new MarkerIndex(transferred);var eligible=index.near(dimension,0,64,0,96,0);
        check(eligible.size()==12000&&eligible.stream().anyMatch(e->e.nanos()==0)&&eligible.stream().anyMatch(e->e.nanos()==1),"No hidden top-64/global marker limit: Min 0 retains every nearby zero/sub-microsecond target");
        check(eligible.stream().map(Report.Entry::kind).distinct().count()==6,"Ticking blocks, random/scheduled block/fluid and other matching captured categories remain eligible");
        long expected=eligible.stream().filter(e->e.micros(100)>=.000015).count();check(index.near(dimension,0,64,0,96,.000015).size()==expected&&index.near(dimension,0,64,0,96,1).isEmpty(),"Positive minima compare unrounded captured timings, without hidden floors");
        for(var e:eligible)check(index.position(e.dimension(),e.x(),e.y(),e.z()).contains(e),"Every retained nearby stationary measurement has exact dimension/position lookup");
        check(index.position(dimension,100,64,100).isEmpty(),"Looking at an unmeasured block never manufactures a zero timing");
        check(index.position(other.dimension(),0,64,0).equals(List.of(other))&&!index.position(dimension,0,64,0).contains(other),"Identical block positions in different dimensions have independent identities");
        check(index.entity(dimension,id).equals(entity)&&index.entity("minecraft:the_nether",id)==null&&!eligible.contains(entity),"UUID entity index is independent of stale capture coordinates and stationary queries");
        var live=new MarkerEligibility.Block(true,"fixture:machine","fixture:ticker","minecraft:water");
        for(var e:eligible)check(MarkerEligibility.timing(e,100,0)&&MarkerEligibility.matches(e,live),"All existing measured activity kinds can match their appropriate loaded block/entity/fluid identity");
        var random=eligible.stream().filter(e->e.kind().equals("Random block")).findFirst().orElseThrow();var fluid=eligible.stream().filter(e->e.kind().equals("Scheduled fluid")).findFirst().orElseThrow();var block=eligible.stream().filter(e->e.kind().equals("Block")).findFirst().orElseThrow();
        check(MarkerEligibility.matches(random,new MarkerEligibility.Block(true,"fixture:machine","",""))&&MarkerEligibility.matches(fluid,new MarkerEligibility.Block(true,"minecraft:water","","minecraft:water")),"Random/scheduled markers never require an unrelated block entity");
        check(!MarkerEligibility.matches(block,new MarkerEligibility.Block(true,"fixture:replacement","fixture:different_ticker",""))&&!MarkerEligibility.matches(random,new MarkerEligibility.Block(true,"fixture:replacement","","")),"Replacement blocks cannot inherit a mismatched ticking-block or random-update measurement");
        check(!MarkerEligibility.matches(fluid,new MarkerEligibility.Block(true,"minecraft:lava","","minecraft:lava"))&&!MarkerEligibility.matches(block,new MarkerEligibility.Block(false,"fixture:machine","fixture:ticker","minecraft:water")),"Fluid replacement and unloaded positions remain unavailable, not fabricated zeroes");
        var p=MarkerPosition.resolve(block,dimension,uuid->{throw new AssertionError("Stationary marker queried an entity");});check(p.x()==block.x()&&p.y()==block.y()&&p.z()==block.z()&&p.labelHeight()==1.3,"Block outline anchors stay on the captured block with a small existing label offset");
        check(MarkerEligibility.inRange(new MarkerPosition(0,64,0,1.3),0,64,0,1)&&!MarkerEligibility.inRange(new MarkerPosition(2,64,0,1.3),0,64,0,1),"Range filtering stays independent of timing and label density");
        check(MarkerPosition.resolve(entity,dimension,uuid->new MarkerPosition.Live(dimension,5,65,7,.25,false)).x()==4.5&&MarkerPosition.resolve(entity,dimension,uuid->null)==null,"Live UUID pose follows dropped items and never falls back to captured coordinates");
        check(MarkerPosition.resolve(entity,dimension,uuid->new MarkerPosition.Live("minecraft:the_nether",5,65,7,1,false))==null&&MarkerPosition.resolve(entity,dimension,uuid->new MarkerPosition.Live(dimension,5,65,7,1,true))==null,"Wrong-dimension/removed entities remain hidden");
        var switched=new MarkerIndex(new Report(402,1001,100,1,true,false,List.of(other)));check(switched.near(dimension,0,64,0,96,0).isEmpty()&&switched.entity(dimension,id)==null,"Capture switching cannot retain stale marker measurements");
        density(eligible,index);
        System.out.println("World marker checks passed (12,000 dense zero/cheap targets, full transfer/index/range/identity distinctions, categories, exact lookup, replacements, UUID movement/removal and independent label crowding).");
    }
    private static void density(List<Report.Entry> eligible,MarkerIndex index){
        var density=new MarkerLabels();density.begin(960,540);int visible=0;for(var e:eligible)if(density.offer(470,260,40,8,false))visible++;
        check(visible==1&&eligible.size()==12000,"Dense overlap suppresses ordinary text only, without truncating target/outline eligibility");
        var lookedAt=eligible.getLast();check(density.offer(470,260,40,8,true)&&index.position(lookedAt.dimension(),lookedAt.x(),lookedAt.y(),lookedAt.z()).contains(lookedAt),"The exact cheapest/zero target under the crosshair remains inspectable despite crowding");
        density.begin(960,540);check(density.offer(470,260,40,8,true)&&!density.offer(470,260,40,8,false),"Priority labels reserve space before ordinary neighbours");
        density.begin(960,540);int uncrowded=0;for(int y=0;y<500;y+=24)for(int x=0;x<900;x+=48)if(density.offer(x,y,20,8,false))uncrowded++;
        check(uncrowded>64,"There is no hidden global label count limit when labels fit without overlap");
        check(!density.offer(Double.NaN,10,10,10,true)&&!density.offer(1000,10,10,10,true),"Off-screen/invalid projected labels do not populate the density grid");
    }
}
