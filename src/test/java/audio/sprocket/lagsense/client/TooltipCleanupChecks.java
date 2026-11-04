package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.util.*;
import net.minecraft.client.gui.components.AbstractWidget;

final class TooltipCleanupChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static String hint(AbstractWidget widget)throws Exception{
        var field=UiHints.class.getDeclaredField("widgets");field.setAccessible(true);var value=((Map<?,?>)field.get(null)).get(widget);
        if(value==null)return "";var text=value.getClass().getDeclaredField("text");text.setAccessible(true);return (String)text.get(value);
    }
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static List<AbstractWidget> widgets(LagScreen screen){
        var widgets=new ArrayList<AbstractWidget>();for(var child:screen.children()){
            if(child instanceof AbstractWidget w)widgets.add(w);
            if(child instanceof UiScrollList list)for(var nested:list.children())if(nested instanceof AbstractWidget w)widgets.add(w);
        }return widgets;
    }
    private static void exact(LagScreen screen,String label,String expected)throws Exception{
        var widget=widgets(screen).stream().filter(w->w instanceof UiButton||w instanceof net.minecraft.client.gui.components.EditBox).filter(w->w.getMessage().getString().startsWith(label)).findFirst().orElseThrow(()->new AssertionError("Missing control: "+label));
        check(hint(widget).equals(expected),"Exact tooltip required for "+label+": "+hint(widget));
    }
    private static ServerSettingsScreen server(String section)throws Exception{
        var constructor=ServerSettingsScreen.class.getDeclaredConstructor(net.minecraft.client.gui.screens.Screen.class,String.class,ServerSettingsForms.View.class,String.class);constructor.setAccessible(true);
        var screen=constructor.newInstance(null,section,ServerSettingsForms.view(ServerSettings.defaults()),"");screen.width=960;screen.height=540;screen.init();return screen;
    }
    static void run()throws Exception{
        var advanced=server("advanced");
        exact(advanced,"CPU sampling","Sample server CPU activity during captures.");
        exact(advanced,"Detailed events","Measure each server event handler, which adds overhead during captures.");
        exact(advanced,"Backups:","Wait pauses automatic captures until the backup and grace period end. Warn allows captures with a backup warning. Ignore turns off backup checks.");
        var discord=server("discord");
        exact(discord,"Enabled","Enable Discord notifications.");
        exact(discord,"Webhook URL","Discord webhook URL used to send notifications.");
        exact(discord,"Avatar URL","Optional image URL for the Discord notification avatar. Leave blank to use the webhook's default avatar.");
        var connectionFields=widgets(discord);var webhook=connectionFields.stream().filter(UiInput.class::isInstance).filter(w->w.getMessage().getString().equals("Webhook URL")).findFirst().orElseThrow();var avatar=connectionFields.stream().filter(UiInput.class::isInstance).filter(w->w.getMessage().getString().equals("Avatar URL")).findFirst().orElseThrow();
        check(avatar.getRectangle().left()==webhook.getRectangle().left()&&avatar.getRectangle().width()==webhook.getRectangle().width()&&avatar.getRectangle().top()==webhook.getRectangle().top()+48,"Avatar uses matching full width and existing field spacing below webhook");
        check(((UiInput)avatar).getValue().isEmpty(),"Avatar field is blank by default");((UiInput)avatar).setValue("https://cdn.example.com/avatar.png");
        var desired=ServerSettingsScreen.class.getDeclaredMethod("desired");desired.setAccessible(true);check(((ServerSettingsForms.View)desired.invoke(discord)).discord().avatarUrl().equals("https://cdn.example.com/avatar.png"),"Avatar edits bind to the existing server-settings draft");
        exact(discord,"Connection","Configure Discord webhook notifications.");
        exact(discord,"Timer","Run profiles at a fixed interval.");
        exact(discord,"Spikes","Start a follow-up profile when a configured threshold is reached.");
        exact(discord,"Manual","Configure notifications for manually started profiles.");
        var overlay=new OverlaySettingsScreen(null);overlay.width=960;overlay.height=540;overlay.init();
        exact(overlay,"HUD ","Show or hide the LagSense HUD. The Hide/show visuals shortcut temporarily hides performance overlays.");
        exact(overlay,"World markers","Show captured timing labels above entities and highlight blocks in the world.");
        exact(overlay,"Chunk heatmap","Show a heatmap of chunk performance using captured timings.");
        for(String name:List.of("Timer","Spikes","Manual")){
            var page=server("discord");set(page,"tab",name);page.init();
            check(widgets(page).stream().noneMatch(w->w.getMessage().getString().startsWith("Coordinates")),"Discord has no coordinate toggle");
            exact(page,"Report file","Attach one report file. None sends the Discord summary without a file.");
            var controls=widgets(page);check(controls.stream().noneMatch(w->w.getMessage().getString().contains("Group repeats")||w.getMessage().getString().equals("Top contributors")),"Obsolete repeat and contributor-count labels are gone");
            var group=controls.stream().filter(UiButton.class::isInstance).map(UiButton.class::cast).filter(w->w.getMessage().getString().startsWith("Group:")).findFirst().orElseThrow();
            var mention=controls.stream().filter(UiButton.class::isInstance).map(UiButton.class::cast).filter(w->w.getMessage().getString().equals("Degraded or worse")).findFirst().orElseThrow();
            check(mention.getX()==group.getX()&&mention.getY()>group.getY()&&mention.getWidth()==group.getWidth(),"Mention severity is in the right column directly below Group");
            check(UiFonts.labelWidth(mention.getMessage().getString())<=mention.getWidth()-10,"Default mention choice fits its existing control");
            check(controls.stream().anyMatch(w->w.getMessage().getString().equals("Top results")),"Top results label is used consistently on all delivery tabs");
            String prefix=name.toLowerCase(Locale.ROOT);var values=(Map<String,String>)field(page,"values");
            var apply=ServerSettingsScreen.class.getDeclaredMethod("desired");apply.setAccessible(true);
            for(String next:List.of("target","type","both")){values.put(prefix+".group",next);var draft=((ServerSettingsForms.View)apply.invoke(page)).discord();var delivery=switch(prefix){case "timer"->draft.timer();case "spikes"->draft.spikes();default->draft.manual();};check(delivery.groupBy().equals(next),"All three grouping choices bind to the editable delivery DTO");}
            check(((ServerSettingsForms.View)apply.invoke(page)).discord().manual().mentionSeverity().equals("degraded"),"Mention threshold retains its default in the editable DTO");

        }
        var defaults=ServerSettings.defaults();var capture=new CaptureSettingsScreen(null,new CaptureSettings(defaults.timer(),defaults.spikes()),"");capture.width=960;capture.height=540;capture.init();
        check(hint((AbstractWidget)field(capture,"interval")).equals("Minutes between automatic profile captures (1–1440)."),"Exact timer interval field tooltip");
        check(hint((AbstractWidget)field(capture,"timerLength")).equals("Duration of each automatic profile capture (5–120 seconds)."),"Exact timer duration field tooltip");
        var spikes=new CaptureSettingsScreen(null,new CaptureSettings(defaults.timer(),defaults.spikes()),"");spikes.width=960;spikes.height=540;set(spikes,"spikePage",true);spikes.init();
        check(hint((AbstractWidget)field(spikes,"spikeLength")).equals("Profile duration after spike detection (5–120 seconds)."),"Exact spike duration field tooltip");
        var target=new Report.Entry("Entity","minecraft:creeper","minecraft:overworld",2,105,-6,6669030,101,UUID.randomUUID());
        String expected="minecraft:creeper\nEntity | minecraft:overworld | 2 105 -6\nChunk 0, -1\n101 updates | 66.03 µs/t";
        check(TargetDetails.hint(target,101).equals(expected),"Target help contains only the four recorded diagnostic lines");
        check(!TargetDetails.hint(target,0).contains("µs/t"),"A missing captured-tick denominator must not invent a per-tick measurement");
        var screen=new ReportScreen();screen.width=640;screen.height=360;var rows=new UiResults(screen,144,76,true).diagnosticHints();
        rows.lazy("target",r->r.row("target",target.type(),"Duplicate coordinates must not be auto-appended","66.03 µs/t",UiTheme.TEXT,12,false,expected,null));
        var list=new UiScrollList(screen.margin(),144,screen.contentWidth(),24,new ListScroll());list.virtual(rows.entries,null);
        check(hint((AbstractWidget)list.children().getFirst()).equals(expected),"Lazy rows preserve exact factual help without automatic names, secondary data or definitions");
        var tree=new CpuInvestigation.Builder("Server thread");tree.add(List.of(new CpuInvestigation.Frame("fixture","fixture.Caller","run","()V"),new CpuInvestigation.Frame("fixture","fixture.Work","tick","()V")));var data=tree.finish();
        var method=data.methods().getLast();String technical=MethodPresentation.hint(data,method);
        check(technical.contains(method.signature())&&technical.contains("Thread: Server thread")&&!technical.contains(MeasurementHelp.DIRECT),"Specialised CPU help preserves raw method/thread data without definitions");
        check(MethodPresentation.details(data,method).stream().anyMatch(s->s.contains("Direct samples:")&&s.contains("In stack:")),"Direct and inclusive diagnostic measurements remain available");
        System.out.println("Tooltip cleanup checks passed (exact settings strings, Discord tabs, four-line target data and retained CPU diagnostics).");
    }
}
