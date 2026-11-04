package audio.sprocket.lagsense;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import org.lwjgl.system.*;
import org.lwjgl.util.freetype.*;

/** Uses Minecraft's FreeType settings and models bilinear GUI sampling, without a game/GPU. */
final class FontDensityChecks {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static final class Face implements AutoCloseable {
        final ByteBuffer memory;final FT_Face face;final float oversample;
        Face(String name)throws Exception{
            JsonObject definition;try(var stream=FontDensityChecks.class.getResourceAsStream("/assets/lagsense/font/"+name+".json")){definition=JsonParser.parseString(new String(Objects.requireNonNull(stream).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("providers").get(0).getAsJsonObject();}
            oversample=definition.get("oversample").getAsFloat();String file=definition.get("file").getAsString().substring(9);byte[] bytes;try(var stream=FontDensityChecks.class.getResourceAsStream("/assets/lagsense/font/"+file)){bytes=Objects.requireNonNull(stream).readAllBytes();}
            memory=MemoryUtil.memAlloc(bytes.length).put(bytes).flip();try(var stack=MemoryStack.stackPush()){var ptr=stack.mallocPointer(1);FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(),memory,0,ptr),"Density fixture");face=FT_Face.create(ptr.get());}
            int pixels=Math.round(definition.get("size").getAsFloat()*oversample);FreeType.FT_Set_Pixel_Sizes(face,pixels,pixels);
        }
        @Override public void close(){FreeType.FT_Done_Face(face);MemoryUtil.memFree(memory);}
    }
    static void run()throws Exception{
        Path directory=Path.of("work/font-scale1");Files.createDirectories(directory);
        var before=new BufferedImage(640,118,BufferedImage.TYPE_INT_RGB);var after=new BufferedImage(640,118,BufferedImage.TYPE_INT_RGB);
        for(var target:List.of(before,after))for(int y=0;y<target.getHeight();y++)for(int x=0;x<target.getWidth();x++)target.setRGB(x,y,0x25282a);
        for(boolean low:List.of(false,true)){
            var image=low?after:before;String suffix=low?"_1x":"";
            try(var body=new Face("ui"+suffix);var strong=new Face("strong"+suffix);var mono=new Face("mono"+suffix)){
                check(low?body.oversample==1:body.oversample==3,"Native-resolution rasterisation is confined to the added scale-1 resources");
                draw(image,strong,"LAGSENSE / SERVER PROFILE",9,16,1,0xd8b76a);
                draw(image,mono,"3.94 MSPT | 20.0 TPS | 601 ticks | 28s ago",9,29,1,0xc0c4c7);
                draw(image,body,"Targets  Chunks  Map  CPU Activity  Call Paths  Event Timings",9,47,1,0xe4eae7);
                draw(image,strong,"minecraft:villager",14,68,1,0xe4eae7);draw(image,mono,"47.76 µs/t",380,68,1,0xf5ca57);
                draw(image,body,"Recording | 8s remaining",9,91,1.1,0xc0c4c7);draw(image,mono,"Top: 55.2 µs/t minecraft:villager",9,108,1.1,0xf5ca57);
            }
        }
        ImageIO.write(before,"png",directory.resolve("before-3x-atlas.png").toFile());ImageIO.write(after,"png",directory.resolve("after-native-1x.png").toFile());
        var compare=new BufferedImage(1280,504,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<118;y++)for(int x=0;x<640;x++){compare.setRGB(x,y,before.getRGB(x,y));compare.setRGB(x,y+130,after.getRGB(x,y));}
        for(int y=0;y<118;y++)for(int x=0;x<640;x++)for(int sy=0;sy<2;sy++)for(int sx=0;sx<2;sx++){compare.setRGB(x*2+sx,260+y*2+sy,after.getRGB(x,y));}
        ImageIO.write(compare,"png",directory.resolve("comparison.png").toFile());
        System.out.println("Scale-1 font checks passed: native FreeType rasters and bilinear sampling fixtures include normal UI text and a fractional 110% HUD transform. Visual fixtures are not in-game verification.");
    }
    private static void draw(BufferedImage out,Face font,String text,double x,double baseline,double scale,int colour){
        double pen=x;
        for(int code:text.codePoints().toArray()){
            int index=FreeType.FT_Get_Char_Index(font.face,code);check(index!=0,"Density fixture glyph exists: "+code);
            // Same FT_LOAD_RENDER used by NativeImage.copyFromFont.
            FreeTypeUtil.assertError(FreeType.FT_Load_Glyph(font.face,index,4),"Density raster");var slot=Objects.requireNonNull(font.face.glyph());var bitmap=slot.bitmap();
            int w=bitmap.width(),h=bitmap.rows();double left=pen+slot.bitmap_left()/font.oversample*scale,top=baseline-slot.bitmap_top()/font.oversample*scale;
            if(w>0&&h>0){ByteBuffer pixels=Objects.requireNonNull(bitmap.buffer(bitmap.pitch()*h));double units=font.oversample/scale;
                for(int py=(int)Math.floor(top);py<Math.ceil(top+h/units);py++)for(int px=(int)Math.floor(left);px<Math.ceil(left+w/units);px++){
                    if(px<0||py<0||px>=out.getWidth()||py>=out.getHeight())continue;
                    double tx=(px+.5-left)*units-.5,ty=(py+.5-top)*units-.5;int ix=(int)Math.floor(tx),iy=(int)Math.floor(ty);double fx=tx-ix,fy=ty-iy;
                    double alpha=(sample(pixels,w,h,bitmap.pitch(),ix,iy)*(1-fx)*(1-fy)+sample(pixels,w,h,bitmap.pitch(),ix+1,iy)*fx*(1-fy)+sample(pixels,w,h,bitmap.pitch(),ix,iy+1)*(1-fx)*fy+sample(pixels,w,h,bitmap.pitch(),ix+1,iy+1)*fx*fy)/255;
                    int old=out.getRGB(px,py),value=0;for(int shift:new int[]{16,8,0})value|=(int)Math.round(((old>>shift)&255)*(1-alpha)+((colour>>shift)&255)*alpha)<<shift;out.setRGB(px,py,value);
                }
            }
            pen+=FreeTypeUtil.x(slot.advance())/font.oversample*scale;
        }
    }
    private static int sample(ByteBuffer pixels,int w,int h,int pitch,int x,int y){return x<0||y<0||x>=w||y>=h?0:Byte.toUnsignedInt(pixels.get(y*pitch+x));}
}
