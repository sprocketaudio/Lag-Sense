package net.sprocketgames.lagsense;

import java.util.function.Consumer;
import net.neoforged.bus.api.*;

/** Keeps the underlying bus responsible for registration, filtering, dispatch and exceptions. */
public final class ProfilingEventBus implements IEventBus {
    private final IEventBus delegate;
    private ProfilingEventBus(IEventBus delegate){this.delegate=delegate;}
    public static IEventBus wrap(IEventBus bus){return bus instanceof ProfilingEventBus?bus:new ProfilingEventBus(bus);}
    static IEventBus underlying(IEventBus bus){while(bus instanceof ProfilingEventBus profiling)bus=profiling.delegate;return bus;}
    public void register(Object target){delegate.register(target);}
    public void unregister(Object target){delegate.unregister(target);}
    public void start(){delegate.start();}
    public <T extends Event>void addListener(Consumer<T> c){delegate.addListener(c);}
    public <T extends Event>void addListener(Class<T> t,Consumer<T> c){delegate.addListener(t,c);}
    public <T extends Event>void addListener(EventPriority p,Consumer<T> c){delegate.addListener(p,c);}
    public <T extends Event>void addListener(EventPriority p,Class<T> t,Consumer<T> c){delegate.addListener(p,t,c);}
    public <T extends Event>void addListener(boolean r,Consumer<T> c){delegate.addListener(r,c);}
    public <T extends Event>void addListener(boolean r,Class<T> t,Consumer<T> c){delegate.addListener(r,t,c);}
    public <T extends Event>void addListener(EventPriority p,boolean r,Consumer<T> c){delegate.addListener(p,r,c);}
    public <T extends Event>void addListener(EventPriority p,boolean r,Class<T> t,Consumer<T> c){delegate.addListener(p,r,t,c);}
    public <T extends Event>T post(T event){var timing=CallbackTimings.activeFor(this);if(timing!=null)timing.prepare(event,null);return delegate.post(event);}
    public <T extends Event>T post(EventPriority phase,T event){var timing=CallbackTimings.activeFor(this);if(timing!=null)timing.prepare(event,phase);return delegate.post(phase,event);}
}
