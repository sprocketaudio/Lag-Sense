package net.sprocketgames.lagsense;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Bounded delivery worker. No network calls, retries or JSON assembly on the server tick thread. */
public final class DiscordDelivery implements AutoCloseable {
    public record Response(int status, double retrySeconds,boolean attachmentsAccepted) {public Response(int status,double retrySeconds){this(status,retrySeconds,true);}}
    @FunctionalInterface public interface Transport { Response send(URI target, DiscordMessage.Body body) throws Exception; }
    @FunctionalInterface interface Sleeper { void sleep(long milliseconds) throws InterruptedException; }
    private final ThreadPoolExecutor worker;
    private final Transport transport;
    private final Sleeper sleeper;
    private final Consumer<String> log;
    private final Map<String, Long> nextEligible = new HashMap<>(); // worker-owned; independent delivery channels
    private volatile ServerSettings.Discord settings;
    private volatile String status = "";
    private volatile long successAt;
    private volatile boolean invalidEndpoint, closed;
    private long nextTest;
    private volatile long rateLimitedUntil;
    public DiscordDelivery(ServerSettings.Discord settings, Consumer<String> log) { this(settings, http(), Thread::sleep, log); }
    DiscordDelivery(ServerSettings.Discord settings, Transport transport, Sleeper sleeper, Consumer<String> log) {
        this.settings = settings; this.transport = transport; this.sleeper = sleeper; this.log = log;
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8),
            r -> Thread.ofPlatform().daemon().name("LagSense Discord delivery").unstarted(r), new ThreadPoolExecutor.AbortPolicy());
    }
    public void configure(ServerSettings.Discord value) {
        boolean changed = !settings.webhookUrl().equals(value.webhookUrl()); settings = value;
        if (changed) { invalidEndpoint = false; rateLimitedUntil = 0; }
    }
    public String summary() { return !settings.enabled() ? "Off" : invalidEndpoint ? "Webhook rejected" : "On"; }
    public String status() {
        var value = settings; if (!value.enabled()) return "Discord: Off";
        var lines = new ArrayList<String>(); lines.add("Discord: " + summary());
        lines.add("Timer alerts: " + StatusText.onOff(value.timer().enabled())); lines.add("Spike alerts: " + StatusText.onOff(value.spikes().enabled())); lines.add("Manual sending: " + StatusText.onOff(value.manual().enabled()));
        int queued = worker.getQueue().size(); if (queued > 0) lines.add("Queued: " + queued);
        if (!status.isEmpty()) lines.add("Last result: " + status);
        if (successAt > 0) lines.add("Last sent: " + StatusText.duration((System.currentTimeMillis() - successAt) / 1000) + " ago");
        return String.join("\n", lines);
    }
    public void capture(Report report, String reason) {
        String channel = report.diagnostics() != null && report.diagnostics().trigger().equals("Scheduled") ? "timer"
            : report.diagnostics() != null && report.diagnostics().trigger().equals("Spike follow-up") ? "spikes" : "manual";
        capture(report, reason, channel);
    }
    public void capture(Report report, String reason, String channel) {
        var connection = settings;
        var delivery = section(connection, channel);
        if (!connection.enabled() || !delivery.enabled() || closed) return;
        if(!report.admin()){status="Player-scoped reports are not sent to Discord";return;}
        if(report.recording()||report.diagnostics()!=null&&report.diagnostics().cpu().state().equals("pending")){status="Waiting for completed capture data";return;}
        enqueue(() -> {
            if (!current(connection) || !section(settings, channel).enabled()) return;
            long now = System.nanoTime();
            if (now < nextEligible.getOrDefault(channel, Long.MIN_VALUE)) { status = "Cooldown active"; return; }
            DiscordMessage.Message message = DiscordMessage.build(report, connection, delivery, Profiler.JSON);
            if (message == null) { status = "Below alert thresholds"; return; }
            java.nio.file.Path directory=null;
            try{
                java.nio.file.Path attachment=null;
                if(!delivery.attachment().equals("none")){
                    directory=java.nio.file.Files.createTempDirectory("lagsense-discord-");attachment=directory.resolve(DiscordMessage.filename(report,delivery.attachment()));
                    switch(delivery.attachment()){
                        case "html" -> HtmlReportExport.write(attachment,report, net.sprocketgames.lagsense.client.UiTheme.DEFAULT);
                        case "json" -> ReportFiles.json(attachment,report,Profiler.JSON,CaptureBudget.MAX_FILE_BYTES);
                        case "csv" -> {try(var out=new java.io.OutputStreamWriter(new LimitedOutput(java.nio.file.Files.newOutputStream(attachment)),java.nio.charset.StandardCharsets.UTF_8)){ReportExport.writeCsv(report,out);}}
                        default -> throw new IllegalArgumentException("Invalid report file format");
                    }
                    if(java.nio.file.Files.size(attachment)>connection.uploadLimitMiB()*1048576L){status="Report attachment exceeds the configured "+connection.uploadLimitMiB()+" MiB upload limit. No report was sent";log.accept(status);return;}
                }
                try(var body=attachment==null?DiscordMessage.body(message):DiscordMessage.body(message,attachment,attachment.getFileName().toString())){
                    if(!send(connection,body)||!current(connection))return;
                }
                nextEligible.put(channel, System.nanoTime() + delivery.cooldownSeconds() * 1_000_000_000L);
            }catch(Exception error){if(!closed){status="Report could not be prepared. No report was sent";log.accept(status);}}
            finally{cleanup(directory);}

        });
    }
    private void cleanup(java.nio.file.Path directory){
        if(directory==null)return;
        try{
            java.util.List<java.nio.file.Path> contents;try(var files=java.nio.file.Files.list(directory)){contents=files.toList();}
            for(var file:contents)java.nio.file.Files.deleteIfExists(file);java.nio.file.Files.deleteIfExists(directory);
        }catch(java.io.IOException error){log.accept("Could not remove temporary Discord report files");}
    }
    private static final class LimitedOutput extends java.io.FilterOutputStream {
        long count;LimitedOutput(java.io.OutputStream out){super(out);}
        private void check(int bytes)throws java.io.IOException{if((count+=bytes)>CaptureBudget.MAX_FILE_BYTES)throw new java.io.IOException("Report exceeds export limit");}
        @Override public void write(int value)throws java.io.IOException{check(1);out.write(value);}
        @Override public void write(byte[] bytes,int offset,int length)throws java.io.IOException{check(length);out.write(bytes,offset,length);}
    }
    public synchronized boolean test() {
        if (!settings.enabled() || settings.webhookUrl().isBlank() || closed || System.nanoTime() < nextTest) return false;
        nextTest = System.nanoTime() + 30_000_000_000L; invalidEndpoint = false; var connection = settings;
        return enqueue(() -> {try(var body=DiscordMessage.body(DiscordMessage.test(connection,Profiler.JSON))){send(connection,body);}});
    }
    private static ServerSettings.Delivery section(ServerSettings.Discord settings, String channel) {
        return channel.equals("timer") ? settings.timer() : channel.equals("spikes") ? settings.spikes() : settings.manual();
    }
    private boolean current(ServerSettings.Discord connection) { return !closed && settings.enabled() && connection.equals(settings); }
    private boolean enqueue(Runnable job) {
        try { worker.execute(() -> { try { job.run(); } catch (RuntimeException error) { status = "Could not prepare message"; log.accept(status); } }); return true; }
        catch (RejectedExecutionException error) { status = "Delivery queue full or closed"; log.accept(status); return false; }
    }
    private boolean send(ServerSettings.Discord connection, DiscordMessage.Body body) {
        if (invalidEndpoint || !current(connection)) return false;
        var target = ServerSettings.validateWebhook(connection.webhookUrl());
        for (int attempt = 0; attempt < 3 && current(connection); attempt++) {
            try {
                long remaining = rateLimitedUntil - System.nanoTime();
                if (remaining > 60_000_000_000L) { status = "Discord rate limit; try later"; return false; }
                if (remaining > 0) sleeper.sleep((remaining + 999_999) / 1_000_000);
                if (!current(connection)) return false;
                Response response = transport.send(target, body);
                if (!current(connection)) return false;
                if (response.status() >= 200 && response.status() < 300) {
                    if(!response.attachmentsAccepted()){status="Discord did not confirm the complete attachment. Delivery failed";log.accept(status);return false;}
                    successAt = System.currentTimeMillis(); status = "Sent"; return true;
                }
                status = response.status()==413?"Report upload rejected as too large (HTTP 413). No report was delivered":response.status()==400?"Discord rejected the message or attachment (HTTP 400)":"HTTP "+response.status();
                if (response.status() == 401 || response.status() == 403 || response.status() == 404) { invalidEndpoint = true; status = "Webhook rejected (HTTP " + response.status() + ")"; log.accept(status); return false; }
                if (response.status() != 429 && response.status() < 500) { log.accept(status); return false; }
                double wait = response.status() == 429 ? response.retrySeconds() : Math.pow(2, attempt);
                if (!Double.isFinite(wait) || wait < 0) { status = "Invalid rate-limit response. Test the webhook to retry."; invalidEndpoint = true; log.accept(status); return false; }
                if (response.status() == 429) rateLimitedUntil = System.nanoTime() + (long)(Math.min(wait, 86400) * 1_000_000_000L);
                if (wait > 60) { status = "Discord rate limit; try later"; log.accept(status); return false; }
                if (attempt < 2 && response.status() != 429) sleeper.sleep(Math.max(100, (long)Math.ceil(wait * 1000)));
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); return false; }
            catch (Exception error) {
                if (closed) return false;
                status = "Connection failed";
                if (attempt < 2) { try { sleeper.sleep(1000L << attempt); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; } }
            }
        }
        log.accept(status + " Retry limit reached."); return false;
    }
    public static Transport http() {
        return new Transport() {
            private HttpClient client;
            @Override public Response send(URI target, DiscordMessage.Body body) throws Exception {
            if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
            var request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(10)).header("Content-Type", body.contentType()).header("User-Agent", "LagSense/1.0-RC5")
                .POST(body.publisher()).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                String text = new String(stream.readNBytes(64*1024), java.nio.charset.StandardCharsets.UTF_8); double retry = -1;
                if (response.statusCode() == 429) {
                    try { retry = Double.parseDouble(response.headers().firstValue("Retry-After").orElse("-1")); } catch (RuntimeException ignored) { }
                    if (retry < 0) try { retry = JsonParser.parseString(text).getAsJsonObject().get("retry_after").getAsDouble(); } catch (RuntimeException ignored) { }
                }
                return new Response(response.statusCode(), retry,body.attachmentsAccepted(text));
            }
            }
        };
    }
    // Tests can await queue order without a sleep or live webhook.
    void flush() throws Exception { worker.submit(() -> {}).get(10, TimeUnit.SECONDS); }
    @Override public void close() { closed = true; worker.getQueue().clear(); worker.shutdownNow(); }
}
