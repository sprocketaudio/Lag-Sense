package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
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
    private int top;
    public ChunkScreen(Screen parent,Report report,boolean archived,String dimension,int x,int z) {
        super("Chunk contributors");this.parent=parent;profile=report;this.archived=archived;travel=report.admin();this.dimension=dimension;chunkX=x;chunkZ=z;branches=new ChunkBranches(this,report,archived);
    }
    public ChunkScreen(Screen parent,Report report,boolean archived,ChunkInvestigation data){this(parent,report,archived,data.dimension(),data.x(),data.z());branches.seed(data);started=true;}
    @Override protected void init() {
        if(!started){started=true;branches.start(dimension,chunkX,chunkZ);}
        var controls=new UiMetrics.Flow(margin(),width-margin(),headerControlsTop());
        action(byCount?"Most updates":"Most time",controls.next(Math.max(UiFonts.labelWidth("Most updates"),UiFonts.labelWidth("Most time"))+12),()->{byCount=!byCount;resetResults();rebuildWidgets();}).role(UiButton.Role.TAB).selected(true);
        action(byType?"By Type":"By Instance",controls.next(Math.max(UiFonts.labelWidth("By Instance"),UiFonts.labelWidth("By Type"))+12),()->{byType=!byType;resetResults();rebuildWidgets();}).role(UiButton.Role.TAB).selected(byType);
        toggle("Group by Mod",controls.next(UiFonts.labelWidth("Group by Mod OFF")+16),byMod,()->{byMod=!byMod;resetResults();rebuildWidgets();}).strongText();
        top=ProfileActions.collapse(this,controls,()->{branches.collapseAll(true);rebuildWidgets();},branches.hasExpanded(false))+8;
        branches.mode(byType,byCount,byMod);
        selection.begin(java.util.List.of(dimension,chunkX,chunkZ,byType,byCount,byMod));
        var out=new UiResults(this,top,travel?76:48,false).diagnosticHints().selectable(selection);
        branches.append(out,dimension,chunkX,chunkZ,0);results(top,out.entries,false);
        copyButton(()->branches.copySelected(null,""));
        UiHints.hint(action("Export",margin()+48,footer(),48,()->{var data=branches.snapshot(dimension,chunkX,chunkZ);if(data!=null)minecraft.setScreen(new ExportScreen(this,data,profile,archived));}),"Export received contributors and category totals.");
        action("Back",width-margin()-54,footer(),54,this::onClose);
    }
    @Override public void tick(){branches.tick(this::resultVisible);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float a) {
        canvas(g,"CHUNK "+chunkX+", "+chunkZ);text(g,branches.summary(dimension,chunkX,chunkZ),margin(),panelTop()+25,contentWidth(),UiTheme.SECONDARY);headerDivider(g);
        super.extractRenderState(g,mx,my,a);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
}
