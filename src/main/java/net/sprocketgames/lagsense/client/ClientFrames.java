package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.FrameProfile;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.TimerQuery;
import org.lwjgl.opengl.GL;

public final class ClientFrames {
    public static boolean active;
    public static FrameProfile latest;
    private static FrameProfile.Capture capture;
    private static long previous, deadline;
    private static boolean gpuSupported;
    private static TimerQuery.FrameProfile eligible, recorded;
    public static boolean gpuEnabled() { return LagSenseClient.settings.gpuTiming; }
    public static void toggleGpu() { LagSenseClient.settings.gpuTiming = !LagSenseClient.settings.gpuTiming; LagSenseClient.settings.save(); }
    public static boolean wantsGpu() { return active && gpuEnabled() && gpuSupported; }
    public static void start(int seconds) {
        capture = new FrameProfile.Capture(); previous = 0; deadline = System.nanoTime() + seconds * 1_000_000_000L; active = true;
        var caps = GL.getCapabilities(); gpuSupported = caps.OpenGL33 || caps.GL_ARB_timer_query;
        eligible = recorded = null; capture.gpuStatus(!gpuEnabled() ? "GPU timing off" : !gpuSupported ? "GPU timing unavailable" : "No GPU samples");
        var mc = Minecraft.getInstance(); mc.setScreen(null);
    }
    public static void frame() {
        if (!active) return;
        long now = System.nanoTime(); if (previous != 0) capture.frame(now - previous); previous = now;
        if (now >= deadline || capture.full()) stop();
    }
    public static void stage(String stage, long nanos) { if (active) capture.stage(stage, nanos); }
    public static void submitted(TimerQuery.FrameProfile profile) { if (wantsGpu()) eligible = profile; }
    public static void gpuFrame(TimerQuery.FrameProfile profile) {
        if (!wantsGpu() || profile == null || profile != eligible || profile == recorded || !profile.isDone()) return;
        long nanos = profile.get(); if (nanos > 0) capture.gpu(nanos); recorded = profile;
    }
    public static void stop() {
        if (!active) return;
        active = false; latest = capture.finish(); capture = null;
        eligible = recorded = null;
        Minecraft.getInstance().setScreen(new FpsScreen(null));
    }
    static void clear(){active=false;capture=null;eligible=recorded=null;previous=deadline=0;gpuSupported=false;}
    public static int remaining() { return (int)Math.max(0, Math.ceil((deadline - System.nanoTime()) / 1_000_000_000.0)); }
}
