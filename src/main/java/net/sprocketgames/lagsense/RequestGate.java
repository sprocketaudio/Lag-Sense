package net.sprocketgames.lagsense;

import java.util.*;

/** Server-thread request budget; entries are removed on logout. */
final class RequestGate {
    private final long interval;
    private final Map<UUID, Long> accepted = new HashMap<>();
    RequestGate(long interval) { this.interval = interval; }
    boolean allow(UUID player, long now) {
        Long previous = accepted.get(player);
        if (previous != null && now - previous < interval) return false;
        accepted.put(player, now); return true;
    }
    void forget(UUID player) { accepted.remove(player); }
    void clear() { accepted.clear(); }
}
