package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

public final class HeatmapScreen extends LagScreen {
    private final Screen parent;
    private final Report report;
    private final boolean archived;
    private final MapTargets allData;
    private MapTargets data;
    private final CapturedMods mods;
    private final Map<String,Long2ObjectOpenHashMap<Report.Chunk>> index=new HashMap<>();
    private final Map<Report.Chunk,String> labels=new HashMap<>();
    private final Map<String,List<HeatmapPlan.Tile>> tiles=new HashMap<>();
    private final MapInteraction gesture=new MapInteraction();
    private HeatmapPlan plan;
    private net.minecraft.client.renderer.texture.DynamicTexture mapTexture;
    private String mappedDimension,dimension,owner,query="";
    private int mappedTheme=-1;
    private boolean selectingMod;
    private double centerX,centerZ,zoomScroll;
    private int cell=18,mapTop;
    private Report.Chunk selected;
    public HeatmapScreen(Screen parent,Report report){this(parent,report,LagSenseClient.archived);}
    public HeatmapScreen(Screen parent,Report report,boolean archived){
        super("Chunk heatmap");this.parent=parent;this.report=report;this.archived=archived;allData=data=new MapTargets(report,null);
        mods=new CapturedMods(report.entries().stream().map(ProfileResults::owner).toList(),ModNames::name);selected=data.chunks.isEmpty()?null:data.chunks.getFirst();dimension=selected==null?"minecraft:overworld":selected.dimension();rebuildData();recenter();
    }
    private static long key(int x,int z){return(long)x<<32|z&0xffffffffL;}
    private void rebuildData(){index.clear();tiles.clear();labels.clear();releaseTexture();plan=null;for(var c:data.chunks){index.computeIfAbsent(c.dimension(),d->new Long2ObjectOpenHashMap<>()).put(key(c.x(),c.z()),c);tiles.computeIfAbsent(c.dimension(),d->new ArrayList<>()).add(new HeatmapPlan.Tile(c.x(),c.z(),TimingSeverity.color(c.micros(report.ticks()))));}}
    private void recenter(){if(selected!=null){centerX=selected.x()+.5;centerZ=selected.z()+.5;}}
    private MapInteraction.Viewport viewport(){return new MapInteraction.Viewport(margin(),mapTop,width-margin(),Math.max(mapTop+1,height-94));}
    private HeatmapPlan map(){
        var shape=MapInteraction.geometry(viewport(),centerX,centerZ,cell);
        if(plan==null||mappedTheme!=UiTheme.revision()||!dimension.equals(mappedDimension)||!plan.matches(shape.left(),shape.top(),shape.columns(),shape.rows(),cell,shape.anchorX(),shape.anchorZ())){
            releaseTexture();plan=new HeatmapPlan(shape.left(),shape.top(),shape.columns(),shape.rows(),cell,shape.anchorX(),shape.anchorZ(),tiles.getOrDefault(dimension,List.of()),UiTheme.PANEL,UiTheme.BACKGROUND);mappedDimension=dimension;mappedTheme=UiTheme.revision();
        }return plan;
    }
    private void releaseTexture(){if(mapTexture!=null){mapTexture.close();mapTexture=null;}}
    private void paintMap(GuiGraphicsExtractor g,HeatmapPlan view){
        if(!view.textureRecommended()){view.paint(g::fill);return;}
        if(mapTexture==null){var image=new com.mojang.blaze3d.platform.NativeImage(view.right()-view.left(),view.bottom()-view.top(),false);try{view.paint((l,t,r,b,c)->image.fillRect(l-view.left(),t-view.top(),r-l,b-t,c));mapTexture=new net.minecraft.client.renderer.texture.DynamicTexture(()->"LagSense chunk map",image);}catch(Throwable failure){image.close();throw failure;}}
        g.blit(mapTexture.getTextureView(),mapTexture.getSampler(),view.left(),view.top(),view.right(),view.bottom(),0,1,0,1);
    }
    private Report.Chunk find(int x,int z){var values=index.get(dimension);return values==null?null:values.get(key(x,z));}
    private String label(Report.Chunk chunk){var value=labels.get(chunk);if(value==null){if(labels.size()>=512)labels.clear();value=String.format(Locale.ROOT,"Chunk %d, %d | %.1f µs/t | %d targets",chunk.x(),chunk.z(),chunk.micros(report.ticks()),chunk.targets());labels.put(chunk,value);}return value;}
    private List<Report.Chunk> relevant(){return data.chunks.stream().filter(c->c.dimension().equals(dimension)&&(owner==null||c.targets()>0)).toList();}
    private void choose(String value){int x=selected==null?0:selected.x(),z=selected==null?0:selected.z();boolean had=selected!=null;owner=value;query="";data=value==null?allData:new MapTargets(report,value);selectingMod=false;rebuildData();selected=had?find(x,z):relevant().stream().findFirst().orElse(null);resetResults();rebuildWidgets();}
    private UiResults selector(int top){return CapturedModSelector.rows(this,top,mods,query,owner,this::choose,"All captured targets");}
    private void hottest(){selected=relevant().stream().max(Comparator.comparingLong(Report.Chunk::nanos)).orElse(null);recenter();rebuildWidgets();}
    @Override protected void init(){
        int y=profileNavigation==null?headerControlsTop():profileNavigation.mount(this,"Map")+4;
        var controls=new ProfileControls(this,y,null,false);
        var search=controls.search(query,value->{boolean wasSelecting=selectingMod;query=value;selectingMod=!value.isBlank();resetResults();if(selectingMod){var rows=selector(mapTop).entries;if(wasSelecting)replaceResults(rows);else results(mapTop,rows,true);}else clearResults();});
        search.setHint(net.minecraft.network.chat.Component.literal("Search mods"));UiHints.hint(search,"Search captured mods to filter the map.");
        var dimensions=allData.chunks.stream().map(Report.Chunk::dimension).distinct().sorted().map(d->ProfileControls.choice(d,d.substring(d.indexOf(':')+1))).toList();
        controls.dropdown("Dimension",134,dimensions,dimension,value->{dimension=value;selected=data.chunks.stream().filter(c->c.dimension().equals(dimension)).findFirst().orElse(null);recenter();rebuildWidgets();});
        var choices=new ArrayList<UiDropdown.Choice>();choices.add(ProfileControls.choice("","All mods"));mods.matching("").forEach(m->choices.add(ProfileControls.choice(m.id(),m.name())));
        controls.dropdown("Mod",150,choices,owner==null?"":owner,value->choose(value.isEmpty()?null:value));
        controls.action("Hottest",52,this::hottest);controls.action("Fit",32,this::fit);
        controls.toggle("World",64,LagSenseClient.settings.chunkHeatmap,()->{LagSenseClient.settings.chunkHeatmap=!LagSenseClient.settings.chunkHeatmap;LagSenseClient.settings.save();rebuildWidgets();});
        mapTop=controls.finish()+8;if(selectingMod)results(mapTop,selector(mapTop).entries,true);else clearResults();
        UiHints.hint(action("Copy",margin(),footer(),44,()->{if(ClientAccess.allowed(report))minecraft.keyboardHandler.setClipboard(Profiler.JSON.toJson(report));}),"Copy this report as JSON.");
        action("Export",margin()+48,footer(),48,()->minecraft.setScreen(new ExportScreen(this,report,archived)));
        if(archived){
            var destination=selectedDestination();
            if(destination!=null){var actions=TargetActions.actions(this,destination);int x=margin()+100;for(var item:actions){action(item.label(),x,footer(),item.label().equals("Mark")?48:36,item.run()).enabledWhen(item.enabled()).visibleWhen(item.visible()==null?()->true:item.visible());x+=item.label().equals("Mark")?52:40;}}
        }else action("Mark",margin()+100,footer(),48,()->{if(selected!=null&&selected.hotspot()!=null){var destination=selectedDestination();if(destination!=null){if(report.admin())LagSenseClient.mark(destination);else ClientAccess.request(new ProfilerRequest("mark_chunk",report.id(),destination.index()));}minecraft.setScreen(null);}}).active=selected!=null&&selected.hotspot()!=null;
        action("Close",width-margin()-54,footer(),54,this::onClose);
    }
    private ComparisonTargets.Destination selectedDestination(){
        if(selected==null||report.chunks()==null)return null;
        for(int i=0;i<report.chunks().size();i++){var source=report.chunks().get(i);if(source.dimension().equals(selected.dimension())&&source.x()==selected.x()&&source.z()==selected.z())return TargetActions.recorded(report,TargetActions.chunk(source),i,true);}
        return null;
    }
    private void zoom(int steps,double x,double y){var next=MapInteraction.zoom(viewport(),centerX,centerZ,cell,x,y,steps);centerX=next.x();centerZ=next.z();cell=next.cell();}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(dropdownOpen())return super.mouseScrolled(x,y,horizontal,vertical);
        if(!selectingMod&&viewport().contains(x,y)&&Double.isFinite(vertical)&&vertical!=0){if(zoomScroll*vertical<0)zoomScroll=0;zoomScroll=Math.max(-8,Math.min(8,zoomScroll+vertical));int steps=(int)zoomScroll;zoomScroll-=steps;if(steps!=0)zoom(steps,x,y);return true;}zoomScroll=0;return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){if(super.mouseClicked(e,twice))return true;if(!selectingMod&&e.button()==0&&viewport().contains(e.x(),e.y())){gesture.begin(e.x(),e.y());return true;}return false;}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(e.button()==0&&gesture.pressed()){var shift=gesture.drag(e.x(),e.y());centerX-=shift.x()/cell;centerZ-=shift.y()/cell;return true;}return super.mouseDragged(e,dx,dy);}
    @Override public boolean mouseReleased(MouseButtonEvent e){
        if(e.button()==0&&gesture.pressed()){boolean click=gesture.release();if(click&&viewport().contains(e.x(),e.y())){var hit=map().at(e.x(),e.y());selected=hit==null?null:find(hit.x(),hit.z());if(selected!=null){if(owner==null)Investigations.chunk(this,report,archived,dimension,selected.x(),selected.z());else minecraft.setScreen(new ChunkScreen(this,report,archived,data.chunk(dimension,selected.x(),selected.z())));}else rebuildWidgets();}return true;}return super.mouseReleased(e);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        if(profileNavigation!=null)profileNavigation.render(this,g,mx,my);else{canvas(g,"CHUNK HEATMAP");text(g,"North (−Z)",margin(),panelTop()+25,contentWidth(),UiTheme.SECONDARY);headerDivider(g);}
        if(!selectingMod){var view=map();var area=viewport();var hover=area.contains(mx,my)?view.at(mx,my):null;
            g.enableScissor(area.left(),area.top(),area.right(),area.bottom());paintMap(g,view);
            if(selected!=null&&selected.dimension().equals(dimension))outline(g,view,selected.x(),selected.z(),UiTheme.TEXT);
            if(hover!=null&&(selected==null||hover.x()!=selected.x()||hover.z()!=selected.z()))outline(g,view,hover.x(),hover.z(),UiTheme.SECONDARY);
            if(minecraft.player!=null&&minecraft.level!=null&&dimension.equals(minecraft.level.dimension().identifier().toString())){var position=minecraft.player.blockPosition();int rx=view.column(position.getX()>>4),rz=view.row(position.getZ()>>4);if(rx>=0&&rz>=0){int px=view.pixelX(rx)+cell/2,py=view.pixelY(rz)+cell/2;g.fill(px-2,py-2,px+2,py+2,UiTheme.BACKGROUND);g.fill(px-1,py-1,px+1,py+1,UiTheme.TEXT);}}
            g.disableScissor();if(hover!=null){var c=find(hover.x(),hover.z());UiHints.region(g,font,mx,my,view.pixelX(view.column(hover.x())),view.pixelY(view.row(hover.z())),cell,cell,c==null?"No received data.":label(c));}
            text(g,selected==null?"No chunk selected":label(selected),margin(),height-74,contentWidth(),UiTheme.TEXT);TimingLegend.paint(g,margin(),height-60,contentWidth());
        }super.extractRenderState(g,mx,my,partial);
    }
    private void fit(){var values=relevant();if(values.isEmpty())return;int minX=values.stream().mapToInt(Report.Chunk::x).min().orElse(0),maxX=values.stream().mapToInt(Report.Chunk::x).max().orElse(0),minZ=values.stream().mapToInt(Report.Chunk::z).min().orElse(0),maxZ=values.stream().mapToInt(Report.Chunk::z).max().orElse(0);centerX=((long)minX+maxX+1)/2.0;centerZ=((long)minZ+maxZ+1)/2.0;var area=viewport();cell=Math.max(2,Math.min(34,(int)Math.min(area.width()/Math.max(1L,(long)maxX-minX+3),area.height()/Math.max(1L,(long)maxZ-minZ+3))));zoomScroll=0;}
    private void outline(GuiGraphicsExtractor g,HeatmapPlan view,int x,int z,int color){int rx=view.column(x),rz=view.row(z);if(rx>=0&&rz>=0)UiTheme.border(g,view.pixelX(rx),view.pixelY(rz),cell-1,cell-1,color);}
    @Override public void onClose(){if(profileNavigation!=null)profileNavigation.close();else minecraft.setScreen(parent);}
    @Override public void removed(){gesture.cancel();releaseTexture();super.removed();}
}
