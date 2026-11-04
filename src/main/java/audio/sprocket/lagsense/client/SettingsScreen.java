package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.SettingsPayload;
import audio.sprocket.lagsense.SettingsMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;

public final class SettingsScreen extends SettingsPage {
    private final Screen parent;
    private SettingsPayload state;
    public SettingsScreen(Screen parent, SettingsPayload state) { super("Settings"); this.parent = parent; this.state = state; }
    public void accept(SettingsPayload state){this.state=state;rebuildWidgets();}
    @Override protected void init() {
        var categories=SettingsMenu.categories(state!=null&&state.admin());
        int w = Math.min(220, contentWidth()), x = (width - w) / 2;
        int stack=categories.size()*20+(categories.size()-1)*12;
        int top=headerControlsTop()+12, y=top+Math.max(0,(footer()-20-top-stack)/2);
        for(var category:categories) {
            action(category.label,x,y,w,()->{
                switch(category) {
                    case HUD -> minecraft.setScreen(new OverlaySettingsScreen(this));
                    case CAPTURES -> ClientAccess.request("captures");
                    case DISCORD -> ServerSettingsScreen.request("discord");
                    case ADVANCED -> ServerSettingsScreen.request("advanced");
                }
            });y+=32;
        }
        action("Back", width - margin() - 54, footer(), 54, this::onClose);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) { canvas(g,"SETTINGS"); headerDivider(g); super.extractRenderState(g,mx,my,a); }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
