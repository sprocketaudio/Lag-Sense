package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.UiMetrics;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

/** Shared second row for profile views. Extra controls scroll instead of wrapping. */
final class ProfileControls {
    static final int PATH_THREADS=120,PATH_GROUP=124,PATH_MOD=88,PATH_SORT=84;
    private static final int PATH_FILTERS=PATH_THREADS+PATH_GROUP+PATH_MOD+PATH_SORT+4*UiMetrics.GAP;
    private final LagScreen screen;
    private final UiMetrics.Flow flow;
    private final int y,right,collapseWidth;
    private final Runnable collapse;
    private final boolean expanded;
    ProfileControls(LagScreen screen,int y,Runnable collapse,boolean expanded){
        this.screen=screen;this.y=y;this.collapse=collapse;this.expanded=expanded;right=screen.profileWidth()-screen.margin();collapseWidth=collapse==null?0:collapseWidth();
        screen.beginControls(y,right-(collapseWidth==0?0:collapseWidth+8),32);flow=new UiMetrics.Flow(screen.margin(),100000,y+12);
    }
    static UiDropdown.Choice choice(String id,String label){return new UiDropdown.Choice(id,label);}
    private void label(String label,UiMetrics.Rect rect){screen.control(new UiLabel(UiFonts.body(),rect.x(),rect.y()-12,rect.width(),label));}
    private static int collapseWidth(){return Math.max(78,UiFonts.labelWidth("Collapse All")+12);}
    private int searchWidth(){
        // Use the busiest normal row on every tab so switching views never resizes Search.
        return Math.clamp(screen.contentWidth()-collapseWidth()-8-PATH_FILTERS,84,168);
    }
    UiInput search(String value,Consumer<String> changed){var rect=flow.next(searchWidth());label("Search",rect);var field=screen.control(new UiInput(UiFonts.body(),rect.x(),rect.y(),rect.width(),"Search"));field.setMaxLength(256);field.setValue(value);field.setHint(Component.literal("Name or ID"));field.setResponder(changed);UiHints.hint(field,"Search these captured results.");return field;}
    UiDropdown dropdown(String label,int width,List<UiDropdown.Choice> choices,String value,Consumer<String> changed){var rect=flow.next(width);label(label,rect);return screen.dropdown(label,rect,choices,value,changed);}
    void action(String label,int width,Runnable run){screen.action(label,flow.next(width),run);}
    void toggle(String label,int width,boolean on,Runnable run){screen.toggle(label,flow.next(width),on,run);}
    int finish(){screen.endControls();if(collapse!=null)screen.action("Collapse All",right-collapseWidth,y+12,collapseWidth,collapse).active=expanded;return y+32;}
}
