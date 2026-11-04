package net.sprocketgames.lagsense.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;

/** A single control row, clipped and horizontally scrollable only when it does not fit. */
final class UiControlStrip extends AbstractContainerEventHandler implements Renderable,NarratableEntry {
    private record Item(AbstractWidget widget,int x){}
    private final int left,top,width,height;
    private final java.util.function.IntConsumer save;
    private final List<Item> items=new ArrayList<>();
    private final List<AbstractWidget> children=new ArrayList<>();
    private final UiButton previous,next;
    private int offset,maximum;
    UiControlStrip(int left,int top,int width,int height,int offset,java.util.function.IntConsumer save){
        this.left=left;this.top=top;this.width=Math.max(32,width);this.height=height;this.offset=offset;this.save=save;
        previous=new UiButton(left,top+height-20,20,20,"‹",()->move(-100));next=new UiButton(left+width-20,top+height-20,20,20,"›",()->move(100));
        UiHints.hint(previous,"Scroll controls left.");UiHints.hint(next,"Scroll controls right.");
    }
    <T extends AbstractWidget>T add(T widget){items.add(new Item(widget,widget.getX()));children.add(widget);return widget;}
    void finish(){int end=items.stream().mapToInt(i->i.widget().getRectangle().right()).max().orElse(left);maximum=Math.max(0,end-left-width);if(maximum>0)maximum+=44;position();}
    private int inset(){return maximum>0?22:0;}
    int offset(){return offset;}
    int maximum(){return maximum;}
    private void position(){offset=Math.clamp(offset,0,maximum);for(var i:items)i.widget().setX(i.x()+inset()-offset);previous.active=offset>0;next.active=offset<maximum;save.accept(offset);}
    void move(int amount){offset+=amount;position();}
    void reveal(AbstractWidget widget){for(var i:items)if(i.widget()==widget){var r=widget.getRectangle();if(r.left()<left+inset())offset-=left+inset()-r.left();else if(r.right()>left+width-inset())offset+=r.right()-(left+width-inset());position();return;}}
    @Override public List<? extends GuiEventListener> children(){if(maximum==0)return children;var result=new ArrayList<AbstractWidget>(children);result.add(previous);result.add(next);return result;}
    @Override public ScreenRectangle getRectangle(){return new ScreenRectangle(left,top,width,height);}
    @Override public boolean isMouseOver(double x,double y){return x>=left&&x<left+width&&y>=top&&y<top+height;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float a){
        g.enableScissor(left+inset(),top,left+width-inset(),top+height);boolean inside=isMouseOver(mx,my)&&mx>=left+inset()&&mx<left+width-inset();
        for(var i:items){var w=i.widget();if(w.getRectangle().right()>left+inset()&&w.getRectangle().left()<left+width-inset())w.extractRenderState(g,inside?mx:-10000,inside?my:-10000,a);}
        g.disableScissor();if(maximum>0){previous.extractRenderState(g,mx,my,a);next.extractRenderState(g,mx,my,a);}
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        if(!isMouseOver(e.x(),e.y()))return false;
        if(maximum>0&&(e.x()<left+22||e.x()>=left+width-22)){var arrow=e.x()<left+22?previous:next;return arrow.mouseClicked(e,twice);}
        return super.mouseClicked(e,twice);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(!isMouseOver(x,y)||maximum==0)return false;move((int)Math.round(-(horizontal!=0?horizontal:vertical)*40));return true;}
    @Override public void setFocused(GuiEventListener child){super.setFocused(child);if(child instanceof AbstractWidget widget)reveal(widget);}
    @Override public NarrationPriority narrationPriority(){return getFocused()==null?NarrationPriority.NONE:NarrationPriority.FOCUSED;}
    @Override public void updateNarration(NarrationElementOutput out){if(getFocused() instanceof NarratableEntry n)n.updateNarration(out);}
}
