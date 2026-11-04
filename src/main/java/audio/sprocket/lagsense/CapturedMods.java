package audio.sprocket.lagsense;

import java.util.*;
import java.util.function.Function;

/** Search only captured owners, with display names resolved once outside rendering. */
public final class CapturedMods {
    public record Mod(String id,String name,String search) {}
    private final List<Mod> mods;
    public CapturedMods(Collection<String> captured,Function<String,String> nameOf) {
        mods=captured.stream().distinct().map(id->{String name=nameOf.apply(id);return new Mod(id,name,(id+"\n"+name).toLowerCase(Locale.ROOT));})
            .sorted(Comparator.comparing(Mod::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Mod::id)).toList();
    }
    public List<Mod> matching(String query) {
        String term=query.strip().toLowerCase(Locale.ROOT);
        return term.isEmpty()?mods:mods.stream().filter(mod->mod.search().contains(term)).toList();
    }
}
