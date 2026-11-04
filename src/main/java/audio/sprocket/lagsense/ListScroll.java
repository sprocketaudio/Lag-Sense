package audio.sprocket.lagsense;

/** Scroll position belongs to a list page, independently of expansion and result counts. */
public final class ListScroll {
    private int page = -1;
    private int offset, maximum;
    public int offset() { return offset; }
    public int maximum() { return maximum; }
    public void restore(int value) { offset = Math.clamp(value, 0, maximum); }
    public void page(int value) { if (page != value) { page = value; offset = 0; } }
    public void bounds(int contentHeight, int viewportHeight) {
        maximum = Math.max(0, contentHeight - viewportHeight);
        offset = Math.clamp(offset, 0, maximum);
    }
    public void move(int pixels) { offset = (int)Math.clamp((long)offset + pixels, 0, maximum); }
    public void reveal(int top, int bottom, int viewportHeight) {
        if (top < offset) offset = top;
        else if (bottom > offset + viewportHeight) offset = Math.min(top, bottom - viewportHeight);
        offset = Math.clamp(offset, 0, maximum);
    }
}
