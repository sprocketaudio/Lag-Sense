package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/** Bounded background decode/matching; comparison data never replaces the active profile/HUD. */
public final class Comparisons {
    private static final ReportAssembly assembly=new ReportAssembly();
    private static final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),r->Thread.ofPlatform().daemon().name("LagSense comparison reader").unstarted(r));
    private static long epoch;
    public static void clear(){epoch++;assembly.clear();worker.getQueue().clear();}
    public static void accept(ComparisonPart part){
        final ReportAssembly.Complete complete;try{complete=assembly.accept(part.part());}catch(IllegalArgumentException error){error("Could not assemble comparison. Try again.");return;}if(complete==null)return;
        long generation=epoch;
        try{worker.execute(()->{try{
            var data=ComparisonWire.decode(complete.bytes());var index=new ComparisonIndex(data);
            Minecraft.getInstance().execute(()->{if(generation!=epoch||complete.serial()!=assembly.serial())return;var mc=Minecraft.getInstance();mc.setScreen(new ComparisonScreen(mc.screen,index));});
        }catch(Exception failure){Minecraft.getInstance().execute(()->{if(generation==epoch&&complete.serial()==assembly.serial())error("Could not read comparison. Try again.");});}});}catch(RejectedExecutionException busy){error("Comparison is busy. Try again shortly.");}
    }
    static void view(ComparisonIndex index,String dimension,Consumer<ComparisonIndex.View> ready,Consumer<String> error){
        long generation=epoch;
        try{worker.execute(()->{try{var view=index.view(dimension);Minecraft.getInstance().execute(()->{if(generation==epoch)ready.accept(view);});}catch(Exception failure){Minecraft.getInstance().execute(()->{if(generation==epoch)error.accept("Could not prepare comparison.");});}});}catch(RejectedExecutionException busy){error.accept("Comparison is busy. Try again shortly.");}
    }
    static void copy(ProfileComparison comparison){
        long generation=epoch;
        try{worker.execute(()->{try{String json=ReportExport.json(comparison);Minecraft.getInstance().execute(()->{if(generation==epoch)Minecraft.getInstance().keyboardHandler.setClipboard(json);});}catch(Exception failure){Minecraft.getInstance().execute(()->error("Could not copy comparison."));}});}catch(RejectedExecutionException busy){error("Comparison is busy. Try again shortly.");}
    }
    private static void error(String message){var mc=Minecraft.getInstance();if(mc.screen instanceof HistoryScreen history)history.navigationFailed(message);if(mc.player!=null)mc.getChatListener().handleSystemMessage(net.minecraft.network.chat.Component.literal(message),false);}
    private Comparisons(){}
}
