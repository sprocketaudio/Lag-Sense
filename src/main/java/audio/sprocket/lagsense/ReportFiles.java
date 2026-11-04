package audio.sprocket.lagsense;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Stream a complete temporary file, or remove it and leave the previous save intact. */
final class ReportFiles {
    static void json(Path temporary,Object value,Gson gson,long maximum)throws IOException{
        try(var writer=new OutputStreamWriter(new FilterOutputStream(Files.newOutputStream(temporary)){
            long written;
            private void check(int count)throws IOException{if(count>maximum-written)throw new IOException("Complete profile exceeds the "+maximum/(1<<20)+" MiB file limit. Previous save was kept.");written+=count;}
            @Override public void write(int b)throws IOException{check(1);out.write(b);}
            @Override public void write(byte[] b,int off,int count)throws IOException{check(count);out.write(b,off,count);}
        },StandardCharsets.UTF_8)){gson.toJson(value,writer);}
        catch(IOException|RuntimeException failure){Files.deleteIfExists(temporary);throw failure;}
    }
    private ReportFiles(){}
}
