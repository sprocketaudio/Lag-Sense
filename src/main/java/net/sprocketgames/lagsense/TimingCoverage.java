package net.sprocketgames.lagsense;

import java.util.Locale;

/** Server-thread interval unions. Hook timestamps are reused; no allocation or clock per scope. */
public final class TimingCoverage {
    public record Snapshot(long totalNanos, long targetNanos, long eventNanos, long overlapNanos, long outsideNanos) {
        public Snapshot {
            if (totalNanos < 0 || targetNanos < 0 || eventNanos < 0 || overlapNanos < 0 || outsideNanos < 0
                || targetNanos > totalNanos || eventNanos > totalNanos || overlapNanos > Math.min(targetNanos,eventNanos)
                || eventNanos-overlapNanos > totalNanos-targetNanos
                || outsideNanos != totalNanos-targetNanos-(eventNanos-overlapNanos))
                throw new IllegalArgumentException("Invalid timing coverage");
        }
        public String describe(int ticks, String eventState) {
            return line("Timed targets",targetNanos,ticks)+"\n"+line("Timed events",eventNanos,ticks)
                +"\n"+line("Overlapping time",overlapNanos,ticks)+"\n"+line("Outside timed categories",outsideNanos,ticks)
                +("disabled".equals(eventState)?"\nDetailed event timing is off.":"");
        }
        private String line(String name,long nanos,int ticks) {
            return String.format(Locale.ROOT,"%s: %.2f ms/t (%.1f%%)",name,nanos/1_000_000.0/Math.max(1,ticks),totalNanos==0?0:100.0*nanos/totalNanos);
        }
    }
    private boolean cycle;
    private int targets,events;
    private long cycleStart,targetStart,eventStart,unionStart,total,target,event,union;
    public void reset(){cycle=false;targets=events=0;total=target=event=union=0;}
    public void beginCycle(long now){if(cycle)throw new IllegalStateException("Cycle already open");cycle=true;cycleStart=now;targets=events=0;}
    public boolean beginTarget(long now){if(!cycle)return false;if(targets+events==0)unionStart=now;if(targets++==0)targetStart=now;return true;}
    public boolean beginEvent(long now){if(!cycle)return false;if(targets+events==0)unionStart=now;if(events++==0)eventStart=now;return true;}
    public void endTarget(long now){if(!cycle||targets==0)throw new IllegalStateException("Unbalanced target timing");if(--targets==0)target+=elapsed(targetStart,now);if(targets+events==0)union+=elapsed(unionStart,now);}
    public void endEvent(long now){if(!cycle||events==0)throw new IllegalStateException("Unbalanced event timing");if(--events==0)event+=elapsed(eventStart,now);if(targets+events==0)union+=elapsed(unionStart,now);}
    public void endCycle(long now){
        if(!cycle)return;
        if(targets!=0||events!=0)throw new IllegalStateException("Timing scope outlived server cycle");
        total+=elapsed(cycleStart,now);cycle=false;
    }
    public Snapshot snapshot(){return new Snapshot(total,target,event,target+event-union,total-union);}
    private static long elapsed(long start,long end){return Math.max(0,end-start);}
}
