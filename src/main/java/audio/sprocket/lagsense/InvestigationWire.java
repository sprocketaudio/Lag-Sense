package audio.sprocket.lagsense;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

/** On-demand payloads have separate compressed and expanded budgets. */
public final class InvestigationWire {
    public static final int MAX_BYTES = FullReportPart.MAX_BYTES, MAX_EXPANDED = CaptureBudget.MAX_FILE_BYTES;
    public record Data(CpuInvestigation cpu, ChunkInvestigation chunk, String error) {}
    public static byte[] encode(Data value) throws IOException {return FullReportWire.encodeValue(value);}
    public static Data decode(byte[] value) throws IOException {
        var data=FullReportWire.decodeValue(value,Data.class);
        if (data == null) throw new IOException("Missing investigation");
        CpuInvestigation.validate(data.cpu());
        if (data.error() != null && data.error().length() > 512) throw new IOException("Invalid investigation error");
        var c = data.chunk();
        if (c != null) {
            if (c.dimension() == null || c.dimension().length() > 512 || c.ticks() < 1 || c.nanos() < 0 || c.targets() < 0 || c.targets() > CaptureBudget.MAX_ENTRIES || c.categories() == null || c.categories().size() > 16 || c.hotspots() == null || c.hotspots().size() > 128 || c.offset() < 0 || c.offset() > CaptureBudget.MAX_ENTRIES || c.category().length() > 512) throw new IOException("Invalid chunk details");
            for (var h : c.hotspots()) { var e = h.entry(); if (e == null || e.kind() == null || e.type() == null || e.dimension() == null || e.kind().length() > 512 || e.type().length() > 512 || e.dimension().length() > 512 || e.nanos() < 0 || e.calls() < 0 || !e.dimension().equals(c.dimension()) || e.x() >> 4 != c.x() || e.z() >> 4 != c.z() || h.index() < -1 || h.index() >= CaptureBudget.MAX_ENTRIES || !c.category().isEmpty() && !c.category().equals(e.kind())) throw new IOException("Invalid chunk target"); }
            for (var category : c.categories()) if (category == null || category.kind() == null || category.kind().length() > 512 || category.nanos() < 0 || category.calls() < 0 || category.targets() < 0) throw new IOException("Invalid category");
            if (c.categories().stream().mapToLong(ChunkInvestigation.Category::nanos).sum() != c.nanos() || c.categories().stream().mapToLong(ChunkInvestigation.Category::targets).sum() != c.targets() || c.hotspots().size() > c.targets() || c.offset() + c.hotspots().size() > c.pageTotal()) throw new IOException("Inconsistent chunk totals");
        }
        return data;
    }
}
