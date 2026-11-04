package net.sprocketgames.lagsense.client;

import java.util.*;
import net.neoforged.fml.ModList;

/** Local display names when installed; server-only mods retain their recorded IDs. */
final class ModNames {
    private static final Map<String,String> names=new java.util.concurrent.ConcurrentHashMap<>();
    static String name(String id){return names.computeIfAbsent(id,key->{var list=ModList.get();return list==null?key:list.getModContainerById(key).map(c->c.getModInfo().getDisplayName()).orElse(key);});}
    private ModNames(){}
}
