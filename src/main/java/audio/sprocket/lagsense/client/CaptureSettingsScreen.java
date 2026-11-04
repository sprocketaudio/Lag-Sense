package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class CaptureSettingsScreen extends SettingsPage {
    private final Screen parent;
    private boolean spikePage, timerEnabled, spikesEnabled, waiting;
    private String message;
    private CaptureSettings confirmed, pendingDesired;
    private boolean closeAfterApply;
    private EditBox interval, timerLength, tps, sustain, work, consecutive, spikeLength, cooldown;
    private String intervalValue, timerValue, tpsValue, sustainValue, workValue, ticksValue, spikeValue, cooldownValue;
    public CaptureSettingsScreen(Screen parent, CaptureSettings state, String message) { super("Server captures"); this.parent = parent; confirmed=state; acceptDraft(state); this.message = message; }
    private void acceptDraft(CaptureSettings state) {
        timerEnabled = state.timer().enabled(); spikesEnabled = state.spikes().enabled(); intervalValue = "" + state.timer().intervalMinutes(); timerValue = "" + state.timer().captureSeconds();
        var s = state.spikes(); tpsValue = "" + s.minimumTps(); sustainValue = "" + s.sustainSeconds(); workValue = "" + s.thresholdMs(); ticksValue = "" + s.consecutive(); spikeValue = "" + s.captureSeconds(); cooldownValue = "" + s.cooldownSeconds();
        interval = timerLength = tps = sustain = work = consecutive = spikeLength = cooldown = null;
    }
    public void accept(CaptureSettings state, String message) { confirmed=state; boolean saved=state.equals(pendingDesired)&&message.equals("Settings applied."); if(saved)acceptDraft(state); this.message = message; waiting = false; pendingDesired=null; if(saved&&closeAfterApply)minecraft.setScreen(parent);else{closeAfterApply=false;rebuildWidgets();} }
    private String value(EditBox box,String stored){return box==null?stored:box.getValue();}
    private CaptureSettings draft(){return new CaptureSettings(new ProfileSchedule.Settings(timerEnabled,Integer.parseInt(value(interval,intervalValue)),Integer.parseInt(value(timerLength,timerValue))),new SpikeDetector.Settings(spikesEnabled,Double.parseDouble(value(work,workValue)),Integer.parseInt(value(consecutive,ticksValue)),Integer.parseInt(value(spikeLength,spikeValue)),Integer.parseInt(value(cooldown,cooldownValue)),Double.parseDouble(value(tps,tpsValue)),Integer.parseInt(value(sustain,sustainValue))));}
    private boolean dirty(){try{return !draft().equals(confirmed);}catch(IllegalArgumentException e){return true;}}
    private void remember() {
        if (interval != null) { intervalValue = interval.getValue(); timerValue = timerLength.getValue(); }
        if (tps != null) { tpsValue = tps.getValue(); sustainValue = sustain.getValue(); workValue = work.getValue(); ticksValue = consecutive.getValue(); spikeValue = spikeLength.getValue(); cooldownValue = cooldown.getValue(); }
        interval = timerLength = tps = sustain = work = consecutive = spikeLength = cooldown = null;
    }
    private EditBox number(String label, int x, int y, String value) {
        var field = input(label, x, y, (contentWidth() - 12) / 2, value, "[0-9.]*", 8); field.active = !waiting; field.setResponder(s -> message = ""); return field;
    }
    private EditBox field(String label,String hint,int x,int y,String value) {
        control(new UiLabel(font,x,y,(contentWidth()-12)/2,label));
        return number(hint,x,y+16,value);
    }
    @Override protected void init() {
        remember(); int x = margin(), second = x + (contentWidth() + 12) / 2;
        var nav = new UiMetrics.Flow(x, width - x, headerControlsTop());
        tab("Timer", nav.next(72), !spikePage, () -> { spikePage = false; rebuildWidgets(); }).active = !waiting;
        tab("Spikes", nav.next(72), spikePage, () -> { spikePage = true; rebuildWidgets(); }).active = !waiting;
        int top=nav.bottom()+20, fields=top+40;
        beginList(top,spikePage?1:0);
        toggle("Enabled:", new UiMetrics.Rect(x, top, UiFonts.labelWidth("Enabled: OFF") + 20, 20), spikePage ? spikesEnabled : timerEnabled, () -> { if (spikePage) spikesEnabled = !spikesEnabled; else timerEnabled = !timerEnabled; message = "Unsaved changes"; rebuildWidgets(); }).active = !waiting;
        if (!spikePage) {
            interval = field("Interval minutes","Timer interval in minutes",x,fields,intervalValue); timerLength = field("Capture seconds","Timer capture seconds",second,fields,timerValue);
        } else {
            tps = field("TPS threshold","TPS alert threshold; zero disables",x,fields,tpsValue); sustain = field("Sustain seconds","Seconds below TPS threshold",second,fields,sustainValue);
            work = field("Tick-work threshold ms","Tick-work alert milliseconds; zero disables",x,fields+48,workValue); consecutive = field("Consecutive ticks","Consecutive slow work ticks",second,fields+48,ticksValue);
            spikeLength = field("Capture seconds","Follow-up capture seconds",x,fields+96,spikeValue); cooldown = field("Cooldown seconds","Repeat cooldown after capture, seconds",second,fields+96,cooldownValue);
        }
        endList(fields+(spikePage?96:0)+36);
        primary("Apply", x, footer(), 60, this::apply).active = !waiting;
        danger("Reset defaults", x + 68, footer(), 96, () -> { acceptDraft(new CaptureSettings(ProfileSchedule.Settings.defaults(), SpikeDetector.Settings.defaults())); message = "Defaults restored. Apply to save."; rebuildWidgets(); }).active = !waiting;
        action("Back", width - x - 54, footer(), 54, this::onClose);
    }
    private void apply() {
        remember();
        try {
            var state = new CaptureSettings(new ProfileSchedule.Settings(timerEnabled, Integer.parseInt(intervalValue), Integer.parseInt(timerValue)),
                new SpikeDetector.Settings(spikesEnabled, Double.parseDouble(workValue), Integer.parseInt(ticksValue), Integer.parseInt(spikeValue), Integer.parseInt(cooldownValue), Double.parseDouble(tpsValue), Integer.parseInt(sustainValue)));
            ClientPacketDistributor.sendToServer(new UpdateCaptureSettingsPayload(Profiler.JSON.toJson(state))); message = "Applying…"; waiting = true;
            pendingDesired=state;
        } catch (NumberFormatException error) { message = "Enter valid numbers."; }
        catch (IllegalArgumentException error) { message = error.getMessage(); }
        rebuildWidgets();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        canvas(g, "SERVER CAPTURES"); footerMessage(g,message.isBlank()||message.equals("Unsaved changes")?dirty()?"Unsaved changes":"":message,mx,my);
        headerDivider(g);
        super.extractRenderState(g, mx, my, partial);
    }
    @Override public void onClose() { if(waiting)return; if(dirty())minecraft.setScreen(new UnsavedScreen(this,()->{acceptDraft(confirmed);minecraft.setScreen(parent);},()->{minecraft.setScreen(this);closeAfterApply=true;apply();}));else minecraft.setScreen(parent); }
}
