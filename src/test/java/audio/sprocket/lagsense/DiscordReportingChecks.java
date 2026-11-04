package audio.sprocket.lagsense;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

final class DiscordReportingChecks {
    private static final String URL="https://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwx";
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static ServerSettings.Delivery section(boolean coordinates){return new ServerSettings.Delivery(true,0,5,0,0,"either","target","html","","degraded");}
    private static ServerSettings.Discord config(boolean coordinates,int size){var s=section(coordinates);return new ServerSettings.Discord(true,URL,"Fixture @everyone",s,s,s,size);}
    private static Report fixture(String origin,int count,long period,long work){
        var counter=new CapturePerformance.Counter();long now=123;counter.start(now);for(int i=0;i<count;i++){now+=period;counter.tick(now,work);}
        var target=new Report.Entry("Block","fixture:machine","minecraft:the_nether",-17,-40,-33,1_000_000,count);
        return new Report(314,1730721600000L,count,work/1e6,true,false,List.of(target),false,0,Report.aggregate(List.of(target)),counter.finish(now,"").averageTps(),new Report.Diagnostics(origin,0,0,List.of(),List.of(),List.of(),"disabled",CpuInvestigation.empty("disabled"),null,BackgroundCpu.empty("disabled"),counter.finish(now,origin.equals("Spike follow-up")?"TPS 12.4 stayed below configured 13.0 for at least 10s":"")));
    }
    private static Report with(Report source,CapturePerformance p){var d=source.diagnostics();return new Report(source.id(),source.capturedAt(),source.ticks(),source.mspt(),source.admin(),source.truncated(),source.entries(),false,0,source.chunks(),source.tps(),new Report.Diagnostics(d.trigger(),0,0,d.tasks(),d.notes(),d.callbacks(),d.callbackState(),d.cpu(),d.coverage(),d.background(),p));}
    private static String summary(JsonObject container){var values=new ArrayList<String>();for(var value:container.getAsJsonArray("components")){var component=value.getAsJsonObject();if(component.get("type").getAsInt()==10)values.add(component.get("content").getAsString());}return String.join("\n",values);}
    static void layout(JsonObject payload,String format){
        check(payload.get("flags").getAsInt()==32768&&!payload.has("embeds")&&!payload.has("content")&&!payload.has("poll"),"Components V2 excludes legacy content and embeds");
        var roots=payload.getAsJsonArray("components");check(roots.size()==1&&roots.get(0).getAsJsonObject().get("type").getAsInt()==17,"One compact summary Container is sent in one message");
        var components=roots.get(0).getAsJsonObject().getAsJsonArray("components");int count=1,characters=0,files=0,performance=-1,contributors=-1;
        for(int i=0;i<components.size();i++){var part=components.get(i).getAsJsonObject();int type=part.get("type").getAsInt();count++;check(Set.of(10,13,14).contains(type),"Only text, separators and files appear; no bot or interactive controls");
            if(type==10){String text=part.get("content").getAsString();characters+=text.length();check(text.indexOf(0x2014)<0,"Report wording has no em dashes");if(text.startsWith("**Performance**"))performance=i;if(text.startsWith("**Highest-cost "))contributors=i;}
            if(type==13){files++;check(i==components.size()-1&&performance>=0&&contributors>performance,"File is explicitly positioned beneath the complete summary");check(part.getAsJsonObject("file").get("url").getAsString().matches("attachment://LagSense-[0-9]{6}-[0-9]{6}-[a-z0-9]+\\."+format),"File component references the selected uploaded filename");}
        }
        check(files==(format.equals("none")?0:1),"Exactly one selected attachment, or no File component for None");check(count<=40&&characters<=4000,"Component/text budgets remain bounded for all report settings");
    }
    private static Set<Path> temporary()throws Exception{try(var files=Files.list(Path.of(System.getProperty("java.io.tmpdir")))){return files.filter(p->p.getFileName().toString().startsWith("lagsense-discord-")).collect(java.util.stream.Collectors.toSet());}}
    static void run()throws Exception{
        var originalTemporary=temporary();
        var healthy=fixture("Manual",200,50_000_000,10_000_000);var warning=fixture("Scheduled",200,50_000_000,45_000_000);var degraded=fixture("Spike follow-up",150,66_666_667,20_000_000);var critical=fixture("Scheduled",80,125_000_000,110_000_000);
        check(ReportSeverity.evaluate(healthy)==ReportSeverity.HEALTHY&&ReportSeverity.evaluate(warning)==ReportSeverity.WARNING&&ReportSeverity.evaluate(degraded)==ReportSeverity.DEGRADED&&ReportSeverity.evaluate(critical)==ReportSeverity.CRITICAL,"Severity uses whole-capture TPS/workload across all four defined classes");
        check(healthy.diagnostics().performance().averageTps()==20&&Math.abs(degraded.diagnostics().performance().averageTps()-15)<.001,"Observed TPS uses tick progression even when MSPT is cheap");
        var isolated=with(healthy,new CapturePerformance(10_000_000_000L,200,2_000_000_000L,1,2_000_000_000L,8,10,""));check(ReportSeverity.evaluate(isolated)==ReportSeverity.WARNING,"An isolated extreme peak/window cannot make an otherwise healthy average Critical");
        var stalled=with(healthy,new CapturePerformance(60_000_000_000L,200,50_000_000_000L,1,50_000_000_000L,1,10,"",1));check(ReportSeverity.evaluate(stalled)!=ReportSeverity.CRITICAL,"Even an extreme single stall that destroys average TPS cannot alone establish Critical severity");
        var shortReport=fixture("Manual",10,50_000_000,10_000_000);check(ReportSeverity.evaluate(shortReport)==ReportSeverity.UNKNOWN&&shortReport.diagnostics().performance().windows()==0,"Short partial windows do not fabricate minimum TPS or severity");
        var partial=fixture("Manual",50,50_000_000,10_000_000);check(partial.diagnostics().performance().windows()==2&&partial.diagnostics().performance().durationSeconds()==2.5,"Tail windows are excluded from lowest TPS but included in elapsed capture duration");
        var counter=new CapturePerformance.Counter();counter.start(0);for(int i=1;i<=20;i++)counter.tick(i*100_000_000L,70_000_000);var p=counter.finish(2_000_000_000L,"");check(p.windows()==2&&p.lowestTps()==10&&p.overBudgetTicks()==20&&p.peakNanos()==70_000_000,"Meaningful one-second windows and over-budget/peak accounting are exact");
        var legacy=with(healthy,null);check(ReportSeverity.evaluate(legacy)==ReportSeverity.UNKNOWN,"Older captures with missing metrics use Insufficient Data");
        check(ReportSeverity.evaluate(fixture("Manual",195,50_000_000,10_000_000))==ReportSeverity.HEALTHY,"Healthy threshold includes 20 observed TPS");
        check(ReportSeverity.evaluate(fixture("Manual",200,50_000_000,40_000_000))==ReportSeverity.WARNING,"40 MSPT is the documented headroom boundary");
        check(ReportSeverity.evaluate(fixture("Manual",100,100_000_000,110_000_000))==ReportSeverity.CRITICAL,"Sustained 100+ MSPT can be Critical without a below-10 TPS trigger");
        check(ReportSeverity.evaluate(fixture("Manual",30,125_000_000,110_000_000))==ReportSeverity.DEGRADED,"Under-five-second lag cannot alone establish persistent Critical severity");
        var limits=ServerSettings.parse("[discord]\nupload_limit_mib=24",Map.of());check(limits.discord().uploadLimitMiB()==24,"Webhook upload allowance is configurable independently of capture budgets");
        for(var report:List.of(healthy,warning,degraded,critical,shortReport,legacy)){
            var message=DiscordMessage.build(report,config(true,10),section(true),Profiler.JSON);var payload=JsonParser.parseString(message.json()).getAsJsonObject();layout(payload,"html");var container=payload.getAsJsonArray("components").get(0).getAsJsonObject();String text=summary(container);
            check(container.get("accent_color").getAsInt()==ReportSeverity.evaluate(report).color&&text.contains("**Severity: "+ReportSeverity.evaluate(report).label+"**"),"Accessible severity labels and colours agree");
            check(text.startsWith("## LagSense | "+DiscordMessage.origin(report)+" Capture"),"Recorded origin overrides the notification channel name");
            check(!message.json().contains("invented trigger"),"Unrecorded spike reasons are never manufactured");
            if(report==shortReport||report==legacy)check(text.contains("Lowest TPS: **N/A**"),"Missing window data displays N/A");
        }
        var temp=Files.createTempDirectory("lagsense-discord-check-");try{
            var file=temp.resolve("whole.html");HtmlReportExport.write(file,healthy,audio.sprocket.lagsense.client.UiTheme.DEFAULT);
            var bytes=Files.readAllBytes(file);var message=DiscordMessage.build(healthy,config(true,10),section(true),Profiler.JSON);
            Path multipart;try(var body=DiscordMessage.body(message,file,DiscordMessage.filename(healthy))){var text=new String(body.bytes(),java.nio.charset.StandardCharsets.UTF_8);check(text.contains("files[0]")&&text.contains("payload_json")&&text.contains(DiscordMessage.filename(healthy))&&text.contains(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)),"Multipart retains the entire current HTML export and attachment identity");var f=body.getClass().getDeclaredField("file");f.setAccessible(true);multipart=(Path)f.get(body);check(body.publisher().contentLength()==body.length(),"Upload streams a known-length disk file");}check(!Files.exists(multipart),"Multipart temporary body is cleaned after use");
            var decoded=FullReportWire.decode(FullReportWire.encode(healthy));check(decoded.diagnostics().performance().equals(healthy.diagnostics().performance()),"Capture metrics survive full report/archive-compatible serialization");
            check(healthy.view(false,e->true).diagnostics()==null,"Player report scope never contains server performance data");
        }finally{try(var files=Files.list(temp)){for(var file:files.toList())Files.deleteIfExists(file);}Files.deleteIfExists(temp);}
        var posted=new CopyOnWriteArrayList<String>();var threads=new CopyOnWriteArrayList<String>();
        try(var delivery=new DiscordDelivery(config(true,10),(uri,body)->{posted.add(new String(body.bytes(),java.nio.charset.StandardCharsets.UTF_8));threads.add(Thread.currentThread().getName());return new DiscordDelivery.Response(204,0);},ms->{},s->{})){
            for(String origin:List.of("Manual","Scheduled","Spike follow-up"))delivery.capture(fixture(origin,200,50_000_000,10_000_000),"ignored");delivery.flush();
            check(posted.size()==3&&posted.stream().allMatch(s->s.contains("<!doctype html>")&&!s.contains("Font licence")&&s.contains("Captured measurements")),"Every capture origin uploads the full exporter document");
            for(int i=0;i<3;i++)check(posted.get(i).contains("LagSense | "+List.of("Manual","Scheduled","Spike").get(i)+" Capture"),"Each source uses the shared Components V2 builder");
            check(threads.stream().allMatch(s->s.equals("LagSense Discord delivery")),"HTML generation/upload pipeline executes off the caller/server tick thread");
            delivery.capture(healthy.view(false,e->true),"");delivery.flush();check(posted.size()==3&&delivery.status().contains("Player-scoped"),"Scoped captures cannot leak through automatic or manual Discord sends");
            check(delivery.test(),"Webhook test can run without invented report data");delivery.flush();check(posted.getLast().toLowerCase(Locale.ROOT).contains("test notification")&&!posted.getLast().contains("files[0]"),"Connectivity test remains explicitly identifiable and carries no fabricated capture");
        }
        var defaults=ServerSettings.defaults().discord();check(defaults.timer().attachment().equals("none")&&defaults.spikes().attachment().equals("none")&&defaults.manual().attachment().equals("none"),"None is the default attachment choice for every capture origin");
        for(String format:List.of("none","html","json","csv")){
            var option=new ServerSettings.Delivery(true,0,5,0,0,"either","target",format,"","degraded");var connection=new ServerSettings.Discord(true,URL,"Fixture",option,option,option,10);var uploads=new ArrayList<String>();var types=new ArrayList<String>();
            try(var delivery=new DiscordDelivery(connection,(uri,body)->{uploads.add(new String(body.bytes(),java.nio.charset.StandardCharsets.UTF_8));types.add(body.contentType());return new DiscordDelivery.Response(204,0);},ms->{},s2->{})){
                for(String origin:List.of("Manual","Scheduled","Spike follow-up"))delivery.capture(fixture(origin,200,50_000_000,10_000_000),"");delivery.flush();
                check(uploads.size()==3,"Every capture origin supports "+format);
                for(int i=0;i<uploads.size();i++){String content=uploads.get(i);String json=content;
                    if(!format.equals("none")){int begin=content.indexOf("\r\n\r\n")+4;json=content.substring(begin,content.indexOf("\r\n--",begin));}
                    var payload=JsonParser.parseString(json).getAsJsonObject();layout(payload,format);
                    if(!format.equals("none")){var file=payload.getAsJsonArray("attachments");check(file.size()==1&&file.get(0).getAsJsonObject().get("filename").getAsString().endsWith("."+format),"Multipart metadata exposes only the selected file");}
                    check(!content.contains("files[1]"),"Only one report file is ever attached");
                    if(format.equals("none")){check(types.get(i).startsWith("application/json")&&!content.contains("files[0]")&&!content.contains("report file")&&!content.contains("Interactive HTML report"),"None sends the summary without files or promises of attachments");}
                    else{check(content.contains("files[0]")&&content.contains("."+format+"\"")&&content.contains("Performance"),"Chosen format and performance summary are both delivered");
                        int start=content.indexOf("\r\n\r\n",content.indexOf("name=\"files[0]\""))+4,end=content.lastIndexOf("\r\n--");String filePayload=content.substring(start,end);
                        if(format.equals("html"))check(filePayload.startsWith("<!doctype html>")&&filePayload.contains("report-data"),"HTML uses the complete interactive exporter");
                        else if(format.equals("json")){var capture=Profiler.JSON.fromJson(filePayload,Report.class);check(capture.entries().size()==1&&capture.diagnostics().performance()!=null&&capture.entries().getFirst().x()==-17,"JSON preserves capture data, diagnostics and coordinates");}
                        else check(filePayload.contains("fixture:machine")&&filePayload.contains("Capture performance")&&filePayload.contains("-17"),"CSV preserves targets, capture metrics and locations");
                    }
                }
            }
        }
        var mentionOption=new ServerSettings.Delivery(true,0,10,0,0,"either","type","none","123456789012345678","always");
        var mentioned=JsonParser.parseString(DiscordMessage.build(healthy,config(true,10),mentionOption,Profiler.JSON).json()).getAsJsonObject();layout(mentioned,"none");
        check(summary(mentioned.getAsJsonArray("components").get(0).getAsJsonObject()).contains("<@&123456789012345678>")&&mentioned.getAsJsonObject("allowed_mentions").getAsJsonArray("roles").get(0).getAsString().equals("123456789012345678")&&mentioned.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty(),"Explicit role mentions are inside Text Displays with a restricted allowlist");
        check(!mentioned.toString().contains("matching notifications skipped")&&mentioned.toString().contains("combined"),"Contributor grouping remains without obsolete repeat counts");
        check(ServerSettings.validateWebhook(URL).getQuery().equals("wait=true&with_components=true"),"Webhook execution requests component support and a confirmed message response");
        var dense=new ArrayList<Report.Entry>();for(int i=0;i<10;i++)dense.add(new Report.Entry("Block","fixture:"+"machine_".repeat(30)+i,"fixture:"+"dimension_".repeat(8),i,64,-i,1000000,1));
        var oldDiagnostics=critical.diagnostics();var detailed=new Report.Diagnostics("Spike follow-up",0,0,List.of(),List.of("Backup active: "+"b".repeat(1000),"CPU results limited: "+"c".repeat(1000)),List.of(),"disabled",CpuInvestigation.empty("disabled"),null,BackgroundCpu.empty("disabled"),oldDiagnostics.performance());
        var longReport=new Report(critical.id(),critical.capturedAt(),critical.ticks(),critical.mspt(),true,true,dense,false,0,Report.aggregate(dense),critical.tps(),detailed);
        for(String format:List.of("none","html","json","csv")){var settings=new ServerSettings.Delivery(true,0,10,0,0,"either","both",format,"123456789012345678","degraded");layout(JsonParser.parseString(DiscordMessage.build(longReport,config(true,10),settings,Profiler.JSON).json()).getAsJsonObject(),format);}
        var exampleDirectory=Path.of("work/discord-v2");Files.createDirectories(exampleDirectory);for(String format:List.of("none","html","json","csv")){var setting=new ServerSettings.Delivery(true,0,5,0,0,"either","target",format,"","degraded");Files.writeString(exampleDirectory.resolve(format+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(DiscordMessage.build(warning,config(true,10),setting,Profiler.JSON).json())));}
        var calls=new AtomicInteger();
        var legacyConfig=ServerSettings.parse("[discord.manual]\ninclude_coordinates=false",Map.of());check(!Profiler.JSON.toJson(legacyConfig.discord().manual()).contains("coordinates"),"The removed coordinate setting is ignored when reading old files and never stored or sent to the GUI");
        var oldDirectory=Files.createTempDirectory("lagsense-discord-config-");try{var file=oldDirectory.resolve("server.toml");Files.writeString(file,"[discord.manual]\ninclude_coordinates=false\n");var old=ServerSettings.load(file);ServerSettings.update(file,old,Map.of("discord.server_name","Updated"));check(!Files.readString(file).contains("include_coordinates"),"Saving an older server config removes obsolete coordinate entries");}finally{try(var files=Files.list(oldDirectory)){for(var file:files.toList())Files.deleteIfExists(file);}Files.deleteIfExists(oldDirectory);}

