package net.sprocketgames.lagsense;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** A bounded, coalescing writer. Callers submit immutable snapshots. */
public final class JsonStore implements AutoCloseable {
    private final Path directory;
    private final Gson gson;
    private final Consumer<Exception> onFailure;
    private final Map<String, Object> pending = new LinkedHashMap<>();
    private final Thread worker;
    private boolean closing;
    public JsonStore(Path directory, Gson gson, Consumer<Exception> onFailure) {
        this.directory = directory; this.gson = gson; this.onFailure = onFailure;
        worker = Thread.ofPlatform().daemon().name("LagSense JSON writer").start(this::run);
    }
    public synchronized void save(String name, Object value) {
        if (!Set.of("latest.json", "areas.json").contains(name)) throw new IllegalArgumentException("Unknown LagSense data file");
        if (closing) throw new IllegalStateException("Writer has closed");
        // At most one queued value per supported file, even if the disk is slow.
        pending.put(name, value); notifyAll();
    }
    private void run() {
        while (true) {
            String name; Object value;
            synchronized (this) {
                while (pending.isEmpty() && !closing) {
                    try { wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
                if (pending.isEmpty()) return;
                name = pending.keySet().iterator().next(); value = pending.remove(name);
            }
            try {
                Files.createDirectories(directory);
                Path temp = directory.resolve(name + ".tmp"), target = directory.resolve(name);
                ReportFiles.json(temp,value,gson,CaptureBudget.MAX_FILE_BYTES);
                try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
            } catch (Exception e) { onFailure.accept(e); }
        }
    }
    @Override public void close() {
        synchronized (this) { closing = true; notifyAll(); }
        try { worker.join(5000); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); onFailure.accept(e); }
        if (worker.isAlive()) onFailure.accept(new IllegalStateException("Writer did not finish within five seconds; data may still be pending"));
    }
}
