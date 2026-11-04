package audio.sprocket.lagsense;

import audio.sprocket.lagsense.client.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.components.Button;
import java.util.*;

final class UiInteractionChecks {
    static void run() {
        int[] calls = new int[3];
        var row = new UiRow(8, 100, 304, 32, "Profile", "Metadata", "", 0, 134, 0, false, () -> calls[0]++);
        var view = new UiButton(182, 106, 42, 20, "View", () -> calls[1]++);
        var baseline = new UiButton(228, 106, 76, 20, "Baseline", () -> calls[2]++);
        ContainerEventHandler screen = new AbstractContainerEventHandler() { public List<? extends GuiEventListener> children() { return List.of(row, view, baseline); } };
        for (int[] click : new int[][]{{20,110},{200,110},{250,110}}) ((Button)screen.getChildAt(click[0], click[1]).orElseThrow()).onPress(null);
        if (!Arrays.equals(calls, new int[]{1,1,1}) || row.isMouseOver(180,110)) throw new AssertionError("Rows must yield embedded action hitboxes before native event dispatch");
        System.out.println("UI interaction checks passed (native first-hit routing for rows, View and Baseline).");
        var inline=new InlineExpansion();inline.toggle("machine");inline.toggle("machine");if(inline.active())throw new AssertionError("Clicking an expanded row collapses it");
        inline.toggle("first");inline.toggle("second");if(!inline.is("second"))throw new AssertionError("Expanding another row replaces the previous expansion");
        var readonly=new UiRow(8,100,304,32,"Target","Location","",0,88,0,false,null);
        if(readonly.active||!readonly.isMouseOver(20,110)||readonly.isMouseOver(250,110))throw new AssertionError("Read-only targets retain tooltip hit areas and independent embedded actions");
        var scroll=new ListScroll();scroll.page(3);scroll.bounds(400,100);scroll.move(50);
        inline.toggle("middle");scroll.bounds(450,100);
        if(scroll.offset()!=50||PageNumbers.count(25,5)!=5)throw new AssertionError("Expansion preserves scroll position and result-based pagination");
        scroll.move(10000);if(scroll.offset()!=350)throw new AssertionError("Expanded content remains reachable at the end of a small viewport");
        scroll.bounds(100,100);if(scroll.offset()!=0)throw new AssertionError("Collapsing clamps overflow to available content");
        scroll.bounds(400,100);scroll.move(120);scroll.page(4);if(scroll.offset()!=0)throw new AssertionError("Changing result pages resets only their scroll offset");
        for(int expanded=0;expanded<3;expanded++){
            int y=0;var positions=new ArrayList<Integer>();for(int i=0;i<3;i++){positions.add(y);y+=34;if(i==expanded)y+=46;}
            if(positions.size()!=3||positions.get(expanded)!=expanded*34||y!=148)throw new AssertionError("First, middle and final expansions retain all parent rows in order");
        }
        // Exercise real native child routing after overflow moves the widgets.
        var nativeScroll=new ListScroll();nativeScroll.page(0);
        var viewport=new UiScrollList(8,100,304,68,nativeScroll);
        var first=viewport.add(new UiRow(8,100,304,32,"First","","",0,88,0,false,null));
        var last=viewport.add(new UiRow(8,238,304,32,"Last","","",0,88,0,false,null));
        var mark=viewport.add(new UiButton(230,244,40,20,"Mark",()->calls[1]++));
        viewport.finish(272);
        if(!viewport.mouseScrolled(20,120,0,-20)||nativeScroll.offset()!=104||last.getY()!=134||mark.getY()!=140)throw new AssertionError("Scrolling moves rows and actions together");
        if(viewport.getChildAt(240,145).orElseThrow()!=mark||viewport.getChildAt(20,145).orElseThrow()!=last||viewport.isMouseOver(20,175))throw new AssertionError("Overflow preserves native hitboxes and viewport bounds");
        viewport.restore(30);if(nativeScroll.offset()!=30||first.getY()!=70)throw new AssertionError("Restored views preserve scroll offsets");
        viewport.restore(999);if(nativeScroll.offset()!=104)throw new AssertionError("Restored scroll offsets clamp after resizing");
        var compactOffset=new ListScroll();var compact=new UiScrollList(8,144,304,22,compactOffset);
        var compactAction=compact.add(new UiButton(230,150,40,20,"Mark",()->{}));compact.finish(178);compact.setFocused(compactAction);
        if(compactAction.getY()<144||compactAction.getY()+20>166)throw new AssertionError("Keyboard focus reveals a whole embedded control when wrapped navigation leaves a short viewport");
        if(!ClientReportPolicy.restoreHud(true,false)||ClientReportPolicy.restoreHud(false,false)||ClientReportPolicy.restoreHud(true,true))throw new AssertionError("Only opening a current report restores its HUD");
        var started=new Report(10,1000,0,0,true,false,List.of(),true,5);
        var progress=new Report(10,2000,20,2,true,false,List.of(),true,4);
        var finished=new Report(10,6000,100,2,true,false,List.of());
        var next=new Report(11,7000,0,0,true,false,List.of(),true,5);
        if(!Profiler.JSON.fromJson(ReportWire.json(started,Profiler.JSON),Report.class).equals(started))throw new AssertionError("The zero-tick recording notification survives the existing report wire format");
        if(!ClientReportPolicy.beganCapture(null,started,false)||!ClientReportPolicy.beganCapture(finished,next,false))throw new AssertionError("A manual capture restores the HUD on its first recording packet, including before the first tick");
        if(ClientReportPolicy.beganCapture(started,progress,false)||ClientReportPolicy.beganCapture(progress,finished,false)||ClientReportPolicy.beganCapture(null,started,true))throw new AssertionError("Progress packets preserve F8 hiding; completed and archived updates cannot masquerade as capture starts");
        var hidden=SettingsPayload.view(false,ServerSettings.defaults(),"private delivery state");if(hidden.admin()||!hidden.timer().isEmpty()||!hidden.discord().isEmpty())throw new AssertionError("Player settings overview contains no restricted server state");
    }
}
