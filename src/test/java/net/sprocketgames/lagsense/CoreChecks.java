package net.sprocketgames.lagsense;

import java.util.*;

public final class CoreChecks {
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        var permitted = new Report.Entry("Block", "minecraft:hopper", "minecraft:overworld", -1, 64, -1, 10_000_000, 100);
        var secret = new Report.Entry("Entity", "minecraft:villager", "minecraft:overworld", 1000, 64, 1000, 50_000_000, 100);
        var otherDimension = new Report.Entry("Block", "minecraft:hopper", "minecraft:the_nether", -1, 64, -1, 5_000_000, 100);
        var original = new Report(123, 456, 100, 3, true, false, List.of(secret, permitted, otherDimension));
        List<PublicArea> regions = new ArrayList<>();
        require(original.view(false, regions).entries().isEmpty(), "No public area must fail closed");
        regions.add(new PublicArea("test", "minecraft:overworld", -16, -16, -1, -1));
        var base = original.view(false, regions);
        require(base.entries().equals(List.of(permitted)), "Filter dimension and negative block coordinates");
        require(!base.admin() && base.mspt() == 0, "Do not expose global statistics in a base view");
        require(original.view(false, regions).entries().equals(List.of(permitted)), "Public regions do not require a player identity");
        require(original.view(true, regions).entries().size() == 3, "Admins can view all entries");
        require(permitted.micros(100) == 100, "Nanoseconds convert to microseconds per captured tick");
        var huge = new Report(1, 2, 1, 1, true, false, Collections.nCopies(1000, permitted));
        require(huge.view(false, regions).entries().size() == 1000, "Retain all permitted entries; transport segmentation has a separate bound");
        var chunk = huge.view(false, regions).chunks().getFirst();
        require(chunk.targets() == 1000 && chunk.nanos() == 10_000_000_000L, "Chunk totals must include targets beyond the individual display limit");
        require(chunk.x() == -1 && chunk.z() == -1, "Chunk aggregation uses floor coordinates");
        require(base.chunks().size() == 1 && base.chunks().getFirst().targets() == 1, "Aggregate only permitted targets");
        var separated = Report.aggregate(List.of(permitted, otherDimension));
        require(separated.size() == 2, "Same chunk coordinates in different dimensions stay separate");
        var hiddenCap = new Report(1, 2, 1, 1, true, true, List.of(permitted));
        require(!hiddenCap.view(false, regions).truncated(), "Do not reveal a global capture cap to base viewers");
        var recording = new Report(1, 2, 1, 1, true, false, List.of(permitted), true, 10).view(false, regions);
        require(recording.recording() && recording.secondsRemaining() == 10, "Retain recording status through filtering");
        var timed = new Report(1, 2, 100, 3, true, false, List.of(permitted), false, 0, List.of(), 19.5);
        require(timed.view(true, regions).tps() == 19.5, "Retain measured TPS for admin reports");
        require(timed.view(false, regions).tps() == 0, "Do not expose global TPS in a base report");
        var gson = new com.google.gson.Gson();
        String json = gson.toJson(base);
        require(!json.contains("villager") && !json.contains("the_nether"), "Private targets never enter serialized player payload");
        require(gson.fromJson(json, Report.class).equals(base), "Snapshot JSON round trip");
        var fluid = new Report.Entry("Scheduled fluid", "minecraft:flowing_water", "minecraft:overworld", -2, 64, -2, 1000, 2);
        var distantTick = new Report.Entry("Scheduled block", "minecraft:repeater", "minecraft:overworld", 2000, 64, 2000, 100000, 1);
        var scheduledReport = new Report(1, 2, 10, 1, true, false, List.of(distantTick, fluid, permitted));
        var scheduledView = scheduledReport.view(false, regions);
        require(scheduledView.entries().equals(List.of(fluid, permitted)), "Scheduled work uses the same server-side region filtering");
        require(scheduledView.chunks().getFirst().targets() == 2, "Permitted scheduled work participates in chunk summaries");
        require(!gson.toJson(scheduledView).contains("repeater"), "Private scheduled locations never enter the player packet");
        regions.clear();
        require(original.view(false, regions).entries().isEmpty(), "Deleted public area blocks subsequent requests");
        var diagnostic = new Report(1, 2, 1, 1, true, false, List.of(permitted), false, 0, List.of(), 20,
            new Report.Diagnostics("Spike follow-up", 100, 200));
        require(diagnostic.view(true, regions).diagnostics() != null, "Admin receives profiler diagnostics");
        require(diagnostic.view(false, regions).diagnostics() == null, "Player cannot infer server-wide capture overhead or spike metadata");
        claimChecks(permitted, secret, otherDimension, gson);
        areaChecks(permitted, gson);
        spikeChecks();
        scheduleChecks();
        storageChecks(gson);
        ArchiveChecks.run(gson);
        AutomationChecks.run(gson);DiscordPolishChecks.run();
        NewFeatureChecks.run(gson);
        TimingCoverageChecks.run();
        CpuSamplesChecks.run();ProfilingLimitsChecks.policy();
        HtmlExportChecks.run();
        RealArchiveChecks.run();
        CallbackChecks.run();
        PresentationChecks.run();
        InvestigationChecks.run(gson);
        UiChecks.run();
        ResultsUsabilityChecks.run();
        HeatmapChecks.run();
        CoverageChecks.run();
        WorldMarkerChecks.run();
        OverlayInspectionChecks.run();MarkerRayChecks.run();
        ProfilingUsabilityChecks.run();
        ComparisonDetailChecks.run();
        ComparisonActionChecks.run();
        HistoricalTargetChecks.run();
        TeleportChecks.run();
        ChunkWorldChecks.run();
        SelectedCopyChecks.run();
        GradientChecks.run();
        TypographyChecks.run();
        UiInteractionChecks.run();
        ChronologicalChecks.run(gson);
        SettingsFormChecks.run(gson);
        System.out.println("LagSense core checks passed (privacy, bounds, timing and serialization).");
    }
    private static void claimChecks(Report.Entry permitted, Report.Entry secret, Report.Entry nether, com.google.gson.Gson gson) {
        int[] lookups = {0};
        BaseAccess.Provider claims = chunk -> {
            lookups[0]++;
            return chunk.dimension().equals("minecraft:overworld") && chunk.x() == -1 && chunk.z() == -1
                ? BaseAccess.Decision.ALLOW : BaseAccess.Decision.UNCLAIMED;
        };
        var access = new BaseAccess(List.of(), List.of(claims));
        var capture = new Report(1, 2, 100, 3, true, false, List.of(secret, permitted, permitted, nether));
        var view = capture.view(false, access);
        require(view.entries().equals(List.of(permitted, permitted)), "Claim permission filters dimension and floor chunk coordinates");
        require(lookups[0] == 3, "Claim APIs queried once per chunk per report request");
        require(view.chunks().size() == 1 && view.chunks().getFirst().targets() == 2, "Claim checks precede aggregation");
        require(!gson.toJson(view).contains("villager") && !gson.toJson(view).contains("the_nether"), "Unauthorized claim data never reaches serialization");
        var conflict = new BaseAccess(List.of(), List.of(claims, c -> BaseAccess.Decision.DENY));
        require(!conflict.test(permitted), "Conflicting claim ownership denies automatic access");
        var reverse = new BaseAccess(List.of(), List.of(c -> BaseAccess.Decision.DENY, claims));
        require(!reverse.test(permitted), "Conflicting claims deny regardless of provider order");
        require(!new BaseAccess(List.of(), List.of(c -> BaseAccess.Decision.UNCLAIMED)).test(permitted), "Unclaimed land does not grant access");
        require(!new BaseAccess(List.of(), List.of(c -> null)).test(permitted), "Unknown claim decisions fail closed");
        var shared = new BaseAccess(List.of(new PublicArea("shared", "minecraft:overworld", -1, -1, -1, -1)), List.of(c -> BaseAccess.Decision.DENY));
        require(shared.test(permitted), "Explicit admin sharing remains available independently of claims");
        require(capture.view(false, new BaseAccess(List.of(), List.of(c -> BaseAccess.Decision.DENY))).entries().isEmpty(), "A fresh request reflects revoked ownership");
        var many = new Report(1, 2, 100, 3, true, false, Collections.nCopies(200, permitted));
        var bounded = many.view(false, new BaseAccess(List.of(), List.of(claims)));
        require(bounded.entries().size() == 200 && bounded.chunks().getFirst().targets() == 200, "Claim filtering preserves all permitted entries and complete chunk aggregation");
    }
    private static void areaChecks(Report.Entry permitted, com.google.gson.Gson gson) {
        var area = PublicArea.between("spawn", "minecraft:overworld", new PublicArea.Point(3, 5), new PublicArea.Point(-2, -4));
        require(area.minX() == -2 && area.minZ() == -4 && area.maxX() == 3 && area.maxZ() == 5 && area.width() == 6 && area.depth() == 10, "Opposite corners normalize and count inclusive block columns");
        require(area.contains("minecraft:overworld", -2, -4) && area.contains("minecraft:overworld", 3, 5), "Both corner columns are included");
        require(!area.contains("minecraft:overworld", 4, 5) && !area.contains("minecraft:the_nether", 3, 5), "Outside edge and other dimension stay private");
        require(area.contains(new Report.Entry("Block", "test", "minecraft:overworld", 0, -64, 0, 1, 1)) && area.contains(new Report.Entry("Block", "test", "minecraft:overworld", 0, 319, 0, 1, 1)), "Public areas cover full build height");
        var narrow = new PublicArea("one", "minecraft:overworld", -1, -1, -1, -1);
        var access = new BaseAccess(List.of(narrow), List.of(c -> BaseAccess.Decision.DENY));
        var neighbor = new Report.Entry("Block", "secret-neighbor", "minecraft:overworld", -2, 64, -1, 1, 1);
        require(access.test(permitted) && !access.test(neighbor) && access.test(permitted), "Block-exact public access never leaks another column through the chunk cache");
        var report = new Report(1, 2, 10, 1, true, false, List.of(neighbor, permitted));
        var view = report.view(false, new BaseAccess(List.of(narrow), List.of()));
        require(view.chunks().getFirst().targets() == 1 && !gson.toJson(view).contains("secret-neighbor"), "Public areas filter before serialization and aggregation within a chunk");
        require(gson.fromJson(gson.toJson(area), PublicArea.class).equals(area), "Public area persistence round trip");
        var selection = new PublicArea.Selection("minecraft:overworld", new PublicArea.Point(0, 0), null);
        require(selection.second() == null, "One-corner selection does not invent a saved area");
        boolean rejected = false;
        try { selection.area("unfinished"); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "Cannot publish an incomplete selection");
        rejected = false;
        try { PublicArea.validated(List.of(area, area)); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "Duplicate names cannot overwrite another public area");
        rejected = false;
        try { new PublicArea.Point(Integer.MIN_VALUE, 0); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "Coordinate bounds do not overflow");
        rejected = false;
        try { gson.fromJson("{\"name\":\"bad\",\"dimension\":\"minecraft:overworld\",\"minX\":2,\"maxX\":1,\"minZ\":0,\"maxZ\":0}", PublicArea.class); } catch (RuntimeException expected) { rejected = true; }
        require(rejected, "Invalid persisted bounds fail closed");
        var state = new AreaPayload.State(List.of(area), selection, true);
        require(gson.fromJson(gson.toJson(state), AreaPayload.State.class).equals(state), "Admin boundary state round trip");
    }
    private static void spikeChecks() {
        long second = 1_000_000_000L, slow = 150_000_000L;
        var detector = new SpikeDetector();
        require(!detector.observe(slow, 60 * second, false), "Spike capture defaults off");
        var settings = new SpikeDetector.Settings(true, 100, 3, 10, 120);
        detector.configure(settings, 0);
        require(!detector.observe(slow, second, false), "Startup warm-up suppresses spike captures");
        require(!detector.observe(slow, 31 * second, false), "One slow tick is not sustained lag");
        require(!detector.observe(1, 32 * second, false), "Fast tick resets the streak");
        require(!detector.observe(slow, 33 * second, false), "First consecutive slow tick");
        require(!detector.observe(slow, 34 * second, false), "Second consecutive slow tick");
        require(detector.observe(slow, 35 * second, false), "Third consecutive slow tick triggers a follow-up capture");
        require(!detector.observe(slow, 40 * second, false), "Cooldown suppresses repeated capture");
        detector.captureFinished(45 * second);
        require(!detector.observe(slow, 166 * second, true), "Manual profile suppresses automatic capture");
        require(!detector.observe(slow, 167 * second, false), "Manual profile reset the slow-tick streak");
        require(!detector.observe(slow, 168 * second, false), "Cooldown expires without requiring healthy ticks");
        require(detector.observe(slow, 169 * second, false), "Continuous trouble can be sampled again after cooldown");
        for (int i = 0; i < 100; i++) require(!detector.observe(slow, (170 + i) * second, false), "No repeats while capture completion is pending");
        detector.captureFinished(270 * second);
        for (int i = 0; i < 119; i++) require(!detector.observe(slow, (271 + i) * second, false), "Cooldown starts at actual completion, not planned duration");
        detector.configure(new SpikeDetector.Settings(true, 120, 3, 10, 600), 500 * second);
        for (int i = 0; i < 100; i++) require(!detector.observe(83_333_333L, (531 + i) * second, false), "Steady 12 TPS work time stays below the server's chosen 120ms trigger");
        detector.observe(slow, 631 * second, false); detector.observe(slow, 632 * second, false);
        require(detector.observe(slow, 633 * second, false), "Worse-than-baseline ticks trigger the configured profile");
        detector.captureFinished(650 * second);
        require(!detector.observe(slow, 1249 * second, false), "Custom ten-minute cooldown is enforced");
        require(!detector.observe(slow, 1250 * second, false), "Fresh streak after custom cooldown");
        require(!detector.observe(slow, 1251 * second, false), "Second tick after custom cooldown");
        require(detector.observe(slow, 1252 * second, false), "Custom cooldown allows later repeat captures");
        detector.configure(SpikeDetector.Settings.defaults(), 1300 * second);
        require(!detector.observe(slow, 1500 * second, false), "Off disables the detector");
        boolean rejected = false;
        try { new SpikeDetector.Settings(true, Double.NaN, 3, 10, 120); } catch (IllegalArgumentException e) { rejected = true; }
        require(rejected, "Reject invalid persisted thresholds");
    }
    private static void storageChecks(com.google.gson.Gson gson) throws Exception {
        java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("lagsense-store-check-");
        List<Exception> failures = new java.util.concurrent.CopyOnWriteArrayList<>();
        try {
            try (var store = new JsonStore(directory, gson, failures::add)) {
                for (int i = 0; i < 100; i++) store.save("latest.json", Map.of("sequence", i));
                store.save("areas.json", List.of(new PublicArea("separate", "minecraft:overworld", 0, 0, 1, 1)));
                boolean rejected = false;
                try { store.save("../outside.json", Map.of()); } catch (IllegalArgumentException e) { rejected = true; }
                require(rejected, "Store restricts file paths");
            }
            require(failures.isEmpty(), "Background writer completed without failures: " + failures);
            require(java.nio.file.Files.readString(directory.resolve("latest.json")).contains("99"), "Flush the newest coalesced snapshot on shutdown");
            require(java.nio.file.Files.readString(directory.resolve("areas.json")).contains("separate"), "Independent saves do not overwrite one another");
            require(!java.nio.file.Files.exists(directory.resolve("latest.json.tmp")), "Atomic replacement leaves no unfinished temporary file");
        } finally {
            for (String name : List.of("latest.json", "areas.json", "latest.json.tmp", "areas.json.tmp")) java.nio.file.Files.deleteIfExists(directory.resolve(name));
            java.nio.file.Files.delete(directory);
        }
    }
    private static void scheduleChecks() {
        long second = 1_000_000_000L;
        var schedule = new ProfileSchedule();
        require(!schedule.due(1000 * second, false), "Timer defaults off");
        schedule.configure(new ProfileSchedule.Settings(true, 1, 5), 0);
        require(!schedule.due(59 * second, false), "No early scheduled capture");
        require(schedule.due(60 * second, false), "Run at the configured interval");
        require(!schedule.due(60 * second, false), "Timer cannot fire twice for one deadline");
        require(!schedule.due(120 * second, true), "Skip overlapping manual or automatic profile");
        require(!schedule.due(121 * second, false), "No catch-up capture after a busy deadline");
        require(schedule.due(180 * second, false), "Next regular deadline remains usable");
        schedule.configure(ProfileSchedule.Settings.defaults(), 181 * second);
        require(!schedule.due(1000 * second, false), "Disabling timer cancels future captures");
        boolean rejected = false;
        try { new ProfileSchedule.Settings(true, 1, 60); } catch (IllegalArgumentException e) { rejected = true; }
        require(rejected, "Capture duration must be shorter than the interval");
        var reload = new ProfileSchedule();
        reload.configure(new ProfileSchedule.Settings(true, 5, 30), 1000 * second);
        require(reload.remainingSeconds(1000 * second) == 300, "Restart starts a fresh interval");
    }
}
