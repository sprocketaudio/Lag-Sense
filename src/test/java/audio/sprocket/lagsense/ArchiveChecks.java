package audio.sprocket.lagsense;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

final class ArchiveChecks {
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static Report report(long id, int ticks, List<Report.Entry> entries) { return new Report(id, id * 1000, ticks, 2, true, false, entries); }
    private static ProfileArchive.State state(ProfileArchive archive) throws Exception {
        var future = new CompletableFuture<ProfileArchive.State>(); archive.list(complete(future)); return future.get(10, TimeUnit.SECONDS);
    }
    private static <T> BiConsumer<T, Exception> complete(CompletableFuture<T> future) {
        return (value, error) -> { if (error != null) future.completeExceptionally(error); else future.complete(value); };
    }
    private static void configure(ProfileArchive archive, ProfileArchive.Settings settings) throws Exception {
        var future = new CompletableFuture<ProfileArchive.State>(); archive.configure(settings, complete(future)); future.get(10, TimeUnit.SECONDS);
    }
    static void run(Gson gson) throws Exception {
        Path directory = Files.createTempDirectory("lagsense-archive-check-");
        List<Exception> failures = new CopyOnWriteArrayList<>();
        var target = new Report.Entry("Block", "minecraft:hopper", "minecraft:overworld", -1, 64, -1, 1000000, 20);
        var settings = new ProfileArchive.Settings(true, 2, 8);
        try {
            try (var archive = new ProfileArchive(directory, gson, ProfileArchive.Settings.defaults(), failures::add)) {
                archive.capture(report(1, 20, List.of(target)));
                require(state(archive).profiles().isEmpty(), "History is opt-in");
                configure(archive, settings);
                archive.capture(report(1, 20, List.of(target))); archive.capture(report(2, 40, List.of(target))); archive.capture(report(3, 20, List.of(target)));
                var saved = state(archive);
                require(saved.profiles().stream().map(ProfileArchive.Summary::id).toList().equals(List.of(3L, 2L)), "Count retention keeps newest profiles, newest first");
                require(!Files.exists(directory.resolve("profile-1.json")), "Count retention actually removes the older file");
                archive.enrich(report(1, 20, List.of(target)));
                require(state(archive).profiles().size() == 2 && !Files.exists(directory.resolve("profile-1.json")), "Late CPU analysis cannot resurrect a pruned profile");
                var enriched = new Report(3, 3000, 20, 2, true, false, List.of(target), false, 0, List.of(), 20,
                    new Report.Diagnostics("Manual", 0, 0, List.of(new Report.Task("example", "example.Handler#tick", 4)), List.of()));
                archive.enrich(enriched); state(archive);
                var future = new CompletableFuture<Report>(); archive.read(3, complete(future));
                var loaded = future.get(10, TimeUnit.SECONDS);
                require(loaded.entries().equals(List.of(target)) && loaded.diagnostics().tasks().equals(enriched.diagnostics().tasks()), "Saved profile round trip includes asynchronous CPU enrichment");
                var missing = new CompletableFuture<Report>(); archive.read(1, complete(missing));
                try { missing.get(10, TimeUnit.SECONDS); throw new AssertionError("Expired IDs must be rejected"); } catch (ExecutionException expected) { }
                configure(archive, new ProfileArchive.Settings(false, 2, 8)); archive.capture(report(4, 20, List.of(target)));
                require(state(archive).profiles().size() == 2, "Disabling recording preserves existing history and adds nothing");
            }
            try (var archive = new ProfileArchive(directory, gson, settings, failures::add)) {
                require(state(archive).profiles().size() == 2, "History survives restarting its worker");
                // A single large but valid profile fits; two exceed the configured 8 MiB disk budget.
                String text = "a".repeat(500);
                var bigTarget = new Report.Entry(text, text, text, 0, 1, 0, 1, 1);
                archive.capture(report(5, 20, Collections.nCopies(3000, bigTarget))); state(archive);
                archive.capture(report(6, 20, Collections.nCopies(3000, bigTarget)));
                var saved = state(archive);
                require(saved.profiles().size() == 1 && saved.profiles().getFirst().id() == 6, "Disk budget prunes oldest saves independently of count limit");
                require(saved.profiles().stream().mapToLong(ProfileArchive.Summary::bytes).sum() <= 8 * 1024 * 1024, "Actual UTF-8 archive sizes respect disk budget");
                archive.capture(report(7, 20, Collections.nCopies(6000, bigTarget))); state(archive);
                require(state(archive).profiles().getFirst().id() == 6 && !Files.exists(directory.resolve("profile-7.json")), "Oversized individual save is rejected without losing existing history");
                require(failures.size() == 1, "Oversized capture produces one actionable failure"); failures.clear();
            }
            Files.writeString(directory.resolve("profile-999.json"), "{broken");
            try (var archive = new ProfileArchive(directory, gson, settings, failures::add)) {
                require(state(archive).profiles().size() == 1, "Corrupt files do not hide valid history on restart");
                require(failures.size() == 1, "Corrupt archive is reported");
            }
            comparison(target, gson);
        } finally {
            try (var paths = Files.walk(directory)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
    private static void comparison(Report.Entry target, Gson gson) {
        var before = report(10, 20, List.of(target));
        var sameRate = new Report.Entry(target.kind(), target.type(), target.dimension(), -2, 64, -2, target.nanos() * 2, target.calls() * 2);
        var added = new Report.Entry("Entity", "minecraft:zombie", "minecraft:the_nether", 0, 64, 0, 4000000, 40);
        var after = report(11, 40, List.of(sameRate, added));
        var comparison = ProfileComparison.between(before, after);
        var hopper = comparison.types().stream().filter(c -> c.type().equals(target.type())).findFirst().orElseThrow();
        require(hopper.delta() == 0, "Different capture lengths compare per tick, not total time");
        require(comparison.chunks().stream().anyMatch(c -> c.x() == -1 && c.z() == -1 && c.beforeTargets() == 1 && c.afterTargets() == 1), "Negative positions share the correct floor chunk across captures");
        var zombie = comparison.types().getFirst();
        require(zombie.beforeTargets() == 0 && zombie.afterTargets() == 1 && zombie.delta() == 100, "New observations compare against zero without mixing dimensions");
        require(ProfileComparison.between(after,before).equals(comparison), "Selection order cannot reverse chronology");
        var disappeared = ProfileComparison.between(after,report(12,40,List.of(sameRate))).types().getFirst();
        require(disappeared.afterTargets()==0&&disappeared.delta()==-100,"No-longer-observed targets remain chronological improvements");
        require(gson.fromJson(gson.toJson(comparison), ProfileComparison.class).equals(comparison), "Comparison payload round trip");
        String csv = ReportExport.csv(before);
        require(csv.contains("50.000000") && csv.contains("minecraft:hopper") && !csv.contains("zombie"), "Export includes only received entries, with normalized units");
        var tricky = new Report.Entry("=formula", "quoted,\"type", target.dimension(), 0, 0, 0, 1, 1);
        String escaped = ReportExport.csv(report(12, 1, List.of(tricky)));
        require(escaped.contains("\"'=formula\"") && escaped.contains("\"quoted,\"\"type\""), "CSV quotes commas and quotes and neutralizes formulas");
        require(ReportExport.csv(comparison).contains("delta_us_per_tick"), "Comparisons have spreadsheet exports");
    }
}
