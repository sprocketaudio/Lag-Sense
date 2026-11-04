package net.sprocketgames.lagsense;

import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class ReleaseHardeningChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("benchmark")){cooldown(true);return;}
        historyOrder();cooldown(false);
        System.out.println("Release hardening checks passed: queued history settings and captures preserve order; cooldowns skip report traversal.");
    }
    private static void historyOrder()throws Exception{
        Path directory=Files.createTempDirectory("lagsense-history-order-");var failures=new CopyOnWriteArrayList<Exception>();
        try(var archive=new ProfileArchive(directory,Profiler.JSON,ProfileArchive.Settings.defaults(),failures::add)){
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            archive.list((state,error)->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            check(entered.await(5,TimeUnit.SECONDS),"Archive worker enters a deterministic queue barrier");
            var configured=new CompletableFuture<ProfileArchive.State>();
            try{
                archive.configure(new ProfileArchive.Settings(true,10,32),(state,error)->{if(error==null)configured.complete(state);else configured.completeExceptionally(error);});
                archive.capture(new Report(71,71000,100,2,true,false,List.of()));
            }finally{release.countDown();}
            configured.get(5,TimeUnit.SECONDS);
            var saved=new CompletableFuture<ProfileArchive.State>();archive.list((state,error)->saved.complete(state));
            check(saved.get(5,TimeUnit.SECONDS).profiles().stream().anyMatch(s->s.id()==71),"Capture queued immediately after enabling history must be saved");
            archive.configure(new ProfileArchive.Settings(false,10,32),(state,error)->{});
            archive.capture(new Report(72,72000,100,2,true,false,List.of()));
            var disabled=new CompletableFuture<ProfileArchive.State>();archive.list((state,error)->disabled.complete(state));
            check(disabled.get(5,TimeUnit.SECONDS).profiles().size()==1,"Disabling history takes effect before later queued captures");
            check(failures.isEmpty(),"Queue-order checks complete without storage errors");
        }finally{try(var files=Files.walk(directory)){for(var f:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(f);}}
    }
    private static void cooldown(boolean benchmark)throws Exception{
        var retained=new ArrayList<Report.Entry>();
        for(int i=0;i<(benchmark?100000:1000);i++)retained.add(new Report.Entry("Block","fixture:machine"+(i%20),"minecraft:overworld",i%1000,64,i/1000,1000+i,100));
        var reads=new long[1];var entries=new AbstractList<Report.Entry>(){
            public int size(){return retained.size();}
            public Report.Entry get(int i){reads[0]++;return retained.get(i);}
        };
        var report=new Report(91,91000,100,20,true,false,entries);
        var options=new ServerSettings.Delivery(true,600,5,0,0,"either","both","none","","degraded");
        var connection=new ServerSettings.Discord(true,"https://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwx","Fixture",options,options,options);
        var sent=new AtomicLong();
        try(var delivery=new DiscordDelivery(connection,(uri,body)->{sent.incrementAndGet();return new DiscordDelivery.Response(204,0);},ms->{},s->{})){
            delivery.capture(report,"");delivery.flush();
            var field=DiscordDelivery.class.getDeclaredField("worker");field.setAccessible(true);var worker=(ThreadPoolExecutor)field.get(delivery);
            long id=worker.submit(()->Thread.currentThread().threadId()).get();
            var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
            for(int i=0;i<3;i++){delivery.capture(report,"");delivery.flush();}
            reads[0]=0;long allocated=bean.getThreadAllocatedBytes(id),started=System.nanoTime();
            int repeats=benchmark?12:1;
            for(int i=0;i<repeats;i++){delivery.capture(report,"");delivery.flush();}
            double millis=(System.nanoTime()-started)/1e6/repeats;double bytes=(bean.getThreadAllocatedBytes(id)-allocated)/(double)repeats;
            check(sent.get()==1,"Cooldown prevents additional HTTP calls");
            if(benchmark)System.out.printf(Locale.ROOT,"COOLDOWN BENCH targets=%d repeats=%d msPerSkipped=%.3f workerBytesPerSkipped=%.0f targetReadsPerSkipped=%d%n",entries.size(),repeats,millis,bytes,reads[0]/repeats);
            else check(reads[0]==0,"Skipped Discord deliveries must not traverse, aggregate or sort target data");
        }
    }
}
