package audio.sprocket.lagsense;

import java.io.IOException;
import java.util.List;

public final class ComparisonWire {
    public static byte[] encode(ProfileComparison data)throws IOException{return FullReportWire.encodeValue(data);}
    public static String previewJson(ProfileComparison c){
        int limit=128;String json;
        do{json=Profiler.JSON.toJson(new ProfileComparison(c.beforeId(),c.afterId(),c.beforeAt(),c.afterAt(),c.beforeTicks(),c.afterTicks(),c.beforeMspt(),c.afterMspt(),c.incomplete(),c.types().stream().limit(limit).toList(),c.chunks().stream().limit(limit).toList(),null,c.types().size()>limit||c.chunks().size()>limit));if(json.length()<=200000)return json;limit/=2;}while(limit>0);
        throw new IllegalArgumentException("Comparison preview exceeds packet budget");
    }
    public static ProfileComparison decode(byte[] bytes)throws IOException{
        try{
            var c=FullReportWire.decodeValue(bytes,ProfileComparison.class);validate(c);return c;
        }catch(RuntimeException error){throw new IOException("Invalid comparison",error);}
    }
    public static void validate(ProfileComparison c){
        if(c==null||c.beforeAt()<1||c.afterAt()<c.beforeAt()||c.beforeTicks()<1||c.afterTicks()<1||!Double.isFinite(c.beforeMspt())||!Double.isFinite(c.afterMspt())||c.beforeMspt()<0||c.afterMspt()<0||c.types()==null||c.chunks()==null||c.types().size()>CaptureBudget.MAX_ENTRIES*2||c.chunks().size()>CaptureBudget.MAX_ENTRIES*2)throw new IllegalArgumentException("Invalid comparison");
        for(var rows:List.of(c.types(),c.chunks()))for(var v:rows)if(v==null||v.dimension()==null||v.kind()==null||v.type()==null||v.dimension().length()>512||v.kind().length()>512||v.type().length()>512||!Double.isFinite(v.beforeMicros())||!Double.isFinite(v.afterMicros())||v.beforeMicros()<0||v.afterMicros()<0||v.beforeTargets()<0||v.afterTargets()<0)throw new IllegalArgumentException("Invalid comparison row");
        var d=c.details();if(d!=null){
            if(d.earlierLimit()==null||d.laterLimit()==null||d.earlierLimit().length()>8192||d.laterLimit().length()>8192||c.incomplete()!=(d.earlierIncomplete()||d.laterIncomplete()))throw new IllegalArgumentException("Invalid completeness metadata");
            var a=new Report(c.beforeId(),c.beforeAt(),c.beforeTicks(),c.beforeMspt(),true,d.earlierIncomplete(),d.earlier());var b=new Report(c.afterId(),c.afterAt(),c.afterTicks(),c.afterMspt(),true,d.laterIncomplete(),d.later());
            ProfileArchive.validate(a);ProfileArchive.validate(b);var expected=ProfileComparison.between(a,b);
            if(!c.types().equals(expected.types())||!c.chunks().equals(expected.chunks()))throw new IllegalArgumentException("Comparison aggregates do not match source measurements");
        }
    }
    private ComparisonWire(){}
}
