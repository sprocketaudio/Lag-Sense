package audio.sprocket.lagsense;

import audio.sprocket.lagsense.client.*;
import java.util.*;
import net.minecraft.client.input.*;

final class ResultsUsabilityChecks {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void run() throws Exception {
        virtualLists();chunkBatches();methodDetails();modSearch();
        System.out.println("Results usability checks passed (virtual widget bounds, scrolling/hitboxes, stable expansion anchors, complete private chunk batches, codecs and compact CPU identities).");
    }
    private static List<UiScrollList.Entry> rows(int count,int[] built){
        var rows=new ArrayList<UiScrollList.Entry>();
        for(int i=0;i<count;i++){
            int id=i;rows.add(new UiScrollList.Entry(i,34,(list,y)->{
                built[0]++;list.add(new UiRow(8,y,664,32,"Method "+id,"Details","12.3%",0,88,0,false,()->{}));
                list.add(new UiButton(600,y+6,32,20,"TP",()->{}));
            }));
        }return rows;
    }
    private static void virtualLists(){
        var scroll=new ListScroll();var list=new UiScrollList(8,100,664,136,scroll);int[] built={0};
        var rows=rows(20000,built);list.virtual(rows,null);
        require(list.widgetCount()==8&&built[0]==4,"20,000 rows create only four visible rows/actions");
        list.restore(34*1234+5);
        require(list.widgetCount()<=10&&built[0]<=9,"Scroll materialises only the new visible window");
        require(list.visible(1234)&&!list.visible(0),"Lazy batch checks use the visible range");
        require(list.getChildAt(610,115).orElseThrow() instanceof UiButton,"Virtual action hitbox follows its scrolled row");
        var anchor=list.anchor();var inserted=new ArrayList<>(rows);inserted.add(0,new UiScrollList.Entry("expanded",120,(viewport,y)->{}));
        list.virtual(inserted,anchor);require(scroll.offset()==34*1234+5+120,"Expansion above viewport preserves the visible row and intra-row offset");
        list.virtual(rows,list.anchor());require(scroll.offset()==34*1234+5,"Collapsing restores the same content anchor");
        list.restore(Integer.MAX_VALUE);require(list.visible(19999)&&list.widgetCount()<=10,"All retained results are reachable without pagination");
        list.virtual(List.of(),list.anchor());require(scroll.offset()==0&&list.widgetCount()==0,"Empty filters release virtual widgets and clamp scrolling");
        list.virtual(rows,null);require(list.mouseClicked(new MouseButtonEvent(670,230,new MouseButtonInfo(0,0)),false),"Scrollbar track accepts a native click");
        require(scroll.offset()>0&&list.widgetCount()<=10,"Track click keeps widgets bounded");
        list.mouseReleased(new MouseButtonEvent(670,230,new MouseButtonInfo(0,0)));
        var index=new VirtualListIndex(List.of("a","b","c"),new int[]{34,500,34});
        require(index.at(33)==0&&index.at(34)==1&&index.at(533)==1&&index.at(534)==2,"Variable-height details share one continuous index");
    }
    private static Report report(List<Report.Entry> entries,boolean limited){return new Report(123,1000,100,3,true,limited,entries,false,0,Report.aggregate(entries),20);}
    private static void chunkBatches() throws Exception {
        var entries=new ArrayList<Report.Entry>();
        for(int i=0;i<605;i++)entries.add(new Report.Entry(i%2==0?"Block":"Entity","fixture:machine_"+i,"minecraft:overworld",i%16,64+i/256,(i/16)%16,1000L+i,10));
        entries.add(new Report.Entry("Block","secret:outside","minecraft:overworld",32,64,32,999999,10));
        var report=report(entries,true);
        require(report.view(true,e->true).entries().size()==606,"The complete report retains all permitted targets");
        Set<Integer> received=new HashSet<>();long nanos=0;
        for(var kind:List.of("Block","Entity")){
            int offset=0,total=entries.stream().filter(e->e.kind().equals(kind)&&e.x()<16).toList().size();long previous=Long.MAX_VALUE;
            while(offset<total){
                var batch=ChunkInvestigation.create(report,"minecraft:overworld",0,0,true,e->false,kind,offset);
                require(batch.hotspots().size()<=128&&batch.offset()==offset&&batch.pageTotal()==total&&batch.limited(),"Chunk pages are bounded independently of the capture-limit flag");
                var wire=new InvestigationWire.Data(null,batch,"");require(InvestigationWire.decode(InvestigationWire.encode(wire)).equals(wire),"Every bounded category batch survives compression and validation");
                for(var item:batch.hotspots()){
                    require(item.entry().kind().equals(kind)&&received.add(item.index()),"Batches contain actual category targets with unique stable teleport indices");
                    require(item.entry().nanos()<=previous,"Category contributors sort highest cost first across batch boundaries");previous=item.entry().nanos();nanos+=item.entry().nanos();
                }offset+=batch.hotspots().size();
            }
        }
        var chunk=ChunkInvestigation.create(report,"minecraft:overworld",0,0,true,e->false);
        require(received.size()==605&&received.contains(0)&&nanos==chunk.nanos(),"Cheap targets beyond the global top 128 remain reachable; individual costs sum to real category totals");
        var privateBatch=ChunkInvestigation.create(report,"minecraft:overworld",0,0,false,e->e.y()==64,"Block",0);
        require(!privateBatch.limited(),"Player batches must not expose a global capture-limit flag");
        require(privateBatch.hotspots().stream().allMatch(h->h.index()==-1&&h.entry().y()==64),"Every batch applies claim/public-area permissions and hides server indices");
        require(!Profiler.JSON.toJson(privateBatch).contains("secret:outside"),"Chunk batches do not leak other chunks");
        var revoked=ChunkInvestigation.create(report,"minecraft:overworld",0,0,false,e->false,"Block",0);
        require(revoked.targets()==0&&!revoked.equals(privateBatch),"Revoked access changes the snapshot used by delivery revalidation");
        try{ChunkInvestigation.create(report,"minecraft:overworld",0,0,false,e->false,"Block",128);throw new AssertionError("Stale offset must fail closed");}catch(IllegalArgumentException expected){}
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY,net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        try{
            var request=new ChunkBatchRequest(new InvestigationRequest(7,123,false,false,false,"minecraft:overworld",-1,2),"Block",128);
            ChunkBatchRequest.CODEC.encode(buffer,request);require(request.equals(ChunkBatchRequest.CODEC.decode(buffer))&&buffer.readableBytes()==0,"Batch codec preserves category, offset, permissions and negative chunk positions");
        }finally{buffer.release();}
        try{new ChunkBatchRequest(new InvestigationRequest(1,1,false,false,false,"",0,0),"Block",CaptureBudget.MAX_ENTRIES+1);throw new AssertionError("Offset budget");}catch(IllegalArgumentException expected){}
    }
    private static void modSearch(){
        var captured=new CapturedMods(List.of("ae2","minecraft","ae2","server_only"),id->Map.of("ae2","Applied Energistics 2","minecraft","Minecraft","server_only","Server Machines").get(id));
        require(captured.matching("").size()==3,"Selector lists only distinct captured owners");
        require(captured.matching("  ENERGISTICS ").stream().map(CapturedMods.Mod::id).toList().equals(List.of("ae2")),"Mod search is case-insensitive by display name");
        require(captured.matching("ae2").getFirst().id().equals("ae2")&&captured.matching("server_only").getFirst().id().equals("server_only"),"IDs remain directly searchable, including server-only mods");
        require(captured.matching("not_installed_or_captured").isEmpty(),"Unrelated installed mods are not invented as captured results");
    }
    private static void methodDetails(){
        require(MethodPresentation.label("test.Machine","lambda$tick$0").equals("Machine#tick (lambda)"),"Named lambda uses retained enclosing-method metadata");
        require(MethodPresentation.label("test.Machine$$Lambda.0x123abc","apply").equals("Machine lambda (unresolved)"),"Hidden synthetic pointer is not presented as a meaningful method");
        require(MethodPresentation.label("test.0x123abc","apply").equals("Unresolved generated method"),"Unresolvable generated identity is labelled honestly");
        var tree=new CpuInvestigation.Builder("Server thread (#123)");
        tree.add(List.of(new CpuInvestigation.Frame("fixture","test.Machine","tick","()V"),new CpuInvestigation.Frame("minecraft","test.Server","run","()V")));
        var cpu=tree.finish();var leaf=cpu.methods().stream().filter(m->m.name().equals("tick")).findFirst().orElseThrow();
        var details=MethodPresentation.details(cpu,leaf);
        require(details.getFirst().contains("Direct samples: 1 | In stack: 1")&&details.stream().anyMatch(s->s.contains("Server#run")),"Compact details retain actual direct/inclusive counts and caller relationships");
        require(details.stream().noneMatch(s->s.contains("()V")||s.contains("#123"))&&MethodPresentation.hint(cpu,leaf).contains("Server thread (#123)"),"Raw thread/signature metadata is preserved in help instead of dominating expansion");
        var encoded=Profiler.JSON.toJson(cpu);require(encoded.contains("()V")&&encoded.contains("#123"),"Copy/export data preserves original diagnostic metadata");
    }
}
