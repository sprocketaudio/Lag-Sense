package net.sprocketgames.lagsense;

public final class InvestigationExport {
    private static String q(String s) { if (!s.isEmpty() && "=+-@\t\r\n".indexOf(s.charAt(0)) >= 0) s = "'" + s; return "\"" + s.replace("\"", "\"\"") + "\""; }
    public static String csv(CpuInvestigation data) {
        var diagnostics=new Report.Diagnostics("Call paths",0,0,java.util.List.of(),java.util.List.of(),java.util.List.of(),"disabled",data);
        return ReportExport.csv(new Report(1,1,1,0,true,false,java.util.List.of(),false,0,java.util.List.of(),0,diagnostics));
    }

    public static String csv(ChunkInvestigation data) {
        var s = new StringBuilder("kind,type,dimension,x,y,z,total_nanos,calls,captured_ticks,entity_uuid\n");
        for (var h : data.hotspots()) { var e = h.entry(); s.append(q(e.kind())).append(',').append(q(e.type())).append(',').append(q(e.dimension())).append(',').append(e.x()).append(',').append(e.y()).append(',').append(e.z()).append(',').append(e.nanos()).append(',').append(e.calls()).append(',').append(data.ticks()).append(',').append(q(e.entityId()==null?"":e.entityId().toString())).append('\n'); }
        return s.toString();
    }
}
