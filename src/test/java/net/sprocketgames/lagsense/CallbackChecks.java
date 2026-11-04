package net.sprocketgames.lagsense;

import java.util.*;
import java.util.function.Consumer;
import example.lagsensefixture.ListenerFixture;
import net.neoforged.bus.api.*;
import net.neoforged.bus.api.EventListener;

final class CallbackChecks {
    private static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    @SuppressWarnings("unchecked") private static List<EventListener> listeners(IEventBus bus)throws Exception{
        var field=Class.forName("net.neoforged.bus.EventBus").getDeclaredField("listeners");field.setAccessible(true);
        return ((Map<Object,List<EventListener>>)field.get(bus)).values().stream().flatMap(List::stream).toList();
    }
    static void run()throws Exception{
        realDispatchChecks();
        var bus=BusBuilder.builder().build();
        for(int i=0;i<9000;i++){bus.addListener(ListenerFixture.Basic.class,new ListenerFixture.Counted(i));}
        try(var timing=new CallbackTimings(bus,Thread.currentThread(),c->"fixture",new DiagnosticBudget(16L<<20))){
            var handlers=listeners(bus);var child=new ListenerFixture.Child();
            for(var handler:handlers){var value=timing.measure(handler,child);require(value!=null,"Every registered handler discoverable above old limits");timing.record(value,100);}
            require(timing.snapshot().size()==9000&&!timing.incomplete(),"No discovery, listener/event or top-128 cutoff");
            require(timing.snapshot().stream().map(Report.Callback::handlerId).distinct().count()==9000,"Identical names retain separate registration identities");
            Consumer<ListenerFixture.Basic> late=new ListenerFixture.Counted(9001);bus.addListener(ListenerFixture.Basic.class,late);
            var seen=new HashSet<>(handlers);var newest=listeners(bus).stream().filter(h->!seen.contains(h)).findFirst().orElseThrow();
            var value=timing.measure(newest,new ListenerFixture.Basic());require(value!=null,"New registrations can resolve on first dispatch without waiting for cache refresh");timing.record(value,999999);
            require(timing.snapshot().getFirst().nanos()==999999&&timing.snapshot().size()==9001,"Expensive late handler survives complete retention");
            Thread other=Thread.ofPlatform().start(()->require(CallbackTimings.activeFor(bus)==null,"Only the capture thread is eligible for direct event timing"));other.join();
            var d=new Report.Diagnostics("Manual",0,0,List.of(),List.of(),timing.snapshot());
            var report=new Report(1,1,1,1,true,false,List.of(),false,0,List.of(),20,d);
            require(FullReportWire.decode(FullReportWire.encode(report)).equals(report),"All event identities and timings survive full transfer");
        }
        try(var limited=new CallbackTimings(bus,Thread.currentThread(),c->"fixture",new DiagnosticBudget(2048))){
            for(var handler:listeners(bus)){var value=limited.measure(handler,new ListenerFixture.Basic());if(value!=null)limited.record(value,1);}
            require(limited.incomplete()&&limited.limitation().contains("memory budget")&&limited.usedBytes()<=2048,"Capture memory exhaustion is explicit and bounded");
        }
        long begin=System.nanoTime();
        for(int i=0;i<200000;i++){require(CallbackTimings.activeFor(bus)==null,"Disabled dispatch hook is inactive");Profiler.pre();Profiler.post(null);Profiler.recordLocated(null,null,null,"Random block",1);}
        System.out.printf(Locale.ROOT,"Disabled server/event hook smoke benchmark: %.1f ns/iteration (not a gameplay overhead estimate).%n",(System.nanoTime()-begin)/200000.0);
    }
    private static void realDispatchChecks()throws Exception{
        var bus=ProfilingEventBus.wrap(BusBuilder.builder().allowPerPhasePost().build());var f=new ListenerFixture();var first=f.normal();
        bus.register(f);bus.addListener(EventPriority.HIGH,ListenerFixture.Basic.class,first);
        bus.addListener(ListenerFixture.Cancelled.class,f.uncancelled());bus.addListener(true,ListenerFixture.Cancelled.class,f.receivesCancelled());
        bus.post(new ListenerFixture.Child());var expected=List.copyOf(f.order);f.order.clear();
        try(var timing=new CallbackTimings(bus,Thread.currentThread(),c->"fixture")){
            bus.post(new ListenerFixture.Child());require(f.order.equals(expected)&&timing.snapshot().stream().mapToLong(Report.Callback::calls).sum()==2,"Cooperative dispatch preserves inherited order with one measurement per invocation");
            bus.post(EventPriority.HIGH,new ListenerFixture.Child());require(timing.snapshot().stream().mapToLong(Report.Callback::calls).sum()==3,"Per-phase dispatch is measured exactly once");
            bus.unregister(first);var late=f.later();Thread registration=Thread.ofPlatform().start(()->bus.addListener(ListenerFixture.Basic.class,late));registration.join();
            f.order.clear();bus.post(new ListenerFixture.Child());require(f.order.equals(List.of("annotated","later")),"Cross-thread registrations and cache invalidation are covered on first dispatch");
            var cancelled=new ListenerFixture.Cancelled();cancelled.setCanceled(true);f.order.clear();bus.post(cancelled);require(f.order.equals(List.of("cancelled")),"Cancellation behaviour remains with the original listener");
            long count=timing.snapshot().stream().mapToLong(Report.Callback::calls).sum();Thread other=Thread.ofPlatform().start(()->bus.post(new ListenerFixture.Child()));other.join();require(timing.snapshot().stream().mapToLong(Report.Callback::calls).sum()==count,"Other-thread posts do not enter direct event totals");
        }
        try(var repeated=new CallbackTimings(bus,Thread.currentThread(),c->"fixture")){bus.post(new ListenerFixture.Child());require(repeated.snapshot().stream().mapToLong(Report.Callback::calls).sum()==2,"Repeated captures do not stack timing wrappers");}
        int[] errors={0};var failure=f.failure;var failing=ProfilingEventBus.wrap(BusBuilder.builder().setExceptionHandler((b,e,list,index,error)->{require(error==failure,"Original exception reaches bus handler");errors[0]++;}).build());failing.addListener(ListenerFixture.Basic.class,f.throwing());
        try(var timing=new CallbackTimings(failing,Thread.currentThread(),c->"fixture")){
            try{failing.post(new ListenerFixture.Basic());throw new AssertionError("Missing original failure");}catch(IllegalStateException error){require(error==failure&&errors[0]==1,"Original exception identity and exception handler preserved");}
            require(timing.snapshot().getFirst().calls()==1,"Exceptional invocations are recorded once");
        }
    }

}
