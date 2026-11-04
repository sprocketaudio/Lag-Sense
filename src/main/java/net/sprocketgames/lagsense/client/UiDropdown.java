package net.sprocketgames.lagsense.client;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** Native button, focus and sound with a bounded, keyboard-accessible choice list. */
final class UiDropdown extends UiButton {
    record Choice(String id,String label){}
    private final LagScreen screen;
    private final String label,value;
    private final List<Choice> choices;
    private final Consumer<String> changed;
    UiDropdown(LagScreen screen,String label,int x,int y,int width,List<Choice> choices,String value,Consumer<String> changed){
        super(x,y,width,20,choices.stream().filter(c->c.id().equals(value)).findFirst().map(Choice::label).orElse(value)+" ▾",()->{});
        this.screen=screen;this.label=label;this.value=value;this.choices=List.copyOf(choices);this.changed=changed;active=!choices.isEmpty();
        UiHints.hint(this,label+": "+getMessage().getString());
    }
    @Override protected net.minecraft.network.chat.MutableComponent createNarrationMessage(){return net.minecraft.network.chat.Component.translatable("gui.narrate.button",net.minecraft.network.chat.Component.literal(label+": "+getMessage().getString()));}
    String label(){return label;}
    String value(){return value;}
    List<Choice> choices(){return choices;}
    void choose(String id){if(choices.stream().noneMatch(c->c.id().equals(id)))return;screen.closeDropdown();if(!value.equals(id)){
            changed.accept(id);var minecraft=net.minecraft.client.Minecraft.getInstance();
            if(minecraft!=null&&minecraft.screen instanceof LagScreen current)current.focusDropdown(label);
        }}
    @Override public void onPress(InputWithModifiers input){super.onPress(input);if(active)screen.toggleDropdown(this);}
    Menu menu(){return new Menu();}
    final class Menu {
        private final UiScrollList list;
        private final int x,y,w,h;
        private int selected;
        Menu(){
            w=Math.min(screen.width-16,Math.max(getWidth(),choices.stream().mapToInt(c->UiFonts.labelWidth(c.label())+20).max().orElse(80)));
            x=Math.clamp(getX(),8,screen.width-w-8);h=Math.min(Math.max(20,choices.size()*20),Math.max(20,screen.height-getY()-32));y=getY()+22;
            list=new UiScrollList(x,y,w,h,new net.sprocketgames.lagsense.ListScroll());
            var entries=new ArrayList<UiScrollList.Entry>();
            for(int i=0;i<choices.size();i++){var choice=choices.get(i);if(choice.id().equals(value))selected=i;entries.add(new UiScrollList.Entry(choice.id(),20,(target,top)->target.add(UiHints.hint(new UiButton(x,top,w-3,20,choice.label(),()->choose(choice.id())).selected(choice.id().equals(value)),choice.label()))));}
            list.virtual(entries,null);list.restore(Math.max(0,selected*20-h/2));
        }
        void render(GuiGraphicsExtractor g,int mx,int my,float a){g.nextStratum();g.fill(x-1,y-1,x+w+1,y+h+1,UiTheme.BORDER);list.extractRenderState(g,mx,my,a);}
        boolean click(MouseButtonEvent e,boolean twice){if(e.x()>=x&&e.x()<x+w&&e.y()>=y&&e.y()<y+h){list.mouseClicked(e,twice);return true;}screen.closeDropdown();return true;}
        boolean scroll(double mx,double my,double horizontal,double vertical){list.mouseScrolled(mx,my,horizontal,vertical);return true;}
        boolean key(KeyEvent event){
            switch(event.key()){
                case 256,258 -> screen.closeDropdown();
                case 264 -> selected=Math.min(choices.size()-1,selected+1);
                case 265 -> selected=Math.max(0,selected-1);
                case 268 -> selected=0;
                case 269 -> selected=choices.size()-1;
                case 257,335,32 -> {if(!choices.isEmpty())choose(choices.get(selected).id());}
                default -> {return true;}
            }
            if(screen.dropdownOpen()){list.restore(Math.max(0,selected*20-h/2));for(var child:list.children())if(child instanceof UiButton b)b.selected(b.getMessage().getString().equals(choices.get(selected).label()));}
            return true;
        }
    }
}
