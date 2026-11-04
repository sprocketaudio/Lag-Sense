package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.Report;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Compact confirmation for the existing operator-only target teleport. */
public final class TravelScreen extends LagScreen {
    private final Screen parent;
    private final Report.Entry target;
    private final Runnable confirm;
    private final String locationLabel;
    private List<FormattedCharSequence> lines = List.of();
    private UiButton cancel;
    public TravelScreen(Screen parent, Report.Entry target, Runnable confirm) { this(parent,target,confirm,""); }
    public TravelScreen(Screen parent,Report.Entry target,Runnable confirm,String locationLabel){super("Teleport to target?");this.parent=parent;this.target=target;this.confirm=confirm;this.locationLabel=locationLabel;}
    @Override protected int panelMargin(){return Math.max(8,(width-320)/2);}
    @Override protected int panelTop(){return (height-Math.min(148,height-24))/2;}
    @Override protected int panelBottom(){return height-panelTop();}
    @Override protected int footer(){return panelBottom()-32;}
    @Override protected void init() {
        lines = font.split(UiFonts.rich((locationLabel.isBlank()?"":locationLabel+": ")+target.dimension() + " at " + target.x() + ", " + target.y() + ", " + target.z() + ".\nReturn with /lagsense return."), contentWidth());
        primary("Teleport", margin(), footer(), 76, () -> { confirm.run(); minecraft.setScreen(parent); }).enabledWhen(()->TargetActions.permitted(target.kind().equals("Chunk"))).visibleWhen(()->TargetActions.permitted(target.kind().equals("Chunk")));
        cancel=action("Cancel", width - margin() - 60, footer(), 60, this::onClose);
    }
    @Override protected void setInitialFocus(){setInitialFocus(cancel);}
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) { canvas(g, "TELEPORT TO TARGET?");headerDivider(g); for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), margin(), panelTop()+52 + i * 13, UiTheme.TEXT, false); super.extractRenderState(g, mx, my, a); }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
