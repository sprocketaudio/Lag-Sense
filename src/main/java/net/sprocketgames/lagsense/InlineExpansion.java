package net.sprocketgames.lagsense;

/** One expanded item; list pagination and scrolling have separate state. */
public final class InlineExpansion {
    public Object key;
    public boolean active() { return key != null; }
    public boolean is(Object value) { return java.util.Objects.equals(key, value); }
    public void toggle(Object value) { if (is(value)) clear(); else key = value; }
    public void clear() { key = null; }
}