        try(var delivery=new DiscordDelivery(config(true,1),(uri,body)->{calls.incrementAndGet();return new DiscordDelivery.Response(204,0);},ms->{},s->{})){
            var many=new ArrayList<Report.Entry>();for(int i=0;i<10000;i++)many.add(new Report.Entry("Block","fixture:machine_"+i,"minecraft:overworld",i,64,0,1000,1));
            var oversized=new Report(315,healthy.capturedAt(),healthy.ticks(),healthy.mspt(),true,false,many,false,0,Report.aggregate(many),healthy.tps(),healthy.diagnostics());
            delivery.capture(oversized,"");delivery.flush();check(calls.get()==0&&delivery.status().contains("upload limit"),"Configured per-destination size limit fails explicitly without dropping data or sending embed-only success");
        }
        for(int status:new int[]{400,401,403,404,413}){
            calls.set(0);var logs=new ArrayList<String>();try(var delivery=new DiscordDelivery(config(true,10),(uri,body)->{calls.incrementAndGet();return new DiscordDelivery.Response(status,0);},ms->{},logs::add)){
                delivery.capture(healthy,"");delivery.flush();check(calls.get()==1&&!delivery.status().contains("Last sent")&&delivery.status().contains("HTTP "+status),"Upload/auth rejection is reported and never retried as a success");check(logs.stream().noneMatch(s->s.contains(URL)||s.contains("abcdefghijkl")),"HTTP errors never expose credentials");
            }
        }
        try(var delivery=new DiscordDelivery(config(true,10),(uri,body)->new DiscordDelivery.Response(200,0,false),ms->{},s->{})){delivery.capture(healthy,"");delivery.flush();check(delivery.status().contains("did not confirm")&&!delivery.status().contains("Last sent"),"An accepted message without attachment confirmation is never reported as a successful full report");}
        calls.set(0);try(var delivery=new DiscordDelivery(config(true,10),(uri,body)->{calls.incrementAndGet();return new DiscordDelivery.Response(204,0);},ms->{},s->{})){
            var malformed=new Report(5,1000,20,2,true,false,List.of(new Report.Entry("Block","fixture:machine","minecraft:overworld",1,80,1,100,1,UUID.randomUUID())),false,0,List.of(),20,healthy.diagnostics());
            delivery.capture(malformed,"");delivery.flush();check(calls.get()==0&&delivery.status().contains("could not be prepared"),"HTML-generation failure sends no incomplete notification");
            var pending=new Report.Diagnostics("Manual",0,0,List.of(),List.of(),List.of(),"disabled",CpuInvestigation.empty("pending"),null,BackgroundCpu.empty("pending"),healthy.diagnostics().performance());
            delivery.capture(new Report(1,1000,200,10,true,false,healthy.entries(),false,0,List.of(),20,pending),"");delivery.flush();check(calls.get()==0&&delivery.status().contains("Waiting"),"Pending diagnostic data cannot produce an incomplete full Discord report");
        }
        check(temporary().equals(originalTemporary),"Success, rejection, size, malformed export and privacy failures leave no HTML/multipart temporary files behind");
        System.out.println("Discord reporting checks passed: all capture origins, exact TPS/windows/duration, sustained versus isolated severity, archive metrics, Components V2 summary-first/File-last layouts, all attachment formats, bounded text, streamed multipart, explicit upload/auth/size failures and scoped-report denial and worker-only delivery; no live webhook used.");
    }
}
