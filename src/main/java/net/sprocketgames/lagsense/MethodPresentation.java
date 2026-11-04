package net.sprocketgames.lagsense;

import java.util.*;

/** Presentation derived only from retained Java metadata; raw identities stay in help/exports. */
public final class MethodPresentation {
    public static String label(String type,String name) {
        int generated=type.indexOf("$$Lambda");
        if(generated>=0){String enclosing=type.substring(0,generated);enclosing=enclosing.substring(enclosing.lastIndexOf('.')+1);return enclosing.isBlank()?"Unresolved lambda":enclosing+" lambda (unresolved)";}
        String simple=type.substring(type.lastIndexOf('.')+1);
        int hidden=simple.indexOf("/0x");if(hidden>=0)simple=simple.substring(0,hidden);
        int lambda=simple.indexOf("$$Lambda");
        if(lambda>=0){String enclosing=simple.substring(0,lambda);return enclosing.isBlank()?"Unresolved lambda":enclosing+" lambda (unresolved)";}
        if(simple.matches("0x[0-9a-fA-F]+"))return "Unresolved generated method";
        if(name.startsWith("lambda$")){
            String enclosing=name.substring(7);int suffix=enclosing.lastIndexOf('$');if(suffix>=0)enclosing=enclosing.substring(0,suffix);
            return simple+"#"+enclosing+" (lambda)";
        }
        return simple+"#"+name;
    }
    public static String label(String method) {
        String value=method.replace("Event callback · ","");int hash=value.indexOf('#');
        return hash<0?value:label(value.substring(0,hash),value.substring(hash+1));
    }
    public static List<String> details(CpuInvestigation data,CpuInvestigation.Method method) {
        var lines=new ArrayList<String>();
        lines.add("Direct samples: "+method.self()+" | In stack: "+method.inclusive());
        links(lines,data,method.id(),true);links(lines,data,method.id(),false);return List.copyOf(lines);
    }
    public static String hint(CpuInvestigation data,CpuInvestigation.Method method) { return method.signature()+"\nMod: "+method.owner()+(data.thread().isBlank()?"":"\nThread: "+data.thread()); }
    private static void links(List<String> lines,CpuInvestigation data,int id,boolean callers) {
        var edges=data.edges().stream().filter(e->callers?e.callee()==id:e.caller()==id).sorted(Comparator.comparingLong(CpuInvestigation.Edge::samples).reversed()).limit(5).toList();
        if(edges.isEmpty())return;lines.add(callers?"Top callers":"Top calls");
        for(var edge:edges){var method=data.methods().get(callers?edge.caller():edge.callee());lines.add(label(method.type(),method.name())+" · "+edge.samples()+" samples");}
    }
    private MethodPresentation(){}
}
