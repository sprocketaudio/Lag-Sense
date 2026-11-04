package net.sprocketgames.lagsense;

import java.util.Set;

/** Capture-start ceilings. No buffers are reserved or allocated to fill these budgets. */
public record ProfilingLimits(String mode,int eventMiB,int cpuMiB,int jfrMiB,int stackDepth) {
    public static final long MIB=1L<<20;
    public ProfilingLimits {
        if(!Set.of("adaptive","custom").contains(mode))throw new IllegalArgumentException("Resource mode must be Adaptive or Custom.");
        if(eventMiB<1||eventMiB>512||cpuMiB<1||cpuMiB>512)throw new IllegalArgumentException("Profiling budgets must be 1–512 MiB.");
        if(jfrMiB<16||jfrMiB>512)throw new IllegalArgumentException("JFR storage must be 16–512 MiB.");
        if(stackDepth<16||stackDepth>512)throw new IllegalArgumentException("Sampled stack depth must be 16–512 frames.");
    }
    public static ProfilingLimits defaults(){return new ProfilingLimits("adaptive",128,128,128,128);}
    public record Effective(long events,long cpu,long jfr,int depth){}
    public Effective effective(){var r=Runtime.getRuntime();return effective(r.maxMemory(),r.totalMemory()-r.freeMemory());}
    public Effective effective(long heap,long used){
        long headroom=Math.max(0,heap-Math.max(0,used));
        // Each of three independent in-memory scopes gets at most 1/32 of the heap
        // in Adaptive mode, and 1/16 of free headroom even with Custom ceilings.
        long safe=Math.max(0,Math.min(heap/8,headroom/16));
        long adaptive=Math.min(128*MIB,Math.min(heap/32,headroom/32));
        return new Effective(mode.equals("adaptive")?adaptive:Math.min(eventMiB*MIB,safe),
            mode.equals("adaptive")?adaptive:Math.min(cpuMiB*MIB,safe),
            Math.max(16*MIB,mode.equals("adaptive")?Math.min(128*MIB,heap/16):jfrMiB*MIB),mode.equals("adaptive")?128:stackDepth);
    }
    public DiagnosticBudget events(Effective e){return new DiagnosticBudget(e.events(),(mode.equals("adaptive")?128:eventMiB)*MIB,mode);}
    public DiagnosticBudget cpu(Effective e){return new DiagnosticBudget(e.cpu(),(mode.equals("adaptive")?128:cpuMiB)*MIB,mode);}
}
