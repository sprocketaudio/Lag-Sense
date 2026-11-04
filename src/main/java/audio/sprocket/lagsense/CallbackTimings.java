package audio.sprocket.lagsense;

import java.lang.reflect.*;
import java.lang.invoke.*;
import java.util.concurrent.atomic.AtomicReference;
import net.neoforged.bus.ListenerList;
import net.neoforged.bus.GeneratedEventListener;
import java.util.*;
import java.util.function.Function;
import net.neoforged.bus.IWrapperListener;
import net.neoforged.bus.api.*;
import net.neoforged.bus.api.EventListener;

/** Capture-scoped cache wrappers; registration, filtering and dispatch remain with the original bus. */
public final class CallbackTimings implements AutoCloseable {
    private static volatile CallbackTimings current;
    private static final class Info {
        final String owner,method;final long id;
        final Map<Class<?>,Measurement> events=new IdentityHashMap<>();
        Info(String owner,String method,long id){this.owner=owner;this.method=method;this.id=id;}
    }
    public static final class Measurement {
        private final Info info;private final String event;private long nanos,calls,max;
        private Measurement(Info info,String event){this.info=info;this.event=event;}
        private Report.Callback snapshot(){return new Report.Callback(info.owner,event,info.method,nanos,calls,max,info.id);}
    }
    private static final class State {final Thread thread;volatile boolean running=true;State(Thread thread){this.thread=thread;}}
    private record Patch(AtomicReference<EventListener[]> slot,EventListener[] original,EventListener[] installed){}
    private record PhasePatch(AtomicReference<EventListener[][]> slot,EventListener[][] original,EventListener[][] installed){}
    private static final class Cache {final ListenerList list;final AtomicReference<EventListener[]> all;final AtomicReference<EventListener[][]> phases;Patch patch;PhasePatch phasePatch;
        Cache(ListenerList list,AtomicReference<EventListener[]> all,AtomicReference<EventListener[][]> phases){this.list=list;this.all=all;this.phases=phases;}}
    private final Map<Class<?>,Cache> caches=new IdentityHashMap<>();
    private final Field listenerCache,phaseCache;
    private final MethodHandle getList;
    private final IEventBus sourceBus;
    private final State state;
    private String adapterError="";
    private final IEventBus bus;
    private final Thread thread;
    private final Function<Class<?>,String> owner;
    private final Field registrations;
    private final DiagnosticBudget budget;
    private final Map<EventListener,Info> info=new IdentityHashMap<>();
    private final List<Measurement> values=new ArrayList<>();
    private volatile boolean running=true;
    private boolean exhausted;
    private long nextId,missed;
    public CallbackTimings(IEventBus bus,Thread thread,Function<Class<?>,String> owner)throws ReflectiveOperationException{this(bus,thread,owner,DiagnosticBudget.events());}
    public CallbackTimings(IEventBus bus,Thread thread,Function<Class<?>,String> owner,DiagnosticBudget budget)throws ReflectiveOperationException{
        this.bus=bus;sourceBus=ProfilingEventBus.underlying(bus);this.thread=thread;state=new State(thread);this.owner=owner;this.budget=budget;
        Class<?> type=Class.forName("net.neoforged.bus.EventBus");if(!type.isInstance(sourceBus))throw new IllegalArgumentException("Unsupported event bus");
        registrations=type.getDeclaredField("listeners");registrations.setAccessible(true);
        var method=type.getDeclaredMethod("getListenerList",Class.class);method.setAccessible(true);getList=MethodHandles.lookup().unreflect(method).bindTo(sourceBus);
        listenerCache=ListenerList.class.getDeclaredField("listeners");listenerCache.setAccessible(true);
        phaseCache=ListenerList.class.getDeclaredField("perPhaseListeners");phaseCache.setAccessible(true);
        refresh();current=this;
    }
    public static CallbackTimings activeFor(IEventBus bus){var value=current;return value!=null&&value.running&&value.bus==bus&&Thread.currentThread()==value.thread?value:null;}
    @SuppressWarnings("unchecked") public void refresh()throws ReflectiveOperationException{
        if(!running||exhausted)return;
        var registered=(Map<Object,List<EventListener>>)registrations.get(sourceBus);
        for(var entry:registered.entrySet())synchronized(entry.getValue()){for(var listener:entry.getValue())discover(entry.getKey(),listener);}
    }
    private void discover(Object target,EventListener listener){
        if(info.containsKey(listener)||exhausted)return;
        Class<?> clazz=target instanceof Class<?> c?c:target instanceof Method m?m.getDeclaringClass():target.getClass();
        String type=ModOwnership.resolvableClass(clazz.getName());
        if(type.startsWith("audio.sprocket.lagsense.")){info.put(listener,null);if(listener instanceof IWrapperListener wrapper)info.put(wrapper.getWithoutCheck(),null);return;}
        String label=type;
        if(target instanceof Method m)label+="#"+m.getName();
        else if(listener instanceof net.neoforged.bus.SubscribeEventListener){String text=listener.toString();int end=text.indexOf('('),start=end<0?-1:text.lastIndexOf(' ',end);label+=start>=0?"#"+text.substring(start+1,end):"#listener";}
        else label+="#listener";
        String mod=owner.apply(clazz);
        if(!budget.reserve(256L+2L*(mod.length()+label.length()))){exhausted=true;return;}
        var metadata=new Info(mod,label,++nextId);info.put(listener,metadata);
        if(listener instanceof IWrapperListener wrapper)info.put(wrapper.getWithoutCheck(),metadata);
    }
    public Measurement measure(EventListener listener,Event event){
        var metadata=info.get(listener);
        if(metadata==null&&info.containsKey(listener))return null;
        if(metadata==null&&!exhausted){
            try{refresh();}catch(ReflectiveOperationException error){exhausted=true;}
            metadata=info.get(listener);
        }
        if(metadata==null){if(!listener.getClass().getName().startsWith("audio.sprocket.lagsense."))missed++;return null;}
        var value=metadata.events.get(event.getClass());
        if(value!=null)return value;
        String name=event.getClass().getName();
        if(!budget.reserve(192L+2L*name.length())){exhausted=true;missed++;return null;}
        value=new Measurement(metadata,name);metadata.events.put(event.getClass(),value);values.add(value);return value;
    }
    @SuppressWarnings("unchecked") public void prepare(Event event,EventPriority phase){
        if(!running||!adapterError.isEmpty())return;
        try{
            var cache=caches.get(event.getClass());
            if(cache==null){if(!budget.reserve(256)){exhausted=true;return;}var list=(ListenerList)getList.invokeExact((Class<?>)event.getClass());cache=new Cache(list,(AtomicReference<EventListener[]>)listenerCache.get(list),(AtomicReference<EventListener[][]>)phaseCache.get(list));caches.put(event.getClass(),cache);}
            if(phase==null){
                var original=cache.list.getListeners();if(cache.patch!=null&&cache.patch.installed()==original)return;
                var installed=wrap(original,event);if(installed==original)return;
                if(cache.all.compareAndSet(original,installed))cache.patch=new Patch(cache.all,original,installed);else adapterError="event cache changed during preparation";
            }else{
                cache.list.getPhaseListeners(phase);var original=cache.phases.get();
                if(cache.phasePatch!=null&&cache.phasePatch.installed()==original)return;
                if(!budget.reserve(32L+8L*original.length)){exhausted=true;return;}
                var installed=original.clone();for(int i=0;i<installed.length;i++)installed[i]=wrap(installed[i],event);
                if(cache.phases.compareAndSet(original,installed))cache.phasePatch=new PhasePatch(cache.phases,original,installed);else adapterError="event phase cache changed during preparation";
            }
        }catch(Error error){throw error;}catch(Throwable error){adapterError="unsupported event bus adapter ("+error.getClass().getSimpleName()+")";}
    }
    private EventListener[] wrap(EventListener[] original,Event event){
        if(!budget.reserve(32L+8L*original.length)){exhausted=true;missed+=original.length;return original;}
        var installed=original.clone();
        for(int i=0;i<installed.length;i++){
            var listener=installed[i];while(listener instanceof Timed timed)listener=timed.original;
            var value=measure(listener,event);if(value==null)continue;
            if(!budget.reserve(48)){exhausted=true;missed++;continue;}
            installed[i]=new Timed(state,listener,value);
        }
        return installed;
    }
    private static final class Timed extends GeneratedEventListener {
        final State state;final EventListener original;final Measurement value;
        Timed(State state,EventListener original,Measurement value){this.state=state;this.original=original;this.value=value;}
        @Override public void invoke(Event event){
            if(!state.running||Thread.currentThread()!=state.thread){original.invoke(event);return;}
            long started=System.nanoTime();boolean measured=Profiler.beginEvent(started);
            try{original.invoke(event);}finally{long ended=System.nanoTime();Profiler.endEvent(measured,ended);add(value,ended-started);}
        }
        @Override public String toString(){return original.toString();}
    }
    private static void add(Measurement value,long nanos){long elapsed=Math.max(0,nanos);value.nanos+=elapsed;value.calls++;value.max=Math.max(value.max,elapsed);}
    public void record(Measurement value,long nanos){add(value,nanos);}
    public List<Report.Callback> snapshot(){return values.stream().filter(v->v.calls>0).map(Measurement::snapshot).sorted(Comparator.comparingLong(Report.Callback::nanos).reversed().thenComparing(Report.Callback::method).thenComparingLong(Report.Callback::handlerId)).toList();}
    public boolean incomplete(){return exhausted||missed>0||!adapterError.isEmpty();}
    public String limitation(){return incomplete()?"Event results limited: "+(!adapterError.isEmpty()?adapterError+". ":"")+"Capture memory budget "+budget.description()+". "+values.stream().filter(v->v.calls>0).count()+" handler/event records retained. "+missed+" handler timing attempts were skipped. Timed event coverage is incomplete.":"";}

    public long usedBytes(){return budget.used();}
    @Override public void close(){running=false;state.running=false;if(current==this)current=null;
        for(var cache:caches.values()){if(cache.patch!=null)cache.patch.slot().compareAndSet(cache.patch.installed(),cache.patch.original());if(cache.phasePatch!=null)cache.phasePatch.slot().compareAndSet(cache.phasePatch.installed(),cache.phasePatch.original());}caches.clear();
    }
}
