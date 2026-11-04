package audio.sprocket.lagsense;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

/** Stream JSON through compression; never build an additional full JSON string on the server tick. */
public final class FullReportWire {
    private static final class LimitedOutput extends FilterOutputStream {
        final long maximum;long written;
        LimitedOutput(OutputStream out,long maximum){super(out);this.maximum=maximum;}
        @Override public void write(int b)throws IOException{check(1);out.write(b);}
        @Override public void write(byte[] b,int off,int n)throws IOException{check(n);out.write(b,off,n);}
        private void check(int n)throws IOException{if((written+=n)>maximum)throw new IOException("Full report exceeds transfer budget");}
    }
    public static byte[] encode(Report report)throws IOException {
        return encodeValue(report);
    }
    static byte[] encodeValue(Object report)throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var writer=new OutputStreamWriter(new LimitedOutput(new GZIPOutputStream(new LimitedOutput(bytes,FullReportPart.MAX_BYTES)),CaptureBudget.MAX_FILE_BYTES),StandardCharsets.UTF_8)){Profiler.JSON.toJson(report,writer);}
        return bytes.toByteArray();
    }
    static <T> T decodeValue(byte[] bytes,Class<T> type)throws IOException {
        if(bytes.length>FullReportPart.MAX_BYTES)throw new IOException("Full report exceeds transfer budget");
        var runtime=Runtime.getRuntime();long headroom=Math.max(0,runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory()));
        long expandedLimit=Math.min(CaptureBudget.MAX_FILE_BYTES,headroom/4);
        if(bytes.length>headroom/8)throw new IOException("Not enough free heap to receive this complete report");
        try(var gzip=new GZIPInputStream(new ByteArrayInputStream(bytes));var limited=new FilterInputStream(gzip){
            long read;
            private void check(int n)throws IOException{if(n>0&&(read+=n)>expandedLimit)throw new IOException("Complete report exceeds the expanded transfer limit or available decoding heap");}
            @Override public int read()throws IOException{int n=in.read();check(n<0?0:1);return n;}
            @Override public int read(byte[] b,int o,int n)throws IOException{int got=in.read(b,o,n);check(got);return got;}
        }){
            return Profiler.JSON.fromJson(new InputStreamReader(limited,StandardCharsets.UTF_8),type);
        }catch(RuntimeException e){throw new IOException("Could not decode full data",e);}
    }
    public static Report decode(byte[] bytes)throws IOException {
        try{
            var report=decodeValue(bytes,Report.class);
            if(report==null||report.entries()==null||report.entries().size()>CaptureBudget.MAX_ENTRIES||report.chunks()==null||report.chunks().size()>CaptureBudget.MAX_ENTRIES||report.recording())throw new IOException("Invalid completed report");
            ProfileArchive.validate(report);
            if(!report.admin()&&(report.mspt()!=0||report.tps()!=0||report.diagnostics()!=null))throw new IOException("Invalid private report");
            if(!report.chunks().equals(Report.aggregate(report.entries())))throw new IOException("Invalid report chunk totals");
            return report;
        }catch(RuntimeException e){throw new IOException("Could not decode full report",e);}
    }
    private FullReportWire(){}
}
