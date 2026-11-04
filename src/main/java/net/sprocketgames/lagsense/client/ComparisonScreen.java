package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class ComparisonScreen extends LagScreen {
    private final Screen parent;
    private final ComparisonIndex index;
    private final ProfileComparison comparison;
    private ComparisonIndex.View view;
    private final Set<ComparisonIndex.Key> expanded=new HashSet<>();
    private boolean chunks,loading;
    private int direction,top;
    private String dimension="All",error="";
    private long query;
    private List<ComparisonIndex.Node> visible=List.of();
    private final String baseline,compared,summary,summaryPrefix,summaryPercentage;
    private final int summaryColor;
    private final UiTheme.Theme summaryTheme=UiTheme.current();
    public ComparisonScreen(Screen parent,ProfileComparison comparison){this(parent,new ComparisonIndex(comparison));}
    public ComparisonScreen(Screen parent,ComparisonIndex index){
        super("What changed?");this.parent=parent;this.index=index;comparison=index.comparison();view=index.all;
        baseline="Earlier: "+HistoryScreen.date(comparison.beforeAt())+" | "+comparison.beforeTicks()+" ticks";
        compared="Later: "+HistoryScreen.date(comparison.afterAt())+" | "+comparison.afterTicks()+" ticks";
        double delta=comparison.afterMspt()-comparison.beforeMspt();String percent=ProfileComparison.percentLabel(ProfileComparison.percentage(comparison.beforeMspt(),comparison.afterMspt()));
        summary=String.format(Locale.ROOT,"Server: %.2f → %.2f MSPT (%+.2f%s)",comparison.beforeMspt(),comparison.afterMspt(),delta,percent.isEmpty()?"":", "+percent);summaryColor=UiTheme.changeColor(Math.abs(delta)<.005?0:delta);
        summaryPercentage=percent.isEmpty()?"":percent+")";summaryPrefix=summary.substring(0,summary.length()-summaryPercentage.length());
    }
    private int headerBottom(){return headerControlsTop()+44;}
    @Override protected void headerDivider(GuiGraphicsExtractor g){super.headerDivider(g);g.fill(margin(),headerBottom(),width-margin(),headerBottom()+1,UiTheme.BORDER);}
    private void direction(int value){direction=value;resetResults();rebuildWidgets();}
    private void cycleDimension(){
        dimension=index.dimensions.get((index.dimensions.indexOf(dimension)+1)%index.dimensions.size());long token=++query;error="";resetResults();
        if(dimension.equals("All")){view=index.all;loading=false;rebuildWidgets();return;}
        loading=true;String requested=dimension;
        Comparisons.view(index,requested,next->{if(token!=query)return;view=next;loading=false;if(minecraft.screen==this)rebuildWidgets();},message->{if(token==query){error=message;loading=false;if(minecraft.screen==this)rebuildWidgets();}});
        rebuildWidgets();
    }
    @Override protected void init(){
        selection.begin(List.of(chunks,direction,dimension,loading));
        var tabs=new UiMetrics.Flow(margin(),width-margin(),headerBottom()+8);
        tab("Types",slot(tabs,"Types",42),!chunks,()->{chunks=false;resetResults();rebuildWidgets();});
        tab("Chunks",slot(tabs,"Chunks",46),chunks,()->{chunks=true;resetResults();rebuildWidgets();});tabs.group();
        tab("All",slot(tabs,"All",30),direction==0,()->direction(0));tab("Higher cost",slot(tabs,"Higher cost",74),direction==1,()->direction(1));tab("Lower cost",slot(tabs,"Lower cost",74),direction==2,()->direction(2));
        var controls=new UiMetrics.Flow(margin(),width-margin(),tabs.bottom()+4);
        String label=dimension.equals("All")?"All dimensions":dimension.substring(dimension.indexOf(':')+1);
        action(label,slot(controls,label,86),this::cycleDimension).role(UiButton.Role.TAB).selected(!dimension.equals("All"));
        top=controls.bottom()+8;
        var roots=chunks?view.chunks():view.types();visible=loading||!view.dimension().equals(dimension)?List.of():roots.stream().filter(n->index.matches(n,direction)).toList();
        var out=new UiResults(this,top,88,true,false).diagnosticHints().selectable(selection);append(out);results(top,out.entries,true);
        copyButton(()->selection.copy(copyWarning()));
        action("Export",margin()+48,footer(),48,()->minecraft.setScreen(new ExportScreen(this,comparison)));
        action("Close",width-margin()-54,footer(),54,this::onClose);
    }
    private void append(UiResults out){for(var node:visible)append(out,node,0);}
    private void append(UiResults out,ComparisonIndex.Node node,int depth){
        out.lazy(node.key(),row->row(row,node,depth));
        if(expanded.contains(node.key()))for(var child:node.children())append(out,child,depth+1);
    }
    private void row(UiResults out,ComparisonIndex.Node node,int depth){
        boolean open=expanded.contains(node.key()),branch=!node.children().isEmpty();String name=ComparisonTargets.individual(node)?ComparisonTargets.title(node):node.name();
        if(branch&&(node.earlier().countKnown()||node.later().countKnown()))name+=" ("+(node.earlier().countKnown()?node.earlier().targets():"?")+" → "+(node.later().countKnown()?node.later().targets():"?")+")";
        if(!branch&&!ComparisonTargets.individual(node)&&!node.status().isBlank())name+=" · "+node.status();if(branch)name=UiResults.branch(name,open);
        double before=node.before(comparison),after=node.after(comparison),delta=node.delta(comparison);boolean ms=Math.max(Math.abs(delta),Math.max(before,after))>=1000;double unit=ms?1000:1;String units=ms?"ms/t":"µs/t";
        String percent=ProfileComparison.percentLabel(node.percent(comparison));
        String values=(node.earlier().targets()==0?"-":String.format(Locale.ROOT,"%.2f",before/unit))+" → "+(node.later().targets()==0?"-":String.format(Locale.ROOT,"%.2f",after/unit))+" "+units+" | "+(percent.isEmpty()?"-%":percent);
        boolean deltaMs=Math.abs(delta)>=1000;
        out.copy(()->new ResultText(ModNames::name).comparison(node,index,false));
        var destination=ComparisonTargets.actionable(node)?index.targets.destination(node):null;
        out.row(node.key(),name,values,String.format(Locale.ROOT,"%+.2f %s",delta/(deltaMs?1000:1),deltaMs?"ms/t":"µs/t"),UiTheme.changeColor(Math.abs(delta)<.005?0:delta),depth*12,open,hint(node),branch?()->{if(!expanded.add(node.key()))expanded.remove(node.key());rebuildWidgets();}:null,destination!=null?TargetActions.actions(this,destination):new UiResults.Action[0]);
    }
    private String copyWarning(){
        var notes=new ArrayList<String>();if(comparison.incomplete())notes.add("Incomplete capture");if(comparison.presentationLimited())notes.add("Comparison preview");if(comparison.details()==null)notes.add("Individual details unavailable");
        else {if(comparison.details().earlierIncomplete())notes.add("Earlier: "+comparison.details().earlierLimit());if(comparison.details().laterIncomplete())notes.add("Later: "+comparison.details().laterLimit());}return String.join("\n",notes);
    }
    private String hint(ComparisonIndex.Node node){
        var k=node.key();var lines=new ArrayList<String>();lines.add(node.name());if(!k.kind().isBlank())lines.add(k.kind());
        if(!k.dimension().equals("All"))lines.add(k.dimension());
        if(!k.scope().equals("chunk")&&(k.scope().startsWith("chunk")||k.scope().equals("category")))lines.add("Chunk "+k.x()+", "+k.z());
        if(node.uuid()!=null)lines.add("UUID: "+node.uuid());if(!node.status().isBlank())lines.add(node.status());
        var a=node.earlier().location();var b=node.later().location();
        if(a!=null&&b!=null&&node.earlier().locations()==1&&node.later().locations()==1&&a.dimension().equals(b.dimension())&&a.x()==b.x()&&a.y()==b.y()&&a.z()==b.z()){
            lines.add("Location: "+a.dimension()+" | "+a.x()+" "+a.y()+" "+a.z());lines.add("Updates: "+node.earlier().calls()+" / "+comparison.beforeTicks()+" ticks → "+node.later().calls()+" / "+comparison.afterTicks()+" ticks");
        }else{location(lines,"Earlier",node.earlier());location(lines,"Later",node.later());}
        lines.add(String.format(Locale.ROOT,"Earlier: %s | Later: %s",node.earlier().targets()==0?"-":UiResults.cost(node.before(comparison)),node.later().targets()==0?"-":UiResults.cost(node.after(comparison))));
        lines.add(String.format(Locale.ROOT,"Change: %+.6f µs/t | %s",node.delta(comparison),node.percent(comparison)==null?"-%":ProfileComparison.percentLabel(node.percent(comparison))));
        if(node.earlier().countKnown()&&node.later().countKnown())lines.add("Measured targets: "+node.earlier().targets()+" → "+node.later().targets());
        if(node.status().equals("Added")||node.status().equals("Removed"))lines.add("Presence in capture data");
        return String.join("\n",lines);
    }
    private void location(List<String> lines,String label,ComparisonIndex.Stats stats){if(stats.location()!=null){var e=stats.location();lines.add(label+" location: "+e.dimension()+" | "+e.x()+" "+e.y()+" "+e.z()+(stats.locations()>1?" | "+stats.locations()+" measured locations":""));lines.add(label+" updates: "+stats.calls()+" / "+(label.equals("Earlier")?comparison.beforeTicks():comparison.afterTicks())+" ticks");}}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        canvas(g,"WHAT CHANGED?");headerDivider(g);int y=headerControlsTop();
        text(g,summaryPrefix,margin(),y,contentWidth(),UiTheme.textColor(summaryColor,summaryTheme));
        int percentageX=margin()+UiFonts.width(font,summaryPrefix);if(percentageX<width-margin())text(g,summaryPercentage,percentageX,y,width-margin()-percentageX,UiTheme.TEXT);text(g,baseline,margin(),y+14,contentWidth(),UiTheme.SECONDARY);text(g,compared,margin(),y+26,contentWidth(),UiTheme.SECONDARY);
        UiHints.region(g,font,mx,my,margin(),y-2,contentWidth(),12,summary);
        if(visible.isEmpty())text(g,loading?"Loading comparison…":error.isBlank()?"No matching changes.":error,margin(),top+8,contentWidth(),UiTheme.SECONDARY);
        String note=comparison.incomplete()?"Incomplete capture":comparison.presentationLimited()?"Comparison preview":comparison.details()==null?"Individual details unavailable":"";
        text(g,note,margin(),height-62,contentWidth(),UiTheme.WARNING);
        if(!note.isEmpty()){String reason=comparison.details()==null?"Aggregate comparison data only.":"Earlier: "+(comparison.details().earlierIncomplete()?comparison.details().earlierLimit():"No collection limit reported")+"\nLater: "+(comparison.details().laterIncomplete()?comparison.details().laterLimit():"No collection limit reported");UiHints.region(g,font,mx,my,margin(),height-64,contentWidth(),14,reason);}
        super.extractRenderState(g,mx,my,partial);
    }
    @Override public void onClose(){query++;minecraft.setScreen(parent);}
}
