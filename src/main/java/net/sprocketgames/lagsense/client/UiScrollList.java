package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.ListScroll;
import net.sprocketgames.lagsense.VirtualListIndex;
import java.util.function.BiConsumer;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** Native child hit-testing and focus inside a clipped, variable-height list. */
public final class UiScrollList extends AbstractContainerEventHandler implements Renderable, NarratableEntry {
    public record Entry(Object key, int height, BiConsumer<UiScrollList,Integer> build) {}
    public record Anchor(Object key, int within) {}
    private List<Entry> entries;
    private VirtualListIndex index;
    private int first = -1, last = -1;
    private boolean draggingBar;
    private record Item(AbstractWidget widget, int originalY) {}
    private final int left, top, width, height;
    private final ListScroll scroll;
    private final List<Item> items = new ArrayList<>();
    private final List<AbstractWidget> children = new ArrayList<>();
    public UiScrollList(int left, int top, int width, int height, ListScroll scroll) {
        this.left = left; this.top = top; this.width = width; this.height = Math.max(1, height); this.scroll = scroll;
    }
    public void virtual(List<Entry> values, Anchor anchor) {
        first = last = Integer.MIN_VALUE;
        entries = List.copyOf(values);
        index = new VirtualListIndex(entries.stream().map(Entry::key).toList(), entries.stream().mapToInt(Entry::height).toArray());
        scroll.bounds(index.height(), height);
        if (anchor != null) { int row = index.position(anchor.key()); if (row >= 0) scroll.restore(index.top(row) + anchor.within()); }
        position();
    }
    public Anchor anchor() {
        if (index == null || index.size() == 0) return null;
        int row = index.at(scroll.offset());
        return new Anchor(entries.get(row).key(), scroll.offset() - index.top(row));
    }
    public boolean visible(Object key) { int row = index == null ? -1 : index.position(key); return row >= 0 && row >= first && row <= last; }
    public int widgetCount() { return children.size(); }
    public <T extends AbstractWidget> T add(T widget) { children.add(widget); items.add(new Item(widget, widget.getY())); return widget; }
    public void finish(int bottom) { scroll.bounds(Math.max(0, bottom - top), height); position(); }
    public void restore(int offset) { scroll.restore(offset); position(); }
    private void position() {
        if (index != null) {
            int from = index.at(scroll.offset()), to = index.at(scroll.offset() + height - 1);
            if (from != first || to != last) {
                super.setFocused(null); items.clear(); children.clear(); first = from; last = to;
                if (from >= 0) for (int i = from; i <= to; i++) entries.get(i).build().accept(this, top + index.top(i));
            }
        }
        for (var item : items) item.widget().setY(item.originalY() - scroll.offset());
    }
    @Override public List<? extends GuiEventListener> children() { return children; }
    @Override public boolean isMouseOver(double x, double y) { return x >= left && x < left + width && y >= top && y < top + height; }
    @Override public ScreenRectangle getRectangle() { return new ScreenRectangle(left, top, width, height); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.enableScissor(left, top, left + width, top + height);
        boolean inside = isMouseOver(mx, my);
        for (var item : items) {
            var w = item.widget();
            if (w.getY() + w.getHeight() > top && w.getY() < top + height) w.extractRenderState(g, inside ? mx : -10000, inside ? my : -10000, a);
        }
        g.disableScissor();
        if (scroll.maximum() > 0) {
            int thumb = Math.max(10, (int)((long)height * height / (height + scroll.maximum())));
            int y = top + (int)((long)scroll.offset() * (height - thumb) / scroll.maximum());
            g.fill(left + width - 2, top, left + width, top + height, UiTheme.BORDER);
            g.fill(left + width - 2, y, left + width, y + thumb, UiTheme.SECONDARY);
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!isMouseOver(x,y) || scroll.maximum() == 0 || vertical == 0) return false;
        scroll.move((int)Math.round(-vertical * 24)); position(); return true;
    }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        if (e.button() == 0 && scroll.maximum() > 0 && isMouseOver(e.x(), e.y()) && e.x() >= left + width - 6) {
            draggingBar = true; dragBar(e.y()); return true;
        }
        return isMouseOver(e.x(),e.y()) && super.mouseClicked(e, twice);
    }
    private void dragBar(double y) {
        int thumb = Math.max(10, (int)((long)height * height / (height + scroll.maximum())));
        scroll.restore((int)Math.round((y - top - thumb / 2.0) * scroll.maximum() / Math.max(1, height - thumb))); position();
    }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (draggingBar) { dragBar(e.y()); return true; }
        return super.mouseDragged(e, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) {
        if (draggingBar) { draggingBar = false; return true; }
        return super.mouseReleased(e);
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == 266 || e.key() == 267) { scroll.move(e.key() == 266 ? -height : height); position(); return true; }
        if (index != null && (e.key() == 268 || e.key() == 269)) { scroll.restore(e.key() == 268 ? 0 : scroll.maximum()); position(); return true; }
        return super.keyPressed(e);
    }
    @Override public void setFocused(GuiEventListener child) {
        super.setFocused(child);
        for (var item : items) if (item.widget() == child) {
            var rectangle=item.widget().getRectangle();
            int originalTop=item.originalY()+rectangle.top()-item.widget().getY()-top;
            scroll.reveal(originalTop,originalTop+rectangle.height(),height);position();break;
        }
    }
    @Override public NarrationPriority narrationPriority() { return getFocused() == null ? NarrationPriority.NONE : NarrationPriority.FOCUSED; }
    @Override public void updateNarration(NarrationElementOutput output) { if (getFocused() instanceof NarratableEntry entry) entry.updateNarration(output); }
}
