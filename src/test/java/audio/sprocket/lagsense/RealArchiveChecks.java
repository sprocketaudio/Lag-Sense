package audio.sprocket.lagsense;

import java.nio.file.*;
import java.util.*;

/** Optional read-only validation against administrator-supplied real archives. */
final class RealArchiveChecks {
    static void run()throws Exception{
        String folder=System.getProperty("lagsense.audit.archives");if(folder==null)return;
        int captures=0;long targets=0;var ids=new HashSet<Long>();
        try(var files=Files.newDirectoryStream(Path.of(folder),"*.json")){
            for(var file:files){
                if(!file.getFileName().toString().matches("(latest|profile-[0-9]+)\\.json"))continue;
                Report report;try(var reader=Files.newBufferedReader(file)){report=Profiler.JSON.fromJson(reader,Report.class);}
                ProfileArchive.validate(report);if(!ids.add(report.id()))continue;
                var view=report.view(true,e->true);var data=FullReportWire.decode(FullReportWire.encode(view));
                if(!data.equals(view)||!data.entries().equals(report.entries()))throw new AssertionError("Real archive transfer changed measurements: "+file.getFileName());
                var index=new MarkerIndex(data);
                for(var e:data.entries())if(!Boolean.FALSE.equals(e.locationKnown())){
                    if(e.kind().equals("Entity")){if(e.entityId()!=null&&index.entity(e.dimension(),e.entityId())==null)throw new AssertionError("Lost entity UUID");}
                    else if(!index.position(e.dimension(),e.x(),e.y(),e.z()).contains(e))throw new AssertionError("Lost stationary target at exact position");
                }
                captures++;targets+=data.entries().size();
            }
        }
        if(captures==0)throw new AssertionError("No real captures validated");
        System.out.println("Real archive checks passed: "+captures+" captures, "+targets+" retained targets; full transfer, dimensions, UUIDs and exact-position indexes. No world rendering verified.");
    }
}
