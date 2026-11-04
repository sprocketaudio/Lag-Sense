package net.sprocketgames.lagsense.client;

/** Session-only override for performance visuals. Individual display preferences and captured data are never edited. */
public final class VisualVisibility {
    private static volatile boolean hidden;
    public static boolean visible(){return !hidden;}
    public static boolean toggle(){hidden=!hidden;return !hidden;}
    public static void reset(){hidden=false;}
    private VisualVisibility(){}
}
