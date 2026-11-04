package audio.sprocket.lagsense;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

final class AutomationChecks {
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final String URL = "https://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwx";
    private static ServerSettings.Delivery delivery(boolean enabled, int cooldown, double ms, double percent, String match, String group, boolean coordinates, String attachment) {
        return new ServerSettings.Delivery(enabled, cooldown, 5, ms, percent, match, group, attachment, "","degraded");
    }
    private static ServerSettings.Discord connection(ServerSettings.Delivery timer, ServerSettings.Delivery spikes) {
        return new ServerSettings.Discord(true, URL, "Test server @everyone", timer, spikes, delivery(true, 0, 0, 0, "either", "target", false, "none"));
    }
    private static Report report(String reason) {
        var a = new Report.Entry("Block", "example:machine", "minecraft:overworld", 9876543, 71, 7654321, 400_000_000, 100);
        var b = new Report.Entry("Block", "example:machine", "minecraft:overworld", 9876544, 71, 7654321, 200_000_000, 100);
        return new Report(123, 456, 100, 80, true, false, List.of(a, b), false, 0, List.of(), 12.5, new Report.Diagnostics(reason, 0, 0));
    }
    static void run(Gson gson) throws Exception {
        var defaults = ServerSettings.defaults();
        require(!defaults.timer().enabled() && !defaults.spikes().enabled() && !defaults.discord().enabled(), "All automation and external delivery defaults OFF");
        require(defaults.spikes().minimumTps() == 12 && defaults.spikes().thresholdMs() == 0, "Explicit TPS threshold replaces automatic baseline learning");
        require(!defaults.discord().timer().enabled() && !defaults.discord().spikes().enabled(), "Timer and spike delivery each require opt-in");
        require(!ServerSettings.template().contains("learning_seconds"), "No learned baseline config remains");
        boolean invalid = false;
        try { ServerSettings.parse("[capture.timer]\nenabled=true\ninterval_minutes=1\nduration_seconds=60", Map.of()); } catch (IllegalArgumentException e) { invalid = true; }
        require(invalid, "Config validation rejects overlapping timer duration before activation");
        invalid = false;
        try { ServerSettings.parse("[discord]\nwebhook_url=\"secret-token\"", Map.of()); } catch (IllegalArgumentException e) { invalid = !e.toString().contains("secret-token"); }
        require(invalid, "Bad webhook errors never expose the secret value");
        invalid = false;
        try { ServerSettings.parse("[discord]\nwebhook_url = \"secret-token", Map.of()); } catch (IllegalArgumentException e) { invalid = !e.toString().contains("secret-token"); }
        require(invalid, "TOML syntax errors never echo secret-bearing lines");
        for (String bad : List.of("http://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrst", "https://discord.com.evil.test/api/webhooks/123456789012345678/abcdefghijklmnopqrst", URL + "?x=1", URL + "#token", "http://127.0.0.1:8080/")) {
            invalid = false; try { ServerSettings.validateWebhook(bad); } catch (IllegalArgumentException e) { invalid = true; } require(invalid, "Only expected HTTPS Discord webhook URLs are accepted");
        }
        var env = ServerSettings.parse("[discord]\nenabled=true", Map.of("LAGSENSE_DISCORD_WEBHOOK", URL));
        require(env.discord().webhookUrl().equals(URL) && !env.toString().contains("abcdefghijkl"), "Environment fallback works and record diagnostics redact webhook");
        Path directory = Files.createTempDirectory("lagsense-config-check-"); Path path = directory.resolve("server.toml");
        try {
            var loaded = ServerSettings.load(path);
            var changed = ServerSettings.update(path, loaded, Map.of("capture.timer.enabled", true, "capture.timer.duration_seconds", 5));
            require(changed.timer().enabled() && changed.timer().captureSeconds() == 5 && !changed.spikes().enabled(), "GUI edits persist independently to the same config");
            require(Files.readString(path).contains("#") && ServerSettings.load(path).equals(changed), "Commented config survives edits and restart");
            var captures = new CaptureSettings(changed.timer(), changed.spikes());
            String packet = gson.toJson(captures);
            require(!packet.contains("discord") && !packet.contains("webhook") && gson.fromJson(packet, CaptureSettings.class).equals(captures), "Capture settings packets include no Discord config or secret");
            invalid = false; try { ServerSettings.update(path, loaded, Map.of("history.enabled", true)); } catch (IllegalArgumentException e) { invalid = true; }
            require(invalid && ServerSettings.load(path).equals(changed), "Stale GUI save cannot overwrite unapplied file changes");
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(directory); }
        thresholds();
        var either = delivery(true, 0, 5, 4, "either", "target", false, "json");
        var both = delivery(true, 0, 5, 4, "both", "target", false, "json");
        var c = connection(either, either); var report = report("Scheduled");
        require(DiscordMessage.rows(report, either).getFirst().msPerTick() == 4 && DiscordMessage.rows(report, either).getFirst().percent() == 5, "Contributor share uses actual 80ms work, not a 50ms budget");
        var colocated = new Report(1, 2, 100, 80, true, false, List.of(report.entries().getFirst(), report.entries().getFirst()));
        require(DiscordMessage.rows(colocated, either).size() == 2, "Individual targets sharing a block position are not silently merged");
        require(DiscordMessage.build(report, c, either, gson) != null, "Either filter accepts sufficient percentage despite lower absolute cost");
        require(DiscordMessage.build(report, c, both, gson) == null, "Both filter requires both enabled thresholds");
        var combined = delivery(true, 0, 5, 4, "both", "type", false, "json");
        require(DiscordMessage.build(report, c, combined, gson) != null && DiscordMessage.rows(report, combined).getFirst().targets() == 2, "Combined type filter catches many individually cheap contributors");
        var redacted = DiscordMessage.build(report, c, either, gson);
        require(redacted.json().contains("9876543"),"Configured administrative webhooks retain captured positions without coordinate redaction");
        require(JsonParser.parseString(redacted.json()).getAsJsonObject().getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty(), "Unrequested mentions blocked even with server-name mention text");
        var csvSettings = delivery(true, 0, 0, 0, "either", "target", false, "csv");
        require(ReportExport.csv(report).contains("9876543"), "CSV supplements preserve captured positions");
        var visible = delivery(true, 0, 0, 0, "both", "target", true, "json");
        require(DiscordMessage.build(report, c, visible, gson).json().contains("9876543"), "Configured administrator reports preserve coordinates");
        worker(); localReceiver();DiscordReportingChecks.run();
    }
    private static void thresholds() {
        var detector = new SpikeDetector(); long now = 1;
        detector.configure(new SpikeDetector.Settings(true, 0, 3, 5, 30, 12, 3), now);
        for (int i = 0; i < 600; i++) { now += 66_666_667; require(!detector.observe(5_000_000, now, false), "15 TPS is healthy above explicit threshold 12"); }
        boolean triggered = false;
        for (int i = 0; i < 50; i++) { now += 100_000_000; triggered |= detector.observe(5_000_000, now, false); }
        require(triggered && detector.reason().contains("configured 12.0"), "Actual throughput drop to 10 TPS triggers even with cheap tick work");
        for (int i = 0; i < 100; i++) { now += 100_000_000; require(!detector.observe(5_000_000, now, false), "No repeats while capture completion is pending"); }
        detector.captureFinished(now); long end = now;
        for (int i = 0; i < 299; i++) { now += 100_000_000; require(!detector.observe(5_000_000, now, false), "TPS capture cooldown begins at actual completion"); }
        triggered = false;
        for (int i = 0; i < 60; i++) { now += 100_000_000; triggered |= detector.observe(5_000_000, now, false); }
        require(triggered, "Persistent slow TPS can repeat after cooldown without recovery gate");
        detector.configure(new SpikeDetector.Settings(true, 0, 3, 5, 30, 10, 3), now);
        for (int i = 0; i < 1000; i++) { now += 83_333_334; require(!detector.observe(5_000_000, now, false), "Lowering explicit threshold to 10 accepts a server normally at 12 TPS"); }
        require(detector.settings().minimumTps() == 10, "Threshold never learns or changes itself");
    }
    private static void worker() throws Exception {
        var timer = delivery(true, 0, 0, 0, "either", "target", true, "none");
        var spikes = delivery(true, 600, 0, 0, "either", "target", true, "none");
        List<String> posted = new CopyOnWriteArrayList<>(), logs = new CopyOnWriteArrayList<>();
        try (var delivery = new DiscordDelivery(connection(timer, spikes), (target, body) -> { posted.add(new String(body.bytes(), java.nio.charset.StandardCharsets.UTF_8)); return new DiscordDelivery.Response(204, 0); }, ms -> {}, logs::add)) {
            delivery.capture(report("Spike follow-up"), "Threshold 12"); delivery.flush();
            delivery.capture(report("Spike follow-up"), "Threshold 12"); delivery.capture(report("Scheduled"), ""); delivery.capture(report("Scheduled"), ""); delivery.flush();
            require(posted.size() == 3, "Spike cooldown never suppresses timer reports, which each send independently");
            require(!delivery.status().contains("abcdefghijkl"), "Delivery status never reveals webhook");
            delivery.configure(new ServerSettings.Discord(false, URL, "off", timer, spikes, timer)); delivery.capture(report("Scheduled"), ""); delivery.flush();
            require(posted.size() == 3, "Global OFF prevents all external delivery");
        }
        AtomicInteger calls = new AtomicInteger(); List<Long> waits = new CopyOnWriteArrayList<>();
        try (var delivery = new DiscordDelivery(connection(timer, spikes), (target, body) -> new DiscordDelivery.Response(calls.incrementAndGet() == 1 ? 429 : 204, .25), waits::add, logs::add)) {
            delivery.capture(report("Scheduled"), ""); delivery.flush(); require(calls.get() == 2 && waits.stream().anyMatch(ms -> ms >= 250), "Discord retry_after is respected before retry");
        }
        calls.set(0);
        try (var delivery = new DiscordDelivery(connection(timer, spikes), (target, body) -> { calls.incrementAndGet(); return new DiscordDelivery.Response(404, 0); }, ms -> {}, logs::add)) {
            delivery.capture(report("Scheduled"), ""); delivery.flush(); delivery.capture(report("Scheduled"), ""); delivery.flush();
            require(calls.get() == 1, "Invalid webhook is not hammered repeatedly");
        }
        calls.set(0);
        try (var delivery = new DiscordDelivery(connection(timer, spikes), (target, body) -> { calls.incrementAndGet(); return new DiscordDelivery.Response(429, 120); }, ms -> {}, logs::add)) {
            delivery.capture(report("Scheduled"), ""); delivery.flush(); delivery.capture(report("Scheduled"), ""); delivery.flush();
            require(calls.get() == 1, "Long Discord rate limits also hold later queued notifications");
        }
        calls.set(0);
        try (var delivery = new DiscordDelivery(connection(timer, spikes), (target, body) -> { calls.incrementAndGet(); throw new java.io.IOException("secret-url-abcdefghijkl"); }, ms -> {}, logs::add)) {
            delivery.capture(report("Scheduled"), ""); delivery.flush();
            require(calls.get() == 3 && !delivery.status().contains("abcdefghijkl"), "Connection failures have bounded retries and sanitized status");
        }
        var started = new CountDownLatch(1); var finished = new CountDownLatch(1); var release = new CountDownLatch(1); calls.set(0);
        var busy = new DiscordDelivery(connection(timer, spikes), (target, body) -> {
            calls.incrementAndGet(); started.countDown();
            try { release.await(5, TimeUnit.SECONDS); return new DiscordDelivery.Response(204, 0); }
            finally { finished.countDown(); }
        }, ms -> {}, logs::add);
        try {
            busy.capture(report("Scheduled"), ""); require(started.await(5, TimeUnit.SECONDS), "Delivery worker starts without blocking caller");
            for (int i = 0; i < 20; i++) busy.capture(report("Scheduled"), "");
            require(logs.stream().anyMatch(s -> s.contains("queue full")), "Slow receiver cannot grow an unbounded notification queue");
            busy.close(); release.countDown(); require(finished.await(5, TimeUnit.SECONDS), "Shutdown interrupts pending network delivery");
            require(calls.get() == 1, "Shutdown discards queued external sends");
        } finally { release.countDown(); busy.close(); }
        require(logs.stream().noneMatch(s -> s.contains("abcdefghijkl")), "Logs contain no webhook token");
    }
    private static void localReceiver() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
        List<String> received = new CopyOnWriteArrayList<>();
        server.createContext("/test", exchange -> {
            received.add(exchange.getRequestHeaders().getFirst("Content-Type") + "\n" + new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            var request=received.getLast();
            require(exchange.getRequestURI().getQuery().equals("wait=true&with_components=true"),"Real HTTP execution uses both Discord webhook query parameters");
            int jsonStart=request.indexOf("\r\n\r\n")+4;var componentPayload=JsonParser.parseString(request.substring(jsonStart,request.indexOf("\r\n--",jsonStart))).getAsJsonObject();DiscordReportingChecks.layout(componentPayload,"html");
            var attachments=new com.google.gson.JsonArray();String boundary=exchange.getRequestHeaders().getFirst("Content-Type").split("boundary=",2)[1];
            for(String part:request.split(java.util.regex.Pattern.quote("--"+boundary))){var match=java.util.regex.Pattern.compile("filename=\"([^\"]+)\"").matcher(part);if(match.find()){int start=part.indexOf("\r\n\r\n")+4;String payload=part.substring(start,part.length()-2);var file=new com.google.gson.JsonObject();file.addProperty("filename",match.group(1));file.addProperty("size",payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);attachments.add(file);}}
            var result=new com.google.gson.JsonObject();result.add("attachments",attachments);byte[] response=result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });
        server.start();
        try {
            var local = java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/test");
            var realHttp = DiscordDelivery.http(); var option = delivery(true, 0, 0, 0, "either", "target", true, "html");
            try (var delivery = new DiscordDelivery(connection(option, option), (ignoredDiscordUri, body) -> realHttp.send(java.net.URI.create(local.toString()+"?"+ignoredDiscordUri.getRawQuery()), body), ms -> {}, s -> {})) {
                delivery.capture(report("Scheduled"), ""); delivery.flush();
                require(received.size() == 1 && received.getFirst().contains("multipart/form-data") && received.getFirst().contains("payload_json") && received.getFirst().contains("LagSense-")&&received.getFirst().contains(".html")&&received.getFirst().contains("<!doctype html>"), "Real HTTP multipart delivery reaches local receiver without contacting Discord");
                require(delivery.status().contains("Last result: Sent"),"Real transport verifies attachment names and byte sizes returned by the mock webhook");
                require(received.getFirst().contains("9876543"), "Explicitly authorised full HTML contains captured locations");
            }
        } finally { server.stop(0); }
    }
}
