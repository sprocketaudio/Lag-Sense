package net.sprocketgames.lagsense.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Field labels participate in list clipping, without intercepting input. */
final class UiLabel extends AbstractWidget {
    private final Font font;
    private final String label;
    private boolean separator;
    UiLabel(Font font,int x,int y,int width,String label) {
        super(x,y,width,10,Component.literal(label));this.font=font;this.label=label;active=false;
    }
    UiLabel separator(){separator=true;return this;}
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float a){
        UiFonts.draw(g,font,label,getX(),getY(),width,UiTheme.SECONDARY,false);
        int start=getX()+UiFonts.width(font,label)+8;
        if(separator&&start<getX()+width)g.fill(start,getY()+4,getX()+width,getY()+5,UiTheme.BORDER);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output){}
    @Override public boolean isMouseOver(double x,double y){return false;}
}
