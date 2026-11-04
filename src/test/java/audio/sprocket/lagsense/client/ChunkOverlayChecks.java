package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import com.mojang.blaze3d.vertex.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.gui.Font;
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
        var state=new LevelRenderState();var key=ChunkOverlay.class.getDeclaredField("FRAME");key.setAccessible(true);state.setRenderData((ContextKey)key.get(null),record("Frame",List.copyOf(cells),List.copyOf(captions),64.04));
        var stack=new PoseStack();var original=new org.joml.Matrix4f(stack.last().pose());var geometry=new ArrayList<SubmitNodeCollector.CustomGeometryRenderer>();var text=new ArrayList<Object[]>();
        var collector=(WorldOverlayDraw)Proxy.newProxyInstance(WorldOverlayDraw.class.getClassLoader(),new Class[]{WorldOverlayDraw.class},(proxy,method,args)->{
            if(method.getName().equals("submitCustomGeometry")){check(args[1]==ChunkOverlay.LINES,"Every highlighted chunk uses the through-walls render type");geometry.add((SubmitNodeCollector.CustomGeometryRenderer)args[2]);}
            if(method.getName().equals("submitMarkerText"))text.add(args.clone());
            if(method.getName().equals("submitText"))throw new AssertionError("Chunk labels must use the brightness-corrected target text path");return method.getReturnType()==void.class?null:proxy;
        });
        check(ChunkOverlay.class.getDeclaredMethod("render",net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterLevel.class)!=null,"Chunk overlays render after translucent-world composition");
        ChunkOverlay.capture(new net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent(state,null,stack,List.of()));state.reset();ChunkOverlay.submit(state,stack,collector);
        check(geometry.size()==1&&text.size()==3&&stack.last().pose().equals(original),"All 1,000 nearby floor grids submit in one batch while only three captions render, restoring transforms afterwards");
        for(var args:text)check(colours.contains((int)args[4])&&((int)args[4]>>>24)==255&&(WorldText.background((int)args[4])>>>24)==153&&((Number)args[1]).floatValue()==-40&&((Number)args[2]).floatValue()==0,"Nearest chunk labels use full-opacity corrected glyphs with dark backing and unchanged alignment");
        int[] vertices={0};var sink=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,args)->{
            if(method.getName().equals("addVertex"))vertices[0]++;if(method.getName().equals("setColor"))check(colours.contains((int)args[0]),"Chunk line colours remain unchanged");if(method.getName().equals("setLineWidth"))check(((Number)args[0]).floatValue()==2,"Chunk line width remains restrained");return method.getReturnType()==void.class?null:proxy;
        });for(var draw:geometry)draw.render(stack.last(),sink);check(vertices[0]==20000,"Existing five-by-five floor grid geometry is preserved for all 1,000 chunks, independently of the three captions");
        System.out.println("Chunk overlay checks passed (all-cell through-walls geometry/text submission, no depth writes, shared brightness-corrected backed text, unchanged colours/line width/grid and pose restoration; no GPU window).");
    }
}
