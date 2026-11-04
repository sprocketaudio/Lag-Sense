package audio.sprocket.lagsense.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A focusable result row; embedded actions own their hit area and cannot activate the row. */
public final class UiRow extends Button {
    private final String primary, secondary, trailing;
    private final int color, reserved, indent;
    private int valueColor;
    private UiTheme.Theme theme=UiTheme.current();
    private java.util.function.BooleanSupplier selectedState;
    private final boolean selected;
    private boolean compact;
    private String sampleCount = "";
    private int countColumn, valueColumn;
    record Columns(int nameX,int nameWidth,int countRight,int countWidth,int valueRight,int valueWidth) {}
    public UiRow(int x, int y, int w, int h, String primary, String secondary, String trailing, int color, int reserved, int indent, boolean selected, Runnable run) {
        super(x, y, w, h, Component.literal(primary + ". " + secondary), b -> { if (run != null) run.run(); }, DEFAULT_NARRATION);
        this.primary = primary; this.secondary = secondary; this.trailing = trailing; this.color = color; this.valueColor = color; this.reserved = reserved; this.indent = indent; this.selected = selected; active = run != null;
    }
    public UiRow valueColor(int value) { valueColor = value; return this; }
    UiRow theme(UiTheme.Theme value){theme=value;return this;}
    int nameColor(){return UiTheme.textColor(color,theme);}
    int measurementColor(){return trailing!=null&&trailing.stripTrailing().endsWith("%")?UiTheme.TEXT:UiTheme.textColor(valueColor,theme);}
    public UiRow compact(String count) { compact = true; sampleCount = count; return this; }
    UiRow selection(java.util.function.BooleanSupplier state){selectedState=state;return this;}
    UiRow sampleColumns(int countWidth,int valueWidth) { countColumn=countWidth;valueColumn=valueWidth;return this; }
    Columns columns() {
        int x=getX(),w=getWidth(),right=x+w-12-reserved;
        int valueWidth=valueColumn>0?valueColumn:trailing==null||trailing.isEmpty()?0:Math.min(Math.max(0,w-24-reserved),UiFonts.width(UiFonts.mono(),trailing));
        int countRight=right-valueWidth-12;
        int nameRight=countColumn>0?countRight-countColumn-12:right-valueWidth-(valueWidth>0?12:0);
        // Limit visual indentation to the available name area, never the retained tree depth.
        int start=x+12+Math.min(Math.max(0,indent),Math.max(0,nameRight-x-12-48));
        return new Columns(start,Math.max(0,nameRight-start),countRight,countColumn,right,valueWidth);
    }
    @Override public boolean isMouseOver(double x, double y) { return visible && x >= getX() && x < getX() + getWidth() - reserved && y >= getY() && y < getY() + getHeight(); }
    @Override protected void handleCursor(GuiGraphicsExtractor g) { if (active) super.handleCursor(g); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        return event.x() < getX() + getWidth() - reserved && super.mouseClicked(event, twice);
    }
    @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
        int x = getX(), y = getY(), w = getWidth(); var font = UiFonts.body();
        var columns=columns();
        boolean selected=selectedState==null?this.selected:selectedState.getAsBoolean();
        g.fill(x, y, x + w, y + getHeight(), selected ? UiTheme.SELECTED : active && isHoveredOrFocused() ? UiTheme.HOVER : UiTheme.ROW);
        if (selected) g.fill(x, y, x + 2, y + getHeight(), UiTheme.ICE);
        if (isFocused() && active) UiTheme.border(g, x, y, w, getHeight(), UiTheme.ICE);
        int textY = compact ? y + (getHeight() - 8) / 2 : y + 6;
        UiFonts.drawName(g,primary,columns.nameX(),textY,columns.nameWidth(),nameColor());
        if (compact && countColumn>0) rightText(g,sampleCount,columns.countRight(),textY,columns.countWidth(),UiTheme.SECONDARY);
        if (columns.valueWidth()>0) rightText(g,trailing,columns.valueRight(),textY,columns.valueWidth(),measurementColor());
        if (!compact) UiFonts.draw(g, font, secondary, columns.nameX(), y + 19, Math.max(0,x+w-12-reserved-columns.nameX()), UiTheme.SECONDARY, false);
        if (mx < x + w - reserved || isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard()) UiHints.widget(this, g, mx, my);
    }
    private void rightText(GuiGraphicsExtractor g,String text,int right,int y,int width,int color) {
        var layout=UiFonts.layout(UiFonts.mono(),text,width);
        g.text(UiFonts.mono(),layout.text(),right-layout.width(),y,color,false);
    }
}
