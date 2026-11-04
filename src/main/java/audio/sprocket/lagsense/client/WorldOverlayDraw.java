package audio.sprocket.lagsense.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Native geometry/font draws after translucent terrain and its compositing have finished. */
interface WorldOverlayDraw {
    void submitCustomGeometry(PoseStack pose,RenderType type,SubmitNodeCollector.CustomGeometryRenderer geometry);
    void submitText(PoseStack pose,float x,float y,FormattedCharSequence text,boolean shadow,Font.DisplayMode mode,int light,int color,int background,int outline);
    default void submitMarkerText(PoseStack pose,float x,float y,FormattedCharSequence text,int color){submitText(pose,x,y,text,false,Font.DisplayMode.SEE_THROUGH,15728880,color,WorldText.background(color),0);}
    default void flush(){}
    static void render(RenderLevelStageEvent.AfterLevel event,BiConsumer<PoseStack,WorldOverlayDraw> draw){
        var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();var modelView=RenderSystem.getModelViewStack();
        modelView.pushMatrix();modelView.mul(event.getModelViewMatrix());
        try{
            draw.accept(event.getPoseStack(),new WorldOverlayDraw(){
                @Override public void flush(){buffers.endBatch();}
                @Override public void submitMarkerText(PoseStack pose,float x,float y,FormattedCharSequence text,int color){WorldText.draw(mc.font,text,x,y,color,pose.last().pose(),buffers,15728880);}
                @Override public void submitCustomGeometry(PoseStack pose,RenderType type,SubmitNodeCollector.CustomGeometryRenderer geometry){geometry.render(pose.last(),buffers.getBuffer(type));}
                @Override public void submitText(PoseStack pose,float x,float y,FormattedCharSequence text,boolean shadow,Font.DisplayMode mode,int light,int color,int background,int outline){
                    if(outline==0)mc.font.drawInBatch(text,x,y,color,shadow,pose.last().pose(),buffers,mode,background,light);
                    else mc.font.drawInBatch8xOutline(text,x,y,color,outline,pose.last().pose(),buffers,light);
                }
            });
        }finally{try{buffers.endBatch();}finally{modelView.popMatrix();}}
    }
}
