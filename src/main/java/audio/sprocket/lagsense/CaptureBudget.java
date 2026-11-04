package audio.sprocket.lagsense;

/** A memory safety stop, rather than silent per-target loss. */
public final class CaptureBudget {
    public static final int MAX_FILE_BYTES=128*1024*1024, MAX_ENTRIES=1_000_000;
    private long maximum;
    private final long ceiling;
    private long used;
    public CaptureBudget(long heap){maximum=ceiling=Math.clamp(heap/32,8L*1024*1024,128L*1024*1024);}
    public void reset(){used=0;var r=Runtime.getRuntime();maximum=Math.min(ceiling,Math.max(0,(r.maxMemory()-(r.totalMemory()-r.freeMemory()))/16));}
    public boolean add(String type,String dimension){return add(type,dimension,false);}
    public boolean add(String type,String dimension,boolean entity){long charge=(entity?640L:192L)+2L*(type.length()+dimension.length());if(used+charge>maximum)return false;used+=charge;return true;}
    public long maximum(){return maximum;}
}
