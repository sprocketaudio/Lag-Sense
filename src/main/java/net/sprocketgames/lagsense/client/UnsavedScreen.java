package net.sprocketgames.lagsense.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class UnsavedScreen extends LagScreen {
    private final Screen editor;
    private final Runnable discard, apply;
    private UiButton keepEditing;
    public UnsavedScreen(Screen editor,Runnable discard,Runnable apply){super("Unsaved changes");this.editor=editor;this.discard=discard;this.apply=apply;}
    @Override protected int panelMargin(){return Math.max(8,(width-320)/2);}
    @Override protected int panelTop(){return (height-Math.min(128,height-24))/2;}
    @Override protected int panelBottom(){return height-panelTop();}
    @Override protected int footer(){return panelBottom()-32;}
    @Override protected void init(){
        primary("Apply",margin(),footer(),60,apply);
        danger("Discard",margin()+68,footer(),68,discard);
        keepEditing=action("Keep editing",width-margin()-92,footer(),92,this::onClose);
    }
    @Override protected void setInitialFocus(){setInitialFocus(keepEditing);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float a){canvas(g,"UNSAVED CHANGES");headerDivider(g);text(g,"Apply changes before leaving?",margin(),panelTop()+52,contentWidth(),UiTheme.TEXT);super.extractRenderState(g,mx,my,a);}
    @Override public void onClose(){minecraft.setScreen(editor);}
}
