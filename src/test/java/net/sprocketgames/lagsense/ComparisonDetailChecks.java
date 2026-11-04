package net.sprocketgames.lagsense;

import java.util.*;

public final class ComparisonDetailChecks {
    private static final UUID MOVED=new UUID(0,1),REMOVED=new UUID(0,2),ADDED=new UUID(0,3);
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static void close(double a,double b,String why){check(Math.abs(a-b)<1e-7,why+": "+a+" / "+b);}
    private static Report.Entry entity(UUID id,String dim,int x,long nanos){return new Report.Entry("Entity","minecraft:creeper",dim,x,64,0,nanos,100,id);}
    public static ProfileComparison fixture(){
        var before=List.of(entity(MOVED,"minecraft:overworld",-1,10_000_000),entity(MOVED,"minecraft:overworld",1,5_000_000),entity(REMOVED,"minecraft:overworld",5,20_000_000),
            new Report.Entry("Block","minecraft:hopper","minecraft:overworld",8,64,0,2_000_000,100),new Report.Entry("Random block","minecraft:grass_block","minecraft:overworld",9,64,0,1_000_000,100),new Report.Entry("Scheduled fluid","minecraft:water","minecraft:overworld",10,64,0,1_000_000,100),
            new Report.Entry("Entity","fixture:legacy","minecraft:overworld",11,64,0,2_000_000,100));
        var after=List.of(entity(MOVED,"minecraft:the_nether",32,20_000_000),entity(ADDED,"minecraft:overworld",5,40_000_000),
            new Report.Entry("Block","minecraft:hopper","minecraft:overworld",8,64,0,4_000_000,200),new Report.Entry("Random block","minecraft:grass_block","minecraft:overworld",9,64,0,2_000_000,200),new Report.Entry("Scheduled fluid","minecraft:water","minecraft:overworld",10,64,0,2_000_000,200),
            new Report.Entry("Entity","fixture:legacy","minecraft:overworld",11,64,0,8_000_000,200));
        return ProfileComparison.between(new Report(90,1000,100,10,true,false,before),new Report(2,2000,200,8,true,false,after));
    }
    public static List<ComparisonIndex.Node> leaves(List<ComparisonIndex.Node> roots){return roots.stream().flatMap(n->n.children().isEmpty()?java.util.stream.Stream.of(n):leaves(n.children()).stream()).toList();}
    public static void run()throws Exception{
        var c=fixture();var index=new ComparisonIndex(c);var types=leaves(index.all.types());
        var moved=types.stream().filter(n->MOVED.equals(n.uuid())).findFirst().orElseThrow();
        check(moved.status().equals("UUID matched")&&moved.earlier().targets()==1&&moved.later().targets()==1,"One UUID matches across chunks/dimensions and counts as one contributor");
        close(moved.before(c),150,"Earlier entity timing sums its actual chunk contributions");close(moved.after(c),100,"Later entity timing uses its own tick denominator");close(moved.delta(c),-50,"Different-duration comparison normalizes before subtracting");close(moved.percent(c),-100.0/3,"Percentage uses normalized earlier cost");
        check(types.stream().filter(n->REMOVED.equals(n.uuid())).allMatch(n->n.status().equals("Removed")&&n.later().targets()==0),"Unmatched earlier UUID is removed from capture data");
        check(types.stream().filter(n->ADDED.equals(n.uuid())).allMatch(n->n.status().equals("Added")&&n.earlier().targets()==0),"Same registry/coordinates do not match unrelated UUIDs");
        for(String kind:List.of("Block","Random block","Scheduled fluid")){var node=types.stream().filter(n->n.key().kind().equals(kind)).findFirst().orElseThrow();check(node.status().equals("Same location"),"Captured category/registry/dimension/position establishes equivalent activity");close(node.delta(c),0,"Doubling ticks and total work does not create a regression");}
        var unknown=types.stream().filter(n->n.key().type().equals("fixture:legacy")).findFirst().orElseThrow();check(unknown.status().equals("Matching unavailable")&&!unknown.earlier().countKnown(),"Older UUID-less entity data keeps aggregate totals without invented matches");
        var chunkMoved=leaves(index.all.chunks()).stream().filter(n->MOVED.equals(n.uuid())).toList();check(chunkMoved.size()==3&&chunkMoved.stream().allMatch(n->n.status().contains("UUID matched")),"Chunk changes keep the same UUID identity but preserve each original chunk contribution");
        for(var node:chunkMoved){check(node.earlier().targets()==0||node.later().targets()==0,"Moved work is never relocated into the latest chunk");check(!node.status().equals("Removed")&&!node.status().equals("Added"),"Chunk movement is not presented as unrelated added/removed entities");}
        for(var view:List.of(index.all,index.view("minecraft:overworld"),index.view("minecraft:the_nether"))){
            double before=c.details().earlier().stream().filter(e->view.dimension().equals("All")||e.dimension().equals(view.dimension())).mapToDouble(e->e.micros(c.beforeTicks())).sum();
            double after=c.details().later().stream().filter(e->view.dimension().equals("All")||e.dimension().equals(view.dimension())).mapToDouble(e->e.micros(c.afterTicks())).sum();
            for(var roots:List.of(view.types(),view.chunks())){
                close(roots.stream().mapToDouble(n->n.before(c)).sum(),before,"Aggregate earlier timing includes filtered measurements exactly once");close(roots.stream().mapToDouble(n->n.after(c)).sum(),after,"Aggregate later timing includes filtered measurements exactly once");
                for(int i=1;i<roots.size();i++)check(Math.abs(roots.get(i-1).delta(c))>=Math.abs(roots.get(i).delta(c)),"Default sorting uses largest absolute change");
                check(roots.stream().filter(n->index.matches(n,1)).allMatch(n->n.delta(c)>0)&&roots.stream().filter(n->index.matches(n,2)).allMatch(n->n.delta(c)<0),"Higher/Lower filters preserve chronological direction");
            }
        }
        for(var chunk:index.all.chunks())for(var category:chunk.children())for(var type:category.children())for(var target:type.children())check(target.key().scope().equals("chunkTarget")&&target.key().dimension().equals(chunk.key().dimension())&&target.key().x()==chunk.key().x()&&target.key().z()==chunk.key().z(),"Chunk → Category → Type → Target preserves location scope");
        var overMoved=leaves(index.view("minecraft:overworld").types()).stream().filter(n->MOVED.equals(n.uuid())).findFirst().orElseThrow();check(overMoved.status().contains("dimension only"),"Dimension filtering preserves global UUID identity without importing timing from another dimension");
        edgeCases(c);large();exports(c);archived(c);ComparisonWire.validate(c);
        check(ComparisonWire.decode(ComparisonWire.encode(c)).equals(c),"Source identities, measurements and completeness survive wire round-trip");
        var wrong=new ArrayList<>(c.types());var first=wrong.getFirst();wrong.set(0,new ProfileComparison.Change(first.dimension(),first.kind(),first.type(),first.x(),first.z(),first.beforeMicros()+1,first.afterMicros(),first.beforeTargets(),first.afterTargets()));
        try{ComparisonWire.decode(ComparisonWire.encode(new ProfileComparison(c.beforeId(),c.afterId(),c.beforeAt(),c.afterAt(),c.beforeTicks(),c.afterTicks(),c.beforeMspt(),c.afterMspt(),c.incomplete(),List.copyOf(wrong),c.chunks(),c.details(),false)));throw new AssertionError("Incorrect aggregate accepted");}catch(java.io.IOException expected){}
        check(Profiler.JSON.toJson(c).equals(Profiler.JSON.toJson(ProfileComparison.between(new Report(2,2000,200,8,true,false,c.details().later()),new Report(90,1000,100,10,true,false,c.details().earlier())))),"Reversing selection preserves complete comparison sources and chronology");
        System.out.println("Comparison detail checks passed (UUID/location matching, movement, missing/legacy/incomplete data, normalization, hierarchy, filters, 6000 targets, segmented transfer and exports).");
    }
    private static void archived(ProfileComparison c)throws Exception{
        var dir=java.nio.file.Files.createTempDirectory("lagsense-comparison-");var failures=new ArrayList<Exception>();
        try{
            try(var archive=new ProfileArchive(dir,Profiler.JSON,new ProfileArchive.Settings(true,3,8),failures::add)){
                archive.capture(new Report(c.beforeId(),c.beforeAt(),c.beforeTicks(),c.beforeMspt(),true,false,c.details().earlier()));
                archive.capture(new Report(c.afterId(),c.afterAt(),c.afterTicks(),c.afterMspt(),true,false,c.details().later()));
                var future=new java.util.concurrent.CompletableFuture<ProfileComparison>();archive.compare(c.afterId(),c.beforeId(),(result,error)->{if(error!=null)future.completeExceptionally(error);else future.complete(result);});
                check(future.get(10,java.util.concurrent.TimeUnit.SECONDS).equals(c)&&failures.isEmpty(),"Actual asynchronous archive reads retain both capture sources and normalize their selection order");
            }
        }finally{java.nio.file.Files.deleteIfExists(dir.resolve("profile-"+c.beforeId()+".json"));java.nio.file.Files.deleteIfExists(dir.resolve("profile-"+c.afterId()+".json"));java.nio.file.Files.deleteIfExists(dir);}
    }
    private static void edgeCases(ProfileComparison c){
        var limited=ProfileComparison.between(new Report(90,1000,100,10,true,true,c.details().earlier()),new Report(2,2000,200,8,true,false,c.details().later()));
        var missing=leaves(new ComparisonIndex(limited).all.types());check(missing.stream().filter(n->ADDED.equals(n.uuid())||REMOVED.equals(n.uuid())).noneMatch(n->n.status().equals("Added")||n.status().equals("Removed")),"Incomplete captures use unmeasured rather than definitive removal/addition labels");
        check(limited.details().earlierIncomplete()&&!limited.details().laterIncomplete()&&!limited.details().earlierLimit().isBlank(),"Completeness is retained separately for both sides");
        var old=new ProfileComparison(c.beforeId(),c.afterId(),c.beforeAt(),c.afterAt(),c.beforeTicks(),c.afterTicks(),c.beforeMspt(),c.afterMspt(),c.incomplete(),c.types(),c.chunks());
        check(new ComparisonIndex(old).all.types().stream().allMatch(n->n.children().isEmpty()&&n.status().contains("unavailable")),"Older aggregate-only comparison payloads remain readable");
        var zero=new Report.Entry("Block","fixture:zero","minecraft:overworld",0,64,0,0,100);var positive=new Report.Entry("Block","fixture:zero","minecraft:overworld",0,64,0,5000,200);
        var z=ProfileComparison.between(new Report(1,100,100,1,true,false,List.of(zero)),new Report(2,200,200,1,true,false,List.of(positive)));var node=leaves(new ComparisonIndex(z).all.types()).getFirst();check(node.percent(z)==null&&Double.isFinite(node.delta(z)),"Observed zero baseline yields a finite absolute change and no invented percentage");
        var replaced=new Report.Entry("Block","fixture:different","minecraft:overworld",0,64,0,5000,200);var r=ProfileComparison.between(new Report(1,100,100,1,true,false,List.of(zero)),new Report(2,200,200,1,true,false,List.of(replaced)));check(leaves(new ComparisonIndex(r).all.types()).size()==2,"Block replacements do not match across different registry IDs");
        var nether=new Report.Entry("Block",zero.type(),"minecraft:the_nether",zero.x(),zero.y(),zero.z(),5000,200);var differentDimension=ProfileComparison.between(new Report(1,100,100,1,true,false,List.of(zero)),new Report(2,200,200,1,true,false,List.of(nether)));check(leaves(new ComparisonIndex(differentDimension).all.types()).size()==2,"Equal block positions in different dimensions are distinct targets");
        var oldEntity=new Report.Entry("Entity","fixture:old","minecraft:overworld",0,64,0,10000,100);var identified=new Report.Entry("Entity",oldEntity.type(),oldEntity.dimension(),0,64,0,20000,200,new UUID(0,99));var mixed=ProfileComparison.between(new Report(1,100,100,1,true,false,List.of(oldEntity)),new Report(2,200,200,1,true,false,List.of(identified)));check(leaves(new ComparisonIndex(mixed).all.types()).stream().noneMatch(n->n.status().equals("Added")||n.status().equals("Removed")),"A UUID appearing after a legacy capture is not proof of an added entity");
    }
    private static void large()throws Exception{
        var before=new ArrayList<Report.Entry>();var after=new ArrayList<Report.Entry>();
        for(int i=0;i<6000;i++){var id=UUID.nameUUIDFromBytes(("entity-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8));before.add(new Report.Entry("Entity","fixture:type_"+i,"minecraft:overworld",i,64,0,i*10000L,100,id));after.add(new Report.Entry("Entity","fixture:type_"+i,"minecraft:overworld",i+16,64,0,i*40000L,200,id));}
        var c=ProfileComparison.between(new Report(1,100,100,1,true,false,List.copyOf(before)),new Report(2,200,200,1,true,false,List.copyOf(after)));
        check(c.types().size()==6000&&c.chunks().size()>128&&leaves(new ComparisonIndex(c).all.types()).size()==6000,"Large comparison has no fixed top-N presentation truncation");
        byte[] bytes=ComparisonWire.encode(c);check(bytes.length>FullReportPart.PART_BYTES,"Large fixture exercises segmented transfer");var assembly=new ReportAssembly();ReportAssembly.Complete complete=null;
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY,net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        try{for(int offset=0;offset<bytes.length;offset+=FullReportPart.PART_BYTES){var packet=new ComparisonPart(new FullReportPart(1,bytes.length,offset,Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+FullReportPart.PART_BYTES)),false,false));buffer.clear();ComparisonPart.CODEC.encode(buffer,packet);complete=assembly.accept(ComparisonPart.CODEC.decode(buffer).part());}}finally{buffer.release();}
        check(complete!=null&&ComparisonWire.decode(complete.bytes()).equals(c),"Complete comparison segments retain all sources and aggregate results");
        var preview=Profiler.JSON.fromJson(ComparisonWire.previewJson(c),ProfileComparison.class);check(preview.details()==null&&preview.presentationLimited()&&preview.types().size()<=128,"Legacy clients receive an explicit bounded preview");
    }
    private static void exports(ProfileComparison c){
        String csv=ReportExport.csv(c),json=ReportExport.json(c);check(csv.contains(MOVED.toString())&&csv.contains("UUID matched")&&csv.contains("before_ticks")&&json.contains("investigation")&&json.contains("deltaMicros"),"Exports include actual matched contributors, timing direction and capture denominators");
        var rows=csv.split("\r\n");int columns=rows[0].split(",",-1).length;for(var row:rows)check(row.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)",-1).length==columns,"Detailed CSV rows retain a consistent column schema");
        check(!json.contains("NaN")&&!json.contains("Infinity"),"Undefined percentages never leak invalid numeric values into exports");
        check(json.contains("\"beforeObserved\":false")&&json.contains("\"beforeMicros\":null"),"Detailed JSON distinguishes a missing observation from an observed zero cost");
    }
}
