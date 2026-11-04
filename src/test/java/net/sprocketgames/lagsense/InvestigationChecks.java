package net.sprocketgames.lagsense;

import java.util.*;
import java.io.*;
import java.util.zip.*;

final class InvestigationChecks {
    private static void require(boolean value, String why) { if (!value) throw new AssertionError(why); }
    private static int csvColumns(String line){boolean quoted=false;int fields=1;for(char c:line.toCharArray()){if(c=='"')quoted=!quoted;else if(c==','&&!quoted)fields++;}return fields;}
    private static CpuInvestigation.Frame frame(String owner, String name) { return new CpuInvestigation.Frame(owner, owner + ".Machine", name, "()V"); }
    static void run(com.google.gson.Gson gson) throws Exception {
        var root = frame("minecraft", "tick"); var tick = frame("ae2", "tick"); var leaf = frame("ae2", "saveChanges");
        var builder = new CpuInvestigation.Builder("Server thread (#42)");
        builder.add(List.of(leaf, tick, root)); builder.add(List.of(tick, root)); builder.add(List.of(leaf, tick, tick, root));
        var cpu = builder.finish(); CpuInvestigation.validate(cpu);
        require(cpu.samples() == 3 && cpu.treeSamples() == 3 && !cpu.limited(), "All retained stacks use the same denominator");
        var m = cpu.methods().stream().filter(v -> v.name().equals("tick") && v.owner().equals("ae2")).findFirst().orElseThrow();
        require(m.inclusive() == 3 && m.self() == 1, "Recursion counts a method once per stack, with self only at the leaf");
        require(cpu.methods().stream().mapToLong(CpuInvestigation.Method::self).sum() == cpu.treeSamples(), "Direct counts partition retained stacks");
        require(cpu.nodes().getFirst().inclusive() == 3 && cpu.nodes().getFirst().self() == 0, "Tree root counts full retained stacks");
        for (var n : cpu.nodes()) {
            long children = cpu.nodes().stream().filter(c -> c.parent() == n.id()).mapToLong(CpuInvestigation.Node::inclusive).sum();
            require(n.inclusive() == n.self() + children, "Every tree branch partitions into direct and child observations");
        }
        require(cpu.edges().stream().anyMatch(e -> e.caller() == m.id() && e.callee() == m.id()), "Actual recursive caller relationships are retained");
        var index = new CallTreeIndex(cpu); var collapsed = index.rows(Set.of(), "ae2");
        require(collapsed.size() == 1 && index.branch(collapsed.getFirst().node()), "Mod-filtered tree retains the parent call chain");
        var expanded = new HashSet<Integer>(); cpu.nodes().forEach(n -> expanded.add(n.id()));
        require(index.rows(expanded, "ae2").size() == cpu.nodes().size() - 1 && index.rows(expanded, "missing").isEmpty(), "Tree expansion and mod filters use actual paths");
        var nav = new TaskNavigation(); nav.page = 3; nav.select("ae2"); require(nav.page == 0 && nav.owner.equals("ae2"), "Opening a mod starts at its first action");
        require(nav.back() && nav.page == 3 && nav.owner == null && !nav.back(), "Back restores the overview page");
        var tasks = List.of(new Report.Task("ae2", "a#tick", 70), new Report.Task("chisel", "b#tick", 30), new Report.Task("ae2", "c#save", 20));
        var grouped = TaskNavigation.tasks(tasks, true, null);
        require(grouped.getFirst().owner().equals("ae2") && grouped.getFirst().samples() == 90, "Mod totals use displayed action counts");
        require(TaskNavigation.tasks(tasks, true, "ae2").stream().mapToLong(Report.Task::samples).sum() == 90 && grouped.stream().mapToLong(Report.Task::samples).sum() == 120, "Mod navigation preserves numerator and global listed denominator");
        var lib = CpuHotPaths.choose(List.of(new CpuHotPaths.Frame("unknown.SharedLibrary", "run"), new CpuHotPaths.Frame("ae2.Callback", "run")), type -> type.startsWith("ae2") ? "ae2" : "unattributed");
        require(lib.owner().equals("ae2") && lib.method().endsWith("ae2.Callback#run"), "A known mod frame is preferred to an unknown library leaf; attribution names the observed mod frame");
        var diagnostics = new Report.Diagnostics("Manual", 1, 2, tasks, List.of(), List.of(), "disabled", cpu);
        require(TaskNavigation.callbackState(diagnostics).contains("off"), "Callback disabled has a distinct message");
        require(TaskNavigation.callbackState(new Report.Diagnostics("Manual", 0, 0, List.of(), List.of(), List.of(), "enabled", null)).contains("No supported"), "Enabled with no events is distinct");
        require(TaskNavigation.callbackState(new Report.Diagnostics("Manual", 0, 0, List.of(), List.of(), List.of(), "unavailable", null)).contains("unavailable"), "Unavailable instrumentation is distinct");
        var entries = new ArrayList<Report.Entry>();
        for (int i = 0; i < 200; i++) entries.add(new Report.Entry(i % 2 == 0 ? "Block" : "Random block", "ae2:machine", "minecraft:overworld", i % 16, 64, 0, 1000, 2));
        entries.add(new Report.Entry("Entity", "secret:mob", "minecraft:overworld", 7, 64, 0, 5000, 3));
        var report = new Report(1, 2, 20, 2, true, false, entries, false, 0, List.of(), 20, diagnostics);
        var chunk = ChunkInvestigation.create(report, "minecraft:overworld", 0, 0, false, e -> e.x() < 7);
        long expected = entries.stream().filter(e -> e.x() < 7).mapToLong(Report.Entry::nanos).sum();
        require(chunk.nanos() == expected && chunk.categories().stream().mapToLong(ChunkInvestigation.Category::nanos).sum() == expected, "Chunk categories sum only permitted timings, without CPU or callback totals");
        require(chunk.hotspots().stream().allMatch(h -> h.index() == -1 && h.entry().x() < 7) && !gson.toJson(chunk).contains("secret"), "Player detail payloads exclude private targets and server indices");
        var adminChunk = ChunkInvestigation.create(report, "minecraft:overworld", 0, 0, true, e -> false);
        require(adminChunk.targets() == 201 && adminChunk.hotspots().size() == 128 && adminChunk.nanos() == 205000, "Full chunk totals include contributors beyond the main display cap");
        require(!ChunkInvestigation.create(report, "minecraft:overworld", 0, 0, false, e -> false).equals(chunk), "Revoked access changes the reply and can be rejected before transmission");
        require(ChunkInvestigation.create(report, "minecraft:the_nether", 0, 0, false, e -> true).targets() == 0, "Chunk inspection cannot cross dimensions");
        require(report.view(false, e -> true).diagnostics() == null, "CPU stacks and callback states remain admin-only");
        require(report.view(true, e -> true).diagnostics().cpu().methods().isEmpty() && gson.fromJson(ReportWire.json(report, gson), Report.class).diagnostics().cpu().nodes().isEmpty(), "Normal report packets contain summaries rather than stack trees");
        var data = new InvestigationWire.Data(cpu, adminChunk, "");
        require(InvestigationWire.decode(InvestigationWire.encode(data)).equals(data), "Bounded compressed investigations round trip");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY, net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        try {
            for (boolean global : new boolean[]{false, true}) {
                buffer.clear(); var request = new InvestigationRequest(7, report.id(), false, false, global, "minecraft:overworld", -1, 2);
                InvestigationRequest.CODEC.encode(buffer, request);
                require(InvestigationRequest.CODEC.decode(buffer).equals(request) && buffer.readableBytes() == 0, "Request codec preserves restricted/global scope and negative chunks");
            }
            buffer.clear(); byte[] encoded = InvestigationWire.encode(data); InvestigationPayload.CODEC.encode(buffer, new InvestigationPayload(7, encoded));
            var received = InvestigationPayload.CODEC.decode(buffer); require(received.token() == 7 && Arrays.equals(received.bytes(), encoded), "Reply codec preserves bounded compressed contents");
        } finally { buffer.release(); }
        require(gson.fromJson(gson.toJson(report), Report.class).diagnostics().cpu().equals(cpu), "Full stacks survive history serialization");
        require(InvestigationExport.csv(cpu).contains("ae2.Machine#saveChanges()V") && InvestigationExport.csv(chunk).contains("captured_ticks") && !InvestigationExport.csv(chunk).contains("secret"), "Exports carry recorded counts and respect the filtered chunk data");
        var callbackReport = new Report(1, 2, 20, 1, true, false, List.of(entries.getFirst()), false, 0, Report.aggregate(List.of(entries.getFirst())), 20,
            new Report.Diagnostics("Manual", 0, 0, tasks, List.of(), List.of(new Report.Callback("ae2", "test.TickEvent", "test.Listener#tick", 100, 2, 60)), "enabled", null));
        String csv = ReportExport.csv(callbackReport);
        require(csv.contains("max_call_nanos,average_call_nanos,entity_uuid") && csv.contains(",60,50.0") && csv.lines().allMatch(line -> csvColumns(line)==34), "CSV exports keep consistent columns and preserve callback maximum and average");
        ProfileArchive.validate(report);
        var crowded = new CpuInvestigation.Builder("Server thread");
        for (int i = 0; i < 10000; i++) crowded.add(List.of(frame("fixture", "leaf" + i), root));
        var bounded = crowded.finish(); CpuInvestigation.validate(bounded);
        require(!bounded.limited() && bounded.methods().size() == 10001 && bounded.nodes().size() == 10002 && bounded.edges().size() == 10000, "Large stack sets stay within storage budgets");
        var deep = new CpuInvestigation.Builder("thread"); deep.add(Collections.nCopies(100, leaf)); var depth = deep.finish();
        require(!depth.limited() && depth.nodes().size() == 101 && depth.methods().getFirst().inclusive() == 1 && depth.methods().getFirst().self() == 1, "Deep and recursive stacks are capped without inflating unique method counts");
        var retainedNodes=new ArrayList<CpuInvestigation.Node>();var retainedExpanded=new HashSet<Integer>();
        retainedNodes.add(new CpuInvestigation.Node(0,-1,-1,1,0));
        for(int i=1;i<=90;i++){retainedNodes.add(new CpuInvestigation.Node(i,i-1,0,1,i==90?1:0));retainedExpanded.add(i);}
        var retainedDeep=new CpuInvestigation("ready","thread",1,1,false,List.of(new CpuInvestigation.Method(0,"fixture","fixture.Machine","recursiveTick","()V",1,1)),retainedNodes,List.of());
        CpuInvestigation.validate(retainedDeep);var retainedRows=new CallTreeIndex(retainedDeep).rows(retainedExpanded,null);
        require(retainedRows.size()==90&&retainedRows.getLast().depth()==89,"The UI must traverse all valid retained nodes without adding its own hierarchy-depth cutoff");
        var bytes = new ByteArrayOutputStream(); try (var zip = new GZIPOutputStream(bytes)) { for(int i=0;i<=InvestigationWire.MAX_EXPANDED/8192;i++)zip.write(new byte[8192]); }
        try { InvestigationWire.decode(bytes.toByteArray()); throw new AssertionError("Expected expansion budget rejection"); } catch (IOException expectedError) { }
        require(InvestigationWire.encode(new InvestigationWire.Data(bounded, null, "")).length <= InvestigationWire.MAX_BYTES, "Large valid trees fit the network budget");
        var badNodes = new ArrayList<>(cpu.nodes()); badNodes.set(1, new CpuInvestigation.Node(1, -1, -1, 0, 0));
        try { CpuInvestigation.validate(new CpuInvestigation("ready", cpu.thread(), cpu.samples(), cpu.treeSamples(), false, cpu.methods(), badNodes, cpu.edges())); throw new AssertionError("Expected malformed tree rejection"); } catch (IllegalArgumentException expectedError) { }
        System.out.println("Investigation checks passed (navigation, stack bounds/counts, states, chunk filtering and wire budgets).");
    }
}
