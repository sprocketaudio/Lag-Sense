package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.Report;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;

public final class ChunkOverlay {
    static final RenderPipeline PIPELINE=RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("lagsense","chunk_overlay"))
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS,false)).build();
    static final RenderType LINES=RenderType.create("lagsense_chunk_overlay",RenderSetup.builder(PIPELINE).createRenderSetup());
    static final Font.DisplayMode TEXT_MODE=Font.DisplayMode.SEE_THROUGH;
    private record Cell(Report.Chunk chunk,int color,Component label,int width) {}
    private record Caption(Cell cell,float scale) {}
    private record Frame(List<Cell> cells,List<Caption> captions,double y) {}
    private static final ContextKey<Frame> FRAME=new ContextKey<>(Identifier.fromNamespaceAndPath("lagsense","chunk_heatmap"));
    private final Map<String,it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Cell>> index=new HashMap<>();
    private final Map<Report.Chunk,Cell> textCache=new LinkedHashMap<>(16,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<Report.Chunk,Cell> e){return size()>64;}};
    private final audio.sprocket.lagsense.NearbyChunkLabels nearest=new audio.sprocket.lagsense.NearbyChunkLabels();
    private final org.joml.Matrix4f projectionView=new org.joml.Matrix4f();
    private final org.joml.Vector4f projected=new org.joml.Vector4f();
    private static Frame pending;
    private Report cached;
    private int fontGeneration=-1;
    public ChunkOverlay(){NeoForge.EVENT_BUS.addListener(this::extract);NeoForge.EVENT_BUS.addListener(ChunkOverlay::capture);NeoForge.EVENT_BUS.addListener(ChunkOverlay::render);}
    private static long key(int x,int z){return (long)x<<32|z&0xffffffffL;}
    private void extract(ExtractLevelRenderStateEvent event){
        var mc=Minecraft.getInstance();var settings=LagSenseClient.settings;var report=LagSenseClient.report;
        if(!VisualVisibility.visible()||!settings.enabled||!settings.chunkHeatmap||report==null||report.chunks()==null||mc.player==null||mc.options.hideGui||mc.screen!=null){
            event.getRenderState().setRenderData(FRAME,null);nearest.clear();if(report==null){cached=null;index.clear();textCache.clear();}return;
        }
        if(cached!=report){
            cached=report;index.clear();textCache.clear();nearest.clear();
            for(var chunk:report.chunks())index.computeIfAbsent(chunk.dimension(),d->new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>()).put(key(chunk.x(),chunk.z()),new Cell(chunk,audio.sprocket.lagsense.TimingSeverity.color(chunk.micros(report.ticks())),null,0));
        }
        if(fontGeneration!=UiFonts.generation()){fontGeneration=UiFonts.generation();textCache.clear();}
        String dimension=event.getLevel().dimension().identifier().toString();var camera=event.getCamera().position();var chunks=index.get(dimension);var cells=new ArrayList<Cell>();var candidates=new ArrayList<Report.Chunk>();
        if(chunks!=null){
            int left=(int)Math.floor((camera.x-settings.distance-8)/16),right=(int)Math.floor((camera.x+settings.distance-8)/16);
            int top=(int)Math.floor((camera.z-settings.distance-8)/16),bottom=(int)Math.floor((camera.z+settings.distance-8)/16);
            for(int x=left;x<=right;x++)for(int z=top;z<=bottom;z++){
                var cell=chunks.get(key(x,z));if(cell==null)continue;
                double dx=x*16.0+8-camera.x,dz=z*16.0+8-camera.z;
                if(dx*dx+dz*dz<=(double)settings.distance*settings.distance){cells.add(cell);candidates.add(cell.chunk());}
            }
        }
        var state=event.getRenderState().cameraRenderState;projectionView.set(state.projectionMatrix).mul(state.viewRotationMatrix);
        double y=Math.floor(mc.player.getY())+.04;var captions=new ArrayList<Caption>();
        for(var chunk:nearest.select(candidates,dimension,camera.x,camera.z)){
            projected.set((float)(chunk.x()*16.0+8-camera.x),(float)(y+2-camera.y),(float)(chunk.z()*16.0+8-camera.z),1);projectionView.transform(projected);
            if(projected.w<=.01||projected.z < -projected.w||projected.z>projected.w)continue;
            var cell=textCache.get(chunk);
            if(cell==null){var text=Component.literal(String.format(Locale.ROOT,"Chunk %d,%d | %.1f µs/t",chunk.x(),chunk.z(),chunk.micros(report.ticks()))).withStyle(s->s.withFont(UiFonts.MONO));cell=new Cell(chunk,audio.sprocket.lagsense.TimingSeverity.color(chunk.micros(report.ticks())),text,mc.font.width(text));textCache.put(chunk,cell);}
            captions.add(new Caption(cell,.025f));
        }
        event.getRenderState().setRenderData(FRAME,new Frame(List.copyOf(cells),List.copyOf(captions),y));
    }
    static void capture(SubmitCustomGeometryEvent event){pending=event.getLevelRenderState().getRenderData(FRAME);}
    private static void render(RenderLevelStageEvent.AfterLevel event){
        if(!VisualVisibility.visible()){pending=null;return;}
        if(pending!=null)WorldOverlayDraw.render(event,(stack,draw)->submit(event.getLevelRenderState(),stack,draw));
    }
    static void submit(net.minecraft.client.renderer.state.level.LevelRenderState state,com.mojang.blaze3d.vertex.PoseStack stack,WorldOverlayDraw collector){
        Frame frame=pending;pending=null;if(frame==null)return;
        var camera=state.cameraRenderState;
        if(!frame.cells().isEmpty())collector.submitCustomGeometry(stack,LINES,(pose,vertices)->{
            for(var cell:frame.cells()){
                float left=(float)(cell.chunk().x()*16.0-camera.pos.x),y=(float)(frame.y()-camera.pos.y),top=(float)(cell.chunk().z()*16.0-camera.pos.z);
                for(int offset=0;offset<=16;offset+=4)for(int axis=0;axis<2;axis++){
                    boolean alongX=axis==0;float x=left+(alongX?0:offset),z=top+(alongX?offset:0);
                    vertices.addVertex(pose,x,y,z).setColor(cell.color()).setNormal(pose,alongX?1:0,0,alongX?0:1).setLineWidth(2);
                    vertices.addVertex(pose,alongX?left+16:x,y,alongX?z:top+16).setColor(cell.color()).setNormal(pose,alongX?1:0,0,alongX?0:1).setLineWidth(2);
                }
            }
        });
        collector.flush();
        for(var caption:frame.captions()){
            var cell=caption.cell();stack.pushPose();
            try{
                stack.translate(cell.chunk().x()*16.0+8-camera.pos.x,frame.y()+2-camera.pos.y,cell.chunk().z()*16.0+8-camera.pos.z);
                stack.mulPose(camera.orientation);stack.scale(caption.scale(),-caption.scale(),caption.scale());
                collector.submitMarkerText(stack,-cell.width()/2f,0,cell.label().getVisualOrderText(),cell.color());
            }finally{stack.popPose();}
        }
    }
}
