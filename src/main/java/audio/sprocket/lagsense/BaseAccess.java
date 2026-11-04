package audio.sprocket.lagsense;

import java.util.*;
import java.util.function.Predicate;

/** Request-scoped authorization. No ownership decisions survive into the next request. */
public final class BaseAccess implements Predicate<Report.Entry> {
    public record ChunkKey(String dimension, int x, int z) {}
    public enum Decision { UNCLAIMED, ALLOW, DENY }
    @FunctionalInterface public interface Provider { Decision check(ChunkKey chunk); }
    private final List<PublicArea> areas;
    private final List<Provider> providers;
    private final Map<ChunkKey, Boolean> chunks = new HashMap<>();

    public BaseAccess(List<PublicArea> areas, List<Provider> providers) {
        this.areas = List.copyOf(areas);
        this.providers = List.copyOf(providers);
    }

    @Override public boolean test(Report.Entry entry) {
        return contains(entry.dimension(),entry.x(),entry.z());
    }
    public boolean contains(String dimension,int x,int z){
        // Explicit administrator sharing also works without a claims mod.
        for (var area : areas) if (area.contains(dimension,x,z)) return true;
        return chunks.computeIfAbsent(new ChunkKey(dimension, x >> 4, z >> 4), key -> {
            boolean claimed = false;
            for (Provider provider : providers) {
                Decision decision = provider.check(key);
                // Conflicting claims must not disclose another owner's data.
                if (decision == null || decision == Decision.DENY) return false;
                claimed |= decision == Decision.ALLOW;
            }
            return claimed;
        });
    }
}
