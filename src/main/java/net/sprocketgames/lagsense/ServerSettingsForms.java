package net.sprocketgames.lagsense;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;

/** Scoped editors: outbound views contain no webhook URL or environment values. */
public final class ServerSettingsForms {
    public record Advanced(boolean modTasks, boolean detailedCallbacks, boolean monitorEmptyServer, BackupMonitor.Settings backups,ProfilingLimits limits) {
        public Advanced(boolean modTasks,boolean detailedCallbacks,boolean monitorEmptyServer,BackupMonitor.Settings backups){this(modTasks,detailedCallbacks,monitorEmptyServer,backups,ProfilingLimits.defaults());}
        public Advanced { Objects.requireNonNull(backups);limits=limits==null?ProfilingLimits.defaults():limits; }
    }
    public record Discord(boolean enabled, boolean webhookConfigured, String serverName, ServerSettings.Delivery timer,
                          ServerSettings.Delivery spikes, ServerSettings.Delivery manual,String avatarUrl) {
        public Discord(boolean enabled,boolean webhookConfigured,String serverName,ServerSettings.Delivery timer,ServerSettings.Delivery spikes,ServerSettings.Delivery manual){this(enabled,webhookConfigured,serverName,timer,spikes,manual,"");}
        public Discord {
            avatarUrl=ServerSettings.validateAvatar(avatarUrl);
            Objects.requireNonNull(timer); Objects.requireNonNull(spikes); Objects.requireNonNull(manual);
            if(serverName==null||serverName.isBlank()||serverName.length()>80)throw new IllegalArgumentException("Server name must have 1–80 characters.");
        }
    }
    public record View(Advanced advanced, Discord discord,ProfilingLimits.Effective effective) {
        public View(Advanced advanced,Discord discord){this(advanced,discord,null);}
        public View { Objects.requireNonNull(advanced); Objects.requireNonNull(discord); }
    }
    public record Edit(View expected, View desired, String webhook) {
        public Edit { Objects.requireNonNull(expected); Objects.requireNonNull(desired); webhook=webhook==null?"":webhook; }
        @Override public String toString(){return "Edit[webhook=<redacted>]";}
    }
    private static final RequestGate gate=new RequestGate(200_000_000L);
    public static View view(ServerSettings s) {
        var d=s.discord();
        return new View(new Advanced(s.modTasks(),s.detailedCallbacks(),s.monitorEmptyServer(),s.backups(),s.limits()),
            new Discord(d.enabled(),!d.webhookUrl().isBlank(),d.serverName(),d.timer(),d.spikes(),d.manual(),d.avatarUrl()),s.limits().effective());
    }
    public static Map<String,Object> changes(String section,ServerSettings current,Edit edit) {
        var live=view(current);var before=edit.expected();var after=edit.desired();
        Map<String,Object> out=new LinkedHashMap<>();
        if(section.equals("advanced")) {
            if(!live.advanced().equals(before.advanced()))throw new IllegalArgumentException("Settings changed. Reopen this page before applying.");
            var a=after.advanced();
            out.put("profiling.mod_tasks",a.modTasks());out.put("profiling.detailed_callbacks",a.detailedCallbacks());
            out.put("capture.spikes.monitor_empty_server",a.monitorEmptyServer());
            out.put("profiling.limits.mode",a.limits().mode());out.put("profiling.limits.event_mib",a.limits().eventMiB());out.put("profiling.limits.cpu_mib",a.limits().cpuMiB());out.put("profiling.limits.jfr_mib",a.limits().jfrMiB());out.put("profiling.limits.stack_depth",a.limits().stackDepth());
            out.put("backups.mode",a.backups().mode());out.put("backups.grace_seconds",a.backups().graceSeconds());
        } else if(section.equals("discord")) {
            if(!live.discord().equals(before.discord()))throw new IllegalArgumentException("Settings changed. Reopen this page before applying.");
            var d=after.discord();out.put("discord.enabled",d.enabled());out.put("discord.server_name",d.serverName());out.put("discord.avatar_url",d.avatarUrl());
            delivery(out,"timer",d.timer());delivery(out,"spikes",d.spikes());delivery(out,"manual",d.manual());
            if(!edit.webhook().isBlank()) {
                if(edit.webhook().length()>512)throw new IllegalArgumentException("Webhook is too long.");
                String url=edit.webhook().strip();ServerSettings.validateWebhook(url);out.put("discord.webhook_url",url);
            }
        } else throw new IllegalArgumentException("Unknown settings page.");
        return out;
    }
    private static void delivery(Map<String,Object> out,String name,ServerSettings.Delivery d) {
        String p="discord."+name+".";
        out.put(p+"enabled",d.enabled());out.put(p+"cooldown_seconds",d.cooldownSeconds());
        out.put(p+"top_results",d.top());out.put(p+"minimum_ms_per_tick",d.minimumMs());out.put(p+"minimum_tick_percent",d.minimumPercent());
        out.put(p+"match",d.match());out.put(p+"group_by",d.groupBy());
        out.put(p+"attachment",d.attachment());out.put(p+"mention_role_id",d.roleId());out.put(p+"mention_severity",d.mentionSeverity());
    }
    public static void request(ServerPlayer player,ServerSettingsRequest packet,Consumer<ServerSettingsPayload> reply) {
        if(!LagSense.isAdmin(player.createCommandSourceStack())) { reply.accept(new ServerSettingsPayload(packet.section(),"","Operator access required.",false));return; }
        if(!Set.of("discord","advanced").contains(packet.section())) { reply.accept(new ServerSettingsPayload(packet.section(),"","Unknown settings page.",false));return; }
        String message="";boolean saved=false;
        if(!gate.allow(player.getUUID(),System.nanoTime()))message="Please wait a moment, then try again.";
        else if(!packet.json().isEmpty())try {
            var edit=Profiler.JSON.fromJson(packet.json(),Edit.class);
            if(edit==null)throw new IllegalArgumentException("Missing settings.");
            Profiler.editServerSettings(packet.section(),edit);saved=true;message="Settings applied.";
        } catch(java.io.IOException e){message="Could not save the configuration, so your settings were not changed.";}
        catch(com.google.gson.JsonParseException e){message="The settings are invalid and were not applied.";}
        catch(IllegalArgumentException e){message=e.getMessage()==null?"The settings are invalid and were not applied.":e.getMessage();}
        catch(RuntimeException e){message="The settings are invalid and were not applied.";}
        reply.accept(new ServerSettingsPayload(packet.section(),Profiler.JSON.toJson(view(Profiler.settings)),message,saved));
    }
    static void clear(){gate.clear();}
    static void forget(UUID player){gate.forget(player);}
    private ServerSettingsForms(){}
}
