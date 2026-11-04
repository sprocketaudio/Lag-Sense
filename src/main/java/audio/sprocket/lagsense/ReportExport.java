package audio.sprocket.lagsense;

import java.util.*;

public final class ReportExport {
    private ReportExport() {}
    public static String csv(Report report) {var out=new StringBuilder();writeCsv(report,out);return out.toString();}
    public static void writeCsv(Report report,Appendable destination) {
        var out=new CsvOutput(destination);out.append("kind,type,dimension,x,y,z,total_nanos,calls,captured_ticks,microseconds_per_tick,captured_at,cpu_samples,owner,event,max_call_nanos,average_call_nanos,entity_uuid,thread_id,thread_name,sample_percent,handler_id,sample_scope,method_id,node_id,parent_node_id,caller_method_id,callee_method_id,direct_samples,inclusive_samples,retained_samples,limit_info,inclusive_sample_percent,direct_sample_percent,sample_kind\r\n");
        row(out,"Profile state",Profiler.JSON.toJson(Map.of("profileId",report.id(),"capturedAt",report.capturedAt(),"ticks",report.ticks(),"mspt",report.mspt(),"tps",report.tps(),"truncated",report.truncated())));
        for(var e:report.entries())row(out,e.kind(),e.type(),e.dimension(),e.x(),e.y(),e.z(),e.nanos(),e.calls(),report.ticks(),number(e.micros(report.ticks())),report.capturedAt(),null,null,null,null,null,e.entityId()==null?null:e.entityId().toString(),null,null,null,null,null);
        if(report.chunks()!=null)for(var c:report.chunks())row(out,"Chunk total","captured permitted targets",c.dimension(),c.x(),null,c.z(),c.nanos(),null,report.ticks(),number(c.micros(report.ticks())),report.capturedAt(),null,null,null,null,null,null,null,null,null,null,null);
        if(report.admin()&&report.diagnostics()!=null){
            var d=report.diagnostics();var coverage=d.coverage();
            if(d.performance()!=null)row(out,"Capture performance",Profiler.JSON.toJson(d.performance()));
            row(out,"Capture diagnostics",Profiler.JSON.toJson(Map.of("trigger",d.trigger(),"bookkeepingNanos",d.bookkeepingNanos(),"snapshotNanos",d.snapshotNanos(),"callbackState",d.callbackState())));
            if(d.background()!=null){var bg=d.background();row(out,"Background CPU state",Profiler.JSON.toJson(Map.of("state",bg.state(),"samples",bg.samples(),"omittedActionObservations",bg.omittedSamples(),"limited",bg.limited(),"reason",bg.reason())));}
            if(coverage!=null){String[] names={"Tick window","Timed targets","Timed events","Overlapping time","Outside timed categories"};long[] values={coverage.totalNanos(),coverage.targetNanos(),coverage.eventNanos(),coverage.overlapNanos(),coverage.outsideNanos()};
                for(int i=0;i<names.length;i++)row(out,"Coverage",names[i],null,null,null,null,values[i],null,report.ticks(),number(values[i]/1000.0/Math.max(1,report.ticks())),report.capturedAt(),null,null,null,null,null,null,null,null,null,null,null);}
            long total=d.tasks().stream().mapToLong(Report.Task::samples).sum();
            for(var task:d.tasks())row(out,"CPU sample",task.method(),null,null,null,null,null,null,null,null,report.capturedAt(),task.samples(),task.owner(),null,null,null,null,null,d.cpu().thread(),number(total==0?0:100.0*task.samples()/total),null,"server listed actions");
            if(d.background()!=null){long background=d.background().threads().stream().flatMap(t->t.tasks().stream()).mapToLong(Report.Task::samples).sum();
                for(var worker:d.background().threads())for(var task:worker.tasks())row(out,"Background CPU sample",task.method(),null,null,null,null,null,null,null,null,report.capturedAt(),task.samples(),task.owner(),null,null,null,null,worker.id(),worker.name(),number(background==0?0:100.0*task.samples()/background),null,"background listed actions");}
            callRows(out,d.cpu(),null,"server");
            if(d.background()!=null)for(var worker:d.background().threads())callRows(out,worker.cpu(),worker.id(),"background");
            for(var note:d.notes())row(out,"Capture note",note);
            for(var c:d.callbacks())row(out,"Callback",c.method(),null,null,null,null,c.nanos(),c.calls(),report.ticks(),number(c.nanos()/1000.0/Math.max(1,report.ticks())),report.capturedAt(),null,c.owner(),c.event(),c.maxNanos(),c.nanos()/(double)Math.max(1,c.calls()),null,null,null,null,c.handlerId(),null);
        }
    }
    private static String number(double n){return String.format(Locale.ROOT,"%.6f",n);}
    private static void row(CsvOutput out,Object... fields){for(int i=0;i<34;i++){if(i>0)out.append(',');Object f=i<fields.length?fields[i]:null;if(f!=null)out.append(f instanceof Number?f:cell(f.toString()));}out.append("\r\n");}
    private static final class CsvOutput {
        final Appendable out;CsvOutput(Appendable out){this.out=out;}
        CsvOutput append(Object value){try{out.append(String.valueOf(value));return this;}catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}}
    }
    private static void callRows(CsvOutput out,CpuInvestigation cpu,Long threadId,String scope){
        row(out,"CPU state",Profiler.JSON.toJson(Map.of("thread",cpu.thread(),"state",cpu.state(),"samples",cpu.samples(),"treeSamples",cpu.treeSamples(),"limited",cpu.limited())),null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,threadId,cpu.thread(),null,null,scope);
        for(var m:cpu.methods())row(out,"CPU method",m.signature(),null,null,null,null,null,null,null,null,null,m.inclusive(),m.owner(),null,null,null,null,threadId,cpu.thread(),null,null,scope,m.id(),null,null,null,null,m.self(),m.inclusive(),cpu.treeSamples(),null,number(cpu.treeSamples()==0?0:100.0*m.inclusive()/cpu.treeSamples()),number(cpu.treeSamples()==0?0:100.0*m.self()/cpu.treeSamples()),"Java execution samples");
        for(var n:cpu.nodes())row(out,"Call-path node",n.method()<0?"Root":cpu.methods().get(n.method()).signature(),null,null,null,null,null,null,null,null,null,n.inclusive(),null,null,null,null,null,threadId,cpu.thread(),null,null,scope,n.method(),n.id(),n.parent(),null,null,n.self(),n.inclusive(),cpu.treeSamples(),null,number(cpu.treeSamples()==0?0:100.0*n.inclusive()/cpu.treeSamples()),number(cpu.treeSamples()==0?0:100.0*n.self()/cpu.treeSamples()),"Java execution samples");
        for(var e:cpu.edges())row(out,"Call-path relationship",cpu.methods().get(e.caller()).signature()+" → "+cpu.methods().get(e.callee()).signature(),null,null,null,null,null,null,null,null,null,e.samples(),null,null,null,null,null,threadId,cpu.thread(),null,null,scope,null,null,null,e.caller(),e.callee(),null,null,cpu.treeSamples(),null,number(cpu.treeSamples()==0?0:100.0*e.samples()/cpu.treeSamples()),null,"Java execution samples");
        if(cpu.limits()!=null)row(out,"Call-path limits",cpu.thread(),null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,threadId,cpu.thread(),null,null,scope,null,null,null,null,null,null,null,cpu.treeSamples(),Profiler.JSON.toJson(cpu.limits()));
    }
    public static String csv(ProfileComparison comparison) {
        if(comparison.details()!=null){
            var index=new ComparisonIndex(comparison);var out=new StringBuilder("group,dimension,kind,type,chunk_x,chunk_z,before_us_per_tick,after_us_per_tick,delta_us_per_tick,before_targets,after_targets,change_percent,level,entity_uuid,status,before_x,before_y,before_z,after_x,after_y,after_z,before_updates,after_updates,before_ticks,after_ticks,before_dimension,after_dimension,before_locations,after_locations,before_observed,after_observed\r\n");
            for(var node:index.all.types())appendNode(out,"types",node,comparison,0);for(var node:index.all.chunks())appendNode(out,"chunks",node,comparison,0);return out.toString();
        }
        StringBuilder out = new StringBuilder("group,dimension,kind,type,chunk_x,chunk_z,before_us_per_tick,after_us_per_tick,delta_us_per_tick,before_targets,after_targets,change_percent\r\n");
        append(out, "type", comparison.types()); append(out, "chunk", comparison.chunks()); return out.toString();
    }
    private static void appendNode(StringBuilder out,String group,ComparisonIndex.Node node,ProfileComparison c,int depth){
        var k=node.key();out.append(group).append(',').append(cell(k.dimension())).append(',').append(cell(k.kind())).append(',').append(cell(k.type())).append(',').append(k.x()).append(',').append(k.z()).append(',');
        out.append(node.earlier().targets()==0?"":String.format(Locale.ROOT,"%.6f",node.before(c))).append(',').append(node.later().targets()==0?"":String.format(Locale.ROOT,"%.6f",node.after(c))).append(',').append(String.format(Locale.ROOT,"%.6f",node.delta(c))).append(',');
        out.append(node.earlier().countKnown()?node.earlier().targets():"").append(',').append(node.later().countKnown()?node.later().targets():"").append(',').append(node.percent(c)==null?"":String.format(Locale.ROOT,"%.6f",node.percent(c))).append(',').append(depth).append(',').append(cell(node.uuid()==null?"":node.uuid().toString())).append(',').append(cell(node.status())).append(',');
        var a=node.earlier().location();var b=node.later().location();out.append(a==null?",,":a.x()+","+a.y()+","+a.z()).append(',').append(b==null?",,":b.x()+","+b.y()+","+b.z()).append(',').append(node.earlier().calls()).append(',').append(node.later().calls()).append(',').append(c.beforeTicks()).append(',').append(c.afterTicks()).append(',').append(cell(a==null?"":a.dimension())).append(',').append(cell(b==null?"":b.dimension())).append(',').append(node.earlier().locations()).append(',').append(node.later().locations()).append(',').append(node.earlier().targets()>0).append(',').append(node.later().targets()>0).append("\r\n");
        for(var child:node.children())appendNode(out,group,child,c,depth+1);
    }
    private static void append(StringBuilder out, String group, List<ProfileComparison.Change> changes) {
        for (var c : changes) out.append(group).append(',').append(cell(c.dimension())).append(',').append(cell(c.kind())).append(',').append(cell(c.type())).append(',')
            .append(c.x()).append(',').append(c.z()).append(',').append(String.format(Locale.ROOT, "%.6f,%.6f,%.6f", c.beforeMicros(), c.afterMicros(), c.delta()))
            .append(',').append(c.beforeTargets()).append(',').append(c.afterTargets()).append(',').append(c.percentChange()==null?"":String.format(Locale.ROOT,"%.6f",c.percentChange())).append("\r\n");
    }
    public static String json(ProfileComparison comparison){
        var tree=Profiler.JSON.toJsonTree(comparison).getAsJsonObject();
        for(String group:List.of("types","chunks")){
            var rows=group.equals("types")?comparison.types():comparison.chunks();var json=tree.getAsJsonArray(group);
            for(int i=0;i<rows.size();i++){var percent=rows.get(i).percentChange();json.get(i).getAsJsonObject().add("changePercent",percent==null?com.google.gson.JsonNull.INSTANCE:new com.google.gson.JsonPrimitive(percent));}
        }
        if(comparison.details()!=null){var index=new ComparisonIndex(comparison);var investigation=new com.google.gson.JsonObject();investigation.add("types",nodes(index.all.types(),comparison));investigation.add("chunks",nodes(index.all.chunks(),comparison));tree.add("investigation",investigation);}
        return tree.toString();
    }
    private static com.google.gson.JsonArray nodes(List<ComparisonIndex.Node> nodes,ProfileComparison c){
        var array=new com.google.gson.JsonArray();for(var node:nodes){var json=new com.google.gson.JsonObject();json.add("key",Profiler.JSON.toJsonTree(node.key()));json.addProperty("name",node.name());json.addProperty("status",node.status());if(node.uuid()!=null)json.addProperty("uuid",node.uuid().toString());json.add("earlier",stats(node.earlier()));json.add("later",stats(node.later()));json.addProperty("beforeObserved",node.earlier().targets()>0);json.addProperty("afterObserved",node.later().targets()>0);json.add("beforeMicros",node.earlier().targets()==0?com.google.gson.JsonNull.INSTANCE:new com.google.gson.JsonPrimitive(node.before(c)));json.add("afterMicros",node.later().targets()==0?com.google.gson.JsonNull.INSTANCE:new com.google.gson.JsonPrimitive(node.after(c)));json.addProperty("deltaMicros",node.delta(c));json.add("changePercent",node.percent(c)==null?com.google.gson.JsonNull.INSTANCE:new com.google.gson.JsonPrimitive(node.percent(c)));json.add("children",nodes(node.children(),c));array.add(json);}return array;
    }
    private static com.google.gson.JsonObject stats(ComparisonIndex.Stats stats){var json=new com.google.gson.JsonObject();json.addProperty("nanos",stats.nanos());json.addProperty("calls",stats.calls());json.addProperty("countKnown",stats.countKnown());json.add("targets",stats.countKnown()?new com.google.gson.JsonPrimitive(stats.targets()):com.google.gson.JsonNull.INSTANCE);json.addProperty("locations",stats.locations());if(stats.location()!=null){var e=stats.location();var position=new com.google.gson.JsonObject();position.addProperty("dimension",e.dimension());position.addProperty("x",e.x());position.addProperty("y",e.y());position.addProperty("z",e.z());json.add("location",position);}return json;}
    private static String cell(String value) {
        // Keep spreadsheet applications from interpreting text fields as formulas.
        if (!value.isEmpty() && "=+-@\t\r\n".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
