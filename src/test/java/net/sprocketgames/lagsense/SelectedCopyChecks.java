package net.sprocketgames.lagsense;

import java.util.*;

final class SelectedCopyChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void run(){
        var text=new ResultText(id->"Mod "+id);var source=new ArrayList<Report.Entry>();
        source.add(new Report.Entry("Entity","a:mob","minecraft:overworld",-1,64,-1,900000,1,UUID.randomUUID()));
        source.add(new Report.Entry("Entity","b:mob","minecraft:overworld",-2,64,-1,800000,2,UUID.randomUUID()));
        source.add(new Report.Entry("Block","a:mob","minecraft:overworld",-3,64,-1,100000,900));
        source.add(new Report.Entry("Block","a:machine","minecraft:the_nether",-3,64,-1,700000,1));
        for(int i=0;i<26000;i++)source.add(new Report.Entry("Block","a:machine","minecraft:overworld",i%16,65+i/16,0,i%3,1));
        var report=new Report(1,1000,100,10,true,false,List.copyOf(source)).view(true,List.of());String json=Profiler.JSON.toJson(report);
        for(boolean types:List.of(false,true))for(boolean mods:List.of(false,true))for(boolean count:List.of(false,true)){
            var targets=ProfileResults.targets(report,new ProfileResults.Filter("All","All",false,"",0,0,0),count);
            var children=text.targets(targets,100,count,types,mods);String copied=CopyTree.format(new CopyTree.Node("root",()->"Selection",()->children),"");
            check(copied.contains("a:machine")&&copied.contains("UUID:"),"Copy includes registry identity and measured values without manufactured totals");
            long leaves=copied.lines().filter(line->line.contains("updates")&&line.contains(" | ")&&!line.contains("measured instances")).count();
            check(leaves==source.size(),"All four grouping combinations retain all 26,004 targets, including zero cost");
            check(!copied.contains("{\"")&&copied.contains("µs/t"),"Copy is readable plain text rather than JSON");
        }
        var filtered=ProfileResults.targets(report,new ProfileResults.Filter("Block","minecraft:overworld",true,"minecraft:overworld",0,64,0),false);
        String filteredText=CopyTree.format(new CopyTree.Node("root",()->"Filtered",()->text.targets(filtered,100,false,true,true)),"");
        check(!filteredText.contains("b:mob")&&!filteredText.contains("the_nether")&&!filteredText.contains("Entity |"),"Copy respects dimension, kind and nearby filters before aggregation");
        var leaf=text.target(new ProfileResults.Target(0,source.getFirst()),100);String selected=CopyTree.format(leaf,"");check(selected.contains("a:mob")&&!selected.contains("b:mob")&&!selected.contains("a:machine"),"A selected leaf excludes unrelated siblings");
        var group=ProfileResults.mods(ProfileResults.targets(report,new ProfileResults.Filter("All","All",false,"",0,0,0),true),true,true).getFirst();
        check(group.owner().equals("a")&&group.types().getFirst().type().registry().equals("a:machine"),"Mods and types use the selected aggregate update sorting");
        check(group.types().stream().filter(g->g.type().registry().equals("a:mob")).count()==2,"Same registry ID in different activity categories never merges");
        var map=new MapTargets(report,"a");var zero=map.chunks.stream().filter(c->c.dimension().equals("minecraft:overworld")&&c.x()==-1&&c.z()==-1).findFirst().orElseThrow();
        check(zero.nanos()==1000000&&zero.targets()==2,"Registry filtering recomputes exact chunk totals");
        var empty=new MapTargets(report,"missing");check(empty.chunks.size()==report.chunks().size()&&empty.chunks.stream().allMatch(c->c.nanos()==0&&c.targets()==0&&c.hotspot()==null),"Filtered chunks with no matching targets remain known zero, with no misleading hotspot");
        for(boolean types:List.of(false,true))for(boolean mods:List.of(false,true)){
            var chunk=new MapTargets(report,null).chunk("minecraft:overworld",-1,-1);String copied=CopyTree.format(text.chunk(chunk,false,types,mods),"Incomplete fixture");
            check(copied.startsWith("Chunk -1, -1")&&copied.contains("Entities")&&copied.contains("Ticking blocks")&&copied.contains("b:mob")&&!copied.contains("a:machine")&&copied.endsWith("Incomplete fixture\n"),"Chunk copy follows category/mod/type grouping without crossing chunks");
            String category=CopyTree.format(text.category(chunk,"Block",false,types,mods),"");check(!category.contains("b:mob")&&!category.contains("Entities"),"Category selection excludes sibling categories");
        }
        var callbacks=List.of(new Report.Callback("a","Tick","test.Events#tick",500000,5,200000),new Report.Callback("a","Load","test.Events#load",300000,3,150000));
        String event=CopyTree.format(text.eventMod(new Report.Callback("a","All events","",800000,8,200000),callbacks,100),"");
        check(event.indexOf("#tick")<event.indexOf("#load")&&event.contains("8.000 µs/t")&&event.contains("Longest")&&!event.contains("samples"),"Event subtree retains exact timings, listener diagnostics and child order without CPU semantics");
        var builder=new CpuInvestigation.Builder("Server thread");builder.add(List.of(new CpuInvestigation.Frame("a","test.Leaf","run","()V"),new CpuInvestigation.Frame("b","test.Parent","tick","()V")));builder.add(List.of(new CpuInvestigation.Frame("c","test.Other","run","()V")));
        var cpu=builder.finish();var cpuText=new ResultText.Cpu(cpu);var index=new CallTreeIndex(cpu);int root=index.children(0).stream().filter(id->cpu.methods().get(cpu.nodes().get(id).method()).owner().equals("b")).findFirst().orElseThrow();
        String hierarchy=CopyTree.format(cpuText.hierarchy(root,"a"),"");check(hierarchy.contains("test.Parent#tick()V")&&hierarchy.contains("test.Leaf#run()V")&&!hierarchy.contains("test.Other")&&hierarchy.contains("retained samples")&&!hierarchy.contains("µs/t"),"Call copy includes collapsed caller/callee paths and respects the mod filter");
        String method=CopyTree.format(cpuText.method(cpu.methods().getFirst()),"");check(method.contains("Caller:")&&method.contains("direct samples"),"Running-code copies include every retained relationship");
        var manyCallers=new ArrayList<CpuInvestigation.Edge>();for(int i=0;i<100;i++)manyCallers.add(new CpuInvestigation.Edge(1,0,i));
        String links=CopyTree.format(new ResultText.Cpu(new CpuInvestigation("ready","Server thread",100,100,false,cpu.methods(),cpu.nodes(),manyCallers)).method(cpu.methods().getFirst()),"");check(links.lines().filter(l->l.contains("Caller:")).count()==100,"Clipboard diagnostics do not inherit the UI's top-five preview");
        deepPaths();
        var comparison=new ComparisonIndex(ComparisonDetailChecks.fixture());var branch=comparison.all.types().getFirst();String change=CopyTree.format(text.comparison(branch,comparison,false),"");
        check(change.contains("Earlier:")&&change.contains("Later:")&&change.contains("UUID:")&&change.contains(" → "),"Comparison copy retains chronological normalized values and actual child identities");
        var sorted=comparison.sort(branch.children(),true);String later=CopyTree.format(text.comparison(branch,comparison,true),"");if(sorted.size()>1)check(later.contains(sorted.getFirst().name()),"Comparison subtree supports later-cost order");
        check(Profiler.JSON.toJson(report).equals(json),"Selected text copying cannot modify the original JSON export dataset");
        System.out.println("Selected-copy checks passed (26,004 targets, all grouping combinations, filtered complete subtrees, chunks, CPU/events, deep paths, comparison values and unchanged source data).");
    }
    private static void deepPaths(){
        var nodes=new ArrayList<CpuInvestigation.Node>();nodes.add(new CpuInvestigation.Node(0,-1,-1,1,0));for(int i=1;i<=2500;i++)nodes.add(new CpuInvestigation.Node(i,i-1,0,1,i==2500?1:0));
        var data=new CpuInvestigation("ready","Server thread",1,1,true,List.of(new CpuInvestigation.Method(0,"a","test.Deep","method","()V",1,1)),nodes,List.of());
        String text=CopyTree.format(new ResultText.Cpu(data).hierarchy(1,null),"Retained call paths limited");
        check(text.lines().filter(l->l.contains("test.Deep#method")).count()==2500&&text.contains("Retained call paths limited"),"Clipboard traversal has no recursion-stack or arbitrary hierarchy-depth limit");
    }
}
