package net.sprocketgames.lagsense;

import java.util.*;

/** Java execution samples, kept separate from server tick timings and server-thread stacks. */
public record BackgroundCpu(String state,List<Worker> threads,long samples,long omittedSamples,boolean limited,String reason) {
    public record Worker(long id,String name,List<Report.Task> tasks,CpuInvestigation cpu){
        public Worker{tasks=tasks==null?List.of():List.copyOf(tasks);}
        public String label(){return name+" (#"+id+")";}
    }
    public BackgroundCpu{threads=threads==null?List.of():List.copyOf(threads);reason=reason==null?"":reason;}
    public static BackgroundCpu empty(String state){return new BackgroundCpu(state,List.of(),0,0,false,"");}
    public List<Report.Task> tasks(){return threads.stream().flatMap(t->t.tasks().stream()).sorted(Comparator.comparingLong(Report.Task::samples).reversed().thenComparing(Report.Task::method).thenComparingLong(Report.Task::threadId)).toList();}
    public Worker thread(long id){return threads.stream().filter(t->t.id()==id).findFirst().orElse(null);}
    public static void validate(BackgroundCpu data){
        if(data==null)return;
        if(!Set.of("disabled","pending","ready","unavailable","legacy").contains(data.state())||data.samples()<0||data.omittedSamples()<0||data.reason().length()>2048)throw new IllegalArgumentException("Invalid background samples");
        var ids=new HashSet<Long>();long samples=0,bytes=0;
        for(var t:data.threads()){
            if(t==null||t.id()<=0||!ids.add(t.id())||t.name()==null||t.name().length()>65536||t.cpu()==null)throw new IllegalArgumentException("Invalid sampled thread");
            CpuInvestigation.validate(t.cpu());samples+=t.cpu().samples();bytes+=1024L+2L*t.name().length();
            for(var task:t.tasks()){
                if(task==null||task.owner()==null||task.method()==null||task.owner().length()>65536||task.method().length()>131072||task.samples()<0||task.samples()>t.cpu().samples()||task.threadId()!=t.id()||!t.name().equals(task.threadName()))throw new IllegalArgumentException("Invalid background action");
                bytes+=192L+2L*(task.owner().length()+task.method().length());
            }
            bytes+=512L*t.cpu().methods().size()+144L*t.cpu().nodes().size()+112L*t.cpu().edges().size();
            if(bytes>1024L<<20)throw new IllegalArgumentException("Background sample data exceeds safety budget");
        }
        if(samples!=data.samples())throw new IllegalArgumentException("Invalid background sample total");
    }
}
