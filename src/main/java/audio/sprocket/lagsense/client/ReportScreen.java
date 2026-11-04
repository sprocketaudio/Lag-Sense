package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ReportScreen extends LagScreen {
    private final Report report;
    private final boolean archived;
    private final net.minecraft.client.gui.screens.Screen parent;
    private int top;
    private String emptyTab="Targets";
    private boolean byCount, byType,byMod;
    private String filter = "All", dimension = "All", note = "", query="",previousCategory="All";
    private record ViewState(int offset) {}
    private final Map<String,ViewState> views = new HashMap<>();
    private record VisibleKey(String dimension,boolean byCount,boolean byType,boolean byMod,String query) {}
    private record VisibleRows(VisibleKey key, List<Row> rows, List<ProfileResults.Group> groups,List<ProfileResults.ModGroup> mods) {}
    private final Map<String,VisibleRows> results = new HashMap<>();
    private final Set<ProfileResults.Type> expandedTypes=new HashSet<>();
    private final Set<String> expandedMods=new HashSet<>();
    private final Map<ProfileResults.Chunk,Integer> chunkCounts;
    private final Map<ProfileResults.Chunk,Long> chunkUpdates;
    private record Row(Report.Entry target, int index, boolean chunk, double micros, long count) {
        String title(){return chunk?"Chunk "+(target.x()>>4)+", "+(target.z()>>4)+" · "+count+" targets":target.type();}
        String detail(){return chunk?target.dimension()+" | "+count+" measured targets":target.kind()+" | "+target.dimension()+" | "+target.x()+" "+target.y()+" "+target.z();}
    }
    private List<Row> visible = List.of();
    private List<ProfileResults.Group> visibleGroups=List.of();
    private List<ProfileResults.ModGroup> visibleMods=List.of();
    private final ChunkBranches contributors;
    public ReportScreen() { this(null); }
    public ReportScreen(net.minecraft.client.gui.screens.Screen parent) { this(parent,LagSenseClient.report,false); }
    public ReportScreen(net.minecraft.client.gui.screens.Screen parent,Report report,boolean archived) {
        super("LagSense");this.parent=parent instanceof ReportScreen?null:parent;this.report=report;this.archived=archived;
        chunkCounts=report==null?Map.of():ProfileResults.chunkCounts(report.entries());
        chunkUpdates=report==null?Map.of():report.entries().stream().collect(java.util.stream.Collectors.groupingBy(e->new ProfileResults.Chunk(e.dimension(),e.x()>>4,e.z()>>4),java.util.stream.Collectors.summingLong(Report.Entry::calls)));
        contributors=new ChunkBranches(this,report,archived);
    }
    @Override public void onClose() { if(profileNavigation!=null)profileNavigation.close();else minecraft.setScreen(parent); }
    void emptyView(String tab){emptyTab=tab;}
    String currentCategory(){return filter;}
    String targetCategory(){return previousCategory;}
    void selectView(String value) {
        if (filter.equals(value)) return;
        views.put(viewKey(),new ViewState(listOffset()));
        if(!filter.equals("Chunks"))previousCategory=filter;filter=value;
        var state=views.getOrDefault(viewKey(),new ViewState(0));
        restoreListOffset(state.offset());
    }
    private String viewKey(){return filter+":"+byType+":"+byMod;}
    private void grouping(){views.put(viewKey(),new ViewState(listOffset()));byType=!byType;restoreListOffset(views.getOrDefault(viewKey(),new ViewState(0)).offset());rebuildWidgets();}
    private void modGrouping(){views.put(viewKey(),new ViewState(listOffset()));byMod=!byMod;restoreListOffset(views.getOrDefault(viewKey(),new ViewState(0)).offset());rebuildWidgets();}
    private boolean allow(Report.Entry e) {
        if (!dimension.equals("All") && !dimension.equals(e.dimension())) return false;
        if(!query.isBlank()&&!searchMatches(e))return false;
        return true;
    }
    private boolean allowChunk(Report.Chunk c){return dimension.equals("All")||dimension.equals(c.dimension());}
    private boolean searchMatches(Report.Entry e){return (e.type()+" "+e.kind()+" "+e.dimension()+" "+e.x()+" "+e.y()+" "+e.z()+" "+ModNames.name(ProfileResults.owner(e))).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));}
    private void cycleDimension() {
        var choices = new ArrayList<String>(List.of("All"));
        report.entries().stream().map(Report.Entry::dimension).distinct().sorted().forEach(choices::add);
        if (report.chunks() != null) report.chunks().stream().map(Report.Chunk::dimension).distinct().sorted().filter(d -> !choices.contains(d)).forEach(choices::add);
        dimension = choices.get((choices.indexOf(dimension) + 1) % choices.size()); resetResults(); rebuildWidgets();
    }
    @Override protected void init() {
        if(profileNavigation==null)profileNavigation=new ProfileNavigation(this,parent,report,archived);
        if(report==null){
            int navBottom=profileNavigation.mount(this,emptyTab);top=navBottom+28;
            int y=emptyMessageY();int buttonWidth=Math.max(112,UiFonts.labelWidth("Start Profile")+20);
            if(ClientAccess.admin())primary("Start Profile",(width-buttonWidth)/2,y+26,buttonWidth,ShortcutBindings::start);
            action("Close",width-margin()-54,footer(),54,this::onClose);return;
        }
        int navBottom=profileNavigation.mount(this,filter);
        resultsMounted=false;
        refreshData();
        top=resultControls(navBottom).bottom()+8;
        refreshRows();
        copyButton(this::copySelected);
        action("Export", margin() + 48, footer(), 48, () -> minecraft.setScreen(new ExportScreen(this, report,archived)));
        action("Unmark", margin()+100, footer(),50,()->{LagSenseClient.waypoint=null;if(!report.admin())ClientAccess.request("unmark");});
        if(report.admin())action("Return",margin()+154,footer(),48,()->{if(TargetActions.permitted(false)&&minecraft.player!=null)minecraft.player.connection.sendCommand("lagsense return");}).enabledWhen(()->TargetActions.permitted(false)).visibleWhen(()->TargetActions.permitted(false));
        action("Close", width - margin() - 54, footer(), 54, this::onClose);
        note=ReportCompleteness.label(report);
        if(note.isEmpty())note = report.admin() && report.diagnostics() != null ? report.diagnostics().notes().stream().filter(n -> n.startsWith("Backup")).map(StatusText::note).findFirst().orElse("") : "";
    }
    int emptyMessageY(){return top+Math.max(8,(footer()-24-top-46)/2);}
    private void replaceOrMount(UiResults out){if(resultsMounted)replaceResults(out.entries);else{results(top,out.entries,true);resultsMounted=true;}}
    private boolean resultsMounted;
    private VisibleKey refreshData(){
        var key=new VisibleKey(dimension,byCount,byType,byMod,query);
        var cached=results.get(filter);
        if (cached==null || !cached.key().equals(key)) {
        var list = new ArrayList<Row>();
        var groups=List.<ProfileResults.Group>of();
        var mods=List.<ProfileResults.ModGroup>of();
        if((byType||byMod)&&!filter.equals("Chunks")){
            var targets=ProfileResults.targets(report,new ProfileResults.Filter(filter,dimension,false,"",0,0,0),byCount).stream().filter(t->query.isBlank()||searchMatches(t.entry())).toList();
            if(byMod)mods=ProfileResults.mods(targets,byCount,byType);else groups=ProfileResults.groups(targets,byCount);
        }else if (filter.equals("Chunks")) {
            var chunks = report.chunks() == null ? List.<Report.Chunk>of() : report.chunks();
            var matchingChunks=query.isBlank()?java.util.Set.<ProfileResults.Chunk>of():report.entries().stream().filter(this::searchMatches).map(e->new ProfileResults.Chunk(e.dimension(),e.x()>>4,e.z()>>4)).collect(java.util.stream.Collectors.toSet());
            for (int i = 0; i < chunks.size(); i++) { var c = chunks.get(i); boolean matches=query.isBlank()||("Chunk "+c.x()+", "+c.z()+" "+c.dimension()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))||matchingChunks.contains(new ProfileResults.Chunk(c.dimension(),c.x(),c.z()));if(matches&&allowChunk(c)) list.add(new Row(c.hotspot(), i, true, c.micros(report.ticks()), chunkCounts.getOrDefault(new ProfileResults.Chunk(c.dimension(),c.x(),c.z()),c.targets()))); }
        } else for (int i = 0; i < report.entries().size(); i++) {
            var e = report.entries().get(i);
            if ((filter.equals("All") || e.kind().equals(filter) || filter.equals("Ticks") && (e.kind().startsWith("Scheduled") || e.kind().startsWith("Random"))) && allow(e))
                list.add(new Row(e, i, false, e.micros(report.ticks()), e.calls()));
        }
        list.sort(byCount ? Comparator.<Row>comparingLong(r->r.chunk()?chunkUpdates.getOrDefault(new ProfileResults.Chunk(r.target().dimension(),r.target().x()>>4,r.target().z()>>4),0L):r.count()).reversed().thenComparing(Comparator.comparingDouble(Row::micros).reversed()) : Comparator.comparingDouble(Row::micros).reversed());
                cached=new VisibleRows(key,List.copyOf(list),groups,mods);results.put(filter,cached);
        }
        visible=cached.rows();visibleGroups=cached.groups();visibleMods=cached.mods();
        return key;
    }
    private void refreshRows(){
        var key=refreshData();
        boolean travel = report.admin(); int reserved = travel ? 76 : 48;


        selection.begin(List.of(report.id(),filter,key));
        var out=new UiResults(this,top,reserved,true).diagnosticHints().selectable(selection);
        appendResults(out,travel);
        replaceOrMount(out);collapseAvailable(hasExpanded());
    }
    UiMetrics.Flow resultControls(int navBottom){
        var row=new ProfileControls(this,navBottom+4,this::collapseAll,hasExpanded());
        row.search(query,value->{query=value;resetResults();refreshRows();});
        var dimensions=new ArrayList<UiDropdown.Choice>();dimensions.add(ProfileControls.choice("All","All"));
        java.util.stream.Stream.concat(report==null?java.util.stream.Stream.empty():report.entries().stream().map(Report.Entry::dimension),report==null||report.chunks()==null?java.util.stream.Stream.empty():report.chunks().stream().map(Report.Chunk::dimension)).distinct().sorted().forEach(d->dimensions.add(ProfileControls.choice(d,d.substring(d.indexOf(':')+1))));
        row.dropdown("Dimension",88,dimensions,dimension,value->{dimension=value;resetResults();rebuildWidgets();});
        if(!filter.equals("Chunks"))row.dropdown("Category",84,List.of(ProfileControls.choice("All","All"),ProfileControls.choice("Entity","Entities"),ProfileControls.choice("Block","Blocks"),ProfileControls.choice("Ticks","Ticks")),filter,value->{selectView(value);rebuildWidgets();});
        row.dropdown("Group",100,List.of(ProfileControls.choice("instance","By instance"),ProfileControls.choice("type","By type"),ProfileControls.choice("mod-instance","By mod"),ProfileControls.choice("mod-type","By mod and type")),(byMod?"mod-":"")+(byType?"type":"instance"),value->{byMod=value.startsWith("mod-");byType=value.endsWith("type");resetResults();rebuildWidgets();});
        row.dropdown("Sort",84,List.of(ProfileControls.choice("time","Most time"),ProfileControls.choice("count","Most updates")),byCount?"count":"time",value->{inline.clear();byCount=value.equals("count");resetResults();rebuildWidgets();});
        int bottom=row.finish();return new UiMetrics.Flow(margin(),width-margin(),bottom-20);
    }
    private void appendResults(UiResults out,boolean travel){
        contributors.mode(byType,byCount,byMod);
        if(byMod&&!filter.equals("Chunks"))for(var mod:visibleMods){
            out.lazy("mod:"+mod.owner(),row->appendMod(row,mod));
            if(!expandedMods.contains(mod.owner()))continue;
            if(byType)for(var group:mod.types()){
                out.lazy(group.type(),row->appendGroup(row,group,12));
                if(expandedTypes.contains(group.type()))for(var target:group.targets())appendTarget(out,target,travel,List.of(group.type(),target.index()),24);
            }else for(var target:mod.targets())appendTarget(out,target,travel,List.of("mod:"+mod.owner(),target.index()),12);
        }else if(byType&&!filter.equals("Chunks"))for(var group:visibleGroups){
            out.lazy(group.type(),row->appendGroup(row,group,0));
            if(expandedTypes.contains(group.type()))for(var target:group.targets()){
                var item=new Row(target.entry(),target.index(),false,target.entry().micros(report.ticks()),target.entry().calls());
                var id=List.of(group.type(),target.index());out.lazy(id,row->appendRow(row,item,travel,id,12));
            }
        }else for(var item:visible){
            var id=filter+":"+item.index();out.lazy(id,row->appendRow(row,item,travel,id,0));
            if(item.chunk())contributors.append(out,report.chunks().get(item.index()),12);
        }
    }
    private void appendTarget(UiResults out,ProfileResults.Target target,boolean travel,Object id,int indent){var item=new Row(target.entry(),target.index(),false,target.entry().micros(report.ticks()),target.entry().calls());out.lazy(id,row->appendRow(row,item,travel,id,indent));}
    private void appendMod(UiResults out,ProfileResults.ModGroup mod){
        boolean open=expandedMods.contains(mod.owner());double cost=mod.micros(report.ticks());out.copy(()->new ResultText(ModNames::name).mod(mod,report.ticks(),byType));out.row("mod:"+mod.owner(),UiResults.branch(ModNames.name(mod.owner()),open),"",UiResults.cost(cost),TimingSeverity.color(cost),0,open,mod.owner()+"\n"+mod.instances()+" measured targets | "+mod.updates()+" updates | "+UiResults.cost(cost),()->{if(!expandedMods.add(mod.owner()))expandedMods.remove(mod.owner());rebuildWidgets();});
    }
    private boolean hasExpanded(){if(filter.equals("Chunks"))return visible.stream().filter(Row::chunk).anyMatch(r->contributors.open(report.chunks().get(r.index())));if(byMod)return visibleMods.stream().anyMatch(m->expandedMods.contains(m.owner()));return byType&&visibleGroups.stream().anyMatch(g->expandedTypes.contains(g.type()));}
    private void collapseAll(){if(filter.equals("Chunks"))contributors.collapseAll(false);else{expandedTypes.clear();expandedMods.clear();}rebuildWidgets();}
    private void appendGroup(UiResults out,ProfileResults.Group group,int indent){
        var key=group.type();boolean open=expandedTypes.contains(key);double cost=group.micros(report.ticks());
        String category=filter.equals("All")||filter.equals("Ticks")||filter.equals("Chunks")?" · "+key.kind():"";
        String hint=key.registry()+"\n"+key.kind()+" | "+group.instances()+" measured instances\n"+group.updates()+" updates | "+UiResults.cost(cost);
        out.copy(()->new ResultText(ModNames::name).type(group,report.ticks()));
        out.row(key,UiResults.branch(key.registry()+" ("+group.instances()+")"+category,open),"",UiResults.cost(cost),TimingSeverity.color(cost),indent,open,hint,()->{if(!expandedTypes.add(key))expandedTypes.remove(key);rebuildWidgets();});
    }
    private void appendRow(UiResults out,Row item,boolean travel,Object id,int indent){
            var e=item.target();
            var actions=new ArrayList<UiResults.Action>();
            actions.add(new UiResults.Action("Mark",()->{LagSenseClient.mark(report,e);minecraft.setScreen(null);}));
            if(travel){var destination=TargetActions.recorded(report,item.chunk()?TargetActions.chunk(report.chunks().get(item.index())):e,item.index(),item.chunk());if(destination!=null)actions.add(TargetActions.actions(this,destination)[1]);}
            Report.Chunk chunk=item.chunk()?report.chunks().get(item.index()):null;
            if(archived){
                actions.clear();var destination=TargetActions.recorded(report,item.chunk()&&report.admin()?TargetActions.chunk(chunk):e,item.index(),item.chunk());
                if(destination!=null)actions.addAll(List.of(TargetActions.actions(this,destination)));
            }
            out.copy(()->item.chunk()?contributors.copy(chunk):new ResultText(ModNames::name).target(new ProfileResults.Target(item.index(),e),report.ticks()));
            out.row(id,item.chunk()?UiResults.branch(item.title(),contributors.open(chunk)):item.title(),
                item.detail()+(byCount&&!item.chunk()?" | "+item.count()+" updates":""),UiResults.cost(item.micros()),TimingSeverity.color(item.micros()),indent,
                item.chunk()&&contributors.open(chunk),item.chunk()?"Chunk "+chunk.x()+", "+chunk.z()+"\n"+chunk.dimension()+"\n"+item.count()+" measured targets | "+chunkUpdates.getOrDefault(new ProfileResults.Chunk(chunk.dimension(),chunk.x(),chunk.z()),0L)+" updates\n"+UiResults.cost(item.micros()):TargetDetails.hint(e,report.ticks()),
                item.chunk()?()->contributors.toggle(chunk):null,actions.toArray(UiResults.Action[]::new));
    }
    private void copySelected(){
        Report.Chunk root=null;if(selection.key() instanceof String key&&key.startsWith("Chunks:")){int i=Integer.parseInt(key.substring(7));root=report.chunks().get(i);}
        contributors.copySelected(root,ReportCompleteness.reason(report));
    }
    @Override public void tick() {
        if(report==null&&LagSenseClient.report!=null){minecraft.setScreen(new ReportScreen(null,LagSenseClient.report,false));return;}
        if(report!=null)contributors.tick(this::resultVisible);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        if(profileNavigation!=null)profileNavigation.render(this,g,mx,my);else canvas(g,"PROFILE");
        if (report == null) {
            String message=ClientAccess.admin()?"No profile captured yet":"No permitted results yet";int w=UiFonts.width(font,message);
            text(g,message,(width-w)/2,emptyMessageY(),w,UiTheme.SECONDARY);
        }
        else {
            if (visible.isEmpty()&&visibleGroups.isEmpty()&&visibleMods.isEmpty()) text(g, "No matching results.", margin(), top + 8, contentWidth(), UiTheme.SECONDARY);
            text(g, note, margin(), height - 62, contentWidth(), note.startsWith("Backup")||report.truncated() ? UiTheme.WARNING : UiTheme.SECONDARY);
            UiHints.region(g, font, mx, my, margin(), height - 64, contentWidth(), 14, report.truncated()?ReportCompleteness.reason(report):note);
        }
        super.extractRenderState(g, mx, my, partial);
    }
}
