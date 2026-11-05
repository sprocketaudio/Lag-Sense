package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.input.KeyEvent;

/** Layout and native input checks without starting Minecraft or a graphics context. */
final class ProfileLayoutChecks {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static Object field(Object value,String name)throws Exception{for(var c=value.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(value);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    static void set(Object value,String name,Object data)throws Exception{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);f.set(value,data);}
    static List<AbstractWidget> widgets(ContainerEventHandler screen){var result=new ArrayList<AbstractWidget>();for(var child:screen.children()){if(child instanceof AbstractWidget w)result.add(w);if(child instanceof ContainerEventHandler nested)result.addAll(widgets(nested));}return result;}
    static UiDropdown dropdown(LagScreen screen,String name){return widgets(screen).stream().filter(UiDropdown.class::isInstance).map(UiDropdown.class::cast).filter(d->d.label().equals(name)).findFirst().orElseThrow(()->new AssertionError("Missing dropdown "+name));}
    private static CpuInvestigation cpu(String name){var builder=new CpuInvestigation.Builder(name);builder.add(List.of(new CpuInvestigation.Frame("fixture","fixture.Worker","run","()V"),new CpuInvestigation.Frame("minecraft","net.minecraft.Server","tick","()V")));return builder.finish();}
    static void run(Report source)throws Exception{
        var saved=LagSenseClient.settings;var theme=UiTheme.current();
        try{
            LagSenseClient.settings=new OverlaySettings();
            var server=cpu("Server thread");var a=cpu("worker (#2)");var b=cpu("worker (#3)");
            var bg=new BackgroundCpu("ready",List.of(new BackgroundCpu.Worker(2,"worker",List.of(new Report.Task("fixture","fixture.Worker#run",1,2,"worker")),a),new BackgroundCpu.Worker(3,"worker",List.of(new Report.Task("fixture","fixture.Worker#run",1,3,"worker")),b)),2,0,false,"");
            var report=new Report(source.id(),source.capturedAt(),source.ticks(),source.mspt(),true,false,source.entries(),false,0,source.chunks(),20,new Report.Diagnostics("Manual",0,0,List.of(new Report.Task("fixture","fixture.Worker#run",1)),List.of(),List.of(new Report.Callback("fixture","test.Event","fixture.Worker#tick",1000,1,1000)),"ready",server,null,bg));
            String original=Profiler.JSON.toJson(report);
            for(var selectedTheme:UiTheme.Theme.values())for(int width:List.of(960,640,480,426,320)){
                UiTheme.select(selectedTheme);int height=Math.max(240,width*9/16);
                var targets=new ReportScreen(null,report,true);targets.width=width;targets.height=height;targets.init();var nav=targets.profileNavigation;
                var footerButtons=List.of("Copy","Export","Unmark","Return").stream().map(label->widgets(targets).stream().filter(w->w.getMessage().getString().equals(label)).findFirst().orElseThrow()).toList();
                for(int i=1;i<footerButtons.size();i++){var before=footerButtons.get(i-1);var after=footerButtons.get(i);check(after.getX()-before.getX()-before.getWidth()==4&&after.getY()==before.getY(),"Left footer buttons keep the same four-pixel gap with native label widths");}

                layout(targets,"Targets");check(dropdown(targets,"Category").choices().stream().map(UiDropdown.Choice::id).toList().equals(List.of("All","Entity","Block","Ticks")),"Targets retains all original category filters");
                check(dropdown(targets,"Group").choices().size()==5,"Both mod and type grouping combinations remain available");
                var input=widgets(targets).stream().filter(UiInput.class::isInstance).map(UiInput.class::cast).findFirst().orElseThrow();
                var strip=targets.children().stream().filter(UiControlStrip.class::isInstance).toList().getLast();input.setValue("b:machine");check(targets.children().contains(strip)&&input.getValue().equals("b:machine"),"Searching replaces only results and keeps the input/control row alive");
                check(((List<?>)field(targets,"visible")).size()==1,"Target search filters visible results");
                targets.selectView("Chunks");check(targets.targetCategory().equals("All"),"Chunks remembers the last Targets category");
                var chunks=new ReportScreen(null,report,true);chunks.width=width;chunks.height=height;chunks.selectView("Chunks");chunks.init();layout(chunks,"Chunks");check(widgets(chunks).stream().anyMatch(w->w instanceof UiDropdown d&&d.label().equals("Category")),"Chunks shares the Targets category control");
                var tasks=new TaskScreen(targets,report,true);tasks.width=width;tasks.height=height;tasks.profileNavigation=nav;set(tasks,"callbackView",false);tasks.init();layout(tasks,"CPU Activity");
                tasks.focusDropdown("Threads");check(dropdown(tasks,"Threads").isFocused(),"Dropdown focus is restored inside its horizontal strip");
                check(dropdown(tasks,"Threads").choices().stream().map(UiDropdown.Choice::id).toList().equals(List.of("server","background")),"CPU scopes remain separate");
                var events=new TaskScreen(targets,report,true);events.width=width;events.height=height;events.profileNavigation=nav;events.init();layout(events,"Event Timings");check(widgets(events).stream().noneMatch(w->w instanceof UiDropdown d&&d.label().equals("Threads")),"Events hides irrelevant thread controls");
                var paths=new CallTreeScreen(tasks,server,null);paths.width=width;paths.height=height;paths.profileNavigation=nav;paths.init();layout(paths,"Call Paths");
                check(dropdown(paths,"Threads").choices().stream().map(UiDropdown.Choice::id).toList().equals(List.of("0","2","3")),"Call paths offers the server and individually identified workers");
                check(dropdown(paths,"Group").choices().stream().map(UiDropdown.Choice::id).toList().equals(List.of("hierarchy","running")),"Call path modes use a single Group dropdown");
                paths.runningMode(true);var running=new CallTreeScreen(tasks,server,null);running.width=width;running.height=height;running.profileNavigation=nav;running.runningMode(true);running.init();layout(running,"Call Paths");check(dropdown(running,"Sort").choices().size()==2,"Running Code keeps relevant sorting");
                for(boolean runningMode:List.of(false,true)){
                    var pending=new CallTreeScreen(tasks,CpuInvestigation.empty("pending"),null);pending.width=width;pending.height=height;pending.profileNavigation=nav;pending.runningMode(runningMode);pending.init();
                    check(widgets(pending).stream().noneMatch(w->w.getMessage().getString().equals("Reload")),"Neither pending call-path mode introduces an unrequested Reload control");
                    if(width>=640){var controls=pending.children().stream().filter(UiControlStrip.class::isInstance).map(UiControlStrip.class::cast).toList().getLast();check(controls.maximum()==0,"The complete pending call-path control row fits normal GUI scales without horizontal scrolling");}
                }
                var map=new HeatmapScreen(targets,report,true);map.width=width;map.height=height;map.profileNavigation=nav;map.init();layout(map,"Map");check((int)field(map,"mapTop")==120,"Map begins below the same two rows");
                int sharedSearch=widgets(targets).stream().filter(UiInput.class::isInstance).findFirst().orElseThrow().getRectangle().width();
                for(var page:List.of(chunks,tasks,events,paths,running,map))check(widgets(page).stream().filter(UiInput.class::isInstance).findFirst().orElseThrow().getRectangle().width()==sharedSearch,"Search has identical width across every tab and both call-path modes");
                if(width>=640)check(sharedSearch>84,"Normal-width Search remains wider than the original field");
                var mapSearch=widgets(map).stream().filter(UiInput.class::isInstance).map(UiInput.class::cast).findFirst().orElseThrow();mapSearch.setValue("b");check((boolean)field(map,"selectingMod"),"Map retains captured-mod search within row 2");mapSearch.setValue("");check(!(boolean)field(map,"selectingMod")&&map.children().stream().noneMatch(UiScrollList.class::isInstance),"Clearing mod search restores only the original map, with no duplicate result list");
            }
            check(Profiler.JSON.toJson(report).equals(original),"Layout/search never mutate captured measurements or reports");
            unrestrictedResults();switchViews();menus();
            var privateView=new ReportScreen(null,report.view(false,e->true),true);privateView.width=640;privateView.height=360;privateView.init();var labels=widgets(privateView).stream().filter(UiButton.class::isInstance).map(w->w.getMessage().getString()).toList();check(labels.contains("History")&&!labels.contains("CPU Activity")&&!labels.contains("Call Paths")&&!labels.contains("Event Timings"),"Scoped profiler keeps History accessible and admin-only views hidden");
        }finally{LagSenseClient.settings=saved;UiTheme.select(theme);}
        System.out.println("Profile navigation/layout checks passed at logical widths 320/426/480/640/960 and all themes: two rows, six tabs, pinned actions, dropdowns, search focus, worker IDs, menu keyboard input and unchanged report data.");
    }
    private static void layout(LagScreen screen,String active)throws Exception{
        var strips=screen.children().stream().filter(UiControlStrip.class::isInstance).map(UiControlStrip.class::cast).toList();check(strips.size()==2,"Exactly two control rows on "+active+" at "+screen.width);
        check(strips.get(0).getRectangle().top()==56&&strips.get(1).getRectangle().top()==80,"Header and row positions remain fixed");
        check(strips.get(0).getRectangle().height()==20&&strips.get(1).getRectangle().height()==32,"Row 2 reserves space for labels without adding a control row");
        var searches=widgets(strips.get(1)).stream().filter(UiInput.class::isInstance).toList();
        check(searches.size()==1&&searches.getFirst().getRectangle().width()>=84&&searches.getFirst().getRectangle().width()<=168,"Search stays within readable compact and wide bounds");
        if(screen.width>=640)check(strips.get(1).maximum()==0,"All normal controls fit without horizontal scrolling on "+active+" at "+screen.width);
        var captions=widgets(strips.get(1)).stream().filter(UiLabel.class::isInstance).toList();
        for(var widget:widgets(strips.get(1)))if(widget instanceof UiDropdown d){
            check(!d.getMessage().getString().startsWith(d.label()+":"),"Dropdown buttons display values without repeating their labels");
            check(captions.stream().anyMatch(c->c.getMessage().getString().equals(d.label())&&c.getX()==d.getX()&&c.getY()+12==d.getY()),"Each dropdown has an aligned label above it");
            check(d.getY()+d.getHeight()<=strips.get(1).getRectangle().bottom(),"Labeled controls fit inside the clipped row");
        }
        var nav=widgets(strips.getFirst()).stream().filter(UiButton.class::isInstance).map(UiButton.class::cast).filter(b->!List.of("‹","›").contains(b.getMessage().getString())).toList();
        check(nav.stream().map(b->b.getMessage().getString()).toList().equals(List.of("Targets","Chunks","Map","CPU Activity","Call Paths","Event Timings")),"Only the six requested top-level tabs");
        for(var button:nav)check((boolean)field(button,"selected")==button.getMessage().getString().equals(active),"Selected state matches the current view");
        var pinned=screen.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast).toList();
        var settings=pinned.stream().filter(b->b.getMessage().getString().equals("Settings")).findFirst().orElseThrow();var history=pinned.stream().filter(b->b.getMessage().getString().equals("History")).findFirst().orElseThrow();
        check(settings.getX()+settings.getWidth()==screen.width-screen.margin()&&settings.getY()==56&&history.getY()==56&&history.getX()+history.getWidth()+4==settings.getX(),"History/Settings stay right-aligned on row 1");
        var collapse=pinned.stream().filter(b->b.getMessage().getString().equals("Collapse All")).findFirst();if(!active.equals("Map"))check(collapse.isPresent()&&collapse.get().getY()==92&&collapse.get().getX()+collapse.get().getWidth()==screen.width-screen.margin(),"Collapse All stays right-aligned on row 2");
        check(strips.get(0).getRectangle().right()<history.getX(),"Nav clipping cannot cover pinned actions");
        if(collapse.isPresent())check(strips.get(1).getRectangle().right()<collapse.get().getX(),"Context clipping cannot cover Collapse All");
        for(var strip:strips){int offset=strip.offset();if(strip.maximum()>0){strip.mouseScrolled(strip.getRectangle().left()+30,strip.getRectangle().top()+8,0,-50);check(strip.offset()>0,"Narrow control row scrolls instead of wrapping");strip.move(-Integer.MAX_VALUE);check(strip.offset()==0,"Controls can be scrolled back to the start");strip.move(offset);}}
    }
    private static void switchViews()throws Exception{
        var entries=new ArrayList<Report.Entry>();for(int i=0;i<240;i++)entries.add(new Report.Entry("Block","fixture:machine","minecraft:overworld",i%4,60+i/4,1,240000-i,20));
        var report=new Report(992,1000,20,3,true,false,entries,false,0,Report.aggregate(entries),20);
        check(report.chunks().size()==1,"Regression fixture has many more target indices than chunk indices");
        var clear=net.minecraft.client.gui.screens.Screen.class.getDeclaredMethod("clearWidgets");clear.setAccessible(true);
        for(boolean type:List.of(false,true))for(boolean mod:List.of(false,true)){
            var screen=new ReportScreen(null,report,false);screen.width=854;screen.height=480;set(screen,"byType",type);set(screen,"byMod",mod);screen.init();
            for(int repeat=0;repeat<3;repeat++)for(String view:List.of("Chunks","All","Entity","Chunks","Block")){
                screen.selectView(view);clear.invoke(screen);screen.init();
                if(view.equals("Chunks"))check(((List<?>)field(screen,"visible")).size()==1,"Switching from populated, grouped or empty target views rebuilds chunk rows before controls inspect their indices");
                var labels=widgets(screen).stream().map(w->w.getMessage().getString()).toList();check(labels.containsAll(List.of("Close","Copy","Export","Collapse All")),"View switching finishes mounting controls and footer actions");
                check(widgets(screen).stream().anyMatch(UiInput.class::isInstance),"View switching keeps the wider search control");
            }
        }
    }
    private static void unrestrictedResults()throws Exception{
        var entries=List.of(new Report.Entry("Entity","test:far_mob","minecraft:overworld",50000,70,-50000,100000,10),new Report.Entry("Block","test:nether_machine","minecraft:the_nether",-30000,20,30000,200000,10),new Report.Entry("Random block","test:far_crop","minecraft:overworld",8000,-16,-8000,50000,10));
        var report=new Report(991,1000,10,1,true,false,entries);
        for(boolean type:List.of(false,true))for(boolean mod:List.of(false,true)){
            var screen=new ReportScreen(null,report,true);screen.width=640;screen.height=360;set(screen,"byType",type);set(screen,"byMod",mod);screen.init();
            check(widgets(screen).stream().noneMatch(w->w.getMessage().getString().startsWith("Near ")),"Results have no Near control");
            if(mod){var groups=(List<ProfileResults.ModGroup>)field(screen,"visibleMods");check(groups.stream().mapToLong(ProfileResults.ModGroup::instances).sum()==entries.size(),"Mod grouping includes distant targets and other dimensions");}
            else if(type){var groups=(List<ProfileResults.Group>)field(screen,"visibleGroups");check(groups.stream().mapToInt(g->g.targets().size()).sum()==entries.size(),"Type grouping includes distant targets and other dimensions");}
            else check(((List<?>)field(screen,"visible")).size()==entries.size(),"Individual results do not depend on the viewer's location or dimension");
        }
        var chunks=new ReportScreen(null,report,true);chunks.width=640;chunks.height=360;chunks.selectView("Chunks");chunks.init();check(((List<?>)field(chunks,"visible")).size()==report.chunks().size(),"Every recorded chunk remains accessible regardless of distance");
        var filtered=new ReportScreen(null,report,true);filtered.width=640;filtered.height=360;set(filtered,"dimension","minecraft:the_nether");filtered.init();check(((List<?>)field(filtered,"visible")).size()==1,"The explicit Dimension filter still works");
    }
    private static void menus()throws Exception{
        var screen=new LagScreen("Control fixture"){};screen.width=426;screen.height=240;var choices=new ArrayList<UiDropdown.Choice>();for(int i=0;i<500;i++)choices.add(ProfileControls.choice("id"+i,"Worker pool with a long name (#"+i+")"));String[] selected={""};
        var dropdown=new UiDropdown(screen,"Threads",300,80,100,choices,"id1",v->selected[0]=v);screen.toggleDropdown(dropdown);check(screen.dropdownOpen(),"Native dropdown opens without another screen");screen.keyPressed(new KeyEvent(269,0,0));screen.keyPressed(new KeyEvent(257,0,0));check(selected[0].equals("id499")&&!screen.dropdownOpen(),"End/Enter can select the last of 500 options without a top-N cap");
        screen.toggleDropdown(dropdown);screen.keyPressed(new KeyEvent(256,0,0));check(!screen.dropdownOpen()&&selected[0].equals("id499"),"Escape closes without changing the selection or leaving the profile");
    }
}
