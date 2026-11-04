package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;

/** Shared right-aligned capture actions; left controls retain their natural widths and wrap. */
final class ProfileActions {
    static int mount(LagScreen screen,UiMetrics.Flow flow,Report report,Runnable collapse,boolean expanded){
        int cw=collapse==null?0:Math.max(78,UiFonts.labelWidth("Collapse All")+12),sw=UiFonts.labelWidth("Settings")+12,hw=report.admin()?UiFonts.labelWidth("History")+12:0;
        int total=cw+(cw>0?4:0)+sw+(hw>0?4+hw:0),right=screen.profileWidth()-screen.margin(),x=right-total,y=flow.bottom()-20;
        if(flow.cursorX()+12>x)y=flow.bottom()+4;
        if(collapse!=null){screen.action("Collapse All",x,y,cw,collapse).active=expanded;x+=cw+4;}
        screen.action("Settings",x,y,sw,LagSenseClient::openSettings);if(hw>0)screen.action("History",right-hw,y,hw,()->HistoryScreen.request("list",0,0));
        return Math.max(flow.bottom(),y+20);
    }
    static int collapse(LagScreen screen,UiMetrics.Flow flow,Runnable collapse,boolean expanded){int w=Math.max(78,UiFonts.labelWidth("Collapse All")+12),x=screen.profileWidth()-screen.margin()-w,y=flow.bottom()-20;if(flow.cursorX()+12>x)y=flow.bottom()+4;screen.action("Collapse All",x,y,w,collapse).active=expanded;return Math.max(flow.bottom(),y+20);}
    private ProfileActions(){}
}
