package net.sprocketgames.lagsense;

import java.util.*;

/** Immutable post-capture stack data. Counts are observations, never inferred durations. */
public record CpuInvestigation(String state, String thread, long samples, long treeSamples, boolean limited,
                               List<Method> methods, List<Node> nodes, List<Edge> edges, Limits limits) {
    public record Limits(long budget,long configured,String mode,int depth,long omittedPaths,long omittedEdges,long shortenedStacks,long jvmTruncatedStacks) {}
    public CpuInvestigation(String state,String thread,long samples,long treeSamples,boolean limited,List<Method> methods,List<Node> nodes,List<Edge> edges){this(state,thread,samples,treeSamples,limited,methods,nodes,edges,null);}
    public String limitation(){
        if(!limited)return "";
        if(limits==null)return "Call paths are incomplete in this capture.";
        return "Call paths: "+methods.size()+" methods, "+Math.max(0,nodes.size()-1)+" nodes and "+edges.size()+" relationships retained. "+
            String.format(Locale.ROOT,"Budget %.2f MiB effective (%s ceiling %.2f MiB). ",limits.budget()/(double)(1<<20),limits.mode(),limits.configured()/(double)(1<<20))+
            limits.omittedPaths()+" path observations and "+limits.omittedEdges()+" relationship observations omitted. "+limits.shortenedStacks()+" stacks exceeded "+limits.depth()+" retained frames. "+limits.jvmTruncatedStacks()+" stacks were shortened by the JVM.";
    }
    public static final int MAX_DEPTH = 128;
    public record Method(int id, String owner, String type, String name, String descriptor, long inclusive, long self) {
        public String signature() { return type + "#" + name + descriptor; }
        public String label() { return type.substring(type.lastIndexOf('.') + 1) + "#" + name; }
    }
    public record Node(int id, int parent, int method, long inclusive, long self) {}
    public record Edge(int caller, int callee, long samples) {}
    public record Frame(String owner, String type, String name, String descriptor) {}
    public CpuInvestigation {
        methods = methods == null ? List.of() : List.copyOf(methods); nodes = nodes == null ? List.of() : List.copyOf(nodes); edges = edges == null ? List.of() : List.copyOf(edges);
    }
    public static CpuInvestigation empty(String state) { return new CpuInvestigation(state, "", 0, 0, false, List.of(), List.of(), List.of()); }
    public CpuInvestigation summary() { return new CpuInvestigation(state, thread, samples, treeSamples, limited, List.of(), List.of(), List.of(),limits); }
    public static final class Builder {
        private record MethodKey(String owner, String type, String name, String descriptor) {}
        private record NodeKey(int parent, int method) {}
        private record Pair(int caller, int callee) {}
        private static final class Count { long inclusive, self,seen; }
        private static final class EdgeCount {long samples,seen;}
        private final String thread;
        private final DiagnosticBudget budget;
        private final Map<MethodKey, Integer> ids = new LinkedHashMap<>();
        private final List<Count> methodCounts = new ArrayList<>();
        private final Map<NodeKey, Integer> nodeIds = new HashMap<>();
        private final List<NodeKey> nodeKeys = new ArrayList<>();
        private final List<Count> nodeCounts = new ArrayList<>();
        private final Map<Pair, EdgeCount> edges = new HashMap<>();
        private long samples, retained,omittedEdges,shortenedStacks,jvmTruncatedStacks;
        private final int depth;
        private boolean limited;
        public Builder(String thread) {this(thread,DiagnosticBudget.background());}
        public Builder(String thread,DiagnosticBudget budget) {this(thread,budget,MAX_DEPTH);}
        public Builder(String thread,DiagnosticBudget budget,int depth) {this.thread=thread;this.budget=Objects.requireNonNull(budget);this.depth=depth;nodeKeys.add(new NodeKey(-1,-1));nodeCounts.add(new Count());}
        private boolean reserve(long bytes){return budget.reserve(bytes);}
        /** Input order is leaf to root, matching JFR. */
        public void add(List<Frame> frames) {add(frames,false);}
        public void add(List<Frame> frames,boolean jvmTruncated) {
            if(jvmTruncated){jvmTruncatedStacks++;limited=true;}
            if (frames.isEmpty()) return; samples++;
            int size = Math.min(depth, frames.size()); if(size<frames.size()){limited=true;shortenedStacks++;}
            int[] methods = new int[size];
            for (int i = 0; i < size; i++) {
                var f = frames.get(i); var key = new MethodKey(Objects.toString(f.owner(),""), Objects.toString(f.type(),""), Objects.toString(f.name(),""), Objects.toString(f.descriptor(),""));
                Integer id = ids.get(key);
                if (id == null) {
                    if (!reserve(320L+2L*(key.owner().length()+key.type().length()+key.name().length()+key.descriptor().length()))) { limited = true; return; }
                    id = ids.size(); ids.put(key, id); methodCounts.add(new Count());
                }
                methods[i] = id;
            }
            int[] path = new int[size + 1]; path[0] = 0; int parent = 0;
            for (int i = size - 1; i >= 0; i--) {
                var key = new NodeKey(parent, methods[i]); Integer id = nodeIds.get(key);
                if (id == null) {
                    if (!reserve(256)) { limited = true; return; }
                    id = nodeKeys.size(); nodeIds.put(key, id); nodeKeys.add(key); nodeCounts.add(new Count());
                }
                parent = id; path[size - i] = id;
            }
            retained++;
            for (int id : path) nodeCounts.get(id).inclusive++;
            nodeCounts.get(path[size]).self++;
            for(int id:methods){var count=methodCounts.get(id);if(count.seen!=samples){count.seen=samples;count.inclusive++;}}
            methodCounts.get(methods[0]).self++;
            for (int i = 1; i < size; i++) {
                var edge = new Pair(methods[i], methods[i - 1]);
                var count=edges.get(edge);
                if(count==null){if(!reserve(192)){limited=true;omittedEdges++;continue;}count=new EdgeCount();edges.put(edge,count);}
                if(count.seen!=samples){count.seen=samples;count.samples++;}
            }
        }
        public CpuInvestigation finish() {
            var methods = new ArrayList<Method>();
            ids.forEach((key, id) -> { var n = methodCounts.get(id); methods.add(new Method(id, key.owner(), key.type(), key.name(), key.descriptor(), n.inclusive, n.self)); });
            var nodes = new ArrayList<Node>();
            for (int i = 0; i < nodeKeys.size(); i++) { var key = nodeKeys.get(i); var n = nodeCounts.get(i); nodes.add(new Node(i, key.parent(), key.method(), n.inclusive, n.self)); }
            var links = edges.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<Pair, EdgeCount> e) -> e.getKey().caller()).thenComparingInt(e -> e.getKey().callee())).map(e -> new Edge(e.getKey().caller(), e.getKey().callee(), e.getValue().samples)).toList();
            return new CpuInvestigation("ready", thread, samples, retained, limited, methods, nodes, links,new Limits(budget.maximum(),budget.configured(),budget.mode(),depth,samples-retained,omittedEdges,shortenedStacks,jvmTruncatedStacks));
        }
    }
    public static void validate(CpuInvestigation data) {
        if (data == null) return;
        if (!Set.of("disabled", "pending", "ready", "unavailable", "legacy").contains(data.state()) || data.thread() == null || data.thread().length() > 131072 || data.samples() < 0 || data.treeSamples() < 0 || data.treeSamples() > data.samples()) throw new IllegalArgumentException("Invalid CPU investigation");
        var limits=data.limits();
        if(limits!=null&&(limits.budget()<0||limits.configured()<0||limits.depth()<16||limits.depth()>512||!Set.of("adaptive","custom").contains(limits.mode())||limits.omittedPaths()<0||limits.omittedEdges()<0||limits.shortenedStacks()<0||limits.jvmTruncatedStacks()<0))throw new IllegalArgumentException("Invalid call-path limit metadata");
        for (int i = 0; i < data.methods().size(); i++) {
            var m = data.methods().get(i);
            if (m == null || m.id() != i || m.owner() == null || m.owner().length() > 65536 || m.type() == null || m.type().length() > 65536 || m.name() == null || m.name().length() > 65536 || m.descriptor() == null || m.descriptor().length() > 65536 || m.self() < 0 || m.inclusive() < m.self() || m.inclusive() > data.treeSamples()) throw new IllegalArgumentException("Invalid CPU method");
        }
        long[] childCounts = new long[data.nodes().size()];
        for (int i = 0; i < data.nodes().size(); i++) {
            var n = data.nodes().get(i);
            if (n == null || n.id() != i || n.parent() >= i || (i == 0 ? n.parent() != -1 || n.method() != -1 : n.parent() < 0 || n.method() < 0) || n.method() >= data.methods().size() || n.self() < 0 || n.inclusive() < n.self() || n.inclusive() > data.treeSamples()) throw new IllegalArgumentException("Invalid CPU node");
            if (n.parent() >= 0) childCounts[n.parent()] += n.inclusive();
        }
        for (var n : data.nodes()) if (n.inclusive() != n.self() + childCounts[n.id()]) throw new IllegalArgumentException("Inconsistent CPU branch counts");
        if (!data.nodes().isEmpty() && (data.nodes().getFirst().inclusive() != data.treeSamples() || data.methods().stream().mapToLong(Method::self).sum() != data.treeSamples())) throw new IllegalArgumentException("Inconsistent CPU totals");
        for (var e : data.edges()) if (e == null || e.caller() < 0 || e.callee() < 0 || e.caller() >= data.methods().size() || e.callee() >= data.methods().size() || e.samples() < 0 || e.samples() > data.treeSamples()) throw new IllegalArgumentException("Invalid CPU edge");
    }
}
