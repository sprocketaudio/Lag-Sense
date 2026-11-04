package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.UiMetrics;

/** One centred panel and footer for every settings page, independent of its active tab. */
abstract class SettingsPage extends LagScreen {
    private static final int PANEL_HEIGHT=400;
    protected SettingsPage(String title){super(title);}
    @Override protected final int panelMargin(){return UiMetrics.settingsLeft(width);}
    private int panelHeight(){return Math.min(PANEL_HEIGHT,Math.max(1,height-24));}
    @Override protected final int panelTop(){return (height-panelHeight())/2;}
    @Override protected final int panelBottom(){return panelTop()+panelHeight();}
    @Override protected final int footer(){return panelBottom()-36;}
    protected final void formSection(String title,int y){control(new UiLabel(UiFonts.strong(),margin(),y,contentWidth(),title).separator());}
}
