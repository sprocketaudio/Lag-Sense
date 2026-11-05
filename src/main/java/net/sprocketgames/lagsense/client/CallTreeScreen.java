package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;

public final class CallTreeScreen extends LagScreen {
    private final Screen parent;
    private final CpuInvestigation data;
    private final CallTreeIndex index;
    private final ResultText.Cpu copyData;
    private final Set<Integer> expanded=new HashSet<>();
    private final Set<String> inspected=new HashSet<>();
    private final Map<Integer,List<String>> details=new HashMap<>();
    private final CapturedMods mods;
    private final List<CpuInvestigation.Method> ranked;
    private String owner,query="";
    private boolean running,selectingMod,selectingThread;
    private String threadQuery="";
    private int top;
    private final Map<Boolean,Integer> offsets=new HashMap<>();
    private int selectionOffset;
    private EditBox search;
    private boolean rowsMounted;
    private String sort="samples";
    private List<CallTreeIndex.Row> branches=List.of();
    private List<CpuInvestigation.Method> methods=List.of();
    public CallTreeScreen(Screen parent,CpuInvestigation data,String owner){
        super("Call paths");this.parent=parent;this.data=data;this.owner=owner;index=new CallTreeIndex(data);copyData=new ResultText.Cpu(data);
        mods=new CapturedMods(data.methods().stream().map(CpuInvestigation.Method::owner).toList(),ModNames::name);
        ranked=data.methods().stream().filter(m->m.self()>0).sorted(Comparator.comparingLong(CpuInvestigation.Method::self).reversed().thenComparing(Comparator.comparingLong(CpuInvestigation.Method::inclusive).reversed())).toList();refresh();
    }
    CpuInvestigation data(){return data;}
    boolean belongsTo(TaskScreen task){return parent==task;}
    String ownerFilter(){return owner;}
    boolean runningMode(){return running;}
    void runningMode(boolean value){running=value;}
    private boolean matches(CpuInvestigation.Method m){return query.isBlank()||(m.type()+"#"+m.name()+" "+m.owner()+" "+ModNames.name(m.owner())).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));}
    private void refresh(){
        var rows=index.rows(expanded,owner);
        if(query.isBlank())branches=rows;else{
            boolean[] relevant=new boolean[data.nodes().size()];
            for(int i=relevant.length-1;i>0;i--){var n=data.nodes().get(i);var m=data.methods().get(n.method());relevant[i]|=(owner==null||m.owner().equals(owner))&&matches(m);if(relevant[i]&&n.parent()>=0)relevant[n.parent()]=true;}
            branches=rows.stream().filter(row->relevant[row.node()]).toList();
        }
        var stream=ranked.stream().filter(m->(owner==null||m.owner().equals(owner))&&matches(m));
        if(sort.equals("name"))stream=stream.sorted(Comparator.comparing(m->m.type()+"#"+m.name()));methods=stream.toList();
    }
    void inheritControls(CallTreeScreen previous){if(previous!=null){running=previous.running;query=previous.query;sort=previous.sort;}refresh();}
    private void mode(boolean value){if(value==running)return;offsets.put(running,listOffset());running=value;refresh();restoreListOffset(offsets.getOrDefault(value,0));rebuildWidgets();}
    private void choose(String value){owner=value;selectingMod=false;offsets.clear();resetResults();refresh();rebuildWidgets();}
    private boolean hasExpanded(){return !selectingMod&&(running?methods.stream().anyMatch(m->inspected.contains("method:"+m.id())):branches.stream().anyMatch(b->expanded.contains(b.node())||inspected.contains("node:"+b.node())));}
    private void collapseAll(){if(!running)expanded.clear();inspected.removeIf(k->k.startsWith(running?"method:":"node:"));refresh();rebuildWidgets();}
    @Override protected void init(){
        rowsMounted=false;refresh();
        int y=parent instanceof TaskScreen task?task.taskControls(this,true).bottom()-20:profileNavigation!=null?profileNavigation.mount(this,"Call Paths")+4:headerControlsTop();
        var controls=new ProfileControls(this,y,this::collapseAll,hasExpanded());
        search=controls.search(query,value->{query=value;resetResults();refresh();refreshRows();});
        if(parent instanceof TaskScreen task)controls.dropdown("Threads",ProfileControls.PATH_THREADS,task.pathThreads(),task.background()?Long.toString(task.selectedThread()):"0",value->task.chooseThread(Long.parseLong(value)));
        controls.dropdown("Group",ProfileControls.PATH_GROUP,List.of(ProfileControls.choice("hierarchy","Call Hierarchy"),ProfileControls.choice("running","Running Code")),running?"running":"hierarchy",value->mode(value.equals("running")));
        var choices=new ArrayList<UiDropdown.Choice>();choices.add(ProfileControls.choice("","All mods"));mods.matching("").forEach(m->choices.add(ProfileControls.choice(m.id(),m.name())));
        controls.dropdown("Mod",ProfileControls.PATH_MOD,choices,owner==null?"":owner,value->choose(value.isEmpty()?null:value));
        if(running)controls.dropdown("Sort",ProfileControls.PATH_SORT,List.of(ProfileControls.choice("samples","Most samples"),ProfileControls.choice("name","Name")),sort,value->{sort=value;resetResults();refresh();rebuildWidgets();});
        top=controls.finish()+8;refreshRows();
        copyButton(()->selection.copy(data.limited()?"Call paths incomplete":""));
        action("Export",margin()+48,footer(),48,()->minecraft.setScreen(new ExportScreen(this,data)));
        action(profileNavigation==null?"Back":"Close",width-margin()-54,footer(),54,this::onClose);
    }
    private void refreshRows(){
        selection.begin(Arrays.asList(data,running,owner,query,sort));
        int actions=running?0:Math.max(52,UiFonts.labelWidth("Inspect")+12)+12;
        var out=new UiResults(this,top,actions,true).samples(data.treeSamples()).nameOnlyIndent().diagnosticHints().selectable(selection);
        if(running)for(var method:methods)methodRow(out,method,method.inclusive(),method.self(),method.id(),0,false);
        else for(var branch:branches){var node=data.nodes().get(branch.node());var method=data.methods().get(node.method());methodRow(out,method,node.inclusive(),node.self(),node.id(),branch.depth()*7,index.branch(node.id()));}
        if(rowsMounted)replaceResults(out.entries);else{results(top,out.entries,true);rowsMounted=true;}collapseAvailable(hasExpanded());
    }
    private UiResults threadRows(TaskScreen task){
        var out=new UiResults(this,top,0,true).samples(task.backgroundData().samples());
        for(var worker:task.backgroundData().threads())if(worker.label().toLowerCase(Locale.ROOT).contains(threadQuery.toLowerCase(Locale.ROOT)))
            out.row("thread:"+worker.id(),worker.label(),"Sampled Java execution",worker.cpu().samples()+" samples",UiResults.share(worker.cpu().samples(),task.backgroundData().samples()),UiTheme.TEXT,0,false,worker.label()+"\nSampled Java execution",()->task.chooseThread(worker.id()));
        return out;
    }
    private UiResults selector(){
        return CapturedModSelector.rows(this,top,mods,query,owner,this::choose,"All captured CPU activity");
    }
    private void methodRow(UiResults out,CpuInvestigation.Method method,long inclusive,long self,int id,int depth,boolean branch){
        int indent=depth;boolean open=expanded.contains(id);
        String key=(running?"method:":"node:")+id;
        String label=MethodPresentation.label(method.type(),method.name());
        if(branch)label=UiResults.branch(label,open);
        out.copy(()->running?copyData.method(method):copyData.hierarchy(id,owner));
        out.row(key,label,self+" direct | "+inclusive+" in stack | "+ModNames.name(method.owner()),(running?self:inclusive)+" samples",UiResults.share(running?self:inclusive,data.treeSamples()),UiTheme.TEXT,indent,inspected.contains(key),
            MethodPresentation.hint(data,method)+"\nThread: "+data.thread()+"\nSampled Java execution\nDirect samples: "+self+" | In stack: "+inclusive+"\n"+UiResults.share(running?self:inclusive,data.treeSamples())+" of "+data.treeSamples()+" retained samples",
            branch?()->{if(!expanded.add(id))expanded.remove(id);refresh();rebuildWidgets();}:()->inspect(key),
            running?new UiResults.Action[0]:new UiResults.Action[]{new UiResults.Action("Inspect",()->inspect(key))});
        if(inspected.contains(key))out.details(key+":details",details.computeIfAbsent(method.id(),k->MethodPresentation.details(data,method)),Math.min(indent+7,Math.max(0,contentWidth()-240)));
    }
    private void inspect(String key){if(!inspected.add(key))inspected.remove(key);rebuildWidgets();}
    @Override public void tick(){if(parent instanceof TaskScreen task)task.pollPaths(this);}
    String emptyMessage(){
        if(parent instanceof TaskScreen task&&!task.pathError().isBlank())return task.pathError();
        return data.state().equals("pending")?parent instanceof TaskScreen task&&task.pathsLoading()?"Loading call paths…":"Preparing call paths…":data.state().equals("disabled")?"CPU sampling was off for this profile.":data.state().equals("legacy")?"This profile has no saved call paths.":data.state().equals("unavailable")?"CPU samples unavailable.":"No matching sampled methods.";
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float a){
        if(profileNavigation!=null)profileNavigation.render(this,g,mx,my);else{canvas(g,"CALL PATHS");text(g,data.treeSamples()+" retained samples",margin(),30,contentWidth(),UiTheme.SECONDARY);}
        text(g,data.limited()?"Call paths incomplete":"",margin(),height-62,contentWidth(),UiTheme.WARNING);
        if(data.limited())UiHints.region(g,font,mx,my,margin(),height-64,contentWidth(),14,data.limitation());
        if(!selectingMod&&!selectingThread&&(running?methods.isEmpty():branches.isEmpty()))text(g,emptyMessage(),margin(),top+8,contentWidth(),parent instanceof TaskScreen task&&!task.pathError().isBlank()?UiTheme.WARNING:UiTheme.SECONDARY);
        super.extractRenderState(g,mx,my,a);
    }
    @Override public void onClose(){if(profileNavigation!=null)profileNavigation.close();else minecraft.setScreen(parent);}
}
