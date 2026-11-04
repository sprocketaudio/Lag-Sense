package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class HistoryScreen extends LagScreen {
    private final Screen parent;
    private ProfileArchive.State state;
    private String message, usage = "", baselineNote = "";
    private Long baseline;
    private boolean enabled;
    private String countDraft, limitDraft;
    private EditBox count, limit;
    private ProfileArchive.Settings pendingDesired;
    private UiButton applyButton, historyToggle;
    private int fieldsY, toolsY, resultsTop, clearX, clearWidth;
    private boolean closeAfterApply;
    private boolean dirty(){if(!ClientAccess.admin())return false;try{return enabled!=state.settings().enabled()||Integer.parseInt(count==null?countDraft:count.getValue())!=state.settings().maxProfiles()||Integer.parseInt(limit==null?limitDraft:limit.getValue())!=state.settings().maxMiB();}catch(NumberFormatException e){return true;}}
    private void updateApply(){
        boolean waiting=pendingDesired!=null;
        if(applyButton!=null)applyButton.active=!waiting&&dirty();
        if(historyToggle!=null)historyToggle.active=!waiting;
        if(count!=null)count.active=!waiting;
        if(limit!=null)limit.active=!waiting;
    }
    private void edited(){message="";updateApply();}
    private void discardDraft(){enabled=state.settings().enabled();countDraft=""+state.settings().maxProfiles();limitDraft=""+state.settings().maxMiB();count=limit=null;message="";closeAfterApply=false;}
    private void apply(){
        if(pendingDesired!=null||!dirty())return;
        try{pendingDesired=new ProfileArchive.Settings(enabled,Integer.parseInt(count.getValue()),Integer.parseInt(limit.getValue()));
            ClientPacketDistributor.sendToServer(new ArchiveRequestPayload("configure",0,0,pendingDesired.enabled(),pendingDesired.maxProfiles(),pendingDesired.maxMiB()));message="Applying…";
        }catch(IllegalArgumentException e){pendingDesired=null;message="Count: 1–50. Disk limit: 8–256 MiB.";}
        updateApply();
    }
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    public HistoryScreen(Screen parent, ProfileArchive.State state, String message) {
        super("Profile history"); this.parent = parent; this.state = state; this.message = message;
        enabled = state.settings().enabled(); countDraft = "" + state.settings().maxProfiles(); limitDraft = "" + state.settings().maxMiB();
    }
    public void accept(ProfileArchive.State state,String message){
        boolean applied=pendingDesired!=null&&pendingDesired.equals(state.settings())&&message.equals("Settings applied.");
        this.state=state;this.message=message;pendingDesired=null;
        if(applied){enabled=state.settings().enabled();countDraft=""+state.settings().maxProfiles();limitDraft=""+state.settings().maxMiB();count=limit=null;}
        if(baseline!=null&&state.profiles().stream().noneMatch(p->p.id()==baseline))baseline=null;
        if(applied&&closeAfterApply)minecraft.setScreen(parent);else{closeAfterApply=false;rebuildWidgets();}
    }
    public static void request(String action, long first, long second) { ClientPacketDistributor.sendToServer(new ArchiveRequestPayload(action, first, second, false, 10, 32)); }
    void navigationFailed(String error){if("Loading…".equals(message)||"Comparing…".equals(message))message=error;}
    static String date(long millis) { return DATE.format(Instant.ofEpochMilli(millis)); }
    @Override protected void init() {
        if (count != null) { countDraft = count.getValue(); limitDraft = limit.getValue(); }
        int x = margin(), right = width - x;
        fieldsY=headerControlsTop()+14;toolsY=fieldsY+32;resultsTop=toolsY+28;
        if(ClientAccess.admin()){
        historyToggle=toggle("History", new UiMetrics.Rect(x, fieldsY, 82, 20), enabled, () -> { enabled = !enabled; message = ""; rebuildWidgets(); });
        count = input("Maximum profiles", x + 90, fieldsY, 62, countDraft, "[0-9]*", 3);
        limit = input("Disk limit in MiB", x + 160, fieldsY, 62, limitDraft, "[0-9]*", 3);
        count.setResponder(s -> edited()); limit.setResponder(s -> edited());
        applyButton=primary("Apply",x,footer(),60,this::apply);updateApply();
        }else{toolsY=headerControlsTop();resultsTop=toolsY+28;}
        int reloadWidth=Math.max(58,UiFonts.labelWidth("Reload")+12);clearWidth=Math.max(104,UiFonts.labelWidth("Clear baseline")+12);
        clearX=right-reloadWidth-8-clearWidth;
        action("Reload", right-reloadWidth, toolsY, reloadWidth, () -> request("list", 0, 0));
        var out=new UiResults(this,resultsTop,ClientAccess.admin()?134:54,true,false);
        for (var item:state.profiles()) {
            String title=date(item.capturedAt())+" | "+item.trigger();
            String detail=item.targets()+" targets | "+item.ticks()+" ticks";
            Runnable open=()->{message="Loading…";request("open",item.id(),0);};
            String choice=baseline==null?"Baseline":baseline==item.id()?"Selected":"Compare";
            var view=new UiResults.Action("View",open);
            if(!ClientAccess.admin())out.row(item.id(),title,detail,"",UiTheme.TEXT,0,false,title+"\n"+detail,open,view);
            else {
            out.row(item.id(),title,detail,String.format(Locale.ROOT,"%.2f MSPT",item.mspt()),UiTheme.TEXT,0,baseline!=null&&baseline==item.id(),
                "View this saved profile.\n"+title+"\n"+detail,open,new UiResults.Action("View",open),new UiResults.Action(choice,()->{
                    if(baseline==null){baseline=item.id();rebuildWidgets();}
                    else if(baseline==item.id()){baseline=null;rebuildWidgets();}
                    else{message="Comparing…";request("compare",baseline,item.id());}
                },baseline!=null&&baseline==item.id()));
            }
        }
        results(resultsTop,out.entries,true);
        if(ClientAccess.admin())UiHints.hint(action("Clear baseline", clearX, toolsY, clearWidth, () -> { baseline = null; rebuildWidgets(); }), "Clear the selected comparison baseline.").active = baseline != null;
        action("Back", right - 54, footer(), 54, this::onClose);
        usage = String.format(Locale.ROOT, "%d saves | %.1f MiB", state.profiles().size(), state.profiles().stream().mapToLong(ProfileArchive.Summary::bytes).sum() / 1048576.0);
        baselineNote = baseline == null ? "" : "Baseline: " + state.profiles().stream().filter(p -> p.id() == baseline).findFirst().map(p -> date(p.capturedAt())).orElse("");
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        canvas(g, "PROFILE HISTORY"); footerMessage(g,message.isEmpty()||message.equals("Saved profiles")?dirty()?"Unsaved changes":"":message,mx,my);
        headerDivider(g);
        if(ClientAccess.admin()){text(g, "Keep count", margin() + 90, fieldsY-14, 70, UiTheme.SECONDARY); text(g, "Limit MiB", margin() + 160, fieldsY-14, 62, UiTheme.SECONDARY);}
        text(g, "Saved profiles", margin(), toolsY+6, clearX-margin()-12, UiTheme.SECONDARY);
        if (state.profiles().isEmpty()) text(g, ClientAccess.admin()?(enabled ? "No saved profiles yet." : "History is off. No saved profiles."):"No permitted saved profiles.", margin(), resultsTop+8, contentWidth(), UiTheme.SECONDARY);
        text(g, ClientAccess.admin()?usage:state.profiles().size()+" saved profiles", margin(), height-76, contentWidth(), UiTheme.SECONDARY);
        if (!baselineNote.isEmpty()) UiHints.region(g, font, mx, my, clearX,toolsY,clearWidth,20, baselineNote);
        super.extractRenderState(g, mx, my, partial);
    }
    @Override public void onClose() {
        if(pendingDesired!=null)return;
        if(dirty())minecraft.setScreen(new UnsavedScreen(this,()->{discardDraft();minecraft.setScreen(parent);},()->{minecraft.setScreen(this);closeAfterApply=true;apply();}));
        else minecraft.setScreen(parent);
    }
    @Override public void removed(){
        if("Loading…".equals(message)||"Comparing…".equals(message))message="";
        super.removed();
    }
}
