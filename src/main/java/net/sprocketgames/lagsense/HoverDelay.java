package net.sprocketgames.lagsense;

/** One shared hover target, with no timers or background work. Times are monotonic milliseconds. */
public final class HoverDelay {
    public static final long INITIAL = 400, SUBSEQUENT = 150, RECENT = 1200;
    private Object target;
    private long entered, delay, lastShown = Long.MIN_VALUE;
    public boolean visit(Object key, long now) {
        if (key == null) { target = null; return false; }
        if (!key.equals(target)) { target = key; entered = now; delay = lastShown != Long.MIN_VALUE && now - lastShown <= RECENT ? SUBSEQUENT : INITIAL; }
        if (now - entered < delay) return false;
        lastShown = now; return true;
    }
    public void clear() { target = null; lastShown = Long.MIN_VALUE; }
}
