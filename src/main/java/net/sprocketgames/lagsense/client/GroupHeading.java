package net.sprocketgames.lagsense.client;

import java.util.Locale;

/** Shared labels for groups of measured targets. */
final class GroupHeading {
    static String format(String name,long count){return name+" ("+String.format(Locale.ROOT,"%,d",count)+")";}
    private GroupHeading(){}
}
