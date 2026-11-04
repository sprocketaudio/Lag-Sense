package audio.sprocket.lagsense;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.IOException;
import java.util.UUID;

/** Streaming legacy-compatible entry codec; absent coordinates never become an actionable origin. */
final class EntryJson extends TypeAdapter<Report.Entry> {
    @Override public Report.Entry read(JsonReader in)throws IOException {
        if(in.peek()==JsonToken.NULL){in.nextNull();return null;}
        String kind=null,type=null,dimension=null;int x=0,y=0,z=0;long nanos=0,calls=0;UUID id=null;Boolean known=null;int coordinates=0;
        in.beginObject();while(in.hasNext()){
            String field=in.nextName();if(in.peek()==JsonToken.NULL){in.nextNull();continue;}
            switch(field){
                case "kind"->kind=in.nextString();case "type"->type=in.nextString();case "dimension"->dimension=in.nextString();
                case "x"->{x=in.nextInt();coordinates|=1;}case "y"->{y=in.nextInt();coordinates|=2;}case "z"->{z=in.nextInt();coordinates|=4;}
                case "nanos"->nanos=in.nextLong();case "calls"->calls=in.nextLong();case "entityId"->id=UUID.fromString(in.nextString());case "locationKnown"->known=in.nextBoolean();default->in.skipValue();
            }
        }in.endObject();return new Report.Entry(kind,type,dimension,x,y,z,nanos,calls,id,coordinates==7?known:Boolean.FALSE);
    }
    @Override public void write(JsonWriter out,Report.Entry e)throws IOException {
        if(e==null){out.nullValue();return;}
        out.beginObject();out.name("kind").value(e.kind());out.name("type").value(e.type());out.name("dimension").value(e.dimension());
        out.name("x").value(e.x());out.name("y").value(e.y());out.name("z").value(e.z());out.name("nanos").value(e.nanos());out.name("calls").value(e.calls());
        if(e.entityId()!=null)out.name("entityId").value(e.entityId().toString());if(e.locationKnown()!=null)out.name("locationKnown").value(e.locationKnown());out.endObject();
    }
}
