package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;
import net.minecraft.client.gui.components.AbstractWidget;

/** Native settings forms and bounds with metrics-only fonts; no window or network. */
final class SettingsLayoutChecks {
    static void run() throws Exception {
        for(int[] viewport:List.of(new int[]{960,540},new int[]{640,360},new int[]{640,280})) {
            var defaults=ServerSettings.defaults();
            var screens=new ArrayList<SettingsPage>();
            screens.add(new SettingsScreen(null,SettingsPayload.view(true,defaults,"")));
            var overlay=new OverlaySettingsScreen(null);screens.add(overlay);
            var position=new OverlaySettingsScreen(null);set(position,"position",true);screens.add(position);
            screens.add(new CaptureSettingsScreen(null,new CaptureSettings(defaults.timer(),defaults.spikes()),""));
            var spikes=new CaptureSettingsScreen(null,new CaptureSettings(defaults.timer(),defaults.spikes()),"");set(spikes,"spikePage",true);screens.add(spikes);
            for(String tab:List.of("Connection","Timer","Spikes","Manual")) {var screen=server("discord",defaults);set(screen,"tab",tab);screens.add(screen);}
            screens.add(server("advanced",defaults));
            var custom=server("advanced",defaults);var valuesField=ServerSettingsScreen.class.getDeclaredField("values");valuesField.setAccessible(true);((Map<String,String>)valuesField.get(custom)).put("resource","custom");screens.add(custom);
            Integer left=null,top=null,bottom=null;
            for(var screen:screens) {
                screen.width=viewport[0];screen.height=viewport[1];
                if(left==null){left=screen.panelMargin();top=screen.panelTop();bottom=screen.panelBottom();}
                if(screen.panelMargin()!=left||screen.panelTop()!=top||screen.panelBottom()!=bottom)throw new AssertionError("All settings pages and tabs must have identical panel bounds");
                if(bottom-top!=Math.min(400,viewport[1]-24)||Math.abs(top+bottom-viewport[1])>1)throw new AssertionError("Settings panel must fit the full delivery form while staying centred and viewport-bounded");
                // Overlay geometry is checked without loading client key bindings/config in this headless harness.
                if(screen instanceof OverlaySettingsScreen)continue;
                if(screen instanceof SettingsScreen s)s.init();
                else if(screen instanceof CaptureSettingsScreen s)s.init();
                else if(screen instanceof ServerSettingsScreen s)s.init();
                for(String status:List.of("Unsaved changes","Settings applied.")) {
                    var bounds=screen.footerStatus(status);
                    if(bounds.y()!=screen.footer()+6)throw new AssertionError("Short settings statuses should fit between footer actions: "+status);
                    if(bounds.y()==screen.footer()+6)for(var child:screen.children())if(child instanceof UiButton b&&b.getY()==screen.footer()&&bounds.x()<b.getX()+b.getWidth()&&bounds.x()+bounds.width()>b.getX())throw new AssertionError("Footer status must never overlap action buttons");
                }
                var warning=screen.footerStatus("Capture duration must be shorter than the timer interval. Enter a valid interval and try again.");
                if(warning.y()+8>=screen.footer()||warning.x()!=screen.margin()||warning.width()!=screen.contentWidth())throw new AssertionError("Long validation messages need a separate footer line, clear of the buttons");
                var list=screen.children().stream().filter(c->c instanceof UiScrollList).map(c->(UiScrollList)c).findFirst().orElse(null);
                var widgets=new ArrayList<AbstractWidget>();
                for(var child:screen.children())if(child instanceof AbstractWidget w) {
                    var r=w.getRectangle();
                    if(r.left()<screen.margin()||r.right()>screen.width-screen.margin()||r.top()<screen.panelTop()+44||r.bottom()>screen.panelBottom()-12)throw new AssertionError("Settings controls must stay inside their common panel");
                    widgets.add(w);
                }
                if(list!=null) {
                    if(list.getRectangle().bottom()>screen.footer()-20)throw new AssertionError("Scrolling form must stop before footer actions");
                    var body=list.children().stream().map(w->(AbstractWidget)w).toList();
                    if((viewport[1]>=424||viewport[1]>=360&&screen instanceof ServerSettingsScreen&&screen.getTitle().getString().equals("Advanced"))&&body.stream().anyMatch(w->w.getRectangle().top()<list.getRectangle().top()||w.getRectangle().bottom()>list.getRectangle().bottom()))throw new AssertionError("Every Discord control must fit without scrolling when the viewport has room");
                    for(int i=0;i<body.size();i++)for(int j=i+1;j<body.size();j++) {
                        var a=body.get(i).getRectangle();var b=body.get(j).getRectangle();
                        if(a.left()<b.right()&&a.right()>b.left()&&a.top()<b.bottom()&&a.bottom()>b.top())throw new AssertionError("Settings labels and controls must not overlap");
                    }
                    if(screen instanceof ServerSettingsScreen&&screen.getTitle().getString().equals("Advanced")){
                        var labels=body.stream().map(w->w.getMessage().getString()).toList();boolean customMode=labels.contains("Resource mode: Custom");
                        long fieldCount=body.stream().filter(UiInput.class::isInstance).count();
                        if(fieldCount!=(customMode?5:1))throw new AssertionError("Adaptive must hide all four Custom fields without losing the backup grace field");
                        if(labels.stream().anyMatch(t->t.startsWith("Effective:")||t.startsWith("Safety ceilings")||t.contains("retained frames")))throw new AssertionError("Advanced does not repeat explanatory resource-budget text in the form");
                    }
                    var fields=body.stream().filter(w->w instanceof UiInput).toList();
                    if(!fields.isEmpty()) {
                        var last=fields.getLast();int initial=last.getY();list.restore(Integer.MAX_VALUE);var r=last.getRectangle();
                        if((viewport[1]>=424||viewport[1]>=360&&screen instanceof ServerSettingsScreen&&screen.getTitle().getString().equals("Advanced"))&&last.getY()!=initial)throw new AssertionError("Full settings panel must not force unnecessary scrolling");
                        if(r.top()<list.getRectangle().top()||r.bottom()>list.getRectangle().bottom())throw new AssertionError("Scrolling must reveal the final field within the contained form");
                    }
                }
            }
        }
        System.out.println("Settings layout checks passed (shared panel bounds, Advanced Adaptive/Custom field visibility, no Advanced scrolling at normal GUI sizes, native form spacing and pinned footers).");
    }
    private static ServerSettingsScreen server(String section,ServerSettings defaults) throws Exception {
        var ctor=ServerSettingsScreen.class.getDeclaredConstructor(net.minecraft.client.gui.screens.Screen.class,String.class,ServerSettingsForms.View.class,String.class);ctor.setAccessible(true);
        return ctor.newInstance(null,section,ServerSettingsForms.view(defaults),"");
    }
    private static void set(Object target,String name,Object value) throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
}
