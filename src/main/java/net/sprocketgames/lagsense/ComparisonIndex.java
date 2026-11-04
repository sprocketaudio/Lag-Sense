package net.sprocketgames.lagsense;

import java.util.*;

/** Derived immutable trees. Entity identity and historical chunk attribution are separate views. */
public final class ComparisonIndex {
    public record Key(String scope,String dimension,int x,int z,String kind,String type,String identity) {}
    public record Stats(long nanos,long calls,int targets,boolean countKnown,Report.Entry location,int locations) {
        public double micros(int ticks){return nanos/1000.0/ticks;}
    }
    public record Node(Key key,String name,Stats earlier,Stats later,String status,UUID uuid,List<Node> children) {
        public double before(ProfileComparison c){return earlier.micros(c.beforeTicks());}
        public double after(ProfileComparison c){return later.micros(c.afterTicks());}
        public double delta(ProfileComparison c){return after(c)-before(c);}
        public Double percent(ProfileComparison c){return earlier.targets()>0&&later.targets()>0?ProfileComparison.percentage(before(c),after(c)):null;}
    }
    public record View(String dimension,List<Node> types,List<Node> chunks) {}
    private record Identity(String dimension,UUID uuid,int x,int y,int z,boolean unavailable) {
        String token(){return uuid!=null?uuid.toString():unavailable?"unknown":dimension+"|"+x+","+y+","+z;}
    }
    private record ChunkKey(String dimension,int x,int z) {}
    private record TypeKey(String kind,String type) {}
    private record Position(String dimension,int x,int y,int z){Position(Report.Entry e){this(e.dimension(),e.x(),e.y(),e.z());}}
    private static final Stats EMPTY=new Stats(0,0,0,true,null,0);
    private static final class Sum {
        long nanos,calls;int records;Report.Entry location;Set<Position> positions;
        void add(Report.Entry e){nanos+=e.nanos();calls+=e.calls();records++;if(location!=null&&positionOrder(e,location)!=0){if(positions==null){positions=new HashSet<>();positions.add(new Position(location));}positions.add(new Position(e));}if(location==null||positionOrder(e,location)<0)location=e;}
        Stats stats(boolean unknown){return records==0?EMPTY:new Stats(nanos,calls,unknown?records:1,!unknown,location,positions==null?1:positions.size());}
    }
    private static final class Pair { final Sum earlier=new Sum(),later=new Sum(); }
    private final ProfileComparison comparison;
    private final Set<UUID> earlierIds=new HashSet<>(),laterIds=new HashSet<>();
    public final List<String> dimensions;
    public final View all;
    public final ComparisonTargets targets;
    public ComparisonIndex(ProfileComparison comparison){
        this.comparison=comparison;targets=new ComparisonTargets(comparison);var dims=new TreeSet<String>();
        if(comparison.details()!=null){for(var e:comparison.details().earlier()){dims.add(e.dimension());if(e.entityId()!=null)earlierIds.add(e.entityId());}for(var e:comparison.details().later()){dims.add(e.dimension());if(e.entityId()!=null)laterIds.add(e.entityId());}}
        else{comparison.types().forEach(c->dims.add(c.dimension()));comparison.chunks().forEach(c->dims.add(c.dimension()));}
        var choices=new ArrayList<String>();choices.add("All");choices.addAll(dims);dimensions=List.copyOf(choices);all=view("All");
    }
    public ProfileComparison comparison(){return comparison;}
    private static int positionOrder(Report.Entry a,Report.Entry b){int n=a.dimension().compareTo(b.dimension());if(n==0)n=Integer.compare(a.x(),b.x());if(n==0)n=Integer.compare(a.y(),b.y());if(n==0)n=Integer.compare(a.z(),b.z());return n;}
    private static Identity identity(Report.Entry e){
        if(e.kind().equals("Entity"))return new Identity("",e.entityId(),0,0,0,e.entityId()==null);
        boolean reliable=e.kind().equals("Block")||e.kind().startsWith("Random ")||e.kind().startsWith("Scheduled ");
        return new Identity(reliable?e.dimension():"",null,reliable?e.x():0,reliable?e.y():0,reliable?e.z():0,!reliable);
    }
    public View view(String dimension){
        if(comparison.details()==null)return legacy(dimension);
        Map<TypeKey,Map<Identity,Pair>> types=new HashMap<>();Map<ChunkKey,Map<TypeKey,Map<Identity,Pair>>> chunks=new HashMap<>();
        add(comparison.details().earlier(),dimension,true,types,chunks);add(comparison.details().later(),dimension,false,types,chunks);
        var typeRows=new ArrayList<Node>();for(var entry:types.entrySet())typeRows.add(type(entry.getKey(),entry.getValue(),dimension,null));
        var chunkRows=new ArrayList<Node>();for(var chunk:chunks.entrySet()){
            var categories=new TreeMap<String,List<Node>>();
            for(var type:chunk.getValue().entrySet())categories.computeIfAbsent(type.getKey().kind(),k->new ArrayList<>()).add(type(type.getKey(),type.getValue(),dimension,chunk.getKey()));
            var categoryRows=new ArrayList<Node>();for(var category:categories.entrySet())categoryRows.add(parent(new Key("category",chunk.getKey().dimension(),chunk.getKey().x(),chunk.getKey().z(),category.getKey(),"",""),categoryName(category.getKey()),category.getValue()));
            var key=chunk.getKey();chunkRows.add(parent(new Key("chunk",key.dimension(),key.x(),key.z(),"","",""),"Chunk "+key.x()+", "+key.z(),categoryRows));
        }
        return new View(dimension,sort(typeRows,false),sort(chunkRows,false));
    }
    private static void add(List<Report.Entry> source,String dimension,boolean earlier,Map<TypeKey,Map<Identity,Pair>> types,Map<ChunkKey,Map<TypeKey,Map<Identity,Pair>>> chunks){
        for(var e:source){if(!dimension.equals("All")&&!e.dimension().equals(dimension))continue;var type=new TypeKey(e.kind(),e.type());var identity=identity(e);
            var a=types.computeIfAbsent(type,k->new HashMap<>()).computeIfAbsent(identity,k->new Pair());(earlier?a.earlier:a.later).add(e);
            var b=chunks.computeIfAbsent(new ChunkKey(e.dimension(),e.x()>>4,e.z()>>4),k->new HashMap<>()).computeIfAbsent(type,k->new HashMap<>()).computeIfAbsent(identity,k->new Pair());(earlier?b.earlier:b.later).add(e);
        }
    }
    private Node type(TypeKey type,Map<Identity,Pair> entries,String dimension,ChunkKey chunk){
        var rows=new ArrayList<Node>();String dim=chunk==null?dimension:chunk.dimension();int x=chunk==null?0:chunk.x(),z=chunk==null?0:chunk.z();
        boolean unknownEarlier=entries.entrySet().stream().anyMatch(e->e.getKey().unavailable()&&e.getValue().earlier.records>0),unknownLater=entries.entrySet().stream().anyMatch(e->e.getKey().unavailable()&&e.getValue().later.records>0);
        for(var item:entries.entrySet()){var id=item.getKey();var p=item.getValue();boolean before=p.earlier.records>0,after=p.later.records>0;String status;
            if(id.unavailable())status="Matching unavailable";
            else if(before&&after)status=id.uuid()!=null?"UUID matched":"Same location";
            else if(id.uuid()!=null&&earlierIds.contains(id.uuid())&&laterIds.contains(id.uuid())&&(chunk!=null||!dimension.equals("All")))status=(before?"Earlier ":"Later ")+(chunk!=null?"chunk":"dimension")+" only · UUID matched";
            else if(before&&unknownLater||after&&unknownEarlier)status=before?"Later identity unavailable":"Earlier identity unavailable";
            else status=before?(comparison.incomplete()?"Not measured later":"Removed"):(comparison.incomplete()?"Not measured earlier":"Added");
            var key=new Key(chunk==null?"target":"chunkTarget",dim,x,z,type.kind(),type.type(),id.token());
            rows.add(new Node(key,type.type(),p.earlier.stats(id.unavailable()),p.later.stats(id.unavailable()),status,id.uuid(),List.of()));
        }
        return parent(new Key(chunk==null?"type":"chunkType",dim,x,z,type.kind(),type.type(),""),type.type(),rows);
    }
    private Node parent(Key key,String name,List<Node> children){return new Node(key,name,total(children,true),total(children,false),"",null,sort(children,false));}
    private static Stats total(List<Node> nodes,boolean earlier){long nanos=0,calls=0;int count=0;boolean known=true;for(var node:nodes){var s=earlier?node.earlier():node.later();nanos+=s.nanos();calls+=s.calls();count+=s.targets();known&=s.countKnown();}return new Stats(nanos,calls,count,known,null,0);}
    public List<Node> sort(Collection<Node> nodes,boolean later){return nodes.stream().sorted(Comparator.<Node>comparingDouble(n->later?n.after(comparison):Math.abs(n.delta(comparison))).reversed().thenComparing(n->n.key().scope()).thenComparing(n->n.key().dimension()).thenComparingInt(n->n.key().x()).thenComparingInt(n->n.key().z()).thenComparing(n->n.key().kind()).thenComparing(n->n.key().type()).thenComparing(n->n.key().identity())).toList();}
    public boolean matches(Node node,int direction){return direction==0||(direction==1?node.delta(comparison)>0:node.delta(comparison)<0);}
    private View legacy(String dim){return new View(dim,sort(legacyRows(comparison.types(),dim,false),false),sort(legacyRows(comparison.chunks(),dim,true),false));}
    private List<Node> legacyRows(List<ProfileComparison.Change> rows,String dim,boolean chunks){return rows.stream().filter(c->dim.equals("All")||dim.equals(c.dimension())).map(c->new Node(new Key(chunks?"chunk":"type",c.dimension(),c.x(),c.z(),c.kind(),c.type(),""),chunks?"Chunk "+c.x()+", "+c.z():c.type(),new Stats(Math.round(c.beforeMicros()*1000*comparison.beforeTicks()),0,c.beforeTargets(),true,null,0),new Stats(Math.round(c.afterMicros()*1000*comparison.afterTicks()),0,c.afterTargets(),true,null,0),"Individual details unavailable",null,List.of())).toList();}
    private static String categoryName(String kind){return switch(kind){case "Entity"->"Entities";case "Block"->"Ticking blocks";default->kind;};}
}
