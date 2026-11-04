package net.sprocketgames.lagsense.client;

import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Height follows content. Its parent list clips and scrolls it together with surrounding rows. */
public final class UiDetails extends AbstractWidget {
    private final Font font;
    private final List<FormattedCharSequence> lines;
    private final int clipTop, clipBottom;
    public record Layout(List<FormattedCharSequence> lines) { public int height() { return lines.isEmpty() ? 1 : lines.size()*11+6; } }
    public static Layout wrap(Font font, int width, List<String> text) {
        var wrapped = new ArrayList<FormattedCharSequence>();
        for (var line : text) if (!line.isBlank()) wrapped.addAll(font.split(UiFonts.rich(line), Math.max(1, width - 24)));
        return new Layout(List.copyOf(wrapped));
    }
    public UiDetails(Font font, int x, int y, int width, List<String> text, int clipTop, int clipBottom) {
        this(font, x, y, width, wrap(font, width, text), clipTop, clipBottom);
    }
    public UiDetails(Font font, int x, int y, int width, Layout layout, int clipTop, int clipBottom) {
        super(x, y, width, layout.height(), Component.empty()); this.font = font; this.clipTop = clipTop; this.clipBottom = clipBottom;
        lines = layout.lines(); active = false;
    }
    @Override public boolean isMouseOver(double x, double y) { return false; }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        if (height == 0) return;
        g.fill(getX(), getY(), getX()+width, getY()+height, UiTheme.PANEL);
        g.fill(getX(), getY(), getX()+2, getY()+height, UiTheme.ICE);
        int from = Math.max(0, (clipTop - getY() - 3) / 11), to = Math.min(lines.size(), (clipBottom - getY() + 10) / 11);
        for (int i = from; i < to; i++) g.text(font, lines.get(i), getX()+12, getY()+3+i*11, UiTheme.SECONDARY, false);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput out) {}
}
