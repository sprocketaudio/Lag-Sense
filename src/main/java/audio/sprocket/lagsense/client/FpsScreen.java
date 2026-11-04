package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class FpsScreen extends LagScreen {
    private final Screen parent;
    private FrameProfile cached;
    private int themeRevision=-1;
    private String summary = "", percentiles = "", gpu = "", stutters = "";
    private List<String> stages = List.of();
    private record Bar(int left, int right, int top, int color, String hint) {}
    private record Reference(int y, String label) {}
    private List<Bar> bars = List.of();
    private List<Reference> references = List.of();
    private int graphX, graphTop, graphBottom, graphWidth;
    public FpsScreen(Screen parent) { super("Frame profile"); this.parent = parent; }
    private void prepare() {
        themeRevision=UiTheme.revision();
        cached = ClientFrames.latest; if (cached == null) return;
        var p = cached;
        summary = String.format(Locale.ROOT, "%.1f FPS | 1%% low %.1f | %d frames", p.fps(), p.onePercentLow(), p.frames());
        percentiles = String.format(Locale.ROOT, "p50 %.1f | p95 %.1f | p99 %.1f | max %.1f ms", p.medianMs(), p.p95Ms(), p.p99Ms(), p.worstMs());
        stages = p.stages().stream().map(s -> String.format(Locale.ROOT, "%s: %.2f CPU ms/frame", s.name(), s.msPerFrame())).toList();
        var q = p.gpu(); gpu = q == null ? "No GPU samples" : q.samples() == 0 ? q.status() : String.format(Locale.ROOT, "GPU %.2f ms | p95 %.2f | max %.2f | %d queries", q.meanMs(), q.p95Ms(), q.worstMs(), q.samples());
        stutters = p.stutters() + " frames >50 ms" + (p.limited() ? " | Frame limit reached" : "");
        graphX = margin() + 28; graphTop = 88; graphBottom = Math.max(graphTop + 24, height - 106); graphWidth = contentWidth() - 28; int gh = graphBottom - graphTop; double ceiling = FrameGraph.ceiling(p);
        var ref = new ArrayList<Reference>(); int lastY = Integer.MAX_VALUE;
        for (int i = FrameGraph.REFERENCES.length - 1; i >= 0; i--) {
            double ms = FrameGraph.REFERENCES[i]; if (ms >= ceiling) continue; int y = graphBottom - (int)(ms / ceiling * gh);
            if (lastY != Integer.MAX_VALUE && y - lastY < 10) continue; ref.add(new Reference(y, String.format(Locale.ROOT, "%.1f", ms))); lastY = y;
        }
        references = List.copyOf(ref);
        var geometry = new ArrayList<Bar>();
        for (int i = 0; i < p.graphMs().size(); i++) {
            double ms = p.graphMs().get(i); int x = graphX + i * graphWidth / p.graphMs().size(), end = graphX + (i + 1) * graphWidth / p.graphMs().size();
            String hint = String.format(Locale.ROOT, "Frames %d–%d\nPeak %.3f ms (~%.1f FPS)", FrameGraph.firstFrame(p, i), FrameGraph.lastFrame(p, i), ms, ms <= 0 ? 0 : 1000 / ms);
            geometry.add(new Bar(x, Math.max(x + 1, end - 1), graphBottom - Math.max(1, (int)(ms / ceiling * gh)), ms > 50 ? 0xffff5252 : UiTheme.ICE, hint));
        }
        bars = List.copyOf(geometry);
    }
    @Override protected void init() {
        prepare(); int x = margin();
        primary("Capture 10s", x, 60, 84, () -> ClientFrames.start(10));
        primary("Capture 30s", x + 88, 60, 84, () -> ClientFrames.start(30));
        toggle("GPU", new UiMetrics.Rect(x + 180, 60, 74, 20), ClientFrames.gpuEnabled(), () -> { ClientFrames.toggleGpu(); rebuildWidgets(); });
        if (ClientFrames.latest != null) {
            UiHints.hint(action("Copy", x, footer(), 44, () -> minecraft.keyboardHandler.setClipboard(Profiler.JSON.toJson(ClientFrames.latest))),"Copy this report as JSON.");
            action("Export", x + 48, footer(), 48, () -> minecraft.setScreen(new ExportScreen(this, ClientFrames.latest)));
        }
        action("Close", width - x - 54, footer(), 54, this::onClose);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        if (cached != ClientFrames.latest||themeRevision!=UiTheme.revision()) rebuildWidgets();
        canvas(g, "FRAME PROFILE");
        if (cached == null) text(g, "No frame capture.", margin(), 100, contentWidth(), UiTheme.SECONDARY);
        else {
            text(g, summary, margin(), 30, contentWidth(), UiTheme.TEXT); text(g, percentiles, margin(), 42, contentWidth(), UiTheme.SECONDARY);
            UiHints.region(g, font, mx, my, margin(), 28, contentWidth(), 12, summary + "\n1% low uses the slowest 1% of frames. Frame times include FPS limits and waits.");
            UiHints.region(g, font, mx, my, margin(), 40, contentWidth(), 12, percentiles + "\np95 means 95% of frames finished within that time.");
            g.fill(graphX, graphTop, graphX + graphWidth, graphBottom, UiTheme.PANEL);
            for (var ref : references) { g.fill(graphX, ref.y(), graphX + graphWidth, ref.y() + 1, UiTheme.BORDER); UiFonts.draw(g, UiFonts.mono(), ref.label(), margin(), ref.y() - 3, 40, UiTheme.SECONDARY, false); }
            for (var bar : bars) {
                boolean hover = mx >= bar.left() && mx < bar.right() + 1 && my >= graphTop && my < graphBottom;
                g.fill(bar.left(), bar.top(), bar.right(), graphBottom, bar.color());
                if (hover) { g.fill(bar.left(), graphTop, bar.right(), graphBottom, 0x22e4eae7); UiHints.region(g, font, mx, my, bar.left(), graphTop, Math.max(1, bar.right() - bar.left() + 1), graphBottom - graphTop, bar.hint()); }
            }
            text(g, stutters, margin(), graphBottom + 4, contentWidth(), UiTheme.SECONDARY);
            int y = graphBottom + 18;
            for (var line : stages) { text(g, line, margin(), y, contentWidth(), UiTheme.SECONDARY); y += 11; }
            UiHints.region(g, font, mx, my, margin(), graphBottom + 16, contentWidth(), stages.size() * 11, "World submission is included in render submission, so adding the stage times would count some work twice.");
            text(g, gpu, margin(), y + 2, contentWidth(), UiTheme.TEXT);
            UiHints.region(g, font, mx, my, margin(), y, contentWidth(), 14, gpu + "\nGPU time measures frame drawing without buffer swaps or FPS limits. It is not broken down by mod.");
        }
        super.extractRenderState(g, mx, my, partial);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
