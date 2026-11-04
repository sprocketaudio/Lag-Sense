package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.util.*;
import java.util.function.Predicate;

/** Per-capture chunk branches; batches come from retained server targets, never global display rows. */
final class ChunkBranches {
    private record Key(String dimension,int x,int z) {}
    private static final class Category {
        final List<ChunkInvestigation.Hotspot> targets=new ArrayList<>();
        boolean open, loading;
        String error="";
        Runnable afterLoad;
        int cachedSize=-1;
        boolean cachedCount;
        List<ProfileResults.Target> sorted=List.of();
        List<ProfileResults.Group> groups;
        List<ProfileResults.ModGroup> mods;
        boolean modTypes;
        final Set<String> expandedMods=new HashSet<>();
        final Set<String> expandedTypes=new HashSet<>();
        void prepare(boolean byCount,boolean byType,boolean byMod){
            if(cachedSize!=targets.size()||cachedCount!=byCount){
                cachedSize=targets.size();cachedCount=byCount;groups=null;mods=null;
                Comparator<ProfileResults.Target> cost=Comparator.comparingLong((ProfileResults.Target t)->t.entry().nanos()).reversed();
                sorted=targets.stream().map(h->new ProfileResults.Target(h.index(),h.entry())).sorted((byCount?Comparator.comparingLong((ProfileResults.Target t)->t.entry().calls()).reversed().thenComparing(cost):cost).thenComparing(t->t.entry().type()).thenComparingInt(ProfileResults.Target::index)).toList();
            }
            if(byType&&!byMod&&groups==null)groups=ProfileResults.groups(sorted,byCount);
            if(byMod&&(mods==null||modTypes!=byType)){mods=ProfileResults.mods(sorted,byCount,byType);modTypes=byType;}
        }
    }
    private static final class Chunk {
        ChunkInvestigation data;
        boolean open, loading;
        String error="", summary="";
        Runnable afterLoad;
        final Map<String,Category> categories=new HashMap<>();
    }
    private record More(Key key,String category) {}
    private record TypeKey(More category,String registry) {}
    private record ModKey(More category,String owner) {}
    private final LagScreen screen;
    private final Report report;
    private final boolean archived;
    private final Map<Key,Chunk> chunks=new LinkedHashMap<>();
    private boolean byType,byCount,byMod;
    ChunkBranches(LagScreen screen,Report report,boolean archived){this.screen=screen;this.report=report;this.archived=archived;}
    void mode(boolean byType,boolean byCount){mode(byType,byCount,false);}
    void mode(boolean byType,boolean byCount,boolean byMod){this.byType=byType;this.byCount=byCount;this.byMod=byMod;}
    void collapseAll(boolean keepChunksOpen){for(var chunk:chunks.values()){if(!keepChunksOpen)chunk.open=false;for(var category:chunk.categories.values()){category.open=false;category.expandedMods.clear();category.expandedTypes.clear();}}}
    boolean hasExpanded(boolean includeChunks){return chunks.values().stream().anyMatch(c->c.open&&(includeChunks||c.categories.values().stream().anyMatch(category->category.open)));}
    void seed(ChunkInvestigation data){var state=state(new Key(data.dimension(),data.x(),data.z()));state.open=true;received(state,data);}
    private Key key(Report.Chunk chunk){return new Key(chunk.dimension(),chunk.x(),chunk.z());}
    private Chunk state(Key key){return chunks.computeIfAbsent(key,k->new Chunk());}
    boolean open(Report.Chunk chunk){return state(key(chunk)).open;}
    void toggle(Report.Chunk chunk){var key=key(chunk);var state=state(key);state.open=!state.open;if(state.open&&state.data==null)load(key,state);screen.rebuildResults();}
    void start(String dimension,int x,int z){var key=new Key(dimension,x,z);var state=state(key);state.open=true;if(state.data==null)load(key,state);}
    private void changed(){if(net.minecraft.client.Minecraft.getInstance().screen==screen)screen.rebuildResults();}
    private void load(Key key,Chunk state){
        if(state.loading)return;state.loading=true;state.error="";
        var scoped=ClientAccess.markers(report);if(!report.admin()&&scoped!=null){received(state,scoped.chunk(key.dimension(),key.x(),key.z()));return;}
        if(LagSenseClient.completeReport&&LagSenseClient.report!=null&&report.id()==LagSenseClient.report.id()&&LagSenseClient.markerIndex!=null){received(state,LagSenseClient.markerIndex.chunk(key.dimension(),key.x(),key.z()));return;}
        Investigations.chunkInline(screen,report,archived,key.dimension(),key.x(),key.z(),data->{received(state,data);changed();},error->{state.loading=false;state.error=error;if(state.afterLoad!=null){state.afterLoad=null;copyFailed(state,error);}changed();});
    }
    private void received(Chunk state,ChunkInvestigation data){
        state.loading=false;state.data=data;state.error="";
            for(var summary:data.categories()){
                var category=state.categories.computeIfAbsent(summary.kind(),kind->new Category());
                category.targets.addAll(data.hotspots().stream().filter(h->h.entry().kind().equals(summary.kind())).toList());
            }
            state.summary=data.targets()+" measured targets | "+UiResults.cost(data.nanos()/1000.0/Math.max(1,data.ticks()));
            if(state.afterLoad!=null){var next=state.afterLoad;state.afterLoad=null;next.run();}
    }
    CopyTree.Node copy(Report.Chunk chunk){return new ResultText(ModNames::name).chunk(snapshot(chunk.dimension(),chunk.x(),chunk.z()),byCount,byType,byMod);}
    private Key location(Object selected){
        if(selected instanceof More m)return m.key();if(selected instanceof ModKey m)return m.category().key();if(selected instanceof TypeKey t)return t.category().key();if(selected instanceof List<?> list&&!list.isEmpty())return location(list.getFirst());return null;
    }
    private String categoryOf(Object selected){if(selected instanceof More m)return m.category();if(selected instanceof ModKey m)return m.category().category();if(selected instanceof TypeKey t)return t.category().category();return null;}
    void copySelected(Report.Chunk root,String warning){
        var selection=screen.selection;if(selection.key()==null)return;Key key=root==null?location(selection.key()):key(root);
        if(key==null||selection.key() instanceof List<?> list&&!list.isEmpty()&&list.getLast() instanceof ProfileResults.Target){selection.copy(String.join("\n",java.util.stream.Stream.of(warning,ReportCompleteness.reason(report)).filter(v->!v.isBlank()).distinct().toList()));return;}
        String kind=root==null?categoryOf(selection.key()):null;
        long token=selection.generation();selection.waiting(true);
        complete(key,kind,token,()->{if(selection.current(token)){selection.waiting(false);selection.copy(String.join("\n",java.util.stream.Stream.of(warning,ReportCompleteness.reason(report)).filter(v->!v.isBlank()).distinct().toList()));}});
    }
    private void complete(Key key,String kind,long token,Runnable ready){
        if(!screen.selection.current(token))return;var chunk=state(key);
        if(chunk.data==null){
            if(chunk.loading){chunk.afterLoad=()->complete(key,kind,token,ready);return;}
            chunk.loading=true;Investigations.chunkInline(screen,report,archived,key.dimension(),key.x(),key.z(),data->{received(chunk,data);complete(key,kind,token,ready);},error->copyFailed(chunk,error));return;
        }
        for(var summary:chunk.data.categories()){
            if(kind!=null&&!kind.equals(summary.kind()))continue;
            var category=chunk.categories.get(summary.kind());if(category.targets.size()>=summary.targets())continue;
            if(category.loading){category.afterLoad=()->complete(key,kind,token,ready);return;}
            if(!category.error.isBlank()){copyFailed(chunk,category.error);return;}
            loadCategory(key,chunk,summary.kind(),category,()->complete(key,kind,token,ready));return;
        }
        ready.run();
    }
    private void copyFailed(Chunk chunk,String error){chunk.loading=false;chunk.error=error;screen.selection.waiting(false);var mc=net.minecraft.client.Minecraft.getInstance();if(mc.player!=null)mc.getChatListener().handleSystemMessage(net.minecraft.network.chat.Component.literal("Could not copy contributors: "+error),false);}
    private CopyTree.Node categoryCopy(Key key,String kind){return new ResultText(ModNames::name).category(snapshot(key.dimension(),key.x(),key.z()),kind,byCount,byType,byMod);}
    private CopyTree.Node modCopy(Key key,String kind,String owner){var data=snapshot(key.dimension(),key.x(),key.z());boolean count=byCount,types=byType;return CopyTree.deferred(owner,()->{var targets=ResultText.chunkTargets(data,kind,count);var group=ProfileResults.mods(targets,count,types).stream().filter(m->m.owner().equals(owner)).findFirst().orElseThrow();return new ResultText(ModNames::name).mod(group,data.ticks(),types);});}
    private CopyTree.Node typeCopy(Key key,String kind,String registry){var data=snapshot(key.dimension(),key.x(),key.z());boolean count=byCount;return CopyTree.deferred(registry,()->{var group=ProfileResults.groups(ResultText.chunkTargets(data,kind,count),count).stream().filter(g->g.type().registry().equals(registry)).findFirst().orElseThrow();return new ResultText(ModNames::name).type(group,data.ticks());});}
    void append(UiResults out,Report.Chunk chunk,int indent){append(out,key(chunk),indent);}
    void append(UiResults out,String dimension,int x,int z,int indent){append(out,new Key(dimension,x,z),indent);}
    private void append(UiResults out,Key key,int indent){
        var state=state(key);if(!state.open)return;
        if(state.data==null){
            out.row(new More(key,""),state.loading?"Loading contributors…":state.error.isBlank()?"Contributors unavailable":state.error,"","",UiTheme.TEXT,indent,false,"Retry loading this chunk.",state.loading?null:()->{load(key,state);changed();});return;
        }
        Comparator<ChunkInvestigation.Category> costOrder=Comparator.comparingLong(ChunkInvestigation.Category::nanos).reversed();
        var summaries=state.data.categories().stream().sorted((byCount?Comparator.comparingLong(ChunkInvestigation.Category::calls).reversed().thenComparing(costOrder):costOrder).thenComparing(ChunkInvestigation.Category::kind)).toList();
        for(var summary:summaries){
            var category=state.categories.computeIfAbsent(summary.kind(),k->new Category());var id=new More(key,summary.kind());
            double cost=summary.nanos()/1000.0/Math.max(1,state.data.ticks());
            out.copy(()->categoryCopy(key,summary.kind()));
            out.row(id,UiResults.branch(categoryName(summary.kind()),category.open),summary.targets()+" measured targets",UiResults.cost(cost),TimingSeverity.color(cost),indent,category.open,
                summary.kind()+" | "+key.dimension()+"\nChunk "+key.x()+", "+key.z()+"\n"+summary.targets()+" measured targets | "+summary.calls()+" updates\n"+UiResults.cost(cost),()->{category.open=!category.open;changed();});
            if(!category.open)continue;
            category.prepare(byCount,byType,byMod);
            if(byMod)for(var mod:category.mods){
                var modKey=new ModKey(id,mod.owner());boolean open=category.expandedMods.contains(mod.owner());
                out.lazy(modKey,row->{double micros=mod.micros(state.data.ticks());row.copy(()->modCopy(key,summary.kind(),mod.owner()));row.row(modKey,UiResults.branch(ModNames.name(mod.owner()),open),"",UiResults.cost(micros),TimingSeverity.color(micros),indent+12,open,mod.owner()+"\n"+summary.kind()+" | "+key.dimension()+"\nChunk "+key.x()+", "+key.z()+"\n"+mod.instances()+" measured targets | "+mod.updates()+" updates | "+UiResults.cost(micros),()->{if(!category.expandedMods.add(mod.owner()))category.expandedMods.remove(mod.owner());changed();});});
                if(!open)continue;
                if(byType)for(var group:mod.types())type(out,key,state,category,id,group,indent+24);
                else for(var target:mod.targets())target(out,List.of(modKey,target),target,state.data.ticks(),indent+24);
            }else if(byType)for(var group:category.groups)type(out,key,state,category,id,group,indent+12);
            else for(var target:category.sorted)target(out,List.of(id,target),target,state.data.ticks(),indent+12);
            if(category.targets.size()<summary.targets()){
                String label=category.error.isBlank()?"Loading contributors…":category.error;
                out.row(List.of(id,"more"),label,category.targets.size()+" of "+summary.targets()+" loaded","",UiTheme.TEXT,indent+12,false,"Load more results for this chunk.",
                    category.loading?null:()->{loadCategory(key,state,summary.kind(),category);changed();});
            }
        }
        if(state.data.limited())out.details(List.of(key,"limited"),List.of(ReportCompleteness.reason(report)),indent);
        if(state.data.targets()==0)out.details(List.of(key,"empty"),List.of("No matching targets."),indent);
    }
    private void type(UiResults out,Key key,Chunk state,Category category,More id,ProfileResults.Group group,int indent){
        var typeKey=new TypeKey(id,group.type().registry());boolean open=category.expandedTypes.contains(group.type().registry());
        out.lazy(typeKey,row->{double micros=group.micros(state.data.ticks());row.copy(()->typeCopy(key,id.category(),group.type().registry()));row.row(typeKey,UiResults.branch(group.type().registry()+" ("+group.instances()+")",open),"",UiResults.cost(micros),TimingSeverity.color(micros),indent,open,group.type().registry()+"\n"+id.category()+" | "+key.dimension()+"\nChunk "+key.x()+", "+key.z()+"\n"+group.instances()+" measured instances | "+group.updates()+" updates | "+UiResults.cost(micros),()->{if(!category.expandedTypes.add(group.type().registry()))category.expandedTypes.remove(group.type().registry());changed();});});
        if(open)for(var target:group.targets())target(out,List.of(typeKey,target),target,state.data.ticks(),indent+12);
    }
    private void target(UiResults out,Object rowKey,ProfileResults.Target measured,int ticks,int indent){
        out.lazy(rowKey,row->{var target=measured.entry();double micros=target.micros(ticks);
            row.copy(()->new ResultText(ModNames::name).target(measured,ticks));
            row.row(rowKey,TargetDetails.name(target),"",UiResults.cost(micros),LagSenseClient.color(micros),indent,false,TargetDetails.hint(target,ticks),null,actions(target,measured.index()));});
    }
    UiResults.Action[] actions(Report.Entry target,int index){
        if(archived){var destination=TargetActions.recorded(report,target,index,false);return destination==null?new UiResults.Action[0]:TargetActions.actions(screen,destination);}
        var mark=new UiResults.Action("Mark",()->{LagSenseClient.mark(report,target);net.minecraft.client.Minecraft.getInstance().setScreen(null);});
        if(report.admin()&&!archived&&index>=0)return new UiResults.Action[]{mark,TargetActions.actions(screen,TargetActions.recorded(report,target,index,false))[1]};
        return new UiResults.Action[]{mark};
    }
    void tick(Predicate<Object> visible){
        for(var pair:chunks.entrySet()){
            var state=pair.getValue();if(!state.open||state.data==null)continue;
            for(var summary:state.data.categories()){
                var category=state.categories.get(summary.kind());
                if(category==null||!category.open||category.loading||!category.error.isBlank()||category.targets.size()>=summary.targets())continue;
                if(visible.test(List.of(new More(pair.getKey(),summary.kind()),"more"))){loadCategory(pair.getKey(),state,summary.kind(),category);return;}
            }
        }
    }
    private void loadCategory(Key key,Chunk state,String kind,Category category){loadCategory(key,state,kind,category,null);}
    private void loadCategory(Key key,Chunk state,String kind,Category category,Runnable next){
        if(category.loading)return;category.loading=true;category.error="";
        Investigations.chunkBatch(screen,report,archived,key.dimension(),key.x(),key.z(),kind,category.targets.size(),true,batch->{
            category.loading=false;
            if(batch.ticks()!=state.data.ticks()||!batch.dimension().equals(key.dimension())||batch.x()!=key.x()||batch.z()!=key.z()||!batch.category().equals(kind)||batch.offset()!=category.targets.size()||batch.nanos()!=state.data.nanos()||batch.targets()!=state.data.targets()||!batch.categories().equals(state.data.categories())) {
                category.error="Contributors changed. Reopen this profile.";
            }else if(batch.hotspots().isEmpty())category.error="No more results for this chunk.";
            else category.targets.addAll(batch.hotspots());
            if(next!=null){if(category.error.isBlank())next.run();else copyFailed(state,category.error);}
            if(category.afterLoad!=null){var pending=category.afterLoad;category.afterLoad=null;pending.run();}
            changed();
        },error->{category.loading=false;category.error=error;if(next!=null)copyFailed(state,error);if(category.afterLoad!=null){category.afterLoad=null;copyFailed(state,error);}changed();});
    }
    private static String categoryName(String kind){return switch(kind){case "Entity"->"Entities";case "Block"->"Ticking blocks";case "Random block"->"Random block updates";case "Random fluid"->"Random fluid updates";case "Scheduled block"->"Scheduled block updates";case "Scheduled fluid"->"Scheduled fluid updates";default->kind;};}
    String summary(String dimension,int x,int z){return state(new Key(dimension,x,z)).summary;}
    ChunkInvestigation snapshot(String dimension,int x,int z){
        var chunk=state(new Key(dimension,x,z));if(chunk.data==null)return null;
        var loaded=new ArrayList<ChunkInvestigation.Hotspot>();
        for(var summary:chunk.data.categories()){var category=chunk.categories.get(summary.kind());if(category!=null)loaded.addAll(category.targets);}
        if(loaded.isEmpty())loaded.addAll(chunk.data.hotspots());
        var data=chunk.data;return new ChunkInvestigation(data.dimension(),data.x(),data.z(),data.ticks(),data.nanos(),data.targets(),data.categories(),List.copyOf(loaded),"",0,data.limited());
    }
}
