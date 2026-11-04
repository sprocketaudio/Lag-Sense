package net.sprocketgames.lagsense;

import java.util.*;

/** Immutable adjacency, built once; visible rows change only after a navigation action. */
public final class CallTreeIndex {
    public record Row(int node, int depth) {}
    private final CpuInvestigation data;
    private final List<List<Integer>> children;
    public CallTreeIndex(CpuInvestigation data) {
        this.data = data; var lists = new ArrayList<List<Integer>>();
        for (int i = 0; i < data.nodes().size(); i++) lists.add(new ArrayList<>());
        for (var n : data.nodes()) if (n.parent() >= 0 && n.inclusive() > 0) lists.get(n.parent()).add(n.id());
        for (var list : lists) list.sort(Comparator.comparingLong((Integer id) -> data.nodes().get(id).inclusive()).reversed().thenComparingInt(Integer::intValue));
        children = lists.stream().map(List::copyOf).toList();
    }
    public boolean branch(int id) { return !children.get(id).isEmpty(); }
    public List<Integer> children(int id){return children.get(id);}
    public boolean[] relevant(String owner){
        boolean[] relevant=new boolean[data.nodes().size()];
        for(int i=relevant.length-1;i>0;i--){var n=data.nodes().get(i);relevant[i]|=owner==null||data.methods().get(n.method()).owner().equals(owner);if(relevant[i]&&n.parent()>=0)relevant[n.parent()]=true;}
        return relevant;
    }
    public List<Row> rows(Set<Integer> expanded, String owner) {
        if (children.isEmpty()) return List.of();
        boolean[] relevant = relevant(owner);
        var result = new ArrayList<Row>();var pending=new ArrayDeque<Row>();
        pushChildren(pending,0,0,relevant);
        while(!pending.isEmpty()) {
            var row=pending.removeLast();result.add(row);
            if(expanded.contains(row.node()))pushChildren(pending,row.node(),row.depth()+1,relevant);
        }
        return List.copyOf(result);
    }
    private void pushChildren(Deque<Row> pending,int parent,int depth,boolean[] relevant) {
        var ids=children.get(parent);
        for(int i=ids.size()-1;i>=0;i--){int id=ids.get(i);if(relevant[id])pending.addLast(new Row(id,depth));}
    }
}
