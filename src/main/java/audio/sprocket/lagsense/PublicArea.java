package audio.sprocket.lagsense;

import java.util.*;

/** Inclusive block-column footprint, covering every Y coordinate in exactly one dimension. */
public record PublicArea(String name, String dimension, int minX, int minZ, int maxX, int maxZ) {
    public static final int LIMIT = 64;
    public record Point(int x, int z) {
        public Point { if (Math.abs((long)x) > 30_000_000 || Math.abs((long)z) > 30_000_000) throw new IllegalArgumentException("Corners must be within Minecraft's coordinate limits."); }
    }
    public record Selection(String dimension, Point first, Point second) {
        public Selection { validateDimension(dimension); Objects.requireNonNull(first, "Set corner 1 first."); }
        public PublicArea area(String name) {
            if (second == null) throw new IllegalArgumentException("Set corner 2 with /lagsense area pos2 first.");
            return between(name, dimension, first, second);
        }
    }
    public PublicArea {
        if (name == null || !name.matches("[a-z0-9_-]{1,32}")) throw new IllegalArgumentException("Use a name of 1–32 lowercase letters, numbers, underscores or hyphens.");
        validateDimension(dimension);
        new Point(minX, minZ); new Point(maxX, maxZ);
        if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("Area bounds are reversed.");
    }
    private static void validateDimension(String dimension) {
        if (dimension == null || dimension.length() > 256 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("Invalid area dimension.");
    }
    public static PublicArea between(String name, String dimension, Point first, Point second) {
        return new PublicArea(name, dimension, Math.min(first.x(), second.x()), Math.min(first.z(), second.z()), Math.max(first.x(), second.x()), Math.max(first.z(), second.z()));
    }
    public boolean contains(String dimension, int x, int z) { return this.dimension.equals(dimension) && x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
    public boolean contains(Report.Entry entry) { return contains(entry.dimension(), entry.x(), entry.z()); }
    public long width() { return (long)maxX - minX + 1; }
    public long depth() { return (long)maxZ - minZ + 1; }
    public static List<PublicArea> validated(List<PublicArea> values) {
        if (values.size() > LIMIT) throw new IllegalArgumentException("At most 64 public areas may be saved.");
        Set<String> names = new HashSet<>();
        for (PublicArea area : values) {
            Objects.requireNonNull(area, "Invalid public area.");
            if (!names.add(area.name())) throw new IllegalArgumentException("Public area names must be unique.");
        }
        return List.copyOf(values);
    }
}
