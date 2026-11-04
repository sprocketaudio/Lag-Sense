package audio.sprocket.lagsense;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Builds a bounded notification DTO. Administrator-configured webhooks receive the complete capture. */
public final class DiscordMessage {
    public record Row(String kind, String type, String dimension, int targets, double msPerTick, double percent, Integer x, Integer y, Integer z) {}
    public record Message(String json) {
        @Override public String toString() { return "DiscordMessage[content hidden]"; }
    }
    private record Key(String kind, String type, String dimension, int x, int y, int z) {}
    public static List<Row> rows(Report report,ServerSettings.Delivery settings){return rows(report,settings.groupBy().equals("type")?"type":"target");}
    private static List<Row> rows(Report report,String grouping) {
        if (grouping.equals("target")) return report.entries().stream().map(e -> {
            double ms = e.nanos() / 1_000_000.0 / Math.max(1, report.ticks());
            return new Row(e.kind(), e.type(), e.dimension(), 1, ms, report.mspt() > 0 ? ms / report.mspt() * 100 : 0,
                e.x(), e.y(), e.z());
        }).sorted(Comparator.comparingDouble(Row::msPerTick).reversed().thenComparing(Row::type).thenComparing(Row::dimension)).toList();
        Map<Key, List<Report.Entry>> grouped = new LinkedHashMap<>();
        for (var e : report.entries()) {
            boolean type = grouping.equals("type");
            Key key = new Key(e.kind(), e.type(), e.dimension(), type ? 0 : e.x(), type ? 0 : e.y(), type ? 0 : e.z());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        List<Row> result = new ArrayList<>();
        for (var group : grouped.entrySet()) {
            Key key = group.getKey(); long nanos = group.getValue().stream().mapToLong(Report.Entry::nanos).sum();
            double ms = nanos / 1_000_000.0 / Math.max(1, report.ticks());
            double percent = report.mspt() > 0 ? ms / report.mspt() * 100 : 0;
            boolean positioned = grouping.equals("target");
            result.add(new Row(key.kind(), key.type(), key.dimension(), group.getValue().size(), ms, percent,
                positioned ? key.x() : null, positioned ? key.y() : null, positioned ? key.z() : null));
        }
        return result.stream().sorted(Comparator.comparingDouble(Row::msPerTick).reversed().thenComparing(Row::type).thenComparing(Row::dimension)).toList();
    }
    public static boolean qualifies(Row row, ServerSettings.Delivery settings) {
        boolean ms = settings.minimumMs() > 0, percent = settings.minimumPercent() > 0;
        if (!ms && !percent) return true;
        boolean a = row.msPerTick() >= settings.minimumMs(), b = row.percent() >= settings.minimumPercent();
        return settings.match().equals("both") ? (!ms || a) && (!percent || b) : ms && a || percent && b;
    }
    public static Message build(Report report, ServerSettings.Discord connection, ServerSettings.Delivery settings, Gson gson) {
        if(!report.admin()||report.recording())return null;
        boolean individual=!settings.groupBy().equals("type"),types=!settings.groupBy().equals("target");
        var individuals=individual?rows(report,"target").stream().filter(r->qualifies(r,settings)).limit(settings.top()).toList():List.<Row>of();
        var grouped=types?rows(report,"type").stream().filter(r->qualifies(r,settings)).limit(settings.top()).toList():List.<Row>of();
        if(individuals.isEmpty()&&grouped.isEmpty()&&(settings.minimumMs()>0||settings.minimumPercent()>0))return null;
        String origin=origin(report);
        var severity=ReportSeverity.evaluate(report);
        var components=new ArrayList<Map<String,Object>>();
        String role=mention(settings,severity)?settings.roleId():"";
        String heading="## LagSense | "+origin+" Capture";
        if(!connection.serverName().strip().equalsIgnoreCase("LagSense"))heading+="\n"+safe(connection.serverName(),80);
        if(!role.isEmpty())heading+="\n<@&"+role+">";
        components.add(text(heading));
        components.add(separator());
        var performance=report.diagnostics()==null?null:report.diagnostics().performance();
        boolean measured=performance!=null&&performance.valid();
        String average=measured?format(performance.averageTps(),1):report.tps()>0?format(Math.min(20,report.tps()),1):"N/A";
        String lowest=measured&&performance.windows()>0?format(performance.lowestTps(),1):"N/A";
        String duration=measured?format(performance.durationSeconds(),1)+" seconds":"N/A";
        String timestamp=report.capturedAt()>0?java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu, HH:mm 'UTC'",Locale.ENGLISH).withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.ofEpochMilli(report.capturedAt())):"Not recorded";
        String details="**Performance**\nAverage TPS: **"+average+" / 20**\nLowest TPS: **"+lowest+"**\nAverage MSPT: **"+format(report.mspt(),1)+" ms**\nPeak MSPT: **"+(measured?format(performance.peakNanos()/1e6,1):"N/A")+" ms**"
            +"\n\n**Severity: "+severity.label+"**\n\nCapture duration: "+duration+"\nCaptured: "+timestamp+"\nProfile ID: "+report.id();
        if(origin.equals("Spike")&&measured&&!performance.triggerReason().isBlank())details+="\n"+safe(performance.triggerReason(),300);
        components.add(text(details));
        var notes=new ArrayList<String>();
        if(report.truncated()||report.diagnostics()!=null&&(report.diagnostics().cpu().limited()||report.diagnostics().background()!=null&&report.diagnostics().background().limited()))notes.add("Some captured measurements are incomplete.");
        if(report.diagnostics()!=null)report.diagnostics().notes().stream().filter(n->n.startsWith("Backup")||n.startsWith("CPU results limited")||n.startsWith("Event timings limited")).forEach(notes::add);
        String note=notes.isEmpty()?"":safe(String.join("\n",notes),600);
        var sections=new LinkedHashMap<String,List<String>>();
        if(individual)sections.put(settings.groupBy().equals("both")?"Highest-cost individuals":"Highest-cost contributors",contributorLines(individuals,false));
        if(types)sections.put(settings.groupBy().equals("both")?"Highest-cost types":"Highest-cost contributors",contributorLines(grouped,true));
        var chunkLines=Report.aggregate(report.entries()).stream().limit(settings.top()).map(chunk->String.format(Locale.ROOT,"%.3f ms/t · %s chunk %d, %d",chunk.micros(report.ticks())/1000,safe(chunk.dimension(),80),chunk.x(),chunk.z())).toList();
        if(!chunkLines.isEmpty())sections.put("Highest-cost chunks",chunkLines);
        // Keep every configured summary entry inside Discord's text budget. Full identities stay in the attachment.
        int used=heading.length()+details.length()+note.length();
        int overhead=sections.keySet().stream().mapToInt(name->name.length()+6).sum();
        int lines=sections.values().stream().mapToInt(List::size).sum();
        int lineLimit=Math.max(1,(3900-used-overhead)/Math.max(1,lines)-1);
        for(var section:sections.entrySet())components.add(text("**"+section.getKey()+"**\n"+String.join("\n",section.getValue().stream().map(line->safe(line,lineLimit)).toList())));
        if(!note.isEmpty())components.add(text(note));
        if(!settings.attachment().equals("none")){
            components.add(separator());
            components.add(Map.of("type",13,"file",Map.of("url","attachment://"+filename(report,settings.attachment()))));
        }
        return message(components,severity.color,role,connection.avatarUrl(),gson);
    }
    private static List<String> contributorLines(List<Row> rows,boolean combined){
        if(rows.isEmpty())return List.of("No measured contributors pass the filters.");
        return rows.stream().map(row->{
            String value=String.format(Locale.ROOT,"%.3f ms/t · %.1f%% · %s %s",row.msPerTick(),row.percent(),safe(row.kind(),40),safe(row.type(),100));
            if(combined)value+=" (combined "+row.targets()+")";
            value+=" · "+safe(row.dimension(),80);
            if(row.x()!=null)value+=" at "+row.x()+", "+row.y()+", "+row.z();return value;
        }).toList();
    }
    static boolean mention(ServerSettings.Delivery settings,ReportSeverity severity){
        if(settings.roleId()==null||!settings.roleId().matches("[0-9]{16,22}"))return false;
        return switch(settings.mentionSeverity()){
            case "always"->true;
            case "critical"->severity==ReportSeverity.CRITICAL;
            case "degraded"->severity==ReportSeverity.DEGRADED||severity==ReportSeverity.CRITICAL;
            case "warning"->severity==ReportSeverity.WARNING||severity==ReportSeverity.DEGRADED||severity==ReportSeverity.CRITICAL;
            default->false;
        };
    }
    private static Map<String,Object> text(String value){return Map.of("type",10,"content",value);}
    private static Map<String,Object> separator(){return Map.of("type",14,"divider",true,"spacing",1);}
    private static Message message(List<Map<String,Object>> components,int color,String role,String avatarUrl,Gson gson){
        Map<String,Object> mentions=new LinkedHashMap<>();mentions.put("parse",List.of());if(!role.isEmpty())mentions.put("roles",List.of(role));
        var payload=new LinkedHashMap<String,Object>();payload.put("flags",32768);payload.put("components",List.of(Map.of("type",17,"accent_color",color,"components",components)));payload.put("allowed_mentions",mentions);if(!avatarUrl.isEmpty())payload.put("avatar_url",avatarUrl);return new Message(gson.toJson(payload));
    }
    public static Message test(ServerSettings.Discord connection,Gson gson){return message(List.of(text("## LagSense | Test notification\n"+safe(connection.serverName(),80)+"\nWebhook connected. No profile data attached.")),ReportSeverity.UNKNOWN.color,"",connection.avatarUrl(),gson);}
    private static String safe(String value,int length){if(value==null)return "";String clean=value.replace("@","@\u200b").replace('\r',' ').replace('`','\'');return clean.length()<=length?clean:clean.substring(0,length-1)+"…";}
    public static String origin(Report report){String trigger=report.diagnostics()==null?"":report.diagnostics().trigger();return trigger.equals("Scheduled")?"Scheduled":trigger.equals("Spike follow-up")?"Spike":trigger.equals("Manual")?"Manual":"Unknown";}
    public static String filename(Report report){
        String stamp=java.time.format.DateTimeFormatter.ofPattern("yyMMdd-HHmmss",Locale.ROOT).withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.ofEpochMilli(Math.max(0,report.capturedAt())));
        // Lossless base-36 ID avoids collisions from truncated hashes, including same-second captures.
        return "LagSense-"+stamp+"-"+Long.toUnsignedString(report.id(),36)+".html";
    }
    public static String filename(Report report,String format){if(!Set.of("html","json","csv").contains(format))throw new IllegalArgumentException("Invalid report format");return filename(report).replace(".html","."+format);}
    private static String format(double value,int places){return Double.isFinite(value)?String.format(Locale.ROOT,"%."+places+"f",value):"N/A";}
    public static final class Body implements AutoCloseable {
        private final String contentType;private final byte[] bytes;private final java.nio.file.Path file;
        private final Map<String,Long> expected;
        Body(String contentType,byte[] bytes){this.contentType=contentType;this.bytes=bytes;this.file=null;expected=Map.of();}
        Body(String contentType,java.nio.file.Path file,Map<String,Long> expected){this.contentType=contentType;this.bytes=null;this.file=file;this.expected=Map.copyOf(expected);}
        boolean attachmentsAccepted(String json){
            if(expected.isEmpty())return true;
            try{var files=com.google.gson.JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("attachments");var found=new HashMap<String,Long>();for(var item:files){var f=item.getAsJsonObject();found.put(f.get("filename").getAsString(),f.get("size").getAsLong());}return expected.entrySet().stream().allMatch(e->Objects.equals(found.get(e.getKey()),e.getValue()));}catch(RuntimeException error){return false;}
        }
        public String contentType(){return contentType;}
        public byte[] bytes(){try{return bytes!=null?bytes:java.nio.file.Files.readAllBytes(file);}catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}}
        public java.net.http.HttpRequest.BodyPublisher publisher()throws java.io.IOException{return file==null?java.net.http.HttpRequest.BodyPublishers.ofByteArray(bytes):java.net.http.HttpRequest.BodyPublishers.ofFile(file);}
        public long length()throws java.io.IOException{return file==null?bytes.length:java.nio.file.Files.size(file);}
        @Override public void close(){if(file!=null)try{java.nio.file.Files.deleteIfExists(file);}catch(java.io.IOException ignored){}}
        @Override public String toString(){return "DiscordBody[content hidden]";}
    }
    public static Body body(Message message){return new Body("application/json; charset=utf-8",message.json().getBytes(StandardCharsets.UTF_8));}
    public static Body body(Message message,java.nio.file.Path report,String filename)throws java.io.IOException{
        String contentType=filename.endsWith(".html")?"text/html":filename.endsWith(".json")?"application/json":"text/csv";
        String boundary="lagsense-"+UUID.randomUUID();var file=java.nio.file.Files.createTempFile("lagsense-discord-body-",".multipart");
        try(var out=java.nio.file.Files.newOutputStream(file)){
            var json=com.google.gson.JsonParser.parseString(message.json()).getAsJsonObject();var attachments=new com.google.gson.JsonArray();var attachment=new com.google.gson.JsonObject();attachment.addProperty("id",0);attachment.addProperty("filename",filename);attachments.add(attachment);json.add("attachments",attachments);
            out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"payload_json\"\r\nContent-Type: application/json\r\n\r\n"+json+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"files[0]\"; filename=\""+filename+"\"\r\nContent-Type: "+contentType+"; charset=utf-8\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            java.nio.file.Files.copy(report,out);out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        }catch(Throwable error){java.nio.file.Files.deleteIfExists(file);throw error;}
        return new Body("multipart/form-data; boundary="+boundary,file,Map.of(filename,java.nio.file.Files.size(report)));
    }
}
