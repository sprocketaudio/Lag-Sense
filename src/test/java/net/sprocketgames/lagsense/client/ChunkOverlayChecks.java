package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import com.mojang.blaze3d.vertex.*;
import java.lang.reflect.*;
import java.util.*;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextKey;

final class ChunkOverlayChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Object record(String name,Object...args)throws Exception{var type=Arrays.stream(ChunkOverlay.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals(name)).findFirst().orElseThrow();var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);return ctor.newInstance(args);}
    @SuppressWarnings({"unchecked","rawtypes"}) static void run()throws Exception{
        var depth=ChunkOverlay.PIPELINE.getDepthStencilState();check(depth!=null&&depth.depthTest()==com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS&&!depth.writeDepth(),"Chunk grids always pass depth and never occlude other overlays by writing depth");
        check(ChunkOverlay.PIPELINE.getColorTargetState().equals(net.minecraft.client.renderer.RenderPipelines.LINES.getColorTargetState())&&ChunkOverlay.PIPELINE.getVertexFormat()==net.minecraft.client.renderer.RenderPipelines.LINES.getVertexFormat(),"Native line blending and vertex format are preserved");
        var cells=new ArrayList<Object>();var colours=new HashSet<Integer>();
        for(int i=0;i<1000;i++){var entry=new Report.Entry("Block","fixture:machine","minecraft:overworld",i*16,64,0,1000,1);int colour=TimingSeverity.color(i*100);colours.add(colour);cells.add(record("Cell",new Report.Chunk(entry.dimension(),i,0,1000,1,entry),colour,Component.literal("Chunk "+i+",0 | 1.0 µs/t"),80));}
        var captions=new ArrayList<Object>();for(var cell:cells.subList(0,3))captions.add(record("Caption",cell,.025f));
        var state=new LevelRenderState();var key=ChunkOverlay.class.getDeclaredField("FRAME");key.setAccessible(true);state.setRenderData((ContextKey)key.get(null),record("Frame",List.copyOf(cells),List.copyOf(captions),52.04,61.0,null));
        var stack=new PoseStack();var original=new org.joml.Matrix4f(stack.last().pose());var geometry=new ArrayList<SubmitNodeCollector.CustomGeometryRenderer>();var text=new ArrayList<Object[]>();var labelHeights=new ArrayList<Float>();
        var collector=(WorldOverlayDraw)Proxy.newProxyInstance(WorldOverlayDraw.class.getClassLoader(),new Class[]{WorldOverlayDraw.class},(proxy,method,args)->{
            if(method.getName().equals("submitCustomGeometry")){check(args[1]==ChunkOverlay.LINES,"Every highlighted chunk uses the through-walls render type");geometry.add((SubmitNodeCollector.CustomGeometryRenderer)args[2]);}
            if(method.getName().equals("submitMarkerText")){text.add(args.clone());labelHeights.add(((PoseStack)args[0]).last().pose().m31());}
            if(method.getName().equals("submitText"))throw new AssertionError("Chunk labels must use the brightness-corrected target text path");return method.getReturnType()==void.class?null:proxy;
        });
        check(ChunkOverlay.class.getDeclaredMethod("render",net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterLevel.class)!=null,"Chunk overlays render after translucent-world composition");
        ChunkOverlay.capture(new net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent(state,null,stack,List.of()));state.reset();ChunkOverlay.submit(state,stack,collector);
        check(geometry.size()==1&&text.size()==3&&stack.last().pose().equals(original),"All 1,000 nearby floor grids submit in one batch while only three captions render, restoring transforms afterwards");
        check(labelHeights.stream().allMatch(y->Math.abs(y-61)<.001),"Chunk captions are placed three blocks below feet independently of the lower floor grid");
        for(var args:text)check(colours.stream().anyMatch(c->(c&0xffffff)==((int)args[4]&0xffffff))&&((int)args[4]>>>24)>0&&(WorldText.background((int)args[4])>>>24)>0&&((Number)args[1]).floatValue()==-40&&((Number)args[2]).floatValue()==0,"Nearest chunk labels use brightness-corrected faded glyphs with dark backing and unchanged alignment");
        int[] vertices={0};var sink=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,args)->{
            if(method.getName().equals("addVertex")){vertices[0]++;check(Math.abs(((Number)args[2]).floatValue()-52.04f)<.001,"Every floor-grid vertex stays twelve blocks below feet independently of caption height");}if(method.getName().equals("setColor"))check(colours.stream().anyMatch(c->(c&0xffffff)==((int)args[0]&0xffffff)),"Chunk fade preserves timing RGB");if(method.getName().equals("setLineWidth"))check(((Number)args[0]).floatValue()==2,"Chunk line width remains restrained");return method.getReturnType()==void.class?null:proxy;
        });for(var draw:geometry)draw.render(stack.last(),sink);check(vertices[0]==20000,"Existing five-by-five floor grid geometry is preserved for all 1,000 chunks, independently of the three captions");
        boolean savedHeatmap=LagSenseClient.settings.chunkHeatmap;LagSenseClient.settings.chunkHeatmap=false;
        try{
            var marked=cells.getFirst();geometry.clear();text.clear();
            state.setRenderData((ContextKey)key.get(null),record("Frame",List.of(),List.of(record("Caption",marked,.025f)),52.04,61.0,marked));
            ChunkOverlay.capture(new net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent(state,null,stack,List.of()));ChunkOverlay.submit(state,stack,collector);
            check(geometry.size()==1&&text.size()==1,"A marked chunk renders its own outline and label with no general heatmap cells");
            int[] edges={0};var markSink=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,args)->{
                if(method.getName().equals("addVertex"))edges[0]++;
                if(method.getName().equals("setColor"))check((int)args[0]==WorldMarkers.MARK_COLOR,"Marked chunks share the bright-blue block/entity mark colour");
                if(method.getName().equals("setLineWidth"))check(((Number)args[0]).floatValue()==3,"A marked chunk is stronger than the general grid");
                return method.getReturnType()==void.class?null:proxy;
            });geometry.getFirst().render(stack.last(),markSink);check(edges[0]==24,"The marked chunk draws its full sixteen-block perimeter and four corner posts");
            geometry.clear();text.clear();state.setRenderData((ContextKey)key.get(null),record("Frame",List.of(),List.of(),52.04,61.0,null));
            ChunkOverlay.capture(new net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent(state,null,stack,List.of()));ChunkOverlay.submit(state,stack,collector);
            check(geometry.isEmpty()&&text.isEmpty(),"Unmark removes the chunk boundary and caption when the general heatmap is off");
        }finally{LagSenseClient.settings.chunkHeatmap=savedHeatmap;}
        var overlay=new ChunkOverlay();
        var cached=ChunkOverlay.class.getDeclaredField("cached");cached.setAccessible(true);
        var retained=new Report(17,17000,100,2,true,false,List.of(new Report.Entry("Block","fixture:retained","minecraft:overworld",0,64,0,1,1)));
        cached.set(overlay,retained);
        var index=ChunkOverlay.class.getDeclaredField("index");index.setAccessible(true);((Map)index.get(overlay)).put("minecraft:overworld",new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>());
        var cache=ChunkOverlay.class.getDeclaredField("textCache");cache.setAccessible(true);((Map)cache.get(overlay)).put(retained,cells.getFirst());
        var pending=ChunkOverlay.class.getDeclaredField("pending");pending.setAccessible(true);pending.set(null,record("Frame",List.copyOf(cells),List.copyOf(captions),52.04,61.0,null));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.start();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut(null,null,null));
        check(cached.get(overlay)==null&&((Map)index.get(overlay)).isEmpty()&&((Map)cache.get(overlay)).isEmpty()&&pending.get(null)==null,"Disconnect releases the previous capture, chunk indexes, caption cache and pending geometry without another world frame");
        var capture=ClientFrames.class.getDeclaredField("capture");capture.setAccessible(true);capture.set(null,new FrameProfile.Capture());ClientFrames.active=true;var previousFrames=ClientFrames.latest;var completedFrames=new FrameProfile.Capture().finish();ClientFrames.latest=completedFrames;
        ClientFrames.clear();check(!ClientFrames.active&&ClientFrames.latest==completedFrames&&capture.get(null)==null,"Disconnect cancels active frame capture without changing completed results or opening a screen");ClientFrames.latest=previousFrames;
        var readerField=Investigations.class.getDeclaredField("READER");readerField.setAccessible(true);var reader=(java.util.concurrent.ThreadPoolExecutor)readerField.get(null);
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        reader.execute(()->{entered.countDown();try{release.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        try{check(entered.await(5,java.util.concurrent.TimeUnit.SECONDS),"Investigation reader starts its queue barrier");reader.execute(()->{});Investigations.clear();check(reader.getQueue().isEmpty(),"Disconnect/access revocation discards queued decoders and their retained screens and payloads");}finally{release.countDown();}
        reader.submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);
        var busyField=ExportScreen.class.getDeclaredField("BUSY");busyField.setAccessible(true);var busy=(java.util.concurrent.atomic.AtomicBoolean)busyField.get(null);
        var waiting=ExportScreen.class.getDeclaredField("waitingForCpu");waiting.setAccessible(true);
        busy.set(true);waiting.set(null,true);Investigations.clear();check(!busy.get()&&!(boolean)waiting.get(null),"Cancelling saved CPU loading releases the export lock for the next connection");
        busy.set(true);waiting.set(null,false);Investigations.clear();check(busy.get(),"Disconnect cannot release the lock of an export already being written");busy.set(false);
        var world=new WorldMarkers();var worldReport=WorldMarkers.class.getDeclaredField("report");worldReport.setAccessible(true);worldReport.set(world,retained);
        world.unload(new net.neoforged.neoforge.event.level.LevelEvent.Unload(null));
        check(worldReport.get(world)==null,"The level-unload handler releases its cached capture");
        EntityMarkers.register();var entityIndex=EntityMarkers.class.getDeclaredField("index");entityIndex.setAccessible(true);entityIndex.set(null,new MarkerIndex(retained));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.LevelEvent.Unload(null));
        check(entityIndex.get(null)==null,"The level-unload event releases entity marker indexes");
        System.out.println("Chunk overlay checks passed (all-cell through-walls geometry/text submission, no depth writes, shared brightness-corrected backed text, unchanged colours/line width/grid and pose restoration; no GPU window).");
    }
}
