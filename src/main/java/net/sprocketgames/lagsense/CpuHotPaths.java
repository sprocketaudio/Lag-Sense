package net.sprocketgames.lagsense;

import java.util.*;
import java.util.function.Function;

/** Attribution only: never derives elapsed time from a sample count. */
public final class CpuHotPaths {
    public record Frame(String type, String method) {}
    public record Path(String owner, String method) {}
    public static boolean adapterFrame(String type){return type.startsWith("net.sprocketgames.lagsense.CallbackTimings$Timed")||type.equals("net.sprocketgames.lagsense.ProfilingEventBus");}
    public static Path chooseOwned(List<CpuInvestigation.Frame> frames){
        var owners=new HashMap<String,String>();var values=new ArrayList<Frame>(frames.size());
        for(var f:frames){owners.putIfAbsent(f.type(),f.owner());values.add(new Frame(f.type(),f.name()));}
        return choose(values,type->owners.getOrDefault(type,"unattributed"));
    }
    public static Path choose(List<Frame> frames, Function<String, String> ownerOf) {
        Path mod = null, vanilla = null, unknown = null; boolean event = false;
        for (var frame : frames) {
            if (adapterFrame(frame.type())) continue;
            if (frame.type().startsWith("net.sprocketgames.lagsense.")) return null;
            event |= frame.type().startsWith("net.neoforged.bus.");
            if (frame.method().contains("lagsense$")) continue;
            String owner = ownerOf.apply(frame.type());
            String label = frame.type() + "#" + frame.method(); if (label.length() > 240) label = label.substring(0, 240);
            var path = new Path(owner, label);
            if (owner.equals("minecraft")) { if (vanilla == null) vanilla = path; }
            else if (owner.equals("unattributed")) { if (unknown == null) unknown = path; }
            else if (!owner.equals("platform") && mod == null) mod = path;
        }
        var chosen = mod != null ? mod : unknown != null ? unknown : vanilla;
        return chosen == null ? null : event ? new Path(chosen.owner(), "Event callback · " + chosen.method()) : chosen;
    }
}
