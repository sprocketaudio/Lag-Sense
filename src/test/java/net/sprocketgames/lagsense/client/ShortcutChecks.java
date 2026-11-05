package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.*;
import org.lwjgl.glfw.GLFW;

/** Native binding contracts and actual client-state/layout paths, without synthetic OS input. */
final class ShortcutChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static InputConstants.Key key(int value){return InputConstants.Type.KEYSYM.getOrCreate(value);}
    static void run()throws Exception{
        var settings=LagSenseClient.settings;var report=LagSenseClient.report;var marker=LagSenseClient.markerIndex;var theme=UiTheme.current();
        var saved=new LinkedHashMap<KeyMapping,Map.Entry<KeyModifier,InputConstants.Key>>();
        ShortcutBindings.all().forEach(m->saved.put(m,Map.entry(m.getKeyModifier(),m.getKey())));
        try{
            var bindings=List.of(ShortcutBindings.REPORT,ShortcutBindings.CAPTURE,ShortcutBindings.SETTINGS,ShortcutBindings.VISIBILITY);
            int[] keys={GLFW.GLFW_KEY_F9,GLFW.GLFW_KEY_F10,GLFW.GLFW_KEY_F11,GLFW.GLFW_KEY_F12};
            check(ShortcutBindings.all().stream().map(KeyMapping::getName).distinct().count()==ShortcutBindings.all().size(),"No duplicate Controls entries");
            for(int i=0;i<bindings.size();i++){
                var mapping=bindings.get(i);mapping.setToDefault();
                check(mapping.getKeyModifier()==KeyModifier.CONTROL&&mapping.getKey().equals(key(keys[i])),"Each function key has the required Ctrl default");
                check(mapping.getDefaultKeyModifier()==KeyModifier.CONTROL&&mapping.isDefault(),"Minecraft Reset restores the modifier too");
                check(mapping.getKeyConflictContext().conflicts(KeyConflictContext.IN_GAME)&&mapping.getKeyConflictContext().conflicts(KeyConflictContext.UNIVERSAL),"Native in-game/global conflicts are declared");
                mapping.setKeyModifierAndCode(KeyModifier.ALT,key(GLFW.GLFW_KEY_K+i));
                check(mapping.getKeyModifier()==KeyModifier.ALT&&!mapping.isDefault()&&mapping.saveString().startsWith("key.keyboard."),"Native remapping and options serialization retain normal keys");
                mapping.setToDefault();
                ShortcutBindings.clear();
                check(ShortcutBindings.pressEdge(key(keys[i]),GLFW.GLFW_PRESS),"First physical press is accepted");
                check(!ShortcutBindings.pressEdge(key(keys[i]),GLFW.GLFW_REPEAT)&&!ShortcutBindings.pressEdge(key(keys[i]),GLFW.GLFW_PRESS),"Repeats and duplicate press callbacks do not toggle twice");
                ShortcutBindings.pressEdge(key(GLFW.GLFW_KEY_LEFT_CONTROL),GLFW.GLFW_RELEASE);
                check(!ShortcutBindings.pressEdge(key(keys[i]),GLFW.GLFW_PRESS),"Releasing Ctrl does not re-arm a held function key");
                ShortcutBindings.pressEdge(key(keys[i]),GLFW.GLFW_RELEASE);
                check(ShortcutBindings.pressEdge(key(keys[i]),GLFW.GLFW_PRESS),"Releasing the function key re-arms the shortcut");
            }
            String hint=ShortcutBindings.hudHint();check(hint.contains(ShortcutBindings.hint(ShortcutBindings.REPORT).replace(" + ","+")+" Profiler")&&hint.contains(ShortcutBindings.hint(ShortcutBindings.VISIBILITY).replace(" + ","+")+" Toggle visuals")&&!hint.contains("settings"),"HUD hints show actions for profiler and master visibility, not Settings");
            String english=ShortcutBindings.hudHint("CTRL + F9","CTRL + F12");check(UiFonts.width(UiFonts.body(),english)<=222,"Default HUD shortcut hints fit the existing panel: "+UiFonts.width(UiFonts.body(),english));
            ShortcutBindings.VISIBILITY.setKeyModifierAndCode(KeyModifier.ALT,key(GLFW.GLFW_KEY_V));check(ShortcutBindings.hudHint().contains(ShortcutBindings.hint(ShortcutBindings.VISIBILITY).replace(" + ","+")+" Toggle visuals"),"HUD visibility hint follows normal remapping");
            ShortcutBindings.VISIBILITY.setKeyModifierAndCode(KeyModifier.NONE,InputConstants.UNKNOWN);check(!ShortcutBindings.hudHint().contains("Toggle visuals")&&!ShortcutBindings.hudHint().endsWith(" | "),"Unbound visuals shortcut leaves no stale or dangling HUD hint");ShortcutBindings.VISIBILITY.setToDefault();
            ShortcutBindings.clear();
            for(var binding:bindings)ShortcutBindings.enqueue(List.of(binding));
            check(ShortcutBindings.takeActions().equals(List.of(ShortcutBindings.Action.REPORT,ShortcutBindings.Action.CAPTURE,ShortcutBindings.Action.SETTINGS,ShortcutBindings.Action.VISIBILITY)),"Native modifier/conflict lookup matches dispatch only their own actions");
            ShortcutBindings.enqueue(List.of(ShortcutBindings.SETTINGS));ShortcutBindings.enqueue(List.of(ShortcutBindings.REPORT));ShortcutBindings.enqueue(List.of(ShortcutBindings.REPORT));
            check(ShortcutBindings.takeActions().equals(List.of(ShortcutBindings.Action.SETTINGS,ShortcutBindings.Action.REPORT,ShortcutBindings.Action.REPORT)),"Distinct physical presses retain their order, even within one tick");
            ShortcutBindings.enqueue(List.of());check(ShortcutBindings.takeActions().isEmpty(),"No native modifier match means no action");
            check(!ShortcutBindings.REPORT.same(ShortcutBindings.SETTINGS),"Distinct default shortcuts do not conflict");
            ShortcutBindings.SETTINGS.setKeyModifierAndCode(KeyModifier.CONTROL,key(GLFW.GLFW_KEY_F9));
            check(ShortcutBindings.REPORT.same(ShortcutBindings.SETTINGS),"Minecraft reports a duplicate modified remap as a conflict");
            ShortcutBindings.SETTINGS.setKeyModifierAndCode(KeyModifier.NONE,key(GLFW.GLFW_KEY_F9));
            check(ShortcutBindings.REPORT.same(ShortcutBindings.SETTINGS),"Minecraft's in-game conflict rules include the unmodified key");ShortcutBindings.SETTINGS.setToDefault();
            ShortcutBindings.FPS.setToDefault();check(ShortcutBindings.FPS.isUnbound(),"Optional frame profiling has no leftover F7 default");
            var mapping=ShortcutBindings.REPORT;mapping.setKeyModifierAndCode(KeyModifier.NONE,key(GLFW.GLFW_KEY_F9));
            check(ShortcutBindings.migrate(mapping,GLFW.GLFW_KEY_F9)&&mapping.isDefault(),"Old unchanged F9 migrates to Ctrl+F9");
            mapping.setKeyModifierAndCode(KeyModifier.ALT,key(GLFW.GLFW_KEY_F9));
            check(!ShortcutBindings.migrate(mapping,GLFW.GLFW_KEY_F9)&&mapping.getKeyModifier()==KeyModifier.ALT,"Custom modifiers survive migration");
            mapping.setKeyModifierAndCode(KeyModifier.NONE,key(GLFW.GLFW_KEY_K));
            check(!ShortcutBindings.migrate(mapping,GLFW.GLFW_KEY_F9),"Custom key choices survive migration");mapping.setToDefault();
            mapping.setKeyModifierAndCode(KeyModifier.NONE,InputConstants.Type.MOUSE.getOrCreate(4));ShortcutBindings.clear();
            check(ShortcutBindings.pressEdge(mapping.getKey(),GLFW.GLFW_PRESS)&&!ShortcutBindings.pressEdge(mapping.getKey(),GLFW.GLFW_REPEAT),"Mouse remapping shares press/repeat protection");mapping.setToDefault();
            for(var palette:UiTheme.Theme.values()){
                UiTheme.select(palette);
                for(int[] size:new int[][]{{480,270},{640,360},{854,480},{960,540}}){
                    LagSenseClient.report=null;var screen=LagSenseClient.latestReportScreen();screen.width=size[0];screen.height=size[1];screen.init();
                    var controls=ProfileLayoutChecks.widgets(screen);var labels=controls.stream().map(w->w.getMessage().getString()).toList();
                    check(labels.containsAll(List.of("Targets","Chunks","Map","CPU Activity","Call Paths","Event Timings","History","Settings","Start Profile","Close")),"Empty profiler retains navigation, History and Settings in every theme/viewport");
                    check(screen.profileNavigation!=null&&ProfileLayoutChecks.field(screen,"report")==null&&ProfileLayoutChecks.field(screen,"parent")==null,"Empty root is a real normal screen with no invented capture or old parent");
                    var start=controls.stream().filter(w->w.getMessage().getString().equals("Start Profile")).findFirst().orElseThrow();
                    check(Math.abs(start.getX()+start.getWidth()/2-screen.width/2)<=1&&start.getY()>screen.emptyMessageY()&&start.getY()+start.getHeight()<screen.footer(),"Empty-state button is centered inside results, clear of the footer");
                    for(String label:List.of("History","Settings","Start Profile","Close")){
                        var button=controls.stream().filter(w->w.getMessage().getString().equals(label)).findFirst().orElseThrow();
                        check(button.getX()>=screen.margin()&&button.getX()+button.getWidth()<=screen.width-screen.margin(),"Essential empty controls stay inside the existing panel");
                    }
                    check(!labels.contains("Export")&&!labels.contains("Copy"),"Empty state has no fabricated result actions");
                }
                for(int mask=0;mask<8;mask++){
                    var value=new OverlaySettings();value.theme=palette;value.enabled=(mask&1)!=0;value.worldMarkers=(mask&2)!=0;value.chunkHeatmap=(mask&4)!=0;LagSenseClient.settings=value;
                    VisualVisibility.reset();String original=Profiler.JSON.toJson(value);
                    VisualVisibility.toggle();var captureBefore=LagSenseClient.report;ShortcutBindings.openProfiler();check(VisualVisibility.visible()&&Profiler.JSON.toJson(value).equals(original)&&LagSenseClient.report==captureBefore,"Explicit profiler shortcut restores only the temporary override for every preference combination without starting a capture");
                    check(!VisualVisibility.toggle()&&!VisualVisibility.visible(),"Master hide is independent of all individual preferences");
                    var capture=new Report(mask+1,100,1,2,true,false,List.of(),true,29,List.of(),20);
                    LagSenseClient.report=null;LagSenseClient.storeReport(capture,false,new MarkerIndex(capture),true);
                    check(LagSenseClient.report==capture&&Profiler.JSON.toJson(value).equals(original),"Captures update while hidden without changing individual visibility settings");
                    check(VisualVisibility.toggle()&&VisualVisibility.visible()&&Profiler.JSON.toJson(value).equals(original),"Restore preserves exactly the chosen preferences and theme");
                    VisualVisibility.toggle();
                    var start=new Report(mask+100,100,0,0,true,false,List.of(),true,30,List.of(),0);
                    LagSenseClient.storeReport(start,false,new MarkerIndex(start),false);
                    check(VisualVisibility.visible()&&Profiler.JSON.toJson(value).equals(original),"Server-confirmed manual capture restores the master override without changing any individual preference");
                    VisualVisibility.toggle();LagSenseClient.storeReport(start,false,new MarkerIndex(start),false);
                    check(!VisualVisibility.visible(),"Duplicate start acknowledgements cannot undo hiding during a capture");
                    var progress=new Report(start.id(),100,20,2,true,false,List.of(),true,29,List.of(),20);
                    LagSenseClient.storeReport(progress,false,new MarkerIndex(progress),false);
                    check(!VisualVisibility.visible(),"Progress does not restore hidden visuals");
                    var finished=new Report(start.id(),100,40,2,true,false,List.of());
                    LagSenseClient.storeReport(finished,false,new MarkerIndex(finished),true);
                    LagSenseClient.storeReport(start,true,new MarkerIndex(start),false);
                    check(!VisualVisibility.visible()&&Profiler.JSON.toJson(value).equals(original),"Completion and archived captures preserve hidden state and individual preferences");
                }
                for(int[] size:new int[][]{{640,360},{854,480},{960,540}}){
                var display=new OverlaySettingsScreen(null);display.width=size[0];display.height=size[1];display.init();
                var master=ProfileLayoutChecks.widgets(display).stream().filter(w->w.getMessage().getString().startsWith("Performance visuals ")).findFirst().orElseThrow();
                check(master.getMessage().getString().endsWith("OFF"),"Settings displays the current keyboard override");
                check(UiFonts.labelWidth(master.getMessage().getString())<=master.getWidth()-14,"Master control label fits without clipping");
                String unchanged=Profiler.JSON.toJson(LagSenseClient.settings);((UiButton)master).onPress(null);
                check(VisualVisibility.visible()&&Profiler.JSON.toJson(LagSenseClient.settings).equals(unchanged),"Settings toggle changes the same temporary override without saving display preferences");
                VisualVisibility.toggle();display.tick();
                check(ProfileLayoutChecks.widgets(display).stream().anyMatch(w->w.getMessage().getString().equals("Performance visuals OFF")),"Open settings refreshes after a shortcut changes visibility");
                var range=ProfileLayoutChecks.widgets(display).stream().filter(w->w.getMessage().getString().startsWith("Range:")).findFirst().orElseThrow();
                check(range.getY()+range.getHeight()<display.footer(),"The added master control leaves filters above the footer");
                }
            }
            check(!ClientReportPolicy.manualCaptureStarted(null,new Report(999,100,0,0,false,false,List.of(),true,30,List.of(),0),false),"A non-admin report cannot restore the manual-capture override");
            // Rendering exits before dereferencing the extractor, preparing text or changing placement.
            VisualVisibility.reset();VisualVisibility.toggle();ProfilerHud.draw(null,false);
        }finally{
            ShortcutBindings.clear();saved.forEach((m,value)->m.setKeyModifierAndCode(value.getKey(),value.getValue()));
            LagSenseClient.settings=settings;LagSenseClient.report=report;LagSenseClient.markerIndex=marker;UiTheme.select(theme);
        }
        System.out.println("Shortcut checks passed (native bindings, empty navigation, 24 visibility preference combinations, manual-start restoration, no restoration on progress/completion/archives, and synchronized Display control in three themes).");
    }
}
