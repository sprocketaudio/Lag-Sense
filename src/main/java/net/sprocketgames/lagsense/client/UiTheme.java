package net.sprocketgames.lagsense.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Client-only presentation palettes. Measured severity and world labels stay independent. */
public final class UiTheme {
    public record Palette(int background,int panel,int row,int button,int accent,int text,int secondary,
                          int border,int selected,int hover,int muted,int disabled,int pressed,int danger,
                          int dangerHover,int buttonLight,int buttonDark,int buttonHover,int selectedHover,int disabledBorder) {}
    public enum Theme {
        @com.google.gson.annotations.SerializedName("charcoal_ice")
        CHARCOAL_ICE("Charcoal + Ice",0xffe9b86c,new Palette(0xff17191c,0xff23262a,0xff303439,0xff444b52,0xffa3dce2,0xffe8ecef,0xffa5afb8,
            0xff4a5058,0xff35444b,0xff3e494f,0xff78828b,0xff292d31,0xff4a5963,0xff53332f,0xff74453b,0xff65717b,0xff252b30,0xff515e66,0xff46565e,0xff3a4147)),
        @com.google.gson.annotations.SerializedName("midnight_violet")
        MIDNIGHT_VIOLET("Midnight + Violet",0xff6ddbc4,new Palette(0xff171625,0xff28253b,0xff302b43,0xff48415f,0xffc7b2ff,0xfff0ebf8,0xffaea6c2,
            0xff555070,0xff3d3556,0xff40394f,0xff8d859f,0xff272434,0xff514569,0xff53313f,0xff704353,0xff796c92,0xff201c2e,0xff574e70,0xff4a405f,0xff41394f)),
        @com.google.gson.annotations.SerializedName("obsidian_gold")
        OBSIDIAN_GOLD("Obsidian + Gold",0xff91d7ef,new Palette(0xff151618,0xff222428,0xff2d3034,0xff41454a,0xffd8b76a,0xfff0ede7,0xffaaa6a0,
            0xff5c5750,0xff393b3b,0xff393c40,0xff85827d,0xff25272a,0xff4b4d4d,0xff53332f,0xff74453b,0xff716d65,0xff202225,0xff505459,0xff484a47,0xff3c3d3e));
        public final String label;
        private final Palette palette;
        private final int savedTitle;
        private final net.minecraft.resources.Identifier tooltip;
        Theme(String label,int savedTitle,Palette palette){this.label=label;this.savedTitle=savedTitle;this.palette=palette;tooltip=name().equals("CHARCOAL_ICE")?null:net.minecraft.resources.Identifier.fromNamespaceAndPath("lagsense",name().toLowerCase(java.util.Locale.ROOT));}
        public Palette palette(){return palette;}
        public Theme next(){return switch(this){case CHARCOAL_ICE->MIDNIGHT_VIOLET;case MIDNIGHT_VIOLET->OBSIDIAN_GOLD;case OBSIDIAN_GOLD->CHARCOAL_ICE;};}
        public net.minecraft.resources.Identifier tooltip(){return tooltip;}
    }
    public static final Theme DEFAULT=Theme.OBSIDIAN_GOLD;
    private static Theme current=DEFAULT;
    private static int revision;
    public static int BACKGROUND,PANEL,ROW,BUTTON,ICE,TEXT,SECONDARY,BORDER,ON,HOVER,SELECTED,MUTED,
        DISABLED,PRESSED,DANGER,DANGER_HOVER,HUD,BUTTON_LIGHT,BUTTON_DARK,BUTTON_HOVER,SELECTED_HOVER,DISABLED_BORDER;
    public static final int WARNING=0xffffab40,LABEL=0xc017191c;
    static { apply(current.palette()); }
    public static Theme current(){return current;}
    public static int titleColor(String title){return title.equals("SAVED PROFILE")?current.savedTitle:ICE;}
    public static int revision(){return revision;}
    public static void select(Theme theme){if(theme==null)theme=DEFAULT;if(theme==current)return;current=theme;apply(theme.palette());revision++;}
    private static void apply(Palette p){
        BACKGROUND=p.background();PANEL=p.panel();ROW=p.row();BUTTON=p.button();ICE=p.accent();TEXT=p.text();SECONDARY=p.secondary();BORDER=p.border();
        ON=SELECTED=p.selected();HOVER=p.hover();MUTED=p.muted();DISABLED=p.disabled();PRESSED=p.pressed();DANGER=p.danger();DANGER_HOVER=p.dangerHover();
        HUD=0xe6000000|(BACKGROUND&0xffffff);BUTTON_LIGHT=p.buttonLight();BUTTON_DARK=p.buttonDark();BUTTON_HOVER=p.buttonHover();SELECTED_HOVER=p.selectedHover();DISABLED_BORDER=p.disabledBorder();
    }
    /** Rebind cached UI text roles, leaving diagnostic colours untouched. */
    static int textColor(int color,Theme captured){
        var p=captured.palette();if(color==p.text())return TEXT;if(color==p.accent())return ICE;if(color==p.secondary())return SECONDARY;if(color==p.muted())return MUTED;return color;
    }
    public static void bevel(GuiGraphicsExtractor g,int x,int y,int w,int h,int light,int dark) {
        g.fill(x,y,x+w,y+1,light);g.fill(x,y+1,x+1,y+h,light);
        g.fill(x,y+h-1,x+w,y+h,dark);g.fill(x+w-1,y,x+w,y+h,dark);
    }
    public static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color); g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color); g.fill(x + w - 1, y, x + w, y + h, color);
    }
    private UiTheme() {}
    public static int changeColor(double delta) { return delta > 0 ? WARNING : delta < 0 ? 0xff69f0ae : TEXT; }
}
