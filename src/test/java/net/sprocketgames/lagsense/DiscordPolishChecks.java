package net.sprocketgames.lagsense;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

final class DiscordPolishChecks {
    private static final String WEBHOOK="https://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwx",ROLE="123456789012345678",AVATAR="https://cdn.example.com/lagsense.png?size=128";
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static ServerSettings.Delivery option(String group,int top,String threshold,String role,String file){return new ServerSettings.Delivery(true,0,top,0,0,"either",group,file,role,threshold);}
    private static ServerSettings.Discord connection(ServerSettings.Delivery option,String avatar,String name){return new ServerSettings.Discord(true,WEBHOOK,name,option,option,option,10,avatar);}
    private static Report fixture(int ms,int tps){
        var entries=new ArrayList<Report.Entry>();for(int i=0;i<8;i++)for(int j=0;j<2;j++)entries.add(new Report.Entry("Block","fixture:machine_"+i,"minecraft:overworld",i*16+j,64,0,(8-i)*(j+1)*100_000_000L,100));
        var counter=new CapturePerformance.Counter();counter.start(0);long now=0;for(int i=0;i<200;i++){now+=1_000_000_000L/tps;counter.tick(now,ms*1_000_000L);}
        return new Report(1001,1793810700000L,200,ms,true,false,entries,false,0,Report.aggregate(entries),counter.finish(now,"").averageTps(),new Report.Diagnostics("Manual",0,0,List.of(),List.of(),List.of(),"disabled",CpuInvestigation.empty("disabled"),null,BackgroundCpu.empty("disabled"),counter.finish(now,"")));
    }
    private static JsonObject payload(Report report,ServerSettings.Delivery option,String avatar,String name){return JsonParser.parseString(DiscordMessage.build(report,connection(option,avatar,name),option,Profiler.JSON).json()).getAsJsonObject();}
    private static Map<String,String> text(JsonObject payload){var result=new LinkedHashMap<String,String>();for(var part:payload.getAsJsonArray("components").get(0).getAsJsonObject().getAsJsonArray("components")){var item=part.getAsJsonObject();if(item.get("type").getAsInt()==10){String value=item.get("content").getAsString();int end=value.indexOf('\n');result.put(end<0?value:value.substring(0,end),end<0?"":value.substring(end+1));}}return result;}
    private static int lines(String section){return section.split("\n").length;}
    static void run()throws Exception{
        var report=fixture(10,20);String original=Profiler.JSON.toJson(report);
        for(String group:List.of("target","type","both"))for(int top:List.of(1,5,10)){
            var option=option(group,top,"degraded","","html");var payload=payload(report,option,"","LagSense");DiscordReportingChecks.layout(payload,"html");var text=text(payload);
            check(text.get("## LagSense | Manual Capture").isEmpty(),"Redundant LagSense server name and blank spacing are omitted");
            if(group.equals("both")){check(lines(text.get("**Highest-cost individuals**"))==Math.min(top,16)&&lines(text.get("**Highest-cost types**"))==Math.min(top,8),"Both applies Top results independently to individual and grouped sections");}
            else check(lines(text.get("**Highest-cost contributors**"))==Math.min(top,group.equals("type")?8:16),"Single grouping keeps the configured result count");
            check(lines(text.get("**Highest-cost chunks**"))==Math.min(top,8),"Chunk section uses the same Top results instead of a fixed top three");
        }
        var individual=DiscordMessage.rows(report,option("target",10,"degraded","","none"));var grouped=DiscordMessage.rows(report,option("type",10,"degraded","","none"));
        check(Math.abs(individual.stream().mapToDouble(DiscordMessage.Row::msPerTick).sum()-grouped.stream().mapToDouble(DiscordMessage.Row::msPerTick).sum())<1e-9,"Individual and Type sections are separate views of the same cost without double-counting");
        check(grouped.stream().allMatch(row->row.targets()==2&&Math.abs(row.percent()-row.msPerTick()/report.mspt()*100)<1e-9),"Type grouping retains measured instance counts and the actual MSPT percentage denominator");
        var filtered=new ServerSettings.Delivery(true,0,5,10,0,"either","both","none","","degraded");check(DiscordMessage.build(report,connection(filtered,"","Fixture"),filtered,Profiler.JSON)!=null,"Both can qualify through grouped totals when individual targets are below the filter");
        var missing=new Report(1002,report.capturedAt(),report.ticks(),report.mspt(),true,false,report.entries());
        var severities=List.of(report,fixture(45,20),fixture(20,15),fixture(110,8),missing);
        for(var capture:severities)for(String threshold:List.of("never","critical","degraded","warning","always"))for(String file:List.of("none","html","json","csv")){
            var severity=ReportSeverity.evaluate(capture);boolean expected=threshold.equals("always")||severity!=ReportSeverity.UNKNOWN&&(threshold.equals("critical")&&severity==ReportSeverity.CRITICAL||threshold.equals("degraded")&&(severity==ReportSeverity.DEGRADED||severity==ReportSeverity.CRITICAL)||threshold.equals("warning")&&severity!=ReportSeverity.HEALTHY);
            var option=option("both",5,threshold,ROLE,file);var payload=payload(capture,option,AVATAR,"FTB Evolution 2");DiscordReportingChecks.layout(payload,file);
            check(payload.toString().contains("<@&"+ROLE+">")==expected&&payload.getAsJsonObject("allowed_mentions").has("roles")==expected,"Mention severity changes only mention text/allowlist for "+severity+" at "+threshold);
            check(payload.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty()&&payload.get("avatar_url").getAsString().equals(AVATAR),"Explicit role-only mentions and optional avatar work for every severity and attachment mode");
            check(text(payload).entrySet().stream().filter(e->e.getKey().startsWith("## LagSense |")).findFirst().orElseThrow().getValue().startsWith("FTB Evolution 2"),"A distinct configured server name is retained");
            check(payload(capture,option("target",5,threshold,"",file),"","Fixture").getAsJsonObject("allowed_mentions").has("roles")==false,"Missing role never produces a mention");
        }
        for(String origin:List.of("Manual","Scheduled","Spike follow-up"))for(String format:List.of("none","html","json","csv")){
            var d=report.diagnostics();var capture=new Report(report.id(),report.capturedAt(),report.ticks(),report.mspt(),true,false,report.entries(),false,0,report.chunks(),report.tps(),new Report.Diagnostics(origin,0,0,d.tasks(),d.notes(),d.callbacks(),d.callbackState(),d.cpu(),d.coverage(),d.background(),d.performance()));
            var setting=option("both",5,"degraded","",format);var received=new ArrayList<JsonObject>();
            try(var delivery=new DiscordDelivery(connection(setting,AVATAR,"Fixture"),(uri,body)->{String content=new String(body.bytes(),java.nio.charset.StandardCharsets.UTF_8);if(content.startsWith("--")){int begin=content.indexOf("\r\n\r\n")+4;content=content.substring(begin,content.indexOf("\r\n--",begin));}received.add(JsonParser.parseString(content).getAsJsonObject());return new DiscordDelivery.Response(204,0);},ms->{},s->{})){
                delivery.capture(capture,"");delivery.flush();check(received.size()==1&&received.getFirst().get("avatar_url").getAsString().equals(AVATAR),"Actual asynchronous delivery preserves avatar in every origin/attachment mode");
            }
        }
        check(ServerSettings.validateAvatar("https://8.8.8.8/image.png").equals("https://8.8.8.8/image.png"),"Public HTTPS IP URLs are accepted without a hostname lookup");
        check(!payload(report,option("target",5,"always",ROLE,"none"),"","Fixture").has("avatar_url"),"Blank avatar is entirely omitted for the webhook default");
        check(JsonParser.parseString(DiscordMessage.test(connection(option("target",5,"degraded","","none"),AVATAR,"Fixture"),Profiler.JSON).json()).getAsJsonObject().get("avatar_url").getAsString().equals(AVATAR),"Connectivity tests use the same configured avatar");
        for(String invalid:List.of("http://example.com/a.png","file:///image.png","data:image/png;base64,x","javascript:alert(1)","https://localhost/a.png","https://127.0.0.1/a.png","https://192.168.0.2/a.png","https://10.0.0.1/a.png","https://[::1]/a.png","https://[fc00::1]/a.png","https://user:secret@example.com/a.png","https://example.com/a.png#fragment")){
            boolean rejected=false;try{ServerSettings.validateAvatar(invalid);}catch(IllegalArgumentException error){rejected=!error.getMessage().contains("secret");}check(rejected,"Unsupported/private avatar URL rejected without echoing the value");
        }
        check(ServerSettings.validateAvatar("  ").isEmpty()&&ServerSettings.validateAvatar(" "+AVATAR+" ").equals(AVATAR),"Blank and surrounding whitespace are handled consistently");
        for(String role:List.of("bad","123","<@&123456789012345678>")){boolean rejected=false;try{option("target",5,"always",role,"none");}catch(IllegalArgumentException error){rejected=true;}check(rejected,"Invalid role IDs cannot be saved or sent");}
        var defaults=ServerSettings.defaults().discord();for(var section:List.of(defaults.timer(),defaults.spikes(),defaults.manual()))check(section.top()==5&&section.mentionSeverity().equals("degraded")&&section.attachment().equals("none"),"Top five, Degraded or worse and None remain defaults");check(defaults.avatarUrl().isEmpty(),"Avatar defaults to blank");
        check(!ServerSettings.template().contains("group_repeats")&&!ServerSettings.template().contains("top_offenders"),"Obsolete config settings are removed from the template");
        Path directory=Files.createTempDirectory("lagsense-discord-polish-");try{
            Path file=directory.resolve("settings.toml");Files.writeString(file,"[discord]\navatar_url=\""+AVATAR+"\"\n[discord.manual]\ngroup_repeats=true\ntop_offenders=7\ngroup_by=\"type\"\n");
            var old=ServerSettings.load(file);check(old.discord().manual().top()==7&&old.discord().manual().mentionSeverity().equals("degraded"),"Old result counts migrate and old repeat flags are safely consumed");
            var desired=new ServerSettings.Delivery(true,30,5,0,0,"either","both","json",ROLE,"warning");var before=ServerSettingsForms.view(old);var discord=new ServerSettingsForms.Discord(false,false,"Fixture",before.discord().timer(),before.discord().spikes(),desired,AVATAR);
            var edit=new ServerSettingsForms.Edit(before,new ServerSettingsForms.View(before.advanced(),discord),"");var changes=ServerSettingsForms.changes("discord",old,edit);var saved=ServerSettings.update(file,old,changes);
            check(saved.discord().manual().equals(desired)&&saved.discord().avatarUrl().equals(AVATAR)&&ServerSettings.load(file).equals(saved),"Both, mention threshold, avatar and file format survive in-game form save and disk reload");
            String disk=Files.readString(file);check(!disk.contains("group_repeats")&&!disk.contains("top_offenders")&&disk.contains("top_results"),"Saving old config removes obsolete fields and preserves migrated values");
            String json=Profiler.JSON.toJson(ServerSettingsForms.view(saved));check(!json.contains("groupRepeats")&&!json.contains("group_repeats")&&Profiler.JSON.fromJson(json,ServerSettingsForms.View.class).equals(ServerSettingsForms.view(saved)),"New settings DTO round-trips without inactive repeat fields or lost avatar/mention settings");
        }finally{try(var files=Files.list(directory)){for(var file:files.toList())Files.deleteIfExists(file);}Files.deleteIfExists(directory);}
        var names=new HashSet<String>();for(long id=0;id<20000;id++){var capture=new Report(id,report.capturedAt(),200,10,true,false,List.of());String name=DiscordMessage.filename(capture);check(name.matches("LagSense-[0-9]{6}-[0-9]{6}-[a-z0-9]+\\.html")&&name.length()<=41&&names.add(name),"Compact same-second capture filenames are distinct and safe");for(String format:List.of("html","json","csv"))check(DiscordMessage.filename(capture,format).equals(name.replace(".html","."+format)),"All file formats use the same deterministic capture token");}
        for(long id:List.of(Long.MIN_VALUE,Long.MAX_VALUE,-1L)){String name=DiscordMessage.filename(new Report(id,report.capturedAt(),200,10,true,false,List.of()));check(names.add(name)&&name.length()<=41,"Lossless compact IDs also cover signed long edge cases");}
        var option=new ServerSettings.Delivery(true,600,5,0,0,"either","target","none","","degraded");var posted=new ArrayList<String>();
        try(var delivery=new DiscordDelivery(connection(option,"","Fixture"),(uri,body)->{posted.add(new String(body.bytes(),java.nio.charset.StandardCharsets.UTF_8));return new DiscordDelivery.Response(204,0);},ms->{},s->{})){
            delivery.capture(report,"");delivery.flush();delivery.capture(report,"");delivery.flush();check(posted.size()==1&&delivery.status().contains("Cooldown active"),"Cooldown simply skips repeated reports");
            var field=DiscordDelivery.class.getDeclaredField("nextEligible");field.setAccessible(true);((Map<?,?>)field.get(delivery)).clear();delivery.capture(report,"");delivery.flush();check(posted.size()==2&&posted.stream().noneMatch(message->message.contains("matching notifications skipped")||message.contains("Grouped repeats")),"A later delivery contains no accumulated repeat count");
        }
        check(Profiler.JSON.toJson(report).equals(original),"Reporting changes never modify capture data or severity inputs");
        System.out.println("Discord polish checks passed (Both/independent section counts, neutral mention thresholds, avatar payload/default/URL validation, legacy migration, compact collision-free capture filenames, duplicate heading removal and skip-only cooldowns).");
    }
}
