package audio.sprocket.lagsense;

import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL33C.*;

/** Executes the shipped shaders in a hidden OpenGL context; no Minecraft/gameplay verification. */
public final class WorldDisplayGpuChecks {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static String resource(String name)throws Exception{
        try(var in=WorldDisplayGpuChecks.class.getResourceAsStream("/assets/lagsense/shaders/core/"+name)){return new String(Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
    }
    private static String source(String name)throws Exception{
        // Same std140 blocks supplied by Minecraft's DynamicTransforms/Projection snippets.
        return resource(name).replace("#moj_import <minecraft:dynamictransforms.glsl>","layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; vec4 ColorModulator; vec3 ModelOffset; mat4 TextureMat; };")
            .replace("#moj_import <minecraft:projection.glsl>","layout(std140) uniform Projection { mat4 ProjMat; };");
    }
    private static int shader(int kind,String text){int id=glCreateShader(kind);glShaderSource(id,text);glCompileShader(id);check(glGetShaderi(id,GL_COMPILE_STATUS)==GL_TRUE,glGetShaderInfoLog(id));return id;}
    private static int program(boolean intensity)throws Exception{
        int vertex=shader(GL_VERTEX_SHADER,source("world_text.vsh")),fragment=shader(GL_FRAGMENT_SHADER,source("world_text.fsh").replace("#version 330","#version 330\n"+(intensity?"#define INTENSITY":"")));
        int p=glCreateProgram();glAttachShader(p,vertex);glAttachShader(p,fragment);glLinkProgram(p);check(glGetProgrami(p,GL_LINK_STATUS)==GL_TRUE,glGetProgramInfoLog(p));glDeleteShader(vertex);glDeleteShader(fragment);return p;
    }
    private static int uniform(int program,String name,int slot,float[] values){
        int index=glGetUniformBlockIndex(program,name);check(index!=GL_INVALID_INDEX,"Missing native uniform block "+name);glUniformBlockBinding(program,index,slot);
        int buffer=glGenBuffers();glBindBuffer(GL_UNIFORM_BUFFER,buffer);glBufferData(GL_UNIFORM_BUFFER,values,GL_STATIC_DRAW);glBindBufferBase(GL_UNIFORM_BUFFER,slot,buffer);return buffer;
    }
    private static float[] matrix(int size){var a=new float[size];for(int i=0;i<4;i++)a[i*5]=1;return a;}
    private static void colour(int program,int color,float alpha){int a=glGetAttribLocation(program,"Color");glDisableVertexAttribArray(a);glVertexAttrib4f(a,(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha);}
    private static int[] pixel(){var b=MemoryUtil.memAlloc(4);try{glReadPixels(32,32,1,1,GL_RGBA,GL_UNSIGNED_BYTE,b);return new int[]{b.get(0)&255,b.get(1)&255,b.get(2)&255};}finally{MemoryUtil.memFree(b);}}
    public static void main(String[] args)throws Exception{
        check(GLFW.glfwInit(),"GLFW initialization failed");long window=0;
        try{
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,3);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
            window=GLFW.glfwCreateWindow(64,64,"LagSense shader checks",0,0);check(window!=0,"Could not create hidden GL context");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            var results=new ArrayList<String>();
            int vao=glGenVertexArrays();glBindVertexArray(vao);int vbo=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,new float[]{-1,-1,.8f, 1,-1,.8f, 1,1,.8f, -1,-1,.8f, 1,1,.8f, -1,1,.8f},GL_STATIC_DRAW);
            int texture=glGenTextures();glBindTexture(GL_TEXTURE_2D,texture);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
            var intensityPixels=MemoryUtil.memAlloc(16);for(int i=0;i<16;i++)intensityPixels.put((byte)128);intensityPixels.flip();glTexImage2D(GL_TEXTURE_2D,0,GL_R8,4,4,0,GL_RED,GL_UNSIGNED_BYTE,intensityPixels);MemoryUtil.memFree(intensityPixels);
            glViewport(0,0,64,64);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glEnable(GL_DEPTH_TEST);glDepthFunc(GL_ALWAYS);glDepthMask(false);
            int program=program(true);glUseProgram(program);glUniform1i(glGetUniformLocation(program,"Sampler0"),0);
            int position=glGetAttribLocation(program,"Position");glEnableVertexAttribArray(position);glVertexAttribPointer(position,3,GL_FLOAT,false,12,0);
            int uv=glGetAttribLocation(program,"UV0");glDisableVertexAttribArray(uv);glVertexAttrib2f(uv,.5f,.5f);
            float[] dynamic=matrix(40);dynamic[16]=dynamic[17]=dynamic[18]=dynamic[19]=1;for(int i=0;i<4;i++)dynamic[24+i*5]=1;
            int dynamicBuffer=uniform(program,"DynamicTransforms",0,dynamic),projection=uniform(program,"Projection",1,matrix(16));
            for(var theme:audio.sprocket.lagsense.client.UiTheme.Theme.values())for(int backdrop:new int[]{0x9ac0ee,0x111417})for(float alpha:new float[]{.24f,1}){
                int timing=TimingSeverity.color(12);colour(program,timing,alpha);glClearColor((backdrop>>16&255)/255f,(backdrop>>8&255)/255f,(backdrop&255)/255f,1);
                glDepthMask(true);glClearDepth(.05);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glDepthMask(false);glDrawArrays(GL_TRIANGLES,0,6);glFinish();int[] actual=pixel();
                double coverage=128/255.0,opacity=coverage*alpha;int channel=0;for(int shift:new int[]{16,8,0}){
                    double expected=(timing>>shift&255)*opacity+(backdrop>>shift&255)*(1-opacity);
                    check(Math.abs(actual[channel++]-expected)<3,"Glyph coverage must affect alpha once, with no RGB darkening or world-depth rejection");
                }
                var depth=MemoryUtil.memAllocFloat(1);try{glReadPixels(32,32,1,1,GL_DEPTH_COMPONENT,GL_FLOAT,depth);check(Math.abs(depth.get(0)-.05)<.001,"Caption pass must not write world depth");}finally{MemoryUtil.memFree(depth);}
                results.add(theme+" background="+Integer.toHexString(backdrop)+" alpha="+alpha+" RGB="+Arrays.toString(actual));
            }
            int rgba=program(false);glUseProgram(rgba);glUniform1i(glGetUniformLocation(rgba,"Sampler0"),0);
            int backgroundPosition=glGetAttribLocation(rgba,"Position");glEnableVertexAttribArray(backgroundPosition);glVertexAttribPointer(backgroundPosition,3,GL_FLOAT,false,12,0);
            int backgroundUv=glGetAttribLocation(rgba,"UV0");glDisableVertexAttribArray(backgroundUv);glVertexAttrib2f(backgroundUv,.5f,.5f);
            int backgroundDynamic=uniform(rgba,"DynamicTransforms",0,dynamic),backgroundProjection=uniform(rgba,"Projection",1,matrix(16));
            int white=glGenTextures();glBindTexture(GL_TEXTURE_2D,white);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
            var whitePixels=MemoryUtil.memAlloc(64);while(whitePixels.hasRemaining())whitePixels.put((byte)255);whitePixels.flip();glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,4,4,0,GL_RGBA,GL_UNSIGNED_BYTE,whitePixels);MemoryUtil.memFree(whitePixels);
            var backgroundMethod=Class.forName("audio.sprocket.lagsense.client.WorldText").getDeclaredMethod("background",int.class);backgroundMethod.setAccessible(true);
            for(int backdrop:new int[]{0x9ac0ee,0x111417,0x589449,0xb5a283})for(float alpha:new float[]{.24f,1}){
                int ink=(Math.round(alpha*255)<<24)|0x66ddaa,backing=(int)backgroundMethod.invoke(null,ink);float backingAlpha=(backing>>>24)/255f;
                glUseProgram(rgba);glBindTexture(GL_TEXTURE_2D,white);colour(rgba,backing,backingAlpha);
                glClearColor((backdrop>>16&255)/255f,(backdrop>>8&255)/255f,(backdrop&255)/255f,1);glDepthMask(true);glClearDepth(.05);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glDepthMask(false);glDrawArrays(GL_TRIANGLES,0,6);glFinish();
                int[] actual=pixel();int channel=0;for(int shift:new int[]{16,8,0}){double expected=(backing>>shift&255)*backingAlpha+(backdrop>>shift&255)*(1-backingAlpha);check(Math.abs(actual[channel++]-expected)<3,"The shipped custom RGBA shader renders native white-atlas background effects as translucent dark rectangles through depth");}
                var depth=MemoryUtil.memAllocFloat(1);try{glReadPixels(32,32,1,1,GL_DEPTH_COMPONENT,GL_FLOAT,depth);check(Math.abs(depth.get(0)-.05)<.001,"Dark backing never modifies the world depth buffer");}finally{MemoryUtil.memFree(depth);}
                results.add("Dark backing background="+Integer.toHexString(backdrop)+" alpha="+backingAlpha+" RGB="+Arrays.toString(actual));
            }
            glDeleteTextures(white);glDeleteBuffers(backgroundDynamic);glDeleteBuffers(backgroundProjection);glDeleteProgram(program);glDeleteProgram(rgba);
            Path directory=Path.of("work/world-display");Files.createDirectories(directory);Files.write(directory.resolve("gpu-results.txt"),results);
            glDeleteBuffers(dynamicBuffer);glDeleteBuffers(projection);glDeleteBuffers(vbo);glDeleteTextures(texture);glDeleteVertexArrays(vao);
            check(glGetError()==GL_NO_ERROR,"OpenGL error after marker shader checks");
            System.out.println("World display GPU checks passed: shipped intensity/RGBA shaders compile, half-coverage glyph colour is not multiplied twice, daylight/dark backdrops and all themes, full/faded alpha, native white-atlas dark backgrounds against sky/grass/machinery/dark surfaces, through-depth drawing and unchanged depth buffer. Hidden GL context only.");
        }finally{if(window!=0)GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
}
