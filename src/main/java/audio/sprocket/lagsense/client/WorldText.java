package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.mixin.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4fc;

/** Native font layout/atlases, with glyph coverage applied once rather than twice. */
final class WorldText {
    static final RenderPipeline INTENSITY=pipeline("intensity",true),COLOR=pipeline("color",false);
    private static final Map<RenderType,RenderType> TYPES=new IdentityHashMap<>();
    private static RenderPipeline pipeline(String name,boolean intensity){
        var builder=RenderPipeline.builder(RenderPipelines.TEXT_SNIPPET).withLocation(Identifier.fromNamespaceAndPath("lagsense","world_text_"+name))
            .withVertexShader(Identifier.fromNamespaceAndPath("lagsense","core/world_text")).withFragmentShader(Identifier.fromNamespaceAndPath("lagsense","core/world_text"))
            .withSampler("Sampler0").withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS,false));
        if(intensity)builder.withShaderDefine("INTENSITY");return builder.build();
    }
    static void clear(){TYPES.clear();}
    private static RenderType type(TextRenderable glyph){
        var original=glyph.renderType(Font.DisplayMode.SEE_THROUGH,true);var cached=TYPES.get(original);if(cached!=null)return cached;
        var setup=((WorldTextRenderTypeAccessor)(Object)original).lagsense$setup();
        var binding=((WorldTextSetupAccessor)(Object)setup).lagsense$textures().get("Sampler0");
        if(binding==null)return original;
        var atlas=((WorldTextBindingAccessor)binding).lagsense$location();
        boolean intensity=glyph.guiPipeline().getFragmentShader().getPath().contains("intensity");
        var value=RenderType.create("lagsense_world_text",RenderSetup.builder(intensity?INTENSITY:COLOR)
            .withTexture("Sampler0",atlas,()->RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)).createRenderSetup());
        TYPES.put(original,value);return value;
    }
    static int background(int color){return (Math.round((color>>>24)*.6f)<<24)|0x08080b;}
    static Font.PreparedText prepare(Font font,FormattedCharSequence text,float x,float y,int color){
        return font.prepareText(text,x,y,color,false,false,background(color));
    }
    static void draw(Font font,FormattedCharSequence text,float x,float y,int color,Matrix4fc pose,MultiBufferSource buffers,int light){
        prepare(font,text,x,y,color).visit(new Font.GlyphVisitor(){
            @Override public void acceptGlyph(TextRenderable.Styled glyph){glyph.render(pose,buffers.getBuffer(type(glyph)),light,false);}
            @Override public void acceptEffect(TextRenderable effect){effect.render(pose,buffers.getBuffer(type(effect)),light,false);}
        });
    }
    private WorldText(){}
}
