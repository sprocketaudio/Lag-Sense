package net.sprocketgames.lagsense;

/** Shared logical-pixel layout; independent of GUI scale and rendering. */
public final class UiMetrics {
    public static final int PROFILE_DIVIDER = 48, PROFILE_TABS = 56;
    public static final int GAP = 4, GROUP = 12, CONTROL = 20, ROW = 34, PAGER = 160;
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public int cursorX() { return x; }
        public boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
    }
    public static int left(int width) { return Math.max(8, (width - 680) / 2); }
    public static int settingsLeft(int width) { return Math.max(8, (width - 336) / 2); }
    public static int rows(int height, int top, int rowHeight) { return Math.max(1, (height - 84 - top) / rowHeight); }
    public static int footer(int height) { return height - 44; }
    public static int pagerY(int height) { return height - 68; }
    public static final class Flow {
        private final int left, right, height;
        private int x, y;
        public Flow(int left, int right, int y) { this(left, right, y, CONTROL); }
        public Flow(int left, int right, int y, int height) { this.left = left; this.right = right; this.x = left; this.y = y; this.height = height; }
        public Rect next(int width) {
            width = Math.min(width, right - left);
            if (x != left && x + width > right) { x = left; y += height + GAP; }
            var rect = new Rect(x, y, width, height); x += width + GAP; return rect;
        }
        public void group() { if (x != left) x += GROUP - GAP; }
        public void group(int width) { if (x != left && x + GROUP - GAP + width > right) { x = left; y += height + GAP; } else group(); }
        public int cursorX(){return x;}
        public int bottom() { return y + height; }
    }
}
