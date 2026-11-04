package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Through-wall outlines and timing captions only for the nearest intersected profiling cube. */
final class WorldMarkers {
    static final Font.DisplayMode TEXT_MODE=Font.DisplayMode.SEE_THROUGH;
    static final com.mojang.blaze3d.pipeline.RenderPipeline PIPELINE=com.mojang.blaze3d.pipeline.RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.LINES_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("lagsense","world_markers"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("lagsense","core/world_lines"))
        .withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS,false)).build();
    static final net.minecraft.client.renderer.rendertype.RenderType LINES=net.minecraft.client.renderer.rendertype.RenderType.create("lagsense_world_markers",net.minecraft.client.renderer.rendertype.RenderSetup.builder(PIPELINE).createRenderSetup());
    static final float LINE_WIDTH=2,FOCUS_SCALE=1.35f;
    private record Block(Report.Entry entry,MarkerPosition position,AABB box,int color) {}
    private record Label(Component text,int color,int width) {}
    private record Text(Label label,MarkerPosition position,float scale,int offset,boolean focused,int color) {}
    private record Frame(List<Block> outlines,List<Text> labels,long focusedPosition) {}
    private record LabelKey(Report.Entry entry,String prefix,int ticks) {}
    private static final ContextKey<Frame> FRAME=new ContextKey<>(Identifier.fromNamespaceAndPath("lagsense","world_markers"));
    private static final float[][] POINTS={{0,0,0},{1,0,0},{1,0,1},{0,0,1},{0,1,0},{1,1,0},{1,1,1},{0,1,1}};
    private static final int[][] EDGES={{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
    private final Map<LabelKey,Label> textCache=new LinkedHashMap<>(128,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<LabelKey,Label> e){return size()>128;}};
    private final MarkerLabels labelBounds=new MarkerLabels();
    private final Matrix4f projectionView=new Matrix4f();
    private final Vector4f projected=new Vector4f();
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet outlinePositions=new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private final BlockPos.MutableBlockPos blockPos=new BlockPos.MutableBlockPos();
    private final Map<Object,String> registryNames=new IdentityHashMap<>();
    private List<Block> outlines=List.of();
    private Frame pending;
    private Report report;
    private MarkerIndex index;
    private ClientLevel level;
    private long tick=Long.MIN_VALUE;
    private int range,fontGeneration=-1,cameraX,cameraY,cameraZ;
    private double minimum;
    void clear(){pending=null;report=null;index=null;level=null;tick=Long.MIN_VALUE;outlines=List.of();textCache.clear();registryNames.clear();EntityMarkers.clear();}
    void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel()==level)clear();}
    private <T>String registryName(net.minecraft.core.Registry<T> registry,T type){var name=registryNames.get(type);if(name==null){name=registry.getKey(type).toString();registryNames.put(type,name);}return name;}
    private MarkerEligibility.Block live(ClientLevel level,int x,int y,int z){
        if(!level.hasChunk(x>>4,z>>4))return new MarkerEligibility.Block(false,"","","");
        blockPos.set(x,y,z);var state=level.getBlockState(blockPos);var blockEntity=state.hasBlockEntity()?level.getBlockEntity(blockPos):null;
        return new MarkerEligibility.Block(true,registryName(BuiltInRegistries.BLOCK,state.getBlock()),blockEntity==null?"":registryName(BuiltInRegistries.BLOCK_ENTITY_TYPE,blockEntity.getType()),registryName(BuiltInRegistries.FLUID,state.getFluidState().getType()));
    }
    private void prepare(ClientLevel current,Report value,MarkerIndex markers,Vec3 camera){
        var s=LagSenseClient.settings;int x=(int)Math.floor(camera.x),y=(int)Math.floor(camera.y),z=(int)Math.floor(camera.z);
        if(report!=value||index!=markers||level!=current||fontGeneration!=UiFonts.generation()){
            clear();report=value;index=markers;level=current;fontGeneration=UiFonts.generation();
        }
        if(tick==current.getGameTime()&&range==s.distance&&minimum==s.minimumMicros&&cameraX==x&&cameraY==y&&cameraZ==z)return;
        tick=current.getGameTime();range=s.distance;minimum=s.minimumMicros;cameraX=x;cameraY=y;cameraZ=z;
        var boxes=new Long2ObjectOpenHashMap<Block>();var identities=new Long2ObjectOpenHashMap<MarkerEligibility.Block>();String dimension=current.dimension().identifier().toString();
        for(var entry:markers.near(dimension,camera.x,camera.y,camera.z,s.distance+2,s.minimumMicros)){
            if(!MarkerEligibility.timing(entry,value.ticks(),s.minimumMicros))continue;long key=BlockPos.asLong(entry.x(),entry.y(),entry.z());
            var identity=identities.get(key);if(identity==null){identity=live(current,entry.x(),entry.y(),entry.z());identities.put(key,identity);}if(!MarkerEligibility.matches(entry,identity))continue;
            if(boxes.containsKey(key))continue;
            var block=new Block(entry,new MarkerPosition(entry.x(),entry.y(),entry.z(),1.3),new AABB(entry.x(),entry.y(),entry.z(),entry.x()+1,entry.y()+1,entry.z()+1),TimingSeverity.color(entry.micros(value.ticks())));
            boxes.putIfAbsent(key,block);
        }
        // Cost order is stable within a capture. Multiple measured categories share one thin position outline.
        outlines=List.copyOf(boxes.values());
    }
    void extract(ExtractLevelRenderStateEvent event){
        var mc=Minecraft.getInstance();var s=LagSenseClient.settings;var value=LagSenseClient.markerReport();var markers=LagSenseClient.markerData();
        if(!VisualVisibility.visible()||!s.enabled||!s.worldMarkers||value==null||markers==null||mc.options.hideGui||mc.screen!=null){event.getRenderState().setRenderData(FRAME,null);if(value==null)clear();return;}
        Vec3 camera=event.getCamera().position();String dimension=event.getLevel().dimension().identifier().toString();prepare(event.getLevel(),value,markers,camera);
        var cameraState=event.getRenderState().cameraRenderState;projectionView.set(cameraState.projectionMatrix).mul(cameraState.viewRotationMatrix);
        int height=mc.getWindow().getGuiScaledHeight();labelBounds.begin(mc.getWindow().getGuiScaledWidth(),height);
        var labels=new ArrayList<Text>();var visibleOutlines=new ArrayList<Block>();outlinePositions.clear();
        var marked=LagSenseClient.waypoint;
        if(marked!=null&&!marked.kind().equals("Entity")&&marked.dimension().equals(dimension)
            &&(LagSenseClient.waypointTicks>0||MarkerEligibility.matches(marked,live(event.getLevel(),marked.x(),marked.y(),marked.z()))))
            addOutline(visibleOutlines,marked,new MarkerPosition(marked.x(),marked.y(),marked.z(),1.3),camera,cameraState.cullFrustum);
        for(var block:outlines)if(MarkerEligibility.inRange(block.position(),camera.x,camera.y,camera.z,s.distance)&&cameraState.cullFrustum.isVisible(block.box())&&outlinePositions.add(BlockPos.asLong(block.entry().x(),block.entry().y(),block.entry().z())))visibleOutlines.add(block);
        // This list already contains only the current authorised, displayed cubes. No report-wide scan or vanilla hit result.
        var focused=focus(visibleOutlines,camera,new Vec3(event.getCamera().forwardVector()));
        long focusedPosition=Long.MIN_VALUE;
        if(focused!=null){
            var entry=focused.entry();focusedPosition=BlockPos.asLong(entry.x(),entry.y(),entry.z());
            boolean markedHere=marked!=null&&!marked.kind().equals("Entity")&&marked.dimension().equals(dimension)&&marked.x()==entry.x()&&marked.y()==entry.y()&&marked.z()==entry.z();
            if(markedHere)add(labels,marked,focused.position(),"> ",0,camera,cameraState.projectionMatrix,height);
            else{
                var exact=MarkerInspection.block(markers,dimension,entry.x(),entry.y(),entry.z(),live(event.getLevel(),entry.x(),entry.y(),entry.z()),value.ticks(),s.minimumMicros);int offset=0;
                for(var measured:exact){add(labels,measured,focused.position(),exact.size()>1?measured.kind()+": ":"",offset,camera,cameraState.projectionMatrix,height);offset+=10;}
            }
        }
        event.getRenderState().setRenderData(FRAME,new Frame(List.copyOf(visibleOutlines),List.copyOf(labels),focusedPosition));
    }
    private static Block focus(List<Block> eligible,Vec3 origin,Vec3 direction){
        var ray=new MarkerRay(origin,direction);Block best=null;double nearest=Double.POSITIVE_INFINITY;
        for(var block:eligible){double distance=ray.intersection(block.box());
            if(distance<nearest||distance==nearest&&best!=null&&BlockPos.asLong(block.entry().x(),block.entry().y(),block.entry().z())<BlockPos.asLong(best.entry().x(),best.entry().y(),best.entry().z())){nearest=distance;best=block;}
        }
        return best;
    }
    private int markedTicks(Report.Entry entry){return entry==LagSenseClient.waypoint&&LagSenseClient.waypointTicks>0?LagSenseClient.waypointTicks:report.ticks();}
    private void addOutline(List<Block> output,Report.Entry entry,MarkerPosition position,Vec3 camera,net.minecraft.client.renderer.culling.Frustum frustum){
        if(!MarkerEligibility.inRange(position,camera.x,camera.y,camera.z,LagSenseClient.settings.distance))return;
        var box=new AABB(entry.x(),entry.y(),entry.z(),entry.x()+1,entry.y()+1,entry.z()+1);
        if(frustum.isVisible(box)&&outlinePositions.add(BlockPos.asLong(entry.x(),entry.y(),entry.z())))output.add(new Block(entry,position,box,TimingSeverity.color(entry.micros(markedTicks(entry)))));
    }
    private void add(List<Text> output,Report.Entry entry,MarkerPosition position,String prefix,int offset,Vec3 camera,Matrix4f projection,int height){
        int ticks=prefix.startsWith("> ")&&LagSenseClient.waypointTicks>0?LagSenseClient.waypointTicks:report.ticks();
        var s=LagSenseClient.settings;if(!MarkerEligibility.timing(entry,ticks,prefix.startsWith("> ")?0:s.minimumMicros)||!MarkerEligibility.inRange(position,camera.x,camera.y,camera.z,s.distance))return;
        projected.set((float)(position.x()+.5-camera.x),(float)(position.y()+position.labelHeight()-camera.y),(float)(position.z()+.5-camera.z),1);projectionView.transform(projected);
        if(projected.w<=.01||projected.z < -projected.w||projected.z>projected.w)return;
        double pixelsPerUnit=Math.abs(projection.m11())*height/(2*projected.w);float scale=(float)Math.min(.025,12/(9*Math.max(.001,pixelsPerUnit)));
        scale*=FOCUS_SCALE;
        double cost=entry.micros(ticks);
        var key=new LabelKey(entry,prefix,ticks);var label=textCache.get(key);
        if(label==null){var text=Component.literal(String.format(Locale.ROOT,"%s%.1f µs/t",prefix,cost)).withStyle(style->style.withFont(UiFonts.MONO));label=new Label(text,TimingSeverity.color(cost),Minecraft.getInstance().font.width(text));textCache.put(key,label);}
        double pixels=pixelsPerUnit*scale;
        double px=(projected.x/projected.w+1)*Minecraft.getInstance().getWindow().getGuiScaledWidth()/2.0-label.width()*pixels/2;
        double py=(1-projected.y/projected.w)*height/2.0+offset*pixels;
        if(!labelBounds.offer(px,py,label.width()*pixels,9*pixels,true))return;
        output.add(new Text(label,position,scale,offset,true,label.color()));
    }
    // LevelRenderState resets custom data before AfterLevel, so retain this frame at submission time.
    void capture(SubmitCustomGeometryEvent event){pending=event.getLevelRenderState().getRenderData(FRAME);}
    void render(RenderLevelStageEvent.AfterLevel event){
        if(!VisualVisibility.visible()){pending=null;return;}
        if(pending!=null)WorldOverlayDraw.render(event,(stack,draw)->submit(event.getLevelRenderState(),stack,draw));
    }
    static int outlineColor(int color,boolean focused,double distance,double range){
        if(focused)return color;
        double t=Math.clamp(distance/Math.max(1,range),0,1);
        double opacity=.22-(.22-.035)*t*t*(3-2*t);
        return (Math.max(1,(int)Math.round((color>>>24)*opacity))<<24)|(color&0xffffff);
    }
    void submit(net.minecraft.client.renderer.state.level.LevelRenderState state,PoseStack stack,WorldOverlayDraw collector){
        var frame=pending;pending=null;if(frame==null)return;var camera=state.cameraRenderState;
        if(!frame.outlines().isEmpty())collector.submitCustomGeometry(stack,LINES,(pose,vertices)->{
            for(var block:frame.outlines()){
                var e=block.entry();float x=(float)(e.x()-camera.pos.x),y=(float)(e.y()-camera.pos.y),z=(float)(e.z()-camera.pos.z);boolean focused=BlockPos.asLong(e.x(),e.y(),e.z())==frame.focusedPosition();
                double distance=Math.sqrt((x+.5)*(x+.5)+(y+.5)*(y+.5)+(z+.5)*(z+.5));
                int color=outlineColor(block.color(),focused,distance,LagSenseClient.settings.distance);float lineWidth=focused?3:LINE_WIDTH;
                for(var edge:EDGES){var a=POINTS[edge[0]];var b=POINTS[edge[1]];
                    vertices.addVertex(pose,x+a[0],y+a[1],z+a[2]).setColor(color).setNormal(pose,b[0]-a[0],b[1]-a[1],b[2]-a[2]).setLineWidth(lineWidth);
                    vertices.addVertex(pose,x+b[0],y+b[1],z+b[2]).setColor(color).setNormal(pose,b[0]-a[0],b[1]-a[1],b[2]-a[2]).setLineWidth(lineWidth);
                }
            }
        });
        collector.flush();
        boolean focusPass=false;
        for(var placed:frame.labels()){
            if(!placed.focused())continue;
            if(placed.focused()&&!focusPass){collector.flush();focusPass=true;}
            var p=placed.position();var label=placed.label();stack.pushPose();
            try{stack.translate(p.x()+.5-camera.pos.x,p.y()+p.labelHeight()-camera.pos.y,p.z()+.5-camera.pos.z);stack.mulPose(camera.orientation);stack.scale(placed.scale(),-placed.scale(),placed.scale());collector.submitMarkerText(stack,-label.width()/2f,placed.offset(),label.text().getVisualOrderText(),placed.color());}
            finally{stack.popPose();}
        }
    }
}
