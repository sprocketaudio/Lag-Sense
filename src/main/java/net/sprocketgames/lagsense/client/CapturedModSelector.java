package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Existing inline Call Paths selector, shared with the map without introducing another screen. */
final class CapturedModSelector {
    static EditBox search(LagScreen screen,int top,String query,Consumer<String> changed,Runnable cancel){
        var search=screen.input("Search mod name or ID",screen.margin(),top,screen.contentWidth()-72,query,".*",128);search.setHint(Component.literal("Search mod name or ID"));
        screen.action("Cancel",screen.profileWidth()-screen.margin()-66,top,66,cancel);search.setResponder(changed);return search;
    }
    static UiResults rows(LagScreen screen,int top,CapturedMods mods,String query,String owner,Consumer<String> choose,String all){
        var out=new UiResults(screen,top,0,true,false);out.row("all-mods","All mods",all,"",UiTheme.TEXT,0,owner==null,null,()->choose.accept(null));
        for(var mod:mods.matching(query))out.row(mod.id(),mod.name(),mod.id(),"",UiTheme.TEXT,0,mod.id().equals(owner),mod.id(),()->choose.accept(mod.id()));
        if(out.entries.size()==1&&!query.isBlank())out.details("none",List.of("No matching captured mods."),0);return out;
    }
    private CapturedModSelector(){}
}
