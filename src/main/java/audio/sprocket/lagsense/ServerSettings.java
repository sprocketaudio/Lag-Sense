package audio.sprocket.lagsense;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** Deliberately not a NeoForge SERVER config: that config type is synchronized to clients. */
public record ServerSettings(ProfileSchedule.Settings timer, SpikeDetector.Settings spikes, boolean monitorEmptyServer,
                             ProfileArchive.Settings history, Discord discord, BackupMonitor.Settings backups, boolean modTasks, boolean detailedCallbacks, ProfilingLimits limits) {
    public ServerSettings {limits=limits==null?ProfilingLimits.defaults():limits;}
    public ServerSettings(ProfileSchedule.Settings timer,SpikeDetector.Settings spikes,boolean monitorEmptyServer,ProfileArchive.Settings history,Discord discord,BackupMonitor.Settings backups,boolean modTasks,boolean detailedCallbacks){this(timer,spikes,monitorEmptyServer,history,discord,backups,modTasks,detailedCallbacks,ProfilingLimits.defaults());}
    public record Discord(boolean enabled, String webhookUrl, String serverName, Delivery timer, Delivery spikes, Delivery manual,int uploadLimitMiB,String avatarUrl) {
        public Discord(boolean enabled,String webhookUrl,String serverName,Delivery timer,Delivery spikes,Delivery manual){this(enabled,webhookUrl,serverName,timer,spikes,manual,10,"");}
        public Discord(boolean enabled,String webhookUrl,String serverName,Delivery timer,Delivery spikes,Delivery manual,int uploadLimitMiB){this(enabled,webhookUrl,serverName,timer,spikes,manual,uploadLimitMiB,"");}
        public Discord { avatarUrl=validateAvatar(avatarUrl);if(uploadLimitMiB<1||uploadLimitMiB>128)throw new IllegalArgumentException("discord.upload_limit_mib must be 1–128.");Objects.requireNonNull(webhookUrl); if (serverName == null || serverName.isBlank() || serverName.length() > 80) throw new IllegalArgumentException("discord.server_name must have 1–80 characters."); }
        @Override public String toString() { return "Discord[enabled=" + enabled + ", webhook=<redacted>]"; }
    }
    public record Delivery(boolean enabled, int cooldownSeconds, int top, double minimumMs, double minimumPercent,
                           String match, String groupBy, String attachment, String roleId, String mentionSeverity) {
        public Delivery {
            if (cooldownSeconds < 0 || cooldownSeconds > 86400 || top < 1 || top > 10) throw new IllegalArgumentException("Discord cooldown must be 0–86400 seconds and top_results must be 1–10.");
            if (!Double.isFinite(minimumMs) || minimumMs < 0 || minimumMs > 10000 || !Double.isFinite(minimumPercent) || minimumPercent < 0 || minimumPercent > 100)
                throw new IllegalArgumentException("Contributor filters require non-negative milliseconds and 0–100 percent.");
            if (!Set.of("either", "both").contains(match) || !Set.of("target", "type", "both").contains(groupBy) || !Set.of("none", "html", "json", "csv").contains(attachment))
                throw new IllegalArgumentException("Invalid Discord match, group_by or attachment option.");
            if(mentionSeverity==null)mentionSeverity="degraded";
            if(!Set.of("never","critical","degraded","warning","always").contains(mentionSeverity))throw new IllegalArgumentException("Invalid Discord mention_severity option.");
            if (roleId == null || !roleId.isEmpty() && !roleId.matches("[0-9]{16,22}")) throw new IllegalArgumentException("mention_role_id must be empty or a Discord role ID.");
        }
    }
    public static ServerSettings defaults() { return parse(template(), Map.of()); }
    public static ServerSettings load(Path path) throws IOException {
        if (!Files.exists(path)) { Files.createDirectories(path.getParent()); Files.writeString(path, template(), StandardOpenOption.CREATE_NEW); }
        if (Files.size(path) > 131072) throw new IOException("LagSense server config exceeds 128 KiB.");
        return parse(Files.readString(path), System.getenv());
    }
    public static ServerSettings update(Path path, ServerSettings expected, Map<String, Object> values) throws IOException {
        if (Files.size(path) > 131072) throw new IOException("Configuration is too large.");
        String text = Files.readString(path);
        if (!parse(text, System.getenv()).equals(expected)) throw new IllegalArgumentException("Config changed on disk. Run /lagsense reload before applying in-game edits.");
        var file = new TomlParser().parse(text);
        values.forEach(file::set);
        for(String section:List.of("timer","spikes","manual")){
            String prefix="discord."+section+".";file.remove(prefix+"include_coordinates");file.remove(prefix+"group_repeats");
            if(file.contains(prefix+"top_offenders")&&!file.contains(prefix+"top_results"))file.set(prefix+"top_results",file.get(prefix+"top_offenders"));file.remove(prefix+"top_offenders");
        }
        String updated = new TomlWriter().writeToString(file);
        ServerSettings validated = parse(updated, System.getenv());
        Path temp = Files.createTempFile(path.getParent(), "lagsense-config-", ".tmp");
        try {
            Files.writeString(temp, updated);
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
        return validated;
    }
    public static ServerSettings parse(String text, Map<String, String> environment) {
        UnmodifiableConfig file;
        try { file = new TomlParser().parse(text); }
        catch (RuntimeException error) { throw new IllegalArgumentException("Invalid TOML in lagsense-server.toml."); }
        var reader = new Reader(file);
        var timer = new ProfileSchedule.Settings(reader.bool("capture.timer.enabled", false), reader.integer("capture.timer.interval_minutes", 5), reader.integer("capture.timer.duration_seconds", 30));
        var spikes = new SpikeDetector.Settings(reader.bool("capture.spikes.enabled", false), reader.number("capture.spikes.tick_work_alert_ms", 0),
            reader.integer("capture.spikes.consecutive_ticks", 3), reader.integer("capture.spikes.duration_seconds", 10), reader.integer("capture.spikes.cooldown_seconds", 120),
            reader.number("capture.spikes.tps_alert_threshold", 12), reader.integer("capture.spikes.sustain_seconds", 10));
        boolean empty = reader.bool("capture.spikes.monitor_empty_server", false);
        var history = new ProfileArchive.Settings(reader.bool("history.enabled", false), reader.integer("history.max_profiles", 10), reader.integer("history.max_mib", 32));
        var backups = new BackupMonitor.Settings(reader.string("backups.mode", "defer_automatic"), reader.integer("backups.grace_seconds", 60));
        boolean modTasks = reader.bool("profiling.mod_tasks", true);
        boolean detailedCallbacks = reader.bool("profiling.detailed_callbacks", false);
        var limits=new ProfilingLimits(reader.string("profiling.limits.mode","adaptive"),reader.integer("profiling.limits.event_mib",128),reader.integer("profiling.limits.cpu_mib",128),reader.integer("profiling.limits.jfr_mib",128),reader.integer("profiling.limits.stack_depth",128));
        boolean enabled = reader.bool("discord.enabled", false);
        String url = reader.string("discord.webhook_url", ""), env = reader.string("discord.webhook_url_env", "LAGSENSE_DISCORD_WEBHOOK");
        if (!env.isEmpty() && !env.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) throw new IllegalArgumentException("Invalid webhook environment variable name.");
        if (url.isBlank() && !env.isEmpty()) url = environment.getOrDefault(env, "");
        if (!url.isBlank()) validateWebhook(url);
        if (enabled && url.isBlank()) throw new IllegalArgumentException("Discord is enabled but no webhook is configured.");
        var discord = new Discord(enabled, url, reader.string("discord.server_name", "Minecraft server"), delivery(reader, "timer", 0), delivery(reader, "spikes", 600), delivery(reader, "manual", 0),reader.integer("discord.upload_limit_mib",10),reader.string("discord.avatar_url",""));
        reader.checkKnown(file, "");
        return new ServerSettings(timer, spikes, empty, history, discord, backups, modTasks, detailedCallbacks,limits);
    }
    public static String validateAvatar(String value){
        if(value==null||value.isBlank())return "";
        try{
            String text=value.strip();URI uri=URI.create(text);String host=uri.getHost();
            if(text.length()>2048||!"https".equalsIgnoreCase(uri.getScheme())||host==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getPort()!=-1&&uri.getPort()!=443)throw new IllegalArgumentException();
            host=host.toLowerCase(Locale.ROOT);if(host.endsWith("."))host=host.substring(0,host.length()-1);
            if(host.contains(":" )||host.matches("[0-9.]+")){
                if(!host.contains(":" )&&!host.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IllegalArgumentException();
                var address=java.net.InetAddress.getByName(host);byte[] bytes=address.getAddress();
                if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress()||bytes.length==16&&(bytes[0]&0xfe)==0xfc||bytes.length==4&&((bytes[0]&255)==0||(bytes[0]&255)==100&&((bytes[1]&255)>=64&&(bytes[1]&255)<=127)))throw new IllegalArgumentException();
            }else {if(!host.contains("."))throw new IllegalArgumentException();for(String suffix:List.of("localhost","local","internal","lan","invalid","test"))if(hostNameEnds(host,suffix))throw new IllegalArgumentException();}
            return text;
        }catch(Exception error){throw new IllegalArgumentException("Avatar URL must be a public HTTPS image URL, or blank.");}
    }
    private static boolean hostNameEnds(String host,String suffix){return host.equals(suffix)||host.endsWith("."+suffix);}
    public static URI validateWebhook(String text) {
        try {
            URI uri = URI.create(text);
            if (!"https".equals(uri.getScheme()) || !"discord.com".equals(uri.getHost()) || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getQuery() != null || !uri.getPath().matches("/api(?:/v[0-9]+)?/webhooks/[0-9]{16,22}/[A-Za-z0-9_-]{20,200}")) throw new IllegalArgumentException();
            return URI.create(text + "?wait=true&with_components=true");
        } catch (RuntimeException error) { throw new IllegalArgumentException("Webhook must be an HTTPS discord.com incoming webhook URL, without query parameters."); }
    }
    private static Delivery delivery(Reader reader, String name, int cooldown) {
        String p = "discord." + name + ".";
        reader.bool(p+"include_coordinates",false); // Accept obsolete files, without retaining or applying the removed option.
        reader.bool(p+"group_repeats",false); // Consume legacy keys without retaining obsolete behaviour.
        int legacyTop=reader.integer(p+"top_offenders",5);
        return new Delivery(reader.bool(p + "enabled", false), reader.integer(p + "cooldown_seconds", cooldown), reader.integer(p + "top_results", legacyTop),
            reader.number(p + "minimum_ms_per_tick", 0), reader.number(p + "minimum_tick_percent", 0), reader.string(p + "match", "either"), reader.string(p + "group_by", "target"),
            reader.string(p + "attachment", "none"), reader.string(p + "mention_role_id", ""),reader.string(p+"mention_severity","degraded"));
    }
    public static String template() {
        try (InputStream input = ServerSettings.class.getResourceAsStream("/lagsense-server.toml")) {
            if (input == null) throw new IllegalStateException("Missing server config template");
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException error) { throw new IllegalStateException("Cannot read server config template"); }
    }
    private static final class Reader {
        private final UnmodifiableConfig config;
        private final Set<String> known = new HashSet<>();
        Reader(UnmodifiableConfig config) { this.config = config; }
        private Object get(String key, Object fallback) { known.add(key); return config.contains(key) ? config.get(key) : fallback; }
        boolean bool(String key, boolean fallback) { Object v = get(key, fallback); if (v instanceof Boolean b) return b; throw invalid(key); }
        String string(String key, String fallback) { Object v = get(key, fallback); if (v instanceof String s) return s; throw invalid(key); }
        double number(String key, double fallback) { Object v = get(key, fallback); if (v instanceof Number n && Double.isFinite(n.doubleValue())) return n.doubleValue(); throw invalid(key); }
        int integer(String key, int fallback) { double value = number(key, fallback); if (value == Math.rint(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) return (int)value; throw invalid(key); }
        private IllegalArgumentException invalid(String key) { return new IllegalArgumentException("Invalid value type/range for " + key + "."); }
        void checkKnown(UnmodifiableConfig node, String prefix) {
            for (var entry : node.entrySet()) {
                String key = prefix + entry.getKey();
                if (entry.getValue() instanceof UnmodifiableConfig child) checkKnown(child, key + ".");
                else if (!known.contains(key)) throw new IllegalArgumentException("Unknown server config option. Check spelling against the bundled template.");
            }
        }
    }
}
