package net.sprocketgames.lagsense;

import java.util.*;

/** Bounded, client-local frame intervals. CPU stage totals can overlap. */
public record FrameProfile(long capturedAt, int frames, double seconds, double fps, double onePercentLow,
                           double medianMs, double p95Ms, double p99Ms, double worstMs, int stutters,
                           boolean limited, List<Double> graphMs, List<Stage> stages, Gpu gpu) {
    public record Stage(String name, double msPerFrame, long calls) {}
    public record Gpu(int samples, double meanMs, double p95Ms, double worstMs, String status) {}
    public static final class Capture {
        private final long[] intervals = new long[60000];
        private final Map<String, long[]> stages = new LinkedHashMap<>();
        private int size;
        private final long[] gpuTimes = new long[60000];
        private int gpuSize;
        private String gpuStatus = "No GPU samples";
        private boolean limited;
        public void frame(long nanos) {
            if (nanos <= 0) return;
            if (size == intervals.length) { limited = true; return; }
            intervals[size++] = nanos;
        }
        public void stage(String name, long nanos) {
            if (nanos < 0 || (!stages.containsKey(name) && stages.size() >= 8)) return;
            var value = stages.computeIfAbsent(name, k -> new long[2]); value[0] += nanos; value[1]++;
        }
        public boolean full() { return size == intervals.length; }
        public void gpu(long nanos) { if (nanos > 0 && gpuSize < gpuTimes.length) gpuTimes[gpuSize++] = nanos; }
        public void gpuStatus(String status) { gpuStatus = status; }
        public FrameProfile finish() {
            var sorted = Arrays.copyOf(intervals, size); Arrays.sort(sorted);
            double total = 0, slow = 0; int stutters = 0, slowCount = Math.max(1, (int)Math.ceil(size * .01));
            for (int i = 0; i < size; i++) { total += sorted[i]; if (i >= size - slowCount) slow += sorted[i]; if (sorted[i] > 50_000_000) stutters++; }
            var graph = new ArrayList<Double>();
            for (int start = 0; start < size; ) {
                int end = Math.min(size, start + Math.max(1, (int)Math.ceil(size / 120.0))); long max = 0;
                for (int i = start; i < end; i++) max = Math.max(max, intervals[i]);
                graph.add(max / 1_000_000.0); start = end;
            }
            var cpu = stages.entrySet().stream().map(e -> new Stage(e.getKey(), e.getValue()[0] / 1_000_000.0 / Math.max(1, size), e.getValue()[1])).toList();
            long[] gpuSorted = Arrays.copyOf(gpuTimes, gpuSize); Arrays.sort(gpuSorted); double gpuTotal = 0; for (long n : gpuSorted) gpuTotal += n;
            var gpu = new Gpu(gpuSize, gpuTotal / Math.max(1, gpuSize) / 1_000_000.0, percentile(gpuSorted, .95), gpuSize == 0 ? 0 : gpuSorted[gpuSize - 1] / 1_000_000.0, gpuSize == 0 ? gpuStatus : "Measured");
            return new FrameProfile(System.currentTimeMillis(), size, total / 1_000_000_000.0, total == 0 ? 0 : size * 1_000_000_000.0 / total,
                slow == 0 ? 0 : slowCount * 1_000_000_000.0 / slow, percentile(sorted, .5), percentile(sorted, .95), percentile(sorted, .99),
                size == 0 ? 0 : sorted[size - 1] / 1_000_000.0, stutters, limited || size == intervals.length, List.copyOf(graph), cpu, gpu);
        }
        private static double percentile(long[] values, double p) { return values.length == 0 ? 0 : values[Math.max(0, (int)Math.ceil(values.length * p) - 1)] / 1_000_000.0; }
    }
    public String csv() {
        var out = new StringBuilder("metric,value,unit\nframes," + frames + ",count\nseconds," + seconds + ",s\nfps," + fps + ",fps\none_percent_low," + onePercentLow + ",fps\nmedian," + medianMs + ",ms\np95," + p95Ms + ",ms\np99," + p99Ms + ",ms\nworst," + worstMs + ",ms\nstutters_over_50ms," + stutters + ",count\n");
        for (Stage stage : stages) out.append(stage.name()).append(',').append(stage.msPerFrame()).append(",CPU ms/frame (inclusive)\n");
        if (gpu != null) {
            out.append("gpu_status,").append(gpu.status()).append(",\ngpu_samples,").append(gpu.samples()).append(",count\n");
            if (gpu.samples() > 0) out.append("gpu_mean,").append(gpu.meanMs()).append(",ms\ngpu_p95,").append(gpu.p95Ms()).append(",ms\ngpu_worst,").append(gpu.worstMs()).append(",ms\n");
        }
        out.append("\nbucket,max_frame_ms\n"); for (int i = 0; i < graphMs.size(); i++) out.append(i).append(',').append(graphMs.get(i)).append('\n');
        return out.toString();
    }
}
