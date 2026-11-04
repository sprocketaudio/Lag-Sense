package net.sprocketgames.lagsense;

import java.util.*;
import net.neoforged.fml.ModList;

public final class ModOwnership {
    private record Jar(String filename, String ids) {}
    private final List<Jar> jars = ModList.get().getModFiles().stream().map(f -> new Jar(f.getFile().getFilePath().getFileName().toString().toLowerCase(Locale.ROOT),
        String.join("/", f.getMods().stream().map(m -> m.getModId()).toList()))).toList();
    public static String resolvableClass(String name){
        int lambda=name.indexOf("$$Lambda");if(lambda>=0)return name.substring(0,lambda);
        int hidden=name.indexOf("/0x");return hidden<0?name:name.substring(0,hidden);
    }
    public String owner(Class<?> type) {
        try {
            var source = type.getProtectionDomain().getCodeSource(); String location = source == null ? "" : source.getLocation().toString().toLowerCase(Locale.ROOT);
            for (var jar : jars) if (!jar.filename().isBlank() && location.contains(jar.filename())) return jar.ids();
        } catch (RuntimeException ignored) { }
        return type.getName().startsWith("net.neoforged.") ? "neoforge" : "unattributed";
    }
}
