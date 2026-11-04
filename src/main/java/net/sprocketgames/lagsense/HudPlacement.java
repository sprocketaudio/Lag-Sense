package net.sprocketgames.lagsense;

/** The same viewport clamping is used in gameplay and the positioning preview. */
public record HudPlacement(int x, int y, int width, int height) {
    public static HudPlacement at(int viewportWidth, int viewportHeight, int x, int y, float scale, boolean marked) {
        int width = (int)(240 * scale), height = (int)((marked ? 86 : 73) * scale);
        return new HudPlacement(Math.max(0, Math.min(x, viewportWidth - width)),
            Math.max(0, Math.min(y, viewportHeight - height)), width, height);
    }
    public boolean overlaps(int left, int top, int width, int height) {
        return x < left + width && left < x + this.width && y < top + height && top < y + this.height;
    }
    public HudPlacement corner(int viewportWidth, int viewportHeight, boolean right, boolean bottom) {
        int x = right ? Math.max(0, viewportWidth - width - 8) : Math.min(8, Math.max(0, viewportWidth - width));
        int y = bottom ? Math.max(0, viewportHeight - height - 8) : Math.min(8, Math.max(0, viewportHeight - height));
        return new HudPlacement(x, y, width, height);
    }
}
