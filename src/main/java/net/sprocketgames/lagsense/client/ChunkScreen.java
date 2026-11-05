package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class ChunkScreen extends LagScreen {
    private final Screen parent;
    private final Report profile;
    private final boolean archived;
    private final String dimension;
    private final int chunkX,chunkZ;
    private final ChunkBranches branches;
    private final boolean travel;
    private boolean started,byCount,byType,byMod;
    private String category="All",query="";
    private int top;
    public ChunkScreen(Screen parent,Report report,boolean archived,String dimension,int x,int z) {
        super("Chunk contributors");this.parent=parent;profile=report;this.archived=archived;travel=report.admin();this.dimension=dimension;chunkX=x;chunkZ=z;branches=new ChunkBranches(this,report,archived);
        var view=LagSenseClient.settings.chunkResults;if(view!=null){category=view.targetCategory();query=view.search();byType=view.group().endsWith("type");byMod=view.group().startsWith("mod-");byCount=view.sort().equals("count");}
    }
    public ChunkScreen(Screen parent,Report report,boolean archived,ChunkInvestigation data){this(parent,report,archived,data.dimension(),data.x(),data.z());branches.seed(data);started=true;}
    @Override protected void init() {
        if(!started){started=true;branches.start(dimension,chunkX,chunkZ);}
        var controls=new ProfileControls(this,headerControlsTop(),()->{branches.collapseAll(true);rebuildWidgets();},branches.hasExpanded(false));
        controls.search(query,value->{query=value;resetResults();refreshRows();});
        controls.dropdown("Category",84,java.util.List.of(ProfileControls.choice("All","All"),ProfileControls.choice("Entity","Entities"),ProfileControls.choice("Block","Blocks"),ProfileControls.choice("Ticks","Ticks")),category,value->{category=value;resetResults();rebuildWidgets();});
        controls.dropdown("Group",100,java.util.List.of(ProfileControls.choice("instance","By instance"),ProfileControls.choice("type","By type"),ProfileControls.choice("mod-instance","By mod"),ProfileControls.choice("mod-type","By mod and type")),(byMod?"mod-":"")+(byType?"type":"instance"),value->{byMod=value.startsWith("mod-");byType=value.endsWith("type");resetResults();rebuildWidgets();});
        controls.dropdown("Sort",84,java.util.List.of(ProfileControls.choice("time","Most time"),ProfileControls.choice("count","Most updates")),byCount?"count":"time",value->{byCount=value.equals("count");resetResults();rebuildWidgets();});
        top=controls.finish()+8;
        rowsMounted=false;refreshRows();
        copyButton(()->branches.copySelected(null,""));
        UiHints.hint(action("Export",margin()+48,footer(),48,()->{var data=branches.snapshot(dimension,chunkX,chunkZ);if(data!=null)minecraft.setScreen(new ExportScreen(this,data,profile,archived));}),"Export received contributors and category totals.");
        action("Back",width-margin()-54,footer(),54,this::onClose);
    }
    private boolean rowsMounted;
    private void refreshRows(){
        branches.mode(byType,byCount,byMod);branches.filter(category,query);
        selection.begin(java.util.List.of(dimension,chunkX,chunkZ,byType,byCount,byMod,category,query));
        var out=new UiResults(this,top,travel?76:48,false).diagnosticHints().selectable(selection);branches.append(out,dimension,chunkX,chunkZ,0);
        if(out.entries.isEmpty())out.details("empty",java.util.List.of("No matching targets."),0);
        if(rowsMounted)replaceResults(out.entries);else{results(top,out.entries,false);rowsMounted=true;}
    }
    @Override public void removed(){var value=new ResultViewSettings(category,"All",(byMod?"mod-":"")+(byType?"type":"instance"),byCount?"count":"time",query);if(!value.equals(LagSenseClient.settings.chunkResults)){LagSenseClient.settings.chunkResults=value;LagSenseClient.settings.save();}super.removed();}
    @Override public void tick(){branches.tick(this::resultVisible);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float a) {
        canvas(g,"CHUNK "+chunkX+", "+chunkZ);text(g,branches.summary(dimension,chunkX,chunkZ),margin(),panelTop()+25,contentWidth(),UiTheme.SECONDARY);headerDivider(g);
        super.extractRenderState(g,mx,my,a);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
}
