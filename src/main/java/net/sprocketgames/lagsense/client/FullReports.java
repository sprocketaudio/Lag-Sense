package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;

/** One bounded segment assembly and one decoder; completed report data is published atomically. */
public final class FullReports {
    private static final ThreadPoolExecutor reader=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),r->Thread.ofPlatform().daemon().name("LagSense report decoder").unstarted(r));
    private static long epoch;
    private static final ReportAssembly assembly=new ReportAssembly();
    public static void clear(){assembly.clear();epoch++;reader.getQueue().clear();}
    public static void accept(FullReportPart part) {
        if(part.open())ClientAccess.reportProgress();
        ReportAssembly.Complete complete;
        try{complete=assembly.accept(part);}catch(IllegalArgumentException e){if(part.open())ClientAccess.failReportLoad();return;}
        if(complete==null)return;
        var bytes=complete.bytes();long token=complete.serial(),generation=epoch;boolean show=complete.open(),saved=complete.archived();
        try{reader.execute(()->{
            try{var report=FullReportWire.decode(bytes);var markers=new MarkerIndex(report);
                Minecraft.getInstance().execute(()->{if(generation==epoch&&token==assembly.serial())LagSenseClient.acceptReport(report,show,saved,markers,true);});
            }catch(Exception error){Minecraft.getInstance().execute(()->{if(generation==epoch&&token==assembly.serial()){if(show)ClientAccess.failReportLoad();if(Minecraft.getInstance().player!=null)Minecraft.getInstance().getChatListener().handleSystemMessage(net.minecraft.network.chat.Component.literal("Could not read the complete LagSense report. Open it again to retry."),false);}});}
        });}catch(RejectedExecutionException error){if(show)ClientAccess.failReportLoad();if(Minecraft.getInstance().player!=null)Minecraft.getInstance().getChatListener().handleSystemMessage(net.minecraft.network.chat.Component.literal("LagSense report decoding is busy. Open the report again shortly."),false);}
    }
    private FullReports(){}
}
