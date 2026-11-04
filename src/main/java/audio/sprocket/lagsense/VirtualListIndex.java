package audio.sprocket.lagsense;

import java.util.*;

/** Pixel offsets over bounded row descriptions; no widgets or rendering state. */
public final class VirtualListIndex {
    private final int[] tops;
    private final Map<Object,Integer> positions = new HashMap<>();
    public VirtualListIndex(List<?> keys, int[] heights) {
        if (keys.size() != heights.length) throw new IllegalArgumentException("Mismatched rows");
        tops = new int[heights.length + 1];
        for (int i = 0; i < heights.length; i++) {
            if (heights[i] < 1) throw new IllegalArgumentException("Empty row");
            tops[i+1] = Math.addExact(tops[i], heights[i]); positions.put(keys.get(i), i);
        }
    }
    public int size() { return tops.length - 1; }
    public int height() { return tops[tops.length - 1]; }
    public int top(int row) { return tops[row]; }
    public int at(int offset) {
        if (size() == 0) return -1;
        int found = Arrays.binarySearch(tops, Math.clamp(offset, 0, Math.max(0, height()-1)));
        return found >= 0 ? found : -found - 2;
    }
    public int position(Object key) { return positions.getOrDefault(key, -1); }
}
