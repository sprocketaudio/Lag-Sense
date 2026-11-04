package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class ServerSettingsScreen extends SettingsPage {
    private final Screen parent;
    private final String section;
    private ServerSettingsForms.View expected;
    private final Map<String,String> values=new HashMap<>();
    private String tab="Connection",message;
    private boolean dirty,waiting,closeAfterApply,denied;
    private UiButton applyButton;
    public static void request(String section) {
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.getConnection()==null)return;
        if(!NetworkRegistry.hasChannel(mc.getConnection(),ServerSettingsRequest.TYPE.id())) {
            mc.getChatListener().handleSystemMessage(Component.literal("Update LagSense on the server to edit these settings."),false);return;
        }
        ClientPacketDistributor.sendToServer(new ServerSettingsRequest(section,""));
    }
    public static void accept(ServerSettingsPayload packet) {
        var mc=Minecraft.getInstance();
        if(packet.json().isEmpty()) {
            if(mc.screen instanceof ServerSettingsScreen screen){screen.waiting=false;screen.denied=true;screen.message=packet.message();screen.rebuildWidgets();}
            else if(mc.player!=null)mc.getChatListener().handleSystemMessage(Component.literal(packet.message()),false);
            return;
        }
        var view=Profiler.JSON.fromJson(packet.json(),ServerSettingsForms.View.class);
        if(mc.screen instanceof ServerSettingsScreen screen&&screen.section.equals(packet.section()))screen.accept(view,packet.message(),packet.saved());
        else mc.setScreen(new ServerSettingsScreen(mc.screen,packet.section(),view,packet.message()));
    }
    private ServerSettingsScreen(Screen parent,String section,ServerSettingsForms.View view,String message) {
        super(section.equals("discord")?"Discord notifications":"Advanced");this.parent=parent;this.section=section;expected=view;this.message=message;load(view);
    }
    private void put(String key,Object value){values.put(key,String.valueOf(value));}
    private void load(ServerSettingsForms.View view) {
        values.clear();
        if(section.equals("advanced")) {
            var a=view.advanced();put("cpu",a.modTasks());put("callbacks",a.detailedCallbacks());put("empty",a.monitorEmptyServer());put("backup",a.backups().mode());put("grace",a.backups().graceSeconds());
            var l=a.limits();put("resource",l.mode());put("eventMiB",l.eventMiB());put("cpuMiB",l.cpuMiB());put("jfrMiB",l.jfrMiB());put("depth",l.stackDepth());
        } else {
            var d=view.discord();put("enabled",d.enabled());put("name",d.serverName());put("webhook","");put("avatar",d.avatarUrl());
            loadDelivery("timer",d.timer());loadDelivery("spikes",d.spikes());loadDelivery("manual",d.manual());
        }
        updateDirty();
    }
    private void loadDelivery(String p,ServerSettings.Delivery d) {
        put(p+".enabled",d.enabled());put(p+".cooldown",d.cooldownSeconds());put(p+".top",d.top());
        put(p+".ms",d.minimumMs());put(p+".percent",d.minimumPercent());put(p+".match",d.match());put(p+".group",d.groupBy());
        put(p+".attachment",d.attachment());put(p+".role",d.roleId());put(p+".mention",d.mentionSeverity());
    }
    private boolean on(String key){return Boolean.parseBoolean(values.get(key));}
    private int integer(String key){return Integer.parseInt(values.get(key));}
    private double number(String key){return Double.parseDouble(values.get(key));}
    private ServerSettings.Delivery delivery(String p) {
        return new ServerSettings.Delivery(on(p+".enabled"),integer(p+".cooldown"),integer(p+".top"),number(p+".ms"),number(p+".percent"),
            values.get(p+".match"),values.get(p+".group"),values.get(p+".attachment"),values.get(p+".role"),values.get(p+".mention"));
    }
    private ServerSettingsForms.View desired() {
        if(section.equals("advanced"))return new ServerSettingsForms.View(new ServerSettingsForms.Advanced(on("cpu"),on("callbacks"),on("empty"),new BackupMonitor.Settings(values.get("backup"),integer("grace")),new ProfilingLimits(values.get("resource"),integer("eventMiB"),integer("cpuMiB"),integer("jfrMiB"),integer("depth"))),expected.discord(),expected.effective());
        var d=expected.discord();return new ServerSettingsForms.View(expected.advanced(),new ServerSettingsForms.Discord(on("enabled"),d.webhookConfigured(),values.get("name"),delivery("timer"),delivery("spikes"),delivery("manual"),values.get("avatar")),expected.effective());
    }
    private void updateDirty(){try{dirty=!desired().equals(expected)||!values.getOrDefault("webhook","").isBlank();}catch(RuntimeException e){dirty=true;}if(applyButton!=null)applyButton.active=dirty&&!waiting&&!denied;}
    private void change(Runnable edit){edit.run();message="";updateDirty();rebuildWidgets();}
    private void resetDefaults() {
        var defaults=ServerSettingsForms.view(ServerSettings.defaults());
        if(section.equals("advanced"))load(defaults);
        else if(tab.equals("Connection")){put("enabled",defaults.discord().enabled());put("name",defaults.discord().serverName());put("webhook","");put("avatar",defaults.discord().avatarUrl());}
        else {String p=tab.toLowerCase(Locale.ROOT);loadDelivery(p,switch(p){case "timer"->defaults.discord().timer();case "spikes"->defaults.discord().spikes();default->defaults.discord().manual();});}
        updateDirty();message="Defaults restored. Apply to save.";rebuildWidgets();
    }
    private void label(String text,int x,int y,int w){control(new UiLabel(font,x,y,w,text));}
    private UiInput field(String key,String label,int x,int y,int w,String pattern,int length,String hint) {
        label(label,x,y,w);
        var box=input(label,x,y+16,w,values.get(key),pattern,length);box.active=!waiting&&!denied;
        UiHints.hint(box,hint);box.setResponder(s->{values.put(key,s);message="";updateDirty();});return box;
    }
    private void toggleField(String key,String label,int x,int y,int w,String hint) {
        UiHints.hint(toggle(label,new UiMetrics.Rect(x,y,w,20),on(key),()->change(()->put(key,!on(key)))),hint).active=!waiting&&!denied;
    }
    private void choice(String key,String label,int x,int y,int w,List<String> choices,List<String> labels,String hint) {
        int i=choices.indexOf(values.get(key));String value=labels.get(Math.max(0,i));
        UiHints.hint(action((label.isEmpty()?"":label+": ")+value,x,y,w,()->change(()->put(key,choices.get((choices.indexOf(values.get(key))+1)%choices.size())))),hint).active=!waiting&&!denied;
    }
    @Override protected void init() {
        int x=margin(),col=(contentWidth()-12)/2,right=x+col+12,top=headerControlsTop()+12;
        if(section.equals("discord")) {
            var tabs=new UiMetrics.Flow(x,width-x,headerControlsTop());
            for(String name:List.of("Connection","Timer","Spikes","Manual"))UiHints.hint(tab(name,slot(tabs,name,48),tab.equals(name),()->{tab=name;rebuildWidgets();}),UiHints.discordTab(name)).active=!waiting&&!denied;
            top=tabs.bottom()+20;
        }
        beginList(top,section.equals("advanced")?0:List.of("Connection","Timer","Spikes","Manual").indexOf(tab));int y=top;
        if(section.equals("advanced")) {
            boolean compact=panelBottom()-panelTop()<380;
            toggleField("cpu","CPU sampling",x,y,col,"Sample server CPU activity during captures.");
            toggleField("callbacks","Detailed events",right,y,col,"Measure each server event handler, which adds overhead during captures.");y+=compact?24:32;
            toggleField("empty","Empty server",x,y,col,"Allow spike monitoring when no players are online.");
            choice("backup","Backups",right,y,col,List.of("defer_automatic","warn","ignore"),List.of("Wait","Warn","Ignore"),"Wait pauses automatic captures until the backup and grace period end. Warn allows captures with a backup warning. Ignore turns off backup checks.");y+=compact?28:40;
            field("grace","Backup grace seconds",x,y,col,"[0-9]*",4,"In Wait mode, automatic captures resume this many seconds after a backup finishes.");y+=compact?44:52;
            formSection("Profiling Limits",y);y+=compact?16:24;
            choice("resource","Resource mode",x,y,col,List.of("adaptive","custom"),List.of("Adaptive","Custom"),"Adaptive adjusts limits to the RAM available to the server. Custom lets you set maximums. Limits are reduced if memory runs low.");y+=compact?28:36;
            if(values.get("resource").equals("custom")){
                field("eventMiB","Event memory MiB",x,y,col,"[0-9]*",3,"Maximum RAM for handler identities, event records and wrappers. 1–512 MiB.");
                field("cpuMiB","CPU / Call Paths MiB",right,y,col,"[0-9]*",3,"Maximum RAM for each sampling scope. The server thread and all background threads have separate budgets. 1–512 MiB.");y+=compact?44:48;
                field("jfrMiB","JFR storage MiB",x,y,col,"[0-9]*",3,"Rolling recording storage on disk. 16–512 MiB. JFR writes complete chunks, so temporary files can be slightly larger.");
                field("depth","Maximum stack frames",right,y,col,"[0-9]*",3,"Retain up to 16–512 sampled frames. JFR may record fewer. For deeper JVM stacks, set -XX:FlightRecorderOptions=stackdepth=128 before starting Java.");y+=36;
            }
        } else if(tab.equals("Connection")) {
            toggleField("enabled","Enabled",x,y,col,"Enable Discord notifications.");
            label("Webhook: "+(expected.discord().webhookConfigured()?"Configured":"Not configured"),right,y+6,col);y+=40;
            field("name","Server name",x,y,contentWidth(),"[^\\p{Cntrl}]*",80,"Name shown in Discord reports.");y+=48;
            var webhook=field("webhook",expected.discord().webhookConfigured()?"Replace webhook":"Webhook URL",x,y,contentWidth(),"[^\\p{Cntrl}]*",512,"Discord webhook URL used to send notifications.");
            webhook.addFormatter((text,cursor)->FormattedCharSequence.forward("*".repeat(text.length()),Style.EMPTY));y+=48;
            field("avatar","Avatar URL",x,y,contentWidth(),"[^\\p{Cntrl}]*",2048,"Optional image URL for the Discord notification avatar. Leave blank to use the webhook's default avatar.");y+=48;
        } else {
            String p=tab.toLowerCase(Locale.ROOT);
            toggleField(p+".enabled","Enabled",x,y,col,"Deliver results for "+tab.toLowerCase(Locale.ROOT)+" captures.");
            y+=40;
            field(p+".cooldown","Delivery cooldown seconds",x,y,col,"[0-9]*",5,"Minimum time between Discord deliveries for this capture type.");
            field(p+".top","Top results",right,y,col,"[0-9]*",2,"Number of results in each contributor section and the chunk list. Both applies this limit separately to individuals and types.");y+=48;
            field(p+".ms","Minimum ms/t",x,y,col,"[0-9.]*",8,"Only include results using this many milliseconds per tick or more. Set to 0 to turn off this filter.");
            field(p+".percent","Minimum tick %",right,y,col,"[0-9.]*",8,"Only include results using this percentage of server tick time or more. Set to 0 to turn off this filter.");y+=48;
            choice(p+".match","Match",x,y,col,List.of("either","both"),List.of("Either","Both"),"Either includes results that pass at least one enabled filter. Both includes results that pass every enabled filter.");
            choice(p+".group","Group",right,y,col,List.of("target","type","both"),List.of("Individual","Type","Both"),"Show individual targets, combined types, or separate lists of both.");y+=32;
            choice(p+".attachment","Report file",x,y,col,List.of("none","html","json","csv"),List.of("None","HTML","JSON","CSV"),"Attach one report file. None sends the Discord summary without a file.");
            label("Mention severity",right,y,col);
            choice(p+".mention","",right,y+14,col,List.of("never","critical","degraded","warning","always"),List.of("Never","Critical only","Degraded or worse","Warning or worse","Always"),"When to mention the configured role. Reports still send below this level. Insufficient Data only mentions with Always.");y+=40;
            field(p+".role","Mention role ID",x,y,contentWidth(),"[0-9]*",22,"Optional Discord role to mention for this capture type. Leave blank for no mention.");y+=48;
        }
        endList(y);
        applyButton=primary("Apply",x,footer(),60,this::apply);applyButton.active=!waiting&&!denied&&dirty;
        UiHints.hint(danger("Reset defaults",x+68,footer(),96,this::resetDefaults),section.equals("discord")?"Reset this tab without changing the webhook, then choose Apply to save.":"Restore profiling, resource limits and backup defaults, then choose Apply to save.").active=!waiting&&!denied;
        action("Back",width-x-54,footer(),54,this::onClose);
    }
    private void apply() {
        try {
            var edit=new ServerSettingsForms.Edit(expected,desired(),values.getOrDefault("webhook",""));
            ClientPacketDistributor.sendToServer(new ServerSettingsRequest(section,Profiler.JSON.toJson(edit)));waiting=true;message="Applying…";
        }catch(NumberFormatException e){message="Enter valid numbers.";closeAfterApply=false;}
        catch(IllegalArgumentException e){message=e.getMessage();closeAfterApply=false;}
        rebuildWidgets();
    }
    private void accept(ServerSettingsForms.View view,String message,boolean saved) {
        waiting=false;this.message=message;
        if(saved){expected=view;load(view);if(closeAfterApply){minecraft.setScreen(parent);return;}}
        closeAfterApply=false;rebuildWidgets();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float a) {
        canvas(g,section.equals("discord")?"DISCORD NOTIFICATIONS":"ADVANCED");
        headerDivider(g);
        String status=message.isEmpty()&&dirty?"Unsaved changes":message;
        footerMessage(g,status,mx,my);
        super.extractRenderState(g,mx,my,a);
    }
    @Override public void onClose() {
        if(waiting)return;
        if(dirty&&!denied)minecraft.setScreen(new UnsavedScreen(this,()->minecraft.setScreen(parent),()->{minecraft.setScreen(this);closeAfterApply=true;apply();}));
        else minecraft.setScreen(parent);
    }
}
