package example.lagsensefixture;

import java.util.*;
import java.util.function.Consumer;
import net.neoforged.bus.api.*;

public final class ListenerFixture {
    public record Counted(int id) implements Consumer<Basic>{public void accept(Basic e){if(id<0)throw new AssertionError();}}
    public static class Basic extends Event {}
    public static final class Child extends Basic {}
    public static final class Cancelled extends Event implements ICancellableEvent {
        private boolean cancelled;
        @Override public boolean isCanceled() { return cancelled; }
        @Override public void setCanceled(boolean value) { cancelled = value; }
    }
    public final List<String> order = new ArrayList<>();
    @SubscribeEvent public void annotated(Basic e) { order.add("annotated"); }
    public Consumer<Basic> normal() { return e -> order.add("normal"); }
    public Consumer<Basic> later() { return e -> order.add("later"); }
    public Consumer<Cancelled> uncancelled() { return e -> order.add("uncancelled"); }
    public Consumer<Cancelled> receivesCancelled() { return e -> order.add("cancelled"); }
    public final IllegalStateException failure = new IllegalStateException("fixture");
    public Consumer<Basic> throwing() { return e -> { throw failure; }; }
}
