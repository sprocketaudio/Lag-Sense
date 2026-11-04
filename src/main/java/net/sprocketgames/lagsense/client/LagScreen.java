package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.UiMetrics;
import net.sprocketgames.lagsense.InlineExpansion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared page shell, native input controls and bounded virtual results. */
public abstract class LagScreen extends Screen {
    protected final InlineExpansion inline = new InlineExpansion();
    final ResultSelection selection=new ResultSelection();
    protected ProfileNavigation profileNavigation;
    int profileWidth(){return width;}
    net.minecraft.client.gui.Font profileFont(){return font;}
    private final net.sprocketgames.lagsense.ListScroll listScroll = new net.sprocketgames.lagsense.ListScroll();
    private UiScrollList buildingList, currentList;
    private UiControlStrip buildingControls;
    private final java.util.Map<Integer,Integer> controlOffsets=new java.util.HashMap<>();
    private UiDropdown dropdown;
    private UiDropdown.Menu dropdownMenu;
    void toggleDropdown(UiDropdown value){if(dropdown==value){closeDropdown();return;}dropdown=value;dropdownMenu=value.menu();}
    void closeDropdown(){dropdown=null;dropdownMenu=null;}
    void focusDropdown(String label){var path=dropdownPath(this,label);if(path!=null)changeFocus(path);}
    private static net.minecraft.client.gui.ComponentPath dropdownPath(net.minecraft.client.gui.components.events.ContainerEventHandler parent,String label){
        for(var child:parent.children()){
            if(child instanceof UiDropdown d&&d.label().equals(label))return net.minecraft.client.gui.ComponentPath.path(parent,net.minecraft.client.gui.ComponentPath.leaf(d));
            if(child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler nested){var path=dropdownPath(nested,label);if(path!=null)return net.minecraft.client.gui.ComponentPath.path(parent,path);}
        }return null;
    }
    boolean dropdownOpen(){return dropdownMenu!=null;}
    void collapseAvailable(boolean value){for(var child:children())if(child instanceof UiButton b&&b.getMessage().getString().equals("Collapse All"))b.active=value;}
    UiControlStrip beginControls(int y,int right){return beginControls(y,right,20);}
    UiControlStrip beginControls(int y,int right,int height){closeDropdown();buildingControls=addRenderableWidget(new UiControlStrip(margin(),y,right-margin(),height,controlOffsets.getOrDefault(y,0),v->controlOffsets.put(y,v)));return buildingControls;}
    void endControls(){buildingControls.finish();buildingControls=null;}
    void revealControl(AbstractWidget widget){if(buildingControls!=null)buildingControls.reveal(widget);}
    UiDropdown dropdown(String label, net.sprocketgames.lagsense.UiMetrics.Rect rect, java.util.List<UiDropdown.Choice> choices, String value, java.util.function.Consumer<String> changed){return control(new UiDropdown(this,label,rect.x(),rect.y(),rect.width(),choices,value,changed));}
    private int listTop;
    private int canvasWidth = -1, canvasHeight, canvasMargin, canvasLeft, canvasRight;
    protected boolean liveHudPositioning() { return false; }
    protected void extractOverlay(GuiGraphicsExtractor g, int mx, int my, float partial) {}
    private Integer restoreOffset;
    protected int listOffset() { return listScroll.offset(); }
    protected void restoreListOffset(int offset) { restoreOffset = offset; }
    protected final <T extends AbstractWidget> T control(T widget) { return buildingControls!=null?buildingControls.add(widget):buildingList == null ? addRenderableWidget(widget) : buildingList.add(widget); }
    final void rebuildResults() { rebuildWidgets(); }
    protected void clearResults(){if(currentList!=null)removeWidget(currentList);currentList=null;buildingList=null;}
    protected void copyButton(Runnable copy){selection.bind(action("Copy",margin(),footer(),44,copy));}
    protected void resetResults() { restoreListOffset(0); }
    protected boolean resultVisible(Object key) { return currentList != null && currentList.visible(key); }
    protected void replaceResults(java.util.List<UiScrollList.Entry> entries) { if(currentList!=null){currentList.virtual(entries,null);if(restoreOffset!=null){currentList.restore(restoreOffset);restoreOffset=null;}} }
    protected void results(int top, java.util.List<UiScrollList.Entry> entries, boolean footnote) {
        var anchor = restoreOffset == null && currentList != null ? currentList.anchor() : null;
        listScroll.page(0);
        currentList = new UiScrollList(margin(), top, contentWidth(), Math.max(1, footer() - (footnote ? 32 : 12) - top), listScroll);
        addRenderableWidget(currentList); currentList.virtual(entries, anchor);
        if (restoreOffset != null) { currentList.restore(restoreOffset); restoreOffset = null; }
    }
    protected void beginList(int top, int page) {
        listTop = top; listScroll.page(page);
        buildingList = new UiScrollList(margin(), top, contentWidth(), Math.max(1, footer() - 20 - top), listScroll);
        currentList = buildingList; addRenderableWidget(buildingList);
    }
    protected void endList(int bottom) { buildingList.finish(bottom); if (restoreOffset != null) { buildingList.restore(restoreOffset); restoreOffset = null; } buildingList = null; }
    protected LagScreen(String title) { super(net.minecraft.client.Minecraft.getInstance(), UiFonts.body(), Component.literal(title)); }
    protected int panelMargin() { return UiMetrics.left(width); }
    protected int panelTop() { return 12; }
    protected int panelBottom() { return height - 12; }
    protected int margin() { return panelMargin() + 8; }
    final void reloadTypography() { rebuildWidgets(); }
    protected int contentWidth() { return width - margin() * 2; }
    protected int footer() { return UiMetrics.footer(height); }
    protected int headerControlsTop() { return panelTop() + UiMetrics.PROFILE_TABS - 12; }
    protected void headerDivider(GuiGraphicsExtractor g) {
        int y = panelTop() + UiMetrics.PROFILE_DIVIDER - 12;
        g.fill(margin(), y, width - margin(), y + 1, UiTheme.BORDER);
    }
    record FooterStatus(int x,int y,int width) {}
    FooterStatus footerStatus(String value) {
        int left=margin(),right=width-margin();
        for(var child:children())if(child instanceof UiButton button&&button.getY()==footer()) {
            if(button.getX()<width/2)left=Math.max(left,button.getX()+button.getWidth()+8);
            else right=Math.min(right,button.getX()-8);
        }
        int room=Math.max(0,right-left);
        return UiFonts.width(font,value)<=room?new FooterStatus(left,footer()+6,room):new FooterStatus(margin(),footer()-14,contentWidth());
    }
    protected void footerMessage(GuiGraphicsExtractor g,String value,int mx,int my) {
        if(value==null||value.isBlank())return;
        var bounds=footerStatus(value);var layout=UiFonts.layout(font,value,bounds.width());
        g.text(font,layout.text(),bounds.x()+(bounds.width()-layout.width())/2,bounds.y(),UiTheme.SECONDARY,false);
        UiHints.region(g,font,mx,my,bounds.x(),bounds.y()-2,bounds.width(),12,value);
    }
    protected void canvas(GuiGraphicsExtractor g, String title) {
        UiHints.begin(this);
        int currentMargin = panelMargin();
        if (canvasWidth != width || canvasHeight != height || canvasMargin != currentMargin) {
            canvasWidth = width; canvasHeight = height; canvasMargin = currentMargin;
            canvasLeft = Math.max(0, currentMargin - 4); canvasRight = width - canvasLeft;
        }
        int top = panelTop(), bottom = panelBottom();
        g.fill(canvasLeft, top, canvasRight, bottom, UiTheme.BACKGROUND);
        UiTheme.border(g, canvasLeft, top, canvasRight - canvasLeft, bottom - top, UiTheme.BORDER);
        if(title.equals("SAVED PROFILE")) {
            var heading=UiFonts.heading();String prefix="LAGSENSE / ";int prefixWidth=UiFonts.width(heading,prefix);
            UiFonts.draw(g,heading,prefix,margin(),top+12,contentWidth(),UiTheme.ICE,false);
            UiFonts.draw(g,heading,title,margin()+prefixWidth,top+12,Math.max(0,contentWidth()-prefixWidth),UiTheme.titleColor(title),false);
        } else UiFonts.draw(g, UiFonts.heading(), "LAGSENSE / " + title, margin(), top + 12, contentWidth(), UiTheme.ICE, false);
    }
    protected void text(GuiGraphicsExtractor g, String value, int x, int y, int w, int color) { UiFonts.draw(g, font, value, x, y == 30 ? panelTop() + 22 : y, w, color, false); }
    protected UiButton action(String label, int x, int y, int w, Runnable run) { return control(UiHints.control(new UiButton(x, y, Math.max(w, UiFonts.labelWidth(label) + 12), 20, label, run), label)); }
    protected UiMetrics.Rect slot(UiMetrics.Flow flow, String label, int min) { return flow.next(Math.max(min, UiFonts.labelWidth(label) + 12)); }
    protected UiButton action(String label, UiMetrics.Rect r, Runnable run) { return action(label, r.x(), r.y(), r.width(), run); }
    protected UiButton tab(String label, UiMetrics.Rect r, boolean selected, Runnable run) { return action(label, r, run).role(UiButton.Role.TAB).selected(selected); }
    protected UiButton toggle(String label, UiMetrics.Rect r, boolean on, Runnable run) { return action(label + " " + (on ? "ON" : "OFF"), r, run).role(UiButton.Role.TOGGLE).on(on); }
    protected UiButton primary(String label, int x, int y, int w, Runnable run) { return action(label, x, y, w, run).role(UiButton.Role.PRIMARY); }
    protected UiButton danger(String label, int x, int y, int w, Runnable run) { return action(label, x, y, w, run).role(UiButton.Role.DESTRUCTIVE); }
    protected UiInput input(String label, int x, int y, int w, String value, String filter, int length) {
        var field = new UiInput(filter.equals("[0-9]*") || filter.equals("[0-9.]*") ? UiFonts.mono() : font, x, y, w, label); field.setMaxLength(length); field.setFilter(s -> s.matches(filter)); field.setValue(value); UiHints.hint(field, UiHints.field(label)); return control(field);
    }
    protected UiRow row(int y, String primary, String secondary, String trailing, int color, int reserved, int indent, boolean selected, String hint, Runnable run) {
        return control(UiHints.hint(new UiRow(margin(), y, contentWidth(), UiMetrics.ROW - 2, primary, secondary, trailing, color, reserved, indent, selected, run), hint));
    }
    protected int details(int y, java.util.List<String> lines) {
        if (buildingList == null) throw new IllegalStateException("Details require a scrollable list");
        var panel = buildingList.add(new UiDetails(font, margin(), y, contentWidth(), lines, listTop, height - 80));
        return y + panel.getHeight();
    }
    protected void section(GuiGraphicsExtractor g, String title, int y) {
        UiFonts.draw(g, UiFonts.strong(), title, margin(), y, contentWidth(), UiTheme.SECONDARY, false); int start = margin() + UiFonts.width(UiFonts.strong(), title) + 8;
        if (start < width - margin()) g.fill(start, y + 4, width - margin(), y + 5, UiTheme.BORDER);
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if(dropdownMenu!=null)return dropdownMenu.key(event);
        if(currentList!=null && (event.key()==266 || event.key()==267) && currentList.keyPressed(event))return true;
        return super.keyPressed(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if(dropdownMenu!=null)return dropdownMenu.scroll(x,y,horizontal,vertical);
        if (currentList != null && currentList.mouseScrolled(x,y,horizontal,vertical)) return true;
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean twice){if(dropdownMenu!=null)return dropdownMenu.click(event,twice);return super.mouseClicked(event,twice);}
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) { super.extractRenderState(g, mx, my, partial); extractOverlay(g, mx, my, partial); if(dropdownMenu!=null)dropdownMenu.render(g,mx,my,partial);UiHints.end(g, font, mx, my); }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        if (liveHudPositioning()) {
            if (minecraft.level == null) extractPanorama(g, partial);
            g.fill(0, 0, width, height, 0x20000000);
        } else if (minecraft.level == null) {
            super.extractBackground(g, mx, my, partial); return;
        } else {
            extractBlurredBackground(g);
            g.fill(0, 0, width, height, 0x20000000);
        }
        minecraft.gui.extractDeferredSubtitles();
    }
    @Override public void removed() { closeDropdown();selection.clear();UiHints.clear(this); super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
}
