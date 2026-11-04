package audio.sprocket.lagsense;

import java.util.*;
import java.util.function.Supplier;

/** Clipboard traversal uses data nodes, never widgets or expansion state. */
public final class CopyTree {
    public record Node(Object key,Supplier<String> text,Supplier<List<Node>> children) {}
    public static Node leaf(Object key,String text){return new Node(key,()->text,List::of);}
    /** Defer regrouping as well as formatting until the clipboard worker visits this node. */
    public static Node deferred(Object key,Supplier<Node> source){
        class Lazy {Node value;Node get(){if(value==null)value=source.get();return value;}}
        var lazy=new Lazy();return new Node(key,()->lazy.get().text().get(),()->lazy.get().children().get());
    }
    private record Frame(Node node,int depth) {}
    public static String format(Node root,String warning){
        var out=new StringBuilder();var pending=new ArrayDeque<Frame>();pending.push(new Frame(root,0));
        while(!pending.isEmpty()){var frame=pending.pop();String indent="  ".repeat(frame.depth());for(String line:frame.node().text().get().split("\n"))if(!line.isBlank())out.append(indent).append(line).append('\n');var children=frame.node().children().get();for(int i=children.size()-1;i>=0;i--)pending.push(new Frame(children.get(i),frame.depth()+1));}
        if(warning!=null&&!warning.isBlank())out.append('\n').append(warning).append('\n');return out.toString();
    }
    private CopyTree(){}
}
