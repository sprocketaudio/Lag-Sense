package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class TaskScreen extends LagScreen {
    private static final CpuInvestigation PENDING=CpuInvestigation.empty("pending");
    private final Screen parent;
    private final Report original;
    private Report resolved;
    private final boolean archived;
    private final String ownerFilter;
    private boolean byMod=TaskNavigation.DEFAULT_EVENT_BY_MOD,callbackView=TaskNavigation.DEFAULT_EVENTS;
    private boolean cpuByMod=TaskNavigation.DEFAULT_CPU_BY_MOD,eventByMod=TaskNavigation.DEFAULT_EVENT_BY_MOD;
    private boolean cachedGroup,cachedMode,cachedBackground,background;
    private long selectedThread;
    private Report cached;
    private CpuInvestigation cpu;
    private boolean loadingCpu,openingPaths;
    private long nextCpuRetry,cpuWaitingSince;
    private Long copyPending;
    private ResultText.Cpu copyData;
    private CallTreeScreen paths;
    private final Map<String,Integer> viewOffsets=new HashMap<>();
    private final Set<String> expanded=new HashSet<>();
    private final Map<String,List<String>> details=new HashMap<>();
    private List<Report.Task> allTasks=List.of(),tasks=List.of();
    private List<Report.Callback> allCallbacks=List.of(),callbacks=List.of();
    private Map<String,List<Report.Task>> taskGroups=Map.of();
    private Map<String,List<Report.Callback>> callbackGroups=Map.of();
    private final Map<Report.Callback,Integer> legacyEvents=new IdentityHashMap<>();
    private long total;
    private String warning="",cpuError="";
    private int top;
    private String query="",sort="cost";
    private boolean rowsMounted;
    public TaskScreen(Screen parent,Report report){this(parent,report,LagSenseClient.archived);}
    public TaskScreen(Screen parent,Report report,boolean archived){this(parent,report,archived,null);}
    public TaskScreen(Screen parent,Report report,boolean archived,String owner){super("Server tasks");this.parent=parent;original=report;this.archived=archived;ownerFilter=owner;}
    Report report(){
        var latest=LagSenseClient.report;
        if(!archived&&latest!=null&&latest.id()==original.id()&&latest.admin()==original.admin())resolved=latest;
        return resolved==null?original:resolved;
    }
    boolean archived(){return archived;}
    private void prepare(){
        if(cached==report()&&cachedGroup==byMod&&cachedMode==callbackView&&cachedBackground==background)return;
        cpuCopies.clear();cached=report();cachedGroup=byMod;cachedMode=callbackView;cachedBackground=background;
        var d=cached.admin()?cached.diagnostics():null;
        warning=d==null?"":d.notes().stream().filter(n->n.startsWith("Backup")).map(StatusText::note).findFirst().orElse(callbackView&&d.callbackState().equals("unavailable")?"Event timing stopped early.":"");
        if(cached.truncated())warning=ReportCompleteness.label(cached);
        else if(!MeasurementHelp.diagnosticLimit(cached,callbackView).isEmpty())warning=callbackView?"Event results limited":"CPU results limited";
        var captured=d==null?List.<Report.Task>of():background&&d.background()!=null?d.background().tasks():background?List.<Report.Task>of():d.tasks();total=captured.stream().mapToLong(Report.Task::samples).sum();
        allTasks=ownerFilter==null?captured:captured.stream().filter(t->t.owner().equals(ownerFilter)).toList();
        String term=query.toLowerCase(Locale.ROOT);
        allTasks=allTasks.stream().filter(t->term.isBlank()||(t.owner()+" "+ModNames.name(t.owner())+" "+t.method()+" "+t.threadName()).toLowerCase(Locale.ROOT).contains(term)).toList();
        tasks=TaskNavigation.tasks(allTasks,byMod,null).stream().sorted(sort.equals("name")?Comparator.comparing(t->byMod?t.owner():t.method()):Comparator.comparingLong(Report.Task::samples).reversed()).toList();
        taskGroups=allTasks.stream().collect(java.util.stream.Collectors.groupingBy(Report.Task::owner));
        var events=d==null?List.<Report.Callback>of():d.callbacks();legacyEvents.clear();for(int i=0;i<events.size();i++)legacyEvents.putIfAbsent(events.get(i),i+1);
        allCallbacks=ownerFilter==null?events:events.stream().filter(c->c.owner().equals(ownerFilter)).toList();
        allCallbacks=allCallbacks.stream().filter(c->term.isBlank()||(c.owner()+" "+ModNames.name(c.owner())+" "+c.method()+" "+c.event()).toLowerCase(Locale.ROOT).contains(term)).toList();
        callbackGroups=allCallbacks.stream().collect(java.util.stream.Collectors.groupingBy(Report.Callback::owner));
        if(!byMod)callbacks=allCallbacks;
        else{
            Map<String,long[]> sums=new HashMap<>();
            for(var c:allCallbacks){var n=sums.computeIfAbsent(c.owner(),k->new long[3]);n[0]+=c.nanos();n[1]+=c.calls();n[2]=Math.max(n[2],c.maxNanos());}
            callbacks=sums.entrySet().stream().map(e->new Report.Callback(e.getKey(),"All events","",e.getValue()[0],e.getValue()[1],e.getValue()[2])).sorted(Comparator.comparingLong(Report.Callback::nanos).reversed()).toList();
        }
        callbacks=callbacks.stream().sorted(sort.equals("name")?Comparator.comparing(c->byMod?c.owner():c.method()):sort.equals("calls")?Comparator.comparingLong(Report.Callback::calls).reversed():Comparator.comparingLong(Report.Callback::nanos).reversed()).toList();
    }
    private String key(Report.Task task){return "task:"+task.threadId()+"|"+task.owner()+"|"+task.method();}
    private String key(Report.Callback event){return "event:"+event.owner()+"|"+event.event()+"|"+event.method()+"|"+(event.handlerId()>0?event.handlerId():"legacy:"+legacyEvents.getOrDefault(event,0));}
    private void toggle(String key){if(!expanded.add(key))expanded.remove(key);rebuildWidgets();}
    private void inspect(Report.Task task){
        String key=key(task);if(!expanded.add(key))expanded.remove(key);else if(!background)requestCpu(false);rebuildWidgets();
    }
    private void requestCpu(boolean openPaths){
        if(background){if(openPaths)showPaths();return;}
        openingPaths|=openPaths;
        retainedCpu();
        if(cpu!=null&&!cpu.state().equals("pending")){if(openingPaths)showPaths();return;}
        if(loadingCpu)return;loadingCpu=true;cpuError="";nextCpuRetry=System.nanoTime()+1_000_000_000L;if(cpuWaitingSince==0)cpuWaitingSince=System.nanoTime();
        Investigations.cpu(minecraft.screen instanceof CallTreeScreen?minecraft.screen:this,report(),archived,value->{loadingCpu=false;cpu=value;copyData=new ResultText.Cpu(cpu);cpuCopies.clear();details.clear();if(copyPending!=null){long token=copyPending;copyPending=null;if(selection.current(token)){selection.waiting(false);copySelected();}}if(openingPaths||minecraft.screen instanceof CallTreeScreen tree&&tree.belongsTo(this))showPaths();else if(minecraft.screen==this)rebuildWidgets();},
            error->{loadingCpu=false;openingPaths=false;if(!background)cpuError=error;copyPending=null;selection.waiting(false);if(minecraft.screen==this)rebuildWidgets();});
    }
    private void showPaths(){
        openingPaths=false;if(minecraft.screen!=this&&!(minecraft.screen instanceof CallTreeScreen tree&&tree.belongsTo(this)))return;
        var data=pathData();
        if(paths==null||paths.data()!=data&&!(paths.data().state().equals("pending")&&data.state().equals("pending"))){var previous=paths;paths=new CallTreeScreen(this,data,previous==null?ownerFilter:previous.ownerFilter());if(previous!=null)paths.inheritControls(previous);}
        paths.profileNavigation=profileNavigation;minecraft.setScreen(paths);
    }
    boolean background(){return background;}
    BackgroundCpu backgroundData(){var d=report().diagnostics();return d==null||d.background()==null?BackgroundCpu.empty("legacy"):d.background();}
    long selectedThread(){return selectedThread;}
    CpuInvestigation pathData(){
        if(!background){retainedCpu();return cpu==null?PENDING:cpu;}
        var data=backgroundData();var worker=data.thread(selectedThread);if(worker==null&&!data.threads().isEmpty()){worker=data.threads().getFirst();selectedThread=worker.id();}
        return worker==null?CpuInvestigation.empty(data.state()):worker.cpu();
    }
    private void retainedCpu(){
        var value=Investigations.retainedCpu(report());
        if(value!=null&&(cpu==null||cpu.state().equals("pending"))){cpu=value;copyData=new ResultText.Cpu(cpu);cpuCopies.clear();details.clear();cpuError="";cpuWaitingSince=0;}
    }
    String pathError(){return cpuError;}
    boolean pathsLoading(){return loadingCpu;}
    void pollPaths(CallTreeScreen screen){
        if(background)return;
        var value=pathData();
        if(!value.state().equals("pending")){if(screen.data()!=value)showPaths();return;}
        if(!cpuError.isBlank()||loadingCpu)return;
        long now=System.nanoTime();
        if(cpuWaitingSince!=0&&now-cpuWaitingSince>=60_000_000_000L){cpuError="CPU analysis timed out. Reopen the profile to retry.";return;}
        if(now>=nextCpuRetry)requestCpu(false);
    }
    void chooseThread(long id){background=id!=0;selectedThread=id;showPaths();if(!background)requestCpu(false);}
    void selectSection(boolean events){view(events,events?eventByMod:cpuByMod);}
    void openPaths(){showPaths();if(!background)requestCpu(false);}
    List<UiDropdown.Choice> pathThreads(){var choices=new ArrayList<UiDropdown.Choice>();choices.add(ProfileControls.choice("0","Server Thread"));for(var worker:backgroundData().threads())choices.add(ProfileControls.choice(Long.toString(worker.id()),worker.label()));return choices;}
    private void scope(boolean value,boolean paths){
        if(background==value)return;
        viewOffsets.put(callbackView+":"+byMod+":"+background,listOffset());background=value;cached=null;details.clear();cpuError="";openingPaths=false;copyPending=null;selection.waiting(false);
        restoreListOffset(viewOffsets.getOrDefault(callbackView+":"+byMod+":"+background,0));
        if(paths){if(background)showPaths();else requestCpu(true);}else rebuildWidgets();
    }
    private CpuInvestigation cpuFor(Report.Task task){if(task.threadId()==0)return cpu;var worker=backgroundData().thread(task.threadId());return worker==null?null:worker.cpu();}
    private ResultText.Cpu copyFor(Report.Task task){var data=cpuFor(task);return data==null?null:cpuCopies.computeIfAbsent(task.threadId(),k->new ResultText.Cpu(data));}
    private final Map<Long,ResultText.Cpu> cpuCopies=new HashMap<>();
    private String cpuLimit(){return background?backgroundData().reason():MeasurementHelp.diagnosticLimit(report(),false);}
    private void view(boolean callbacks,boolean mods) {
        if(callbacks)eventByMod=mods;else cpuByMod=mods;
        if(callbackView!=callbacks||byMod!=mods){
            viewOffsets.put(callbackView+":"+byMod,listOffset());if(callbackView!=callbacks){query="";sort="cost";cached=null;}callbackView=callbacks;byMod=mods;
            restoreListOffset(viewOffsets.getOrDefault(callbackView+":"+byMod,0));
        }
        if(minecraft.screen==this)rebuildWidgets();else minecraft.setScreen(this);
    }
    UiMetrics.Flow taskControls(LagScreen screen,boolean callPaths){
        int y=profileNavigation==null?screen.headerControlsTop():profileNavigation.mount(screen,callPaths?"Call Paths":callbackView?"Event Timings":"CPU Activity")+4;
        return new UiMetrics.Flow(screen.margin(),100000,y);
    }
    private boolean hasExpanded(){return callbackView?callbacks.stream().anyMatch(c->expanded.contains(byMod?"event-mod:"+c.owner():key(c))):tasks.stream().anyMatch(t->expanded.contains(byMod?"task-mod:"+t.owner():key(t)));}
    private void collapseAll(){expanded.removeIf(k->k.startsWith(callbackView?"event":"task"));rebuildWidgets();}
    @Override protected void init(){
        prepare();rowsMounted=false;var flow=taskControls(this,false);
        var controls=new ProfileControls(this,flow.bottom()-20,this::collapseAll,hasExpanded());
        controls.search(query,value->{query=value;cached=null;resetResults();refreshRows();});
        if(!callbackView)controls.dropdown("Threads",148,List.of(ProfileControls.choice("server","Server Thread"),ProfileControls.choice("background","Background Threads")),background?"background":"server",value->scope(value.equals("background"),false));
        controls.dropdown("Group",116,List.of(ProfileControls.choice("mod","By mod"),ProfileControls.choice("action","By action")),byMod?"mod":"action",value->view(callbackView,value.equals("mod")));
        controls.dropdown("Sort",116,callbackView?List.of(ProfileControls.choice("cost","Most time"),ProfileControls.choice("calls","Most calls"),ProfileControls.choice("name","Name")):List.of(ProfileControls.choice("cost","Most samples"),ProfileControls.choice("name","Name")),sort,value->{sort=value;cached=null;resetResults();rebuildWidgets();});
        top=controls.finish()+8;refreshRows();
        copyButton(this::copySelected);
        action("Export",margin()+48,footer(),48,()->minecraft.setScreen(new ExportScreen(this,report(),archived)));
        action(profileNavigation==null?"Back":"Close",width-margin()-54,footer(),54,this::onClose);
    }
    private void refreshRows(){
        prepare();selection.begin(Arrays.asList(cached,callbackView,byMod,ownerFilter,background,query,sort));
        var out=new UiResults(this,top,0,true).diagnosticHints().selectable(selection);
        if(!callbackView)out.samples(total);
        if(callbackView)for(var event:callbacks){
            String key=byMod?"event-mod:"+event.owner():key(event);
            out.lazy(key,row->callbackRow(row,key,event,byMod,0));
            if(expanded.contains(key)){
                if(byMod)for(var child:callbackGroups.getOrDefault(event.owner(),List.of())){out.lazy(key(child),row->callbackRow(row,key(child),child,false,12));if(expanded.contains(key(child)))out.details(key(child)+":details",callbackLines(child),24);}
                else out.details(key+":details",callbackLines(event),12);
            }
        }else for(var task:tasks){
            String key=byMod?"task-mod:"+task.owner():key(task);
            out.lazy(key,row->taskRow(row,key,task,byMod,0));
            if(expanded.contains(key)){
                if(byMod)for(var child:taskGroups.getOrDefault(task.owner(),List.of())){out.lazy(key(child),row->taskRow(row,key(child),child,false,12));if(expanded.contains(key(child)))out.details(key(child)+":details",methodLines(child),24);}
                else out.details(key+":details",methodLines(task),12);
            }
        }
        if(rowsMounted)replaceResults(out.entries);else{results(top,out.entries,true);rowsMounted=true;}collapseAvailable(hasExpanded());
    }
    private void taskRow(UiResults out,String key,Report.Task task,boolean group,int indent){
        String name=group?ModNames.name(task.owner()):MethodPresentation.label(task.method());
        String secondary=task.samples()+" samples"+(group?" | "+task.owner():" | "+ModNames.name(task.owner()));
        String hint=(group?"":task.method()+"\n")+modHint(task.owner());
        var taskCpu=cpuFor(task);
        if(taskCpu!=null&&!group)for(var m:taskCpu.methods())if((m.type()+"#"+m.name()).equals(task.method().replace("Event callback · ",""))){hint=MethodPresentation.hint(taskCpu,m)+"\nDirect samples: "+m.self()+" | In stack: "+m.inclusive();break;}
        hint+="\n"+task.samples()+" samples | "+UiResults.share(task.samples(),total)+" of "+total+" listed samples";
        if(task.threadId()>0)hint+="\nThread: "+task.threadName()+" (#"+task.threadId()+")";
        hint+="\nSampled Java execution";
        String limit=cpuLimit();if(!limit.isEmpty())hint+="\n"+limit;
        out.copy(()->group?background?new ResultText(ModNames::name).taskThreads(task,taskGroups.getOrDefault(task.owner(),List.of()),total,this::copyFor):new ResultText(ModNames::name).taskMod(task,taskGroups.getOrDefault(task.owner(),List.of()),total,copyData):new ResultText(ModNames::name).task(task,total,copyFor(task)));
        out.row(key,UiResults.branch(name,expanded.contains(key)),secondary,task.samples()+" samples",UiResults.share(task.samples(),total),UiTheme.TEXT,indent,expanded.contains(key),hint,
            group?()->toggle(key):()->inspect(task));
    }
    private void callbackRow(UiResults out,String key,Report.Callback event,boolean group,int indent){
        double cost=event.nanos()/1000.0/Math.max(1,report().ticks());
        out.copy(()->group?new ResultText(ModNames::name).eventMod(event,callbackGroups.getOrDefault(event.owner(),List.of()),report().ticks()):new ResultText(ModNames::name).event(event,report().ticks()));
        out.row(key,UiResults.branch(group?ModNames.name(event.owner()):MethodPresentation.label(event.method()),expanded.contains(key)),
            event.calls()+" calls"+(group?" | "+event.owner():" | "+ModNames.name(event.owner())+" | "+event.event()),UiResults.cost(cost),TimingSeverity.color(cost),indent,expanded.contains(key),
            (group?"":event.method()+"\n"+event.event()+"\n")+modHint(event.owner())+"\n"+event.calls()+" calls | "+UiResults.cost(cost)+(event.handlerId()>0?"\nHandler: #"+event.handlerId():"")+"\n"+String.join("\n",callbackLines(event)),()->toggle(key));
    }
    private void copySelected(){
        if(selection.key()==null)return;
        if(!callbackView&&!background&&(cpu==null||cpu.state().equals("pending"))){copyPending=selection.generation();selection.waiting(true);requestCpu(false);return;}
        selection.copy(String.join("\n",java.util.stream.Stream.of(ReportCompleteness.reason(report()),callbackView?MeasurementHelp.diagnosticLimit(report(),true):cpuLimit(),!callbackView&&cpu!=null&&cpu.limited()?cpu.limitation():"").filter(s->!s.isBlank()).toList()));
    }
    private static String modHint(String owner){String name=ModNames.name(owner);return (name.equals(owner)?"":name+"\n")+"Mod: "+owner;}
    private List<String> callbackLines(Report.Callback event){return List.of(String.format(Locale.ROOT,"Total %.3f ms | Average %.3f ms/call",event.nanos()/1_000_000.0,event.nanos()/1_000_000.0/Math.max(1,event.calls())),String.format(Locale.ROOT,"Longest %.3f ms",event.maxNanos()/1_000_000.0));}
    private List<String> methodLines(Report.Task task){
        var data=cpuFor(task);
        if(data==null)return List.of(loadingCpu?"Loading call details…":cpuError.isBlank()?"No saved call details.":cpuError);
        return details.computeIfAbsent(key(task),k->{var lines=new ArrayList<String>();if(task.threadId()>0)lines.add("Thread: "+task.threadName()+" (#"+task.threadId()+")");lines.addAll(methodLines(data,task,total));return List.copyOf(lines);});
    }
    static List<String> methodLines(CpuInvestigation cpu,Report.Task task,long total){
        var lines=new ArrayList<String>();String full=task.method().replace("Event callback · ","");
        for(var method:cpu.methods())if((method.type()+"#"+method.name()).equals(full))lines.addAll(MethodPresentation.details(cpu,method));
        if(lines.isEmpty())lines.add("No saved call paths for this method.");return List.copyOf(lines);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float a){
        if(cached!=report())rebuildWidgets();if(profileNavigation!=null)profileNavigation.render(this,g,mx,my);else canvas(g,"SERVER TASKS");
        text(g,openingPaths?"Loading call paths…":cpuError.isBlank()?background&&!callbackView&&!cpuLimit().isEmpty()?"Background CPU incomplete":warning:cpuError,margin(),height-62,contentWidth(),UiTheme.WARNING);
        if(report().truncated())UiHints.region(g,font,mx,my,margin(),height-64,contentWidth(),14,ReportCompleteness.reason(report()));
        else UiHints.region(g,font,mx,my,margin(),height-64,contentWidth(),14,callbackView?MeasurementHelp.diagnosticLimit(report(),true):cpuLimit());
        if((callbackView?callbacks:tasks).isEmpty())text(g,callbackView?TaskNavigation.callbackState(report().diagnostics()):background&&backgroundData().state().equals("legacy")?"This profile has no background samples.":background&&backgroundData().state().equals("disabled")?"CPU sampling was off for this profile.":"No CPU activity recorded.",margin(),top+8,contentWidth(),UiTheme.SECONDARY);
        super.extractRenderState(g,mx,my,a);
    }
    @Override public void onClose(){if(profileNavigation!=null)profileNavigation.close();else minecraft.setScreen(parent);}
}
