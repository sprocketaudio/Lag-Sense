package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.util.*;

final class BackgroundCpuUiChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Object field(Object target,String name)throws Exception{for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static CpuInvestigation cpu(long id,String type){var b=new CpuInvestigation.Builder("same worker (#"+id+")");b.add(List.of(new CpuInvestigation.Frame("fixture",type,"run","()V")));return b.finish();}
    @SuppressWarnings("unchecked") static void run()throws Exception{
        var a=cpu(2,"mod.FirstWorker");var b=cpu(3,"mod.SecondWorker");
        var bg=new BackgroundCpu("ready",List.of(new BackgroundCpu.Worker(2,"same worker",List.of(new Report.Task("fixture","mod.FirstWorker#run",1,2,"same worker")),a),new BackgroundCpu.Worker(3,"same worker",List.of(new Report.Task("fixture","mod.SecondWorker#run",1,3,"same worker")),b)),2,0,false,"");
        var callbacks=new ArrayList<Report.Callback>();for(int i=0;i<9001;i++)callbacks.add(new Report.Callback("fixture","test.Event","test.Handler#run",i+1,1,i+1,i+1));
        var report=new Report(200,1000,100,2,true,false,List.of(),false,0,List.of(),20,new Report.Diagnostics("Manual",0,0,List.of(),List.of(),callbacks,"ready",cpu(1,"server.Tick"),null,bg));
        for(int scale:List.of(2,3)){
            var events=new TaskScreen(null,report,true);events.width=1920/scale;events.height=1080/scale;set(events,"byMod",false);events.init();
            var list=(UiScrollList)field(events,"currentList");check(list.widgetCount()<100,"Thousands of event handlers create only visible widgets");check(list.visible("event:fixture|test.Event|test.Handler#run|9001"),"The highest-cost event sorts first despite identical labels");list.restore(Integer.MAX_VALUE);
            check(list.visible("event:fixture|test.Event|test.Handler#run|1"),"The lowest-cost event remains reachable through continuous scrolling");
            var tasks=new TaskScreen(null,report,true);tasks.width=events.width;tasks.height=events.height;set(tasks,"callbackView",false);set(tasks,"byMod",false);set(tasks,"background",true);set(tasks,"selectedThread",2L);tasks.init();
            check(ProfileLayoutChecks.dropdown(tasks,"Threads").choices().size()==2&&ProfileLayoutChecks.dropdown(tasks,"Group").choices().size()==2,"CPU activity exposes scope and grouping dropdowns");
            var rows=((UiScrollList)field(tasks,"currentList")).children().stream().filter(UiRow.class::isInstance).map(UiRow.class::cast).toList();check(rows.size()==2,"Workers remain distinct in By Action");
            check(rows.get(0).columns().valueRight()==rows.get(1).columns().valueRight(),"Worker sample/percentage columns align");
            var hints=UiHints.class.getDeclaredField("widgets");hints.setAccessible(true);
            var help=new ArrayList<String>();for(var row:rows){var h=((Map<?,?>)hints.get(null)).get(row);var f=h.getClass().getDeclaredField("text");f.setAccessible(true);help.add((String)f.get(h));}
            check(help.stream().anyMatch(h->h.contains("same worker (#2)"))&&help.stream().anyMatch(h->h.contains("same worker (#3)")),"Actual names and IDs are accessible in row hints");
            var tree=new CallTreeScreen(tasks,a,null);tree.width=events.width;tree.height=events.height;tree.init();
            var method=CallTreeScreen.class.getDeclaredMethod("threadRows",TaskScreen.class);method.setAccessible(true);var threadRows=(UiResults)method.invoke(tree,tasks);
            check(threadRows.entries.size()==2&&threadRows.entries.get(0).key().equals("thread:2")&&threadRows.entries.get(1).key().equals("thread:3"),"Call-path selector distinguishes duplicate-name threads");
            check(tasks.pathData()==a,"Background call paths use the selected worker only");set(tasks,"selectedThread",3L);check(tasks.pathData()==b,"Changing thread selects its own stack data");
            String copied=CopyTree.format(new ResultText.Cpu(a).task(bg.thread(2).tasks().getFirst(),2),"");
            check(copied.contains("same worker (#2)")&&copied.contains("FirstWorker")&&!copied.contains("SecondWorker"),"Selected result copy includes its worker's details without mixing call paths");
        }
        System.out.println("Background/event UI checks passed at scales 2/3: separate scopes, worker IDs, call-path selection, copy, aligned columns and 9,001 virtualised event rows.");
    }
}
