package audio.sprocket.lagsense;

/** Presentation scale only; never changes captured values or clips a recorded spike. */
public final class FrameGraph {
    public static final double[] REFERENCES = {8.3, 16.7, 33.3, 50};
    public static double ceiling(FrameProfile profile) {
        double max = Math.max(profile.worstMs(), profile.graphMs().stream().mapToDouble(Double::doubleValue).max().orElse(0));
        double step = max <= 10 ? 1 : max <= 50 ? 5 : max <= 200 ? 10 : 50;
        return Math.max(2, Math.ceil(max * 1.08 / step) * step);
    }
    public static int firstFrame(FrameProfile p, int bucket) { return Math.min(p.frames(), bucket * Math.max(1, (int)Math.ceil(p.frames() / 120.0)) + 1); }
    public static int lastFrame(FrameProfile p, int bucket) { return Math.min(p.frames(), (bucket + 1) * Math.max(1, (int)Math.ceil(p.frames() / 120.0))); }
    private FrameGraph() {}
}
