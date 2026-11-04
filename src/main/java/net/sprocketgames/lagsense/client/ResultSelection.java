package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.CopyTree;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** One selected result per view; clipboard formatting runs on a bounded background worker. */
final class ResultSelection {
    private static final ThreadPoolExecutor writer=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),r->Thread.ofPlatform().daemon().name("LagSense result copy").unstarted(r));
    private Object context,key;
    private Supplier<CopyTree.Node> source;
    private UiButton button;
    private long generation;
    private boolean busy;
    void begin(Object value){if(!Objects.equals(context,value)){clear();context=value;}}
    void clear(){key=null;source=null;busy=false;generation++;update();}
    void select(Object value,Supplier<CopyTree.Node> source){key=value;this.source=source;generation++;busy=false;update();}
    boolean is(Object value){return Objects.equals(key,value)&&source!=null;}
    Object key(){return key;}
    long generation(){return generation;}
    boolean current(long token){return token==generation&&source!=null;}
    void bind(UiButton button){this.button=button;update();}
    void waiting(boolean value){busy=value;update();}
    private void update(){if(button!=null)button.active=source!=null&&!busy;}
    void copy(String warning){
        if(source==null||busy)return;CopyTree.Node node;try{node=source.get();}catch(RuntimeException failure){error("Could not read selected result.");return;}if(node==null){clear();return;}long token=generation,access=ClientAccess.epoch();waiting(true);
        try{writer.execute(()->{try{String text=CopyTree.format(node,warning);Minecraft.getInstance().execute(()->{if(!current(token)||!ClientAccess.current(access))return;waiting(false);Minecraft.getInstance().keyboardHandler.setClipboard(text);});}catch(Exception failure){Minecraft.getInstance().execute(()->{if(current(token)){waiting(false);error("Could not copy selected result.");}});}});}catch(RejectedExecutionException busy){waiting(false);error("Clipboard copy is busy. Try again shortly.");}
    }
    private static void error(String text){var mc=Minecraft.getInstance();if(mc.player!=null)mc.getChatListener().handleSystemMessage(Component.literal(text),false);}
}
