package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class ExportScreen extends LagScreen {
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> Thread.ofPlatform().daemon().name("LagSense export writer").unstarted(r));
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static boolean waitingForCpu;
    static void cancelInvestigation(){if(waitingForCpu){waitingForCpu=false;BUSY.set(false);}}
    private final Screen parent;
    private final Object data;
    private final boolean comparison;
    private boolean archived;
    private Report htmlProfile;
    private String message = "";
    private String filename = "";
    public ExportScreen(Screen parent, Report report) {this(parent,report,false);}
    public ExportScreen(Screen parent,Report report,boolean archived){super("Export report");this.parent=parent;data=report;comparison=false;this.archived=archived;}
    public ExportScreen(Screen parent, ProfileComparison report) { super("Export comparison"); this.parent = parent; data = report; comparison = true; }
    public ExportScreen(Screen parent, FrameProfile report) { super("Export client frames"); this.parent = parent; data = report; comparison = false; }
    public ExportScreen(Screen parent, CpuInvestigation report) { super("Export call paths"); this.parent = parent; data = report; comparison = false; }
    public ExportScreen(Screen parent, ChunkInvestigation report) { super("Export chunk"); this.parent = parent; data = report; comparison = false; }
    public ExportScreen(Screen parent,ChunkInvestigation chunk,Report profile,boolean archived){this(parent,chunk);htmlProfile=profile;this.archived=archived;}
    private boolean permitted(){if(data instanceof FrameProfile)return true;if(data instanceof Report report)return ClientAccess.allowed(report);return ClientAccess.valid();}
    private void save(boolean csv) {
        if(!permitted()){message="Profile access expired. Open the report again.";return;}
        if(!BUSY.compareAndSet(false,true)){message="Wait for the current export to finish before saving another.";return;}
        if(data instanceof Report report){
            if(report.diagnostics()!=null&&report.diagnostics().notes().stream().anyMatch(n->n.startsWith("Result presentation limited:"))){BUSY.set(false);message="Open a complete report before exporting.";return;}
            if(report.diagnostics()!=null&&report.diagnostics().cpu().state().equals("pending")){BUSY.set(false);message="CPU analysis is still running. Reopen the report when it finishes.";return;}
            if(HtmlReportExport.needsCpu(report)){
                message="Loading saved call paths…";waitingForCpu=true;
                Investigations.cpu(this,report,archived,cpu->{
                    waitingForCpu=false;
                    if(!cpu.state().equals("ready")||cpu.samples()!=report.diagnostics().cpu().samples()||cpu.nodes().isEmpty()){BUSY.set(false);message="The complete call paths are unavailable. Export was not saved.";return;}
                    writeExport(HtmlReportExport.withCpu(report,cpu),csv);
                },error->{waitingForCpu=false;BUSY.set(false);message="Export was not saved. "+error;});return;
            }
        }
        writeExport(data,csv);
    }
    private void writeExport(Object snapshot,boolean csv){
        if(!permitted()){BUSY.set(false);return;}
        boolean serverData=!(snapshot instanceof FrameProfile);long access=ClientAccess.epoch();message="Saving…";Path directory=minecraft.gameDirectory.toPath().resolve("lagsense/exports");var mc=minecraft;
        CompletableFuture.runAsync(()->{
            Path temporary=null;
            try{
                if(serverData&&!ClientAccess.current(access))throw new java.io.IOException("Profile access expired");
                Files.createDirectories(directory);
                String stamp=DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").format(LocalDateTime.now());
                String name=(snapshot instanceof CpuInvestigation?"call-paths-":snapshot instanceof ChunkInvestigation?"chunk-":snapshot instanceof FrameProfile?"frames-":comparison?"comparison-":"profile-")+stamp+"-"+UUID.randomUUID().toString().substring(0,8)+(csv?".csv":".json");
                temporary=Files.createTempFile(directory,"lagsense-export-",".tmp");
                try(var out=new java.io.OutputStreamWriter(new ExportOutput(Files.newOutputStream(temporary)),java.nio.charset.StandardCharsets.UTF_8)){
                    if(!csv){if(snapshot instanceof ProfileComparison c)out.write(ReportExport.json(c));else Profiler.JSON.toJson(snapshot,out);}
                    else if(snapshot instanceof Report r)ReportExport.writeCsv(r,out);
                    else out.write(snapshot instanceof CpuInvestigation c?InvestigationExport.csv(c):snapshot instanceof ChunkInvestigation c?InvestigationExport.csv(c):snapshot instanceof FrameProfile f?f.csv():ReportExport.csv((ProfileComparison)snapshot));
                }
                if(serverData&&!ClientAccess.current(access))throw new java.io.IOException("Profile access expired");
                Files.move(temporary,directory.resolve(name));mc.execute(()->{message="Saved to lagsense/exports";filename=name;});
            }catch(Exception error){LagSense.LOG.warn("Could not export complete LagSense data",error);mc.execute(()->message="Complete export could not be saved. Check the 128 MiB file limit, disk space and client log.");}
            finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(java.io.IOException ignored){}BUSY.set(false);}
        },WRITER);
    }
    private static final class ExportOutput extends java.io.FilterOutputStream {
        long written;ExportOutput(java.io.OutputStream out){super(out);}
        private void check(int n)throws java.io.IOException{if((written+=n)>CaptureBudget.MAX_FILE_BYTES)throw new java.io.IOException("Complete export exceeds 128 MiB");}
        @Override public void write(int b)throws java.io.IOException{check(1);out.write(b);}
        @Override public void write(byte[] b,int off,int n)throws java.io.IOException{check(n);out.write(b,off,n);}
    }
    private void saveHtml(){
        if(!permitted()){message="Profile access expired. Open the report again.";return;}
        if(!BUSY.compareAndSet(false,true)){message="Wait for the current export to finish before saving another.";return;}
        var theme=UiTheme.current();Object source=htmlProfile!=null?htmlProfile:data;
        if(source instanceof Report report&&report.diagnostics()!=null&&report.diagnostics().notes().stream().anyMatch(n->n.startsWith("Result presentation limited:"))){BUSY.set(false);message="Open a complete report before exporting HTML.";return;}
        if(source instanceof Report report&&report.diagnostics()!=null&&report.diagnostics().cpu().state().equals("pending")){BUSY.set(false);message="CPU analysis is still running. Reopen the report when it finishes.";return;}
        if(source instanceof Report report&&HtmlReportExport.needsCpu(report)){
            message="Loading saved call paths…";waitingForCpu=true;
            Investigations.cpu(this,report,archived,cpu->{
                    waitingForCpu=false;
                if(!cpu.state().equals("ready")||cpu.samples()!=report.diagnostics().cpu().samples()||cpu.methods().isEmpty()&&cpu.nodes().isEmpty()){BUSY.set(false);message="The HTML export could not be saved because call paths are not ready.";return;}
                writeHtml(HtmlReportExport.withCpu(report,cpu),theme);
            },error->{waitingForCpu=false;BUSY.set(false);message="The HTML export could not be saved because call paths could not be loaded. "+error;});
        }else if(source instanceof ChunkInvestigation c&&(c.hotspots().size()<c.targets()||c.offset()>0)){BUSY.set(false);message="Open the full profile to export all chunk contributors.";}else writeHtml(source,theme);
    }
    private void writeHtml(Object snapshot,UiTheme.Theme theme){
        if(!permitted()){BUSY.set(false);return;}
        boolean serverData=!(snapshot instanceof FrameProfile);long access=ClientAccess.epoch();message="Saving HTML…";var mc=minecraft;Path directory=mc.gameDirectory.toPath().resolve("lagsense/exports");
        CompletableFuture.runAsync(()->{
            Path temporary=null;
            try{
                if(serverData&&!ClientAccess.current(access))throw new java.io.IOException("Profile access expired");
                String id=snapshot instanceof Report r?"profile-"+Long.toUnsignedString(r.id()):snapshot instanceof ProfileComparison c?"comparison-"+Long.toUnsignedString(c.beforeId())+"-"+Long.toUnsignedString(c.afterId()):snapshot instanceof CpuInvestigation?"call-paths":snapshot instanceof FrameProfile?"frames":"chunk";
                String name=id+"-"+DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").format(LocalDateTime.now())+"-"+UUID.randomUUID().toString().substring(0,8)+".html";
                Files.createDirectories(directory);temporary=Files.createTempFile(directory,"lagsense-export-",".tmp");
                Files.delete(temporary);HtmlReportExport.write(temporary,snapshot,theme);
                if(serverData&&!ClientAccess.current(access))throw new java.io.IOException("Profile access expired");
                Files.move(temporary,directory.resolve(name));
                mc.execute(()->{message="Saved to lagsense/exports";filename=name;});
            }catch(Exception error){LagSense.LOG.warn("Could not export LagSense HTML report",error);boolean large=false;for(Throwable cause=error;cause!=null;cause=cause.getCause())if(cause instanceof HtmlReportExport.TooLarge)large=true;String detail=large?"HTML export exceeds 128 MiB and could not be saved.":"Could not save the complete HTML export. Check the client log and disk space.";mc.execute(()->message=detail);}
            finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(java.io.IOException ignored){}BUSY.set(false);}
        },WRITER);
    }
    @Override protected void init() {
        primary("Save JSON", margin(), 84, 104, () -> save(false));
        primary("Save CSV", margin() + 112, 84, 104, () -> save(true));
        UiHints.hint(primary("Export HTML",margin()+224,84,104,this::saveHtml),"Save an interactive HTML report with the current theme that opens without an internet connection.");
        action("Close", width - margin() - 54, footer(), 54, this::onClose);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        canvas(g, "EXPORT " + (data instanceof CpuInvestigation ? "CALL PATHS" : data instanceof ChunkInvestigation ? "CHUNK" : data instanceof FrameProfile ? "FRAMES" : comparison ? "COMPARISON" : "REPORT"));
        footerMessage(g,message,mouseX,mouseY);
        text(g, filename, margin(), 52, contentWidth(), UiTheme.TEXT);
        text(g,"Exports can include coordinates, UUIDs and server diagnostics.",margin(),124,contentWidth(),UiTheme.SECONDARY);
        UiHints.region(g, font, mouseX, mouseY, margin(), 50, contentWidth(), 12, filename);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
