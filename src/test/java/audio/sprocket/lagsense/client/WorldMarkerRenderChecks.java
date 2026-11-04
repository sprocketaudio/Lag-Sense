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
import net.minecraft.world.phys.AABB;

/** Exercise the real submission method with native pose stacks and recording collectors, without a GPU/window. */
final class WorldMarkerRenderChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Class<?> nested(String name){return Arrays.stream(WorldMarkers.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals(name)).findFirst().orElseThrow();}
    private static Object record(Class<?> type,Object...values)throws Exception{var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);return constructor.newInstance(values);}
    @SuppressWarnings({"unchecked","rawtypes"}) static void run()throws Exception{
        check(WorldMarkers.class.getDeclaredMethod("render",net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterLevel.class)!=null,"Target overlays render after the world/translucency pass, preserving label brightness");
        var overlay=new WorldMarkers();var state=new LevelRenderState();var stack=new PoseStack();var before=new org.joml.Matrix4f(stack.last().pose());
        var blocks=new ArrayList<Object>();
        for(int i=0;i<1000;i++){
            var e=new Report.Entry("Block","fixture:machine","minecraft:overworld",i%40,64,i/40,i,1);var position=new MarkerPosition(e.x(),e.y(),e.z(),1.3);
            blocks.add(record(nested("Block"),e,position,new AABB(e.x(),e.y(),e.z(),e.x()+1,e.y()+1,e.z()+1),TimingSeverity.color(e.micros(100))));
        }
        var entry=new Report.Entry("Entity","minecraft:item","minecraft:overworld",0,0,0,1000,1,UUID.randomUUID());
        var label=record(nested("Label"),Component.literal("0.0 µs/t").withStyle(s->s.withFont(UiFonts.MONO)),TimingSeverity.color(entry.micros(100)),48);
        var text=record(nested("Text"),label,new MarkerPosition(3,65,4,1.3),.025f,0,true,TimingSeverity.color(.01));var frame=record(nested("Frame"),List.copyOf(blocks),List.of(text),Long.MIN_VALUE);
        var field=WorldMarkers.class.getDeclaredField("FRAME");field.setAccessible(true);ContextKey key=(ContextKey)field.get(null);state.setRenderData(key,frame);
        var textCalls=new ArrayList<Object[]>();var geometry=new ArrayList<SubmitNodeCollector.CustomGeometryRenderer>();
        var collector=(WorldOverlayDraw)Proxy.newProxyInstance(WorldOverlayDraw.class.getClassLoader(),new Class[]{WorldOverlayDraw.class},(proxy,method,args)->{
            if(method.getName().equals("submitMarkerText"))textCalls.add(args.clone());
            if(method.getName().equals("submitCustomGeometry")){check(args[1]==WorldMarkers.LINES,"Every target outline uses the through-walls pipeline without changing its colour");geometry.add((SubmitNodeCollector.CustomGeometryRenderer)args[2]);}
            return method.getReturnType()==void.class?null:proxy;
        });
        overlay.capture(new net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent(state,null,stack,List.of()));state.reset();overlay.submit(state,stack,collector);
        check(geometry.size()==1,"All 1,000 outline boxes submit in one batch independently of a single visible label");
        var depth=WorldText.INTENSITY.getDepthStencilState();
        check(textCalls.size()==1&&depth!=null&&depth.depthTest()==com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS&&!depth.writeDepth(),"Nearby and focused native glyphs pass world depth without writing depth");
        backgroundChecks();
        check(((int)textCalls.getFirst()[4]>>>24)==255&&(int)textCalls.getFirst()[4]==TimingSeverity.color(.01)&&stack.last().pose().equals(before),"Original gradient colours and pose-stack state survive marker billboard submission");
        int[] vertices={0},colours={0},widths={0};
        var vertex=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,args)->{
            if(method.getName().equals("addVertex"))vertices[0]++;
            if(method.getName().equals("setColor")){colours[0]++;int alpha=(int)args[0]>>>24;check(alpha>0&&alpha<=57,"All ordinary outlines remain present at subdued opacity");}
            if(method.getName().equals("setLineWidth")){widths[0]++;check(((Number)args[0]).floatValue()==2,"Thin existing outline width remains two");}
            return method.getReturnType()==void.class?null:proxy;
        });
        geometry.getFirst().render(stack.last(),vertex);check(vertices[0]==24000&&colours[0]==24000&&widths[0]==24000,"Every retained outline receives its original twelve thin coloured edges without a top-N cap");
        state.setRenderData(key,record(nested("Frame"),List.of(),List.of(text),Long.MIN_VALUE));textCalls.clear();geometry.clear();overlay.capture(new net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent(state,null,stack,List.of()));overlay.submit(state,stack,collector);
        check(textCalls.size()==1&&geometry.isEmpty(),"Moving entity labels have no block-outline geometry");
        var nearText=record(nested("Text"),label,new MarkerPosition(3,65,4,1.3),.025f,0,false,0xad000000|(TimingSeverity.color(.01)&0xffffff));
        var focusText=record(nested("Text"),label,new MarkerPosition(3,65,4,1.3),.025f*WorldMarkers.FOCUS_SCALE,0,true,TimingSeverity.color(.01));
        var passes=new ArrayList<String>();var scales=new ArrayList<Float>();var coloursInOrder=new ArrayList<Integer>();geometry.clear();
        var layered=(WorldOverlayDraw)Proxy.newProxyInstance(WorldOverlayDraw.class.getClassLoader(),new Class[]{WorldOverlayDraw.class},(proxy,method,args)->{
            if(method.getName().equals("flush"))passes.add("flush");
            if(method.getName().equals("submitCustomGeometry"))geometry.add((SubmitNodeCollector.CustomGeometryRenderer)args[2]);
            if(method.getName().equals("submitMarkerText")){passes.add("text");scales.add(((PoseStack)args[0]).last().pose().m00());coloursInOrder.add((int)args[4]);}
            return null;
        });
        state.setRenderData(key,record(nested("Frame"),List.copyOf(blocks),List.of(nearText,focusText),net.minecraft.core.BlockPos.asLong(0,64,0)));
        overlay.capture(new net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent(state,null,stack,List.of()));overlay.submit(state,stack,layered);
        check(passes.equals(List.of("flush","flush","text")),"Only the focused caption is drawn, after all ordinary outlines");
        check(coloursInOrder.size()==1&&(coloursInOrder.getFirst()>>>24)==255&&Math.abs(scales.getFirst()-.025f*WorldMarkers.FOCUS_SCALE)<.0001,"Unfocused timing text is suppressed while focus keeps its existing opacity and scale");
        int[] focusedEdges={0},ordinaryEdges={0},focusedColours={0},ordinaryColours={0};
        var focusSink=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,args)->{
            if(method.getName().equals("setColor")){int color=(int)args[0];if((color>>>24)==255)focusedColours[0]++;else{ordinaryColours[0]++;check((color>>>24)>0&&(color>>>24)<=57,"Unfocused geometry stays faint without dropping any edge");}}
            if(method.getName().equals("setLineWidth")){float w=((Number)args[0]).floatValue();if(w==3)focusedEdges[0]++;else if(w==2)ordinaryEdges[0]++;else throw new AssertionError("Unexpected outline width");}
            return method.getReturnType()==void.class?null:proxy;
        });geometry.getFirst().render(stack.last(),focusSink);
        check(focusedEdges[0]==24&&ordinaryEdges[0]==23976&&focusedColours[0]==24&&ordinaryColours[0]==23976,"Only the aimed-at machine has a thicker outline and all other outlines remain present");
        check(WorldMarkers.PIPELINE.getDepthStencilState().depthTest()==com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS&&!WorldMarkers.PIPELINE.getDepthStencilState().writeDepth(),"Emphasised outlines still pass world depth without modifying it");
        var throwing=(WorldOverlayDraw)Proxy.newProxyInstance(WorldOverlayDraw.class.getClassLoader(),new Class[]{WorldOverlayDraw.class},(proxy,method,args)->{if(method.getName().equals("submitMarkerText"))throw new IllegalStateException("Fixture renderer failure");return null;});
        state.setRenderData(key,record(nested("Frame"),List.of(),List.of(text),Long.MIN_VALUE));
        try{overlay.capture(new net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent(state,null,stack,List.of()));overlay.submit(state,stack,throwing);throw new AssertionError("Expected fixture renderer failure");}catch(IllegalStateException expected){check(stack.last().pose().equals(before),"Billboard state is restored even if text submission fails");}
        state.setRenderData(key,null);textCalls.clear();overlay.capture(new net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent(state,null,stack,List.of()));overlay.submit(state,stack,collector);check(textCalls.isEmpty(),"Hidden/cleared marker frames submit no stale text");
        for(int i=0;i<=1000;i++){int base=TimingSeverity.color(12),color=WorldMarkers.outlineColor(base,false,i/10.0,100);check((color&0xffffff)==(base&0xffffff),"Fading preserves exact timing RGB");if(i>0)check((color>>>24)<=(WorldMarkers.outlineColor(base,false,(i-1)/10.0,100)>>>24),"Outline fade is monotonic");check(WorldMarkers.outlineColor(base,true,i/10.0,100)==base,"Focus stays unchanged at every distance");}
        selectionChecks(blocks,overlay,state,stack,collector,key,nearText,textCalls);
        System.out.println("World marker rendering checks passed (native coverage-corrected through-wall submission, 1,000 batched subdued outlines, nearest through-wall cube selection, focus-only backed labels and pose restoration).");
    }
    private static void backgroundChecks(){
        int full=TimingSeverity.color(12),faint=(Math.round(.24f*255)<<24)|(full&0xffffff);
        check((WorldText.background(full)>>>24)==153&&(WorldText.background(faint)>>>24)<40,"Dark backing has 60 percent opacity for focus and follows faint nearby labels");
        check((WorldText.background(full)&0xffffff)==0x08080b,"Backing is near black rather than opaque grey");
        var glyph=new net.minecraft.client.gui.font.glyphs.BakedSheetGlyph(com.mojang.blaze3d.font.GlyphInfo.simple(5),null,null,0,1,0,1,0,5,0,8);
        var source=new net.minecraft.client.gui.GlyphSource(){
            @Override public net.minecraft.client.gui.font.glyphs.BakedGlyph getGlyph(int codepoint){return glyph;}
            @Override public net.minecraft.client.gui.font.glyphs.BakedGlyph getRandomGlyph(net.minecraft.util.RandomSource random,int width){return glyph;}
        };
        var font=new Font(new Font.Provider(){
            @Override public net.minecraft.client.gui.GlyphSource glyphs(net.minecraft.network.chat.FontDescription face){return source;}
            @Override public net.minecraft.client.gui.font.glyphs.EffectGlyph effect(){return glyph;}
        });var text=Component.literal("12.3 µs/t").withStyle(s->s.withFont(UiFonts.MONO)).getVisualOrderText();
        var effects=new ArrayList<net.minecraft.client.gui.font.TextRenderable>();int[] glyphs={0};
        WorldText.prepare(font,text,-24,10,full).visit(new Font.GlyphVisitor(){
            @Override public void acceptGlyph(net.minecraft.client.gui.font.TextRenderable.Styled glyph){glyphs[0]++;}
            @Override public void acceptEffect(net.minecraft.client.gui.font.TextRenderable effect){effects.add(effect);}
        });
        check(effects.size()==1&&glyphs[0]>0,"The production custom text preparation emits a native background effect as well as glyphs");
        var effect=effects.getFirst();check(Math.abs(effect.left()+25)<.01&&Math.abs(effect.top()-9)<.01&&Math.abs(effect.bottom()-19)<.01&&Math.abs(effect.right()-(-24+font.width(text)))<1.01,"Native backing follows the label origin and measured text bounds with one-pixel padding");
        int[] vertices={0};var sink=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,args)->{
            if(method.getName().equals("addVertex"))vertices[0]++;
            if(method.getName().equals("setColor"))check((int)args[0]==WorldText.background(full),"Native effect vertices carry the dark translucent backing colour");
            return method.getReturnType()==void.class?null:proxy;
        });effect.render(new org.joml.Matrix4f(),sink,15728880,false);check(vertices[0]==4,"Each label adds just one compact native background quad");
        var depth=WorldText.COLOR.getDepthStencilState();check(depth.depthTest()==com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS&&!depth.writeDepth(),"Background effects use the custom RGBA text pipeline through walls without writing depth");
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static void selectionChecks(List<Object> dense,WorldMarkers overlay,LevelRenderState state,PoseStack stack,WorldOverlayDraw collector,ContextKey key,Object nearText,List<Object[]> textCalls)throws Exception{
        var select=WorldMarkers.class.getDeclaredMethod("focus",List.class,net.minecraft.world.phys.Vec3.class,net.minecraft.world.phys.Vec3.class);select.setAccessible(true);
        var origin=net.minecraft.world.phys.Vec3.ZERO;var direction=new net.minecraft.world.phys.Vec3(0,0,1);
        var first=block(0,0,3,new AABB(-.5,-.5,3,.5,.5,8));var closerCentre=block(0,0,4,new AABB(-.5,-.5,4,.5,.5,4.1));
        check(select.invoke(null,List.of(closerCentre,first),origin,direction)==first,"Nearest intersection wins even when another bounding-box centre is closer");
        // Ordinary solid terrain cannot occlude this selection: only displayed profiler boxes enter the selector.
        var terrain=new AABB(-1,-1,1,1,1,2);check(new MarkerRay(origin,direction).intersection(terrain)==1&&select.invoke(null,List.of(first),origin,direction)==first,"A profiling cube behind a solid wall remains selectable without consulting vanilla block hits");
        check(select.invoke(null,List.of(),origin,direction)==null,"A hidden, unauthorised, unloaded or below-threshold cube omitted by the displayed-cube list cannot focus");
        var miss=block(1,0,1,new AABB(.001,-.5,1,1,.5,2));var far=block(0,0,9,new AABB(-.5,-.5,8.001,.5,.5,9));
        check(select.invoke(null,List.of(miss,far),origin,direction)==null,"Narrow misses and out-of-range displayed cubes focus nothing");
        var tie=block(1,0,3,new AABB(-.5,-.5,3,.5,.5,8));
        check(select.invoke(null,List.of(tie,first),origin,direction)==first&&select.invoke(null,List.of(first,tie),origin,direction)==first,"Exact intersection ties have a stable location ordering");
        var secret=new Report.Entry("Block","fixture:private","minecraft:overworld",0,0,1,100,1);var permitted=new Report.Entry("Block","fixture:public","minecraft:overworld",0,0,4,100,1);
        var scoped=new Report(88,100,20,1,true,false,List.of(secret,permitted)).view(false,e->e.type().equals("fixture:public"));var scopeIndex=new MarkerIndex(scoped);var allowed=new ArrayList<Object>();
        for(var entry:scopeIndex.near("minecraft:overworld",.5,.5,0,96,0))allowed.add(record(nested("Block"),entry,new MarkerPosition(entry.x(),entry.y(),entry.z(),1.3),new AABB(entry.x(),entry.y(),entry.z(),entry.x()+1,entry.y()+1,entry.z()+1),TimingSeverity.color(1)));
        var selected=select.invoke(null,allowed,new net.minecraft.world.phys.Vec3(.5,.5,0),direction);var target=nested("Block").getDeclaredMethod("entry");target.setAccessible(true);
        check(allowed.size()==1&&target.invoke(selected).equals(permitted),"The real scoped-report/index pipeline excludes a nearer private cube and selects only an authorised outlined target");
        var crowd=new ArrayList<Object>(dense);crowd.add(first);crowd.add(closerCentre);
        for(int frame=0;frame<100;frame++){Collections.rotate(crowd,13);check(select.invoke(null,crowd,origin,new net.minecraft.world.phys.Vec3((frame%2==0?1:-1)*.00001,0,1))==first,"A thousand visible cubes and tiny turns preserve nearest-hit selection independent of list order");}
        var lookup=java.lang.invoke.MethodHandles.privateLookupIn(WorldMarkers.class,java.lang.invoke.MethodHandles.lookup());
        var handle=lookup.unreflect(select);for(int i=0;i<2000;i++)invokeSelection(handle,crowd,origin,direction);
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();long id=Thread.currentThread().threadId(),allocated=bean.getThreadAllocatedBytes(id),start=System.nanoTime();
        for(int i=0;i<200;i++)invokeSelection(handle,crowd,origin,direction);
        double ms=(System.nanoTime()-start)/200e6,bytes=(bean.getThreadAllocatedBytes(id)-allocated)/200.0;
        System.out.printf(Locale.ROOT,"World selection smoke benchmark: %.3f ms / 1,002 already eligible cubes, %.1f allocated bytes/frame. Excludes world extraction/GPU.%n",ms,bytes);check(bytes<256,"Ray selection allocates no per-cube objects: "+bytes+" bytes/frame");
        textCalls.clear();state.setRenderData(key,record(nested("Frame"),dense,List.of(nearText),Long.MIN_VALUE));overlay.capture(new net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent(state,null,stack,List.of()));overlay.submit(state,stack,collector);check(textCalls.isEmpty(),"No focused target means no floating timing labels, even if stale ordinary captions are supplied");
    }
    private static Object block(int x,int y,int z,AABB box)throws Exception{
        var entry=new Report.Entry("Block","fixture:machine","minecraft:overworld",x,y,z,100,1);
        return record(nested("Block"),entry,new MarkerPosition(x,y,z,1.3),box,TimingSeverity.color(1));
    }
    private static void invokeSelection(java.lang.invoke.MethodHandle handle,List<Object> boxes,net.minecraft.world.phys.Vec3 origin,net.minecraft.world.phys.Vec3 direction)throws Exception{
        try{handle.invoke(boxes,origin,direction);}catch(Throwable error){if(error instanceof Exception e)throw e;throw new AssertionError(error);}
    }
}
