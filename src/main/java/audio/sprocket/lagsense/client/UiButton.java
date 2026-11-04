package audio.sprocket.lagsense.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Native button input, sounds and narration; shared restrained charcoal bevel. */
public class UiButton extends Button {
    public enum Role { SECONDARY, TAB, TOGGLE, PRIMARY, DESTRUCTIVE }
    record Surface(int fill,int topLeft,int bottomRight) {}
    private record Surfaces(Surface normal,Surface hover,Surface selected,Surface selectedHover,Surface danger,Surface dangerHover,Surface pressed,Surface disabled){
        Surfaces(UiTheme.Palette p){this(new Surface(p.button(),p.buttonLight(),p.buttonDark()),new Surface(p.buttonHover(),p.accent(),p.accent()),new Surface(p.selected(),p.buttonLight(),p.buttonDark()),new Surface(p.selectedHover(),p.accent(),p.accent()),new Surface(p.danger(),p.buttonLight(),p.buttonDark()),new Surface(p.dangerHover(),p.accent(),p.accent()),new Surface(p.pressed(),p.buttonDark(),p.buttonLight()),new Surface(p.disabled(),p.disabledBorder(),p.buttonDark()));}
    }
    private static final java.util.Map<UiTheme.Theme,Surfaces> SURFACES=new java.util.EnumMap<>(UiTheme.Theme.class);
    static {for(var theme:UiTheme.Theme.values())SURFACES.put(theme,new Surfaces(theme.palette()));}
    static Surface surface(Role role,boolean active,boolean on,boolean selected,boolean hover,boolean pressed) {
        var s=SURFACES.get(UiTheme.current());
        if(!active)return s.disabled();
        if(pressed)return s.pressed();
        if(role==Role.DESTRUCTIVE)return hover?s.dangerHover():s.danger();
        if(selected||role==Role.PRIMARY||role==Role.TOGGLE&&on)return hover?s.selectedHover():s.selected();
        return hover?s.hover():s.normal();
    }
    private Role role = Role.SECONDARY;
    private boolean selected, on;
    private boolean strongText;
    private java.util.function.BooleanSupplier enabledWhen,visibleWhen;
    private long pressedUntil;
    public UiButton(int x, int y, int w, int h, String label, Runnable run) {
        super(x, y, w, h, Component.literal(label), b -> run.run(), DEFAULT_NARRATION);
    }
    public UiButton(Builder builder) { super(builder); }
    public UiButton role(Role role) { this.role = role; return this; }
    public UiButton selected(boolean value) { selected = value; return this; }
    public UiButton on(boolean value) { on = value; return this; }
    public UiButton strongText(){strongText=true;return this;}
    UiButton enabledWhen(java.util.function.BooleanSupplier condition){enabledWhen=condition;active=condition.getAsBoolean();return this;}
    UiButton visibleWhen(java.util.function.BooleanSupplier condition){visibleWhen=condition;visible=condition.getAsBoolean();return this;}
    void refreshAvailability(){if(enabledWhen!=null)active=enabledWhen.getAsBoolean();if(visibleWhen!=null)visible=visibleWhen.getAsBoolean();}
    static void refreshAvailability(net.minecraft.client.gui.components.events.ContainerEventHandler container){for(var child:container.children()){if(child instanceof UiButton button)button.refreshAvailability();if(child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler nested)refreshAvailability(nested);}}
    static boolean highlighted(boolean hovered,boolean focused,boolean keyboard){return hovered||focused&&keyboard;}
    @Override public void onPress(InputWithModifiers input) { refreshAvailability();if(!active||!visible)return;pressedUntil = System.nanoTime() + 90_000_000; super.onPress(input); }
    @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
        if(enabledWhen!=null)active=enabledWhen.getAsBoolean();
        var font = strongText || role == Role.TAB || role == Role.PRIMARY ? UiFonts.strong() : UiFonts.body(); int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = highlighted(isHovered(),isFocused(),isFocused()&&Minecraft.getInstance().getLastInputType().isKeyboard());
        boolean pressed = pressedUntil != 0 && System.nanoTime() < pressedUntil;
        if (pressedUntil != 0 && !pressed) pressedUntil = 0;
        var surface=surface(role,active,on,selected,hover,pressed);
        g.fill(x, y, x + w, y + h, surface.fill());
        UiTheme.bevel(g,x,y,w,h,surface.topLeft(),surface.bottomRight());
        if (selected && role == Role.TAB) g.fill(x+1, y+h-3, x+w-1, y+h-1, UiTheme.ICE);
        if (role == Role.TOGGLE) g.fill(x + 3, y + 5, x + 5, y + h - 5, !active ? UiTheme.MUTED : on ? UiTheme.ICE : UiTheme.SECONDARY);
        var label = UiFonts.layout(font, getMessage().getString(), w - (role == Role.TOGGLE ? 14 : 10));
        g.text(font, label.text(), x + (w - label.width()) / 2 + (role == Role.TOGGLE ? 2 : 0), y + (h - 8) / 2, active ? UiTheme.TEXT : UiTheme.MUTED, false);
        UiHints.widget(this, g, mx, my);
    }
}
