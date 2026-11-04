package audio.sprocket.lagsense;

public final class PageNumbers {
    private PageNumbers() {}
    public static int count(int items, int rows) { return Math.max(1, (int)Math.ceil(Math.max(0, items) / (double)Math.max(1, rows))); }
    public static int clamp(int page, int items, int rows) { return Math.clamp(page, 0, count(items, rows) - 1); }
    public static String label(int page, int items, int rows) { return "Page " + (clamp(page, items, rows) + 1) + " of " + count(items, rows); }
}
