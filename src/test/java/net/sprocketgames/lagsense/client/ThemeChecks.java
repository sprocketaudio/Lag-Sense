package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.neoforged.fml.loading.FMLPaths;

/** Actual client settings, native widgets and cached row/map presentation; no game window. */
final class ThemeChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static List<AbstractWidget> widgets(LagScreen screen){var result=new ArrayList<AbstractWidget>();for(var child:screen.children()){if(child instanceof AbstractWidget w)result.add(w);if(child instanceof UiScrollList list)for(var nested:list.children())if(nested instanceof AbstractWidget w)result.add(w);}return result;}
    private static UiButton button(LagScreen screen,String prefix){return widgets(screen).stream().filter(UiButton.class::isInstance).map(UiButton.class::cast).filter(b->b.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow();}
    static void run()throws Exception{
        var oldTheme=UiTheme.current();var oldSettings=LagSenseClient.settings;var oldReport=LagSenseClient.report;
        var pathField=FMLPaths.class.getDeclaredField("absolutePath");pathField.setAccessible(true);var oldPath=pathField.get(FMLPaths.CONFIGDIR);var directory=Files.createTempDirectory("lagsense-themes-");pathField.set(FMLPaths.CONFIGDIR,directory);
        try{
            defaults();persistence(directory);palettes();
            for(int scale:List.of(2,3,4))layout(scale);
            cachedRows();cachedMap();tooltipAssets();
        }finally{
            LagSenseClient.settings=oldSettings;LagSenseClient.report=oldReport;UiTheme.select(oldTheme);pathField.set(FMLPaths.CONFIGDIR,oldPath);
            Files.deleteIfExists(directory.resolve("lagsense-client.json"));Files.deleteIfExists(directory.resolve("lagsense-client.json.tmp"));Files.delete(directory);
        }
        System.out.println("Theme checks passed (exact legacy palette, three themes, client persistence/migration, live cached rows/map/tooltips, unchanged timing colours and native controls at GUI scales 2/3/4).");
    }
    private static void defaults(){
        UiTheme.select(UiTheme.Theme.CHARCOAL_ICE);
        var expected=new UiTheme.Palette(0xff17191c,0xff23262a,0xff303439,0xff444b52,0xffa3dce2,0xffe8ecef,0xffa5afb8,0xff4a5058,0xff35444b,0xff3e494f,0xff78828b,0xff292d31,0xff4a5963,0xff53332f,0xff74453b,0xff65717b,0xff252b30,0xff515e66,0xff46565e,0xff3a4147);
        check(UiTheme.current().palette().equals(expected),"Default palette must match the established colour values");
        check(UiTheme.HUD==0xe617191c&&UiTheme.LABEL==0xc017191c&&UiTheme.ON==0xff35444b,"Legacy HUD alpha, world-label backing and selected state stay identical");
        check(UiTheme.current().tooltip()==null&&UiFonts.tooltip("Tooltip").getStyle().getColor()==null,"Default tooltips retain the exact existing Minecraft skin and text treatment");
        check(SettingsMenu.Category.HUD.label.equals("Display")&&SettingsMenu.categories(true).size()==4&&SettingsMenu.categories(false).size()==1,"Renaming Display preserves category counts and player/admin visibility");
        check(Arrays.stream(UiTheme.Theme.values()).map(t->t.label).toList().equals(List.of("Charcoal + Ice","Midnight + Violet","Obsidian + Gold")),"Exactly the three requested themes, in the requested order");
    }
    private static void persistence(Path directory)throws Exception{
        check(OverlaySettings.load().theme==UiTheme.Theme.OBSIDIAN_GOLD,"Fresh installs default to Obsidian + Gold");
        var file=directory.resolve("lagsense-client.json");Files.writeString(file,"{\"enabled\":false,\"worldMarkers\":false,\"x\":123,\"y\":234,\"scale\":0.8,\"distance\":64,\"minimumMicros\":10}");
        var legacy=OverlaySettings.load();check(legacy.theme==UiTheme.Theme.OBSIDIAN_GOLD&&!legacy.enabled&&!legacy.worldMarkers&&legacy.x==123&&legacy.y==234&&legacy.distance==64,"Missing theme uses Gold while preserving all other saved display choices");
        for(var theme:UiTheme.Theme.values()){
            legacy.theme=theme;legacy.gpuTiming=false;legacy.save();UiTheme.select(theme.next());var loaded=OverlaySettings.load();
            check(loaded.theme==theme&&UiTheme.current()==theme&&loaded.x==123&&loaded.y==234&&loaded.scale==.8f&&loaded.minimumMicros==10&&!loaded.gpuTiming,"Reload restores the selected palette without changing other client preferences");
            check(Files.readString(file).contains(switch(theme){case CHARCOAL_ICE->"charcoal_ice";case MIDNIGHT_VIOLET->"midnight_violet";case OBSIDIAN_GOLD->"obsidian_gold";}),"Client JSON stores stable palette IDs");
        }
        for(String invalid:List.of("null","\"future_theme\"")){
            Files.writeString(file,"{\"theme\":"+invalid+",\"x\":123,\"distance\":64}");var loaded=OverlaySettings.load();check(loaded.theme==UiTheme.Theme.OBSIDIAN_GOLD&&loaded.x==123&&loaded.distance==64,"Missing/invalid theme safely falls back without discarding other preferences");
        }
        var settings=new OverlaySettings();settings.theme=UiTheme.Theme.OBSIDIAN_GOLD;settings.enabled=false;settings.x=321;settings.scale=.7f;settings.distance=192;settings.gpuTiming=false;
        settings.resetDisplay();settings.save();check(settings.theme==UiTheme.Theme.OBSIDIAN_GOLD&&UiTheme.current()==settings.theme&&settings.enabled&&settings.x==8&&settings.scale==1&&settings.distance==96&&!settings.gpuTiming,"Display reset restores theme and existing display defaults, preserving unrelated GPU preference");
    }
    private static void palettes(){
        for(var theme:UiTheme.Theme.values()){
            UiTheme.select(theme);var p=theme.palette();int revision=UiTheme.revision();UiTheme.select(theme);check(UiTheme.revision()==revision,"Unchanged palette does not invalidate caches repeatedly");
            check(UiTheme.BACKGROUND==p.background()&&UiTheme.PANEL==p.panel()&&UiTheme.ROW==p.row()&&UiTheme.ICE==p.accent()&&UiTheme.TEXT==p.text(),"Every theme binds all shared surface/text roles");
            for(var role:UiButton.Role.values())for(boolean on:List.of(false,true))for(boolean selected:List.of(false,true)){
                var normal=UiButton.surface(role,true,on,selected,false,false);var hover=UiButton.surface(role,true,on,selected,true,false);var pressed=UiButton.surface(role,true,on,selected,true,true);
                check(hover.topLeft()==p.accent()&&hover.bottomRight()==p.accent(),"Hover uses the selected theme accent for every button role");
                check(pressed.topLeft()==normal.bottomRight()&&pressed.bottomRight()==normal.topLeft(),"Theme changes preserve the reversed pressed bevel");
                check(normal==UiButton.surface(role,true,on,selected,false,false),"Theme-aware button drawing reuses immutable surfaces without frame allocations");
                check(UiButton.surface(role,false,on,selected,true,true).fill()==p.disabled(),"Disabled state stays independent of hover/press");
                check(contrast(p.text(),normal.fill())>=4.5&&contrast(p.text(),hover.fill())>=4.5,"Button labels remain readable in normal/selected/hover states");
            }
            check(contrast(p.text(),p.row())>=4.5&&contrast(p.secondary(),p.row())>=4.5,"Primary and secondary result text retain readable contrast");
            for(double cost:new double[]{0,10,100,1000,5000,10000}){
                int colour=TimingSeverity.color(cost);check(colour==LagSenseClient.color(cost),"Timing gradient is independent of the theme");
                check(contrast(colour,p.row())>=3&&contrast(colour,p.selected())>=2.45,"Timing indicators remain distinct on normal and selected result rows");
                check(colour!=p.accent(),"Gold/violet UI accents do not replace severity colours");
            }
            check(UiTheme.changeColor(1)==0xffffab40&&UiTheme.changeColor(-1)==0xff69f0ae&&UiTheme.changeColor(0)==p.text(),"Comparison signs keep diagnostic meaning while neutral text follows the theme");
            if(theme!=UiTheme.Theme.CHARCOAL_ICE)check(UiFonts.tooltip("Tooltip").getStyle().getColor().getValue()==(p.text()&0xffffff),"Tooltip text uses the theme's readable foreground");
        }
    }
    private static void layout(int scale)throws Exception{
        LagSenseClient.settings=new OverlaySettings();LagSenseClient.settings.theme=UiTheme.Theme.CHARCOAL_ICE;LagSenseClient.settings.x=93;LagSenseClient.settings.y=117;LagSenseClient.settings.scale=.7f;LagSenseClient.settings.minimumMicros=10;LagSenseClient.settings.gpuTiming=false;UiTheme.select(UiTheme.Theme.CHARCOAL_ICE);
        var screen=new OverlaySettingsScreen(null);screen.width=1920/scale;screen.height=1080/scale;screen.init();
        check(screen.getTitle().getString().equals("Display settings"),"Destination screen is renamed Display Settings");
        var heatmap=button(screen,"Chunk heatmap");var theme=button(screen,"Theme:");var world=button(screen,"World markers");var range=button(screen,"Range:");
        check(theme.getY()==heatmap.getY()&&theme.getX()==world.getX()&&theme.getX()==heatmap.getX()+heatmap.getWidth()+8&&theme.getWidth()==heatmap.getWidth(),"Theme occupies the exact empty right-column slot beside Chunk heatmap");
        check(range.getY()==heatmap.getY()+32&&button(screen,"Display").getY()==button(screen,"Position").getY(),"Existing control spacing and tabs are preserved");
        var bounds=theme.getRectangle();int panelTop=screen.panelTop(),panelBottom=screen.panelBottom();
        for(var expected:List.of(UiTheme.Theme.MIDNIGHT_VIOLET,UiTheme.Theme.OBSIDIAN_GOLD,UiTheme.Theme.CHARCOAL_ICE)){
            theme.onPress(null);check(LagSenseClient.settings.theme==expected&&UiTheme.current()==expected&&theme.getMessage().getString().equals("Theme: "+expected.label),"Actual button immediately cycles all three palettes and updates its label");
            check(theme.getRectangle().equals(bounds)&&screen.panelTop()==panelTop&&screen.panelBottom()==panelBottom,"Cycling does not resize or move the button/panel");
            check(UiFonts.labelWidth(theme.getMessage().getString())<=theme.getWidth()-10,"Full theme labels fit the existing button at GUI scales 2/3/4");
            check(OverlaySettings.load().theme==expected&&LagSenseClient.settings.x==93&&LagSenseClient.settings.y==117&&LagSenseClient.settings.scale==.7f&&LagSenseClient.settings.minimumMicros==10&&!LagSenseClient.settings.gpuTiming,"Actual control persists only the theme change, retaining all other preferences");
        }
        var position=new OverlaySettingsScreen(null);set(position,"position",true);position.width=screen.width;position.height=screen.height;position.init();check(widgets(position).stream().noneMatch(w->w.getMessage().getString().startsWith("Theme:")),"Theme selector appears only in Display; no extra control in Position");
        var menu=new SettingsScreen(null,null);menu.width=screen.width;menu.height=screen.height;menu.init();check(button(menu,"Display")!=null&&widgets(menu).stream().noneMatch(w->w.getMessage().getString().equals("Overlays / HUD")),"Existing Settings entry is renamed, without adding a category");
    }
    private static void cachedRows()throws Exception{
        UiTheme.select(UiTheme.Theme.CHARCOAL_ICE);var screen=new ChunkScreen(null,new Report(1,1,100,1,true,false,List.of()),false,"minecraft:overworld",0,0);screen.width=960;screen.height=540;
        var rows=new UiResults(screen,100,0,false);rows.row("cpu","Method","","50.0%",UiTheme.ICE,0,false,"Retained method",null);rows.row("timing","minecraft:hopper","","100.0 µs/t",TimingSeverity.color(100),0,false,"Retained measurement",null);
        var list=new UiScrollList(screen.margin(),100,screen.contentWidth(),24,new ListScroll());list.virtual(rows.entries,null);var cpu=(UiRow)list.children().getFirst();
        var hints=UiHints.class.getDeclaredField("widgets");hints.setAccessible(true);var hint=((Map<?,?>)hints.get(null)).get(cpu);var refresh=hint.getClass().getDeclaredMethod("refresh");refresh.setAccessible(true);
        for(var theme:UiTheme.Theme.values()){
            UiTheme.select(theme);check(cpu.nameColor()==UiTheme.TEXT&&cpu.measurementColor()==UiTheme.TEXT,"Already-created percentage rows follow neutral live theme colours");
            refresh.invoke(hint);check((int)field(hint,"revision")==UiTheme.revision(),"Already-created tooltip refreshes its theme component without replacing the row");
            list.restore(24);var timing=(UiRow)list.children().getFirst();check(timing.nameColor()==UiTheme.TEXT&&timing.measurementColor()==TimingSeverity.color(100),"Off-screen rows retain measured severity while adopting current text colours");
        }
    }
    private static void cachedMap()throws Exception{
        UiTheme.select(UiTheme.Theme.CHARCOAL_ICE);var report=new Report(1,1,100,1,true,false,List.of(new Report.Entry("Block","a:machine","minecraft:overworld",0,64,0,1000,1))).view(true,List.of());
        var screen=new HeatmapScreen(null,report,false);screen.width=960;screen.height=540;screen.init();var map=HeatmapScreen.class.getDeclaredMethod("map");map.setAccessible(true);var first=(HeatmapPlan)map.invoke(screen);var selected=field(screen,"selected");
        for(var theme:List.of(UiTheme.Theme.MIDNIGHT_VIOLET,UiTheme.Theme.OBSIDIAN_GOLD,UiTheme.Theme.CHARCOAL_ICE)){
            UiTheme.select(theme);var next=(HeatmapPlan)map.invoke(screen);check(next!=first&&map.invoke(screen)==next&&field(screen,"selected")==selected&&(int)field(screen,"cell")==18,"Cached map invalidates only its paint plan on theme change, preserving selection/zoom");
            var colours=new HashSet<Integer>();next.paint((l,t,r,b,c)->colours.add(c));check(colours.contains(UiTheme.PANEL)&&colours.contains(UiTheme.BACKGROUND)&&colours.contains(TimingSeverity.color(report.chunks().getFirst().micros(report.ticks()))),"Map backgrounds update while chunk severity remains unchanged");first=next;
        }
    }
    private static void tooltipAssets()throws Exception{
        for(var theme:List.of(UiTheme.Theme.MIDNIGHT_VIOLET,UiTheme.Theme.OBSIDIAN_GOLD))for(String layer:List.of("background","frame")){
            String path="assets/lagsense/textures/gui/sprites/tooltip/"+theme.tooltip().getPath()+"_"+layer+".png";
            try(var stream=ThemeChecks.class.getClassLoader().getResourceAsStream(path);var metadata=ThemeChecks.class.getClassLoader().getResourceAsStream(path+".mcmeta")){
                check(stream!=null&&metadata!=null,"Namespaced tooltip sprites/metadata are packaged for both new themes");
                try(var image=com.mojang.blaze3d.platform.NativeImage.read(stream)){
                    check(image.getWidth()==100&&image.getHeight()==100&&image.getPixel(0,0)==0,"Tooltip native nine-slice geometry preserves padding/transparency");
                    check(image.getPixel(9,9)==(layer.equals("background")?theme.palette().background():theme.palette().border()),"Tooltip backing/frame uses its matching palette");
                }
            }
        }
    }
    private static double luminance(int color){double result=0;for(int i=0;i<3;i++){double channel=((color>>(16-8*i))&255)/255.0;channel=channel<=.04045?channel/12.92:Math.pow((channel+.055)/1.055,2.4);result+=channel*(i==0?.2126:i==1?.7152:.0722);}return result;}
    private static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return(Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
}
