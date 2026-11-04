package audio.sprocket.lagsense.client;

import java.time.Duration;
import java.util.*;
import audio.sprocket.lagsense.HoverDelay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;

/** Native tooltips shared by controls and result regions. */
public final class UiHints {
    private UiHints() {}
    private static final Map<AbstractWidget, Hint> widgets = new WeakHashMap<>();
    private static final Map<Region, Hint> regions = new LinkedHashMap<>();
    private static final HoverDelay delay = new HoverDelay();
    private static Object owner, candidate;
    private static Hint help;
    private static int helpX, helpY;
    private record Region(int x, int y, int w, int h) {}
    private static final class Hint {
        final String text; Component component;
        java.util.List<net.minecraft.util.FormattedCharSequence> wrapped;
        int width,revision;
        Hint(String text) { this.text = text; component = UiFonts.tooltip(text);revision=UiTheme.revision(); }
        void refresh(){if(revision!=UiTheme.revision()){revision=UiTheme.revision();component=UiFonts.tooltip(text);wrapped=null;}}
    }
    static void fontsReloaded() { widgets.values().forEach(h -> h.wrapped = null); regions.clear(); help = null; delay.clear(); }
    static void begin(Object screen) { if (owner != screen) { delay.clear(); regions.clear(); owner = screen; } candidate = null; help = null; }
    static void clear(Object screen) { if (owner == screen) { owner = candidate = null; help = null; regions.clear(); delay.clear(); } }
    static void widget(AbstractWidget widget, GuiGraphicsExtractor g, int mx, int my) {
        var hint = widgets.get(widget); if (hint == null) return;
        boolean focus = widget.isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard();
        if (widget.isMouseOver(mx, my) || focus) { candidate = widget; help = hint; helpX = focus ? widget.getX() + widget.getWidth() / 2 : mx; helpY = focus ? widget.getY() + widget.getHeight() : my; }
    }
    static void end(GuiGraphicsExtractor g, Font font, int mx, int my) {
        if (!delay.visit(candidate, System.nanoTime() / 1_000_000) || help == null) return;
        help.refresh();
        int width = Math.max(80, Math.min(240, g.guiWidth() - 24));
        if (help.wrapped == null || help.width != width) { help.width = width; help.wrapped = font.split(help.component, width); }
        if(UiTheme.current().tooltip()==null)g.setTooltipForNextFrame(font, help.wrapped, net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE, helpX, helpY, true);
        else g.setTooltipForNextFrame(font,help.wrapped,helpX,helpY,UiTheme.current().tooltip());
    }
    public static <T extends AbstractWidget> T hint(T widget, String text) {
        if (text != null && !text.isBlank()) {
            widgets.put(widget, new Hint(text)); widget.setTooltip(Tooltip.create(UiFonts.tooltip(text)));
            // Native narration retains the hint; the shared controller owns visual timing.
            widget.setTooltipDelay(Duration.ofDays(365));
        }
        return widget;
    }
    public static <T extends AbstractWidget> T control(T widget, String label) { return hint(widget, action(label)); }
    public static void pageButtons(Button previous, Button next, int page, int items, int rows) {
        previous.active = page > 0; next.active = page + 1 < audio.sprocket.lagsense.PageNumbers.count(items, rows);
    }
    public static void region(GuiGraphicsExtractor g, Font font, int mx, int my, int x, int y, int w, int h, String text) {
        if (text != null && !text.isBlank() && mx >= x && mx < x + w && my >= y && my < y + h) {
            var key = new Region(x, y, w, h); var hint = regions.get(key);
            if (hint == null || !hint.text.equals(text)) { if (regions.size() >= 128) regions.clear(); hint = new Hint(text); regions.put(key, hint); }
            candidate = key; help = hint; helpX = mx; helpY = my;
        }
    }
    static String action(String label) {
        if (label.startsWith("Group by Mod ")) return "Group results by the mod ID in their registry names.";
        if (label.startsWith("Range:")) return "Maximum distance for world markers and the chunk grid. Cycles from 32 to 192 blocks.";
        if (label.startsWith("Min:")) return "Hide markers for things using less than this much time.";
        if (label.startsWith("World markers")) return "Show captured timing labels above entities and highlight blocks in the world.";
        if (label.startsWith("Chunk heatmap")) return "Show a heatmap of chunk performance using captured timings.";
        if (label.startsWith("World ")) return "Show the chunk grid from this capture around you. Use the Hide/show visuals shortcut to hide it.";
        if (label.startsWith("HUD ")) return "Show or hide the LagSense HUD. The Hide/show visuals shortcut temporarily hides performance overlays.";
        if (label.startsWith("GPU ")) return "Measure how long the graphics card spends drawing frames.";
        if (label.startsWith("Capture ")) return "Record frame times while you play for this long. The Client FPS profiling shortcut can stop a capture early.";
        if (label.startsWith("Timer ") || label.equals("Timer")) return "Capture a profile at regular intervals.";
        if (label.startsWith("Spikes ") || label.equals("Spikes")) return "Start a profile when a spike threshold is reached.";
        if (label.startsWith("History ")) return "Save completed profiles on the server. Existing saves remain when this is off, subject to the history limits.";
        if (label.startsWith("Scale")) return "Resize the HUD. Click the percentage to return to 100%.";
        return switch (label) {
            case "Blocks" -> "Ticking blocks, such as hoppers and machines.";
            case "Ticks" -> "Block and fluid updates, such as crop growth and flowing water.";
            case "Chunks" -> "Add up recorded work in each chunk.";
            case "Map" -> "Open the chunk heatmap for this report.";
            case "Tasks" -> "Inspect sampled server activity and recorded event timings.";
            case "Settings" -> "Configure the HUD, world markers and chunk grid.";
            case "Server" -> "Edit the server's timer and spike capture settings.";
            case "History" -> "Browse saved profiles and compare captures.";
            case "Most updates" -> "Sort by the number of updates rather than the time they used.";
            case "Most items" -> "Sort by the number of measured objects in each chunk.";
            case "Most time" -> "Sort by the time used per server tick.";
            case "Types" -> "Combine changes for each target type and dimension.";
            case "Mark" -> "Highlight this target in the world and show its coordinates and distance.";
            case "Unmark" -> "Clear the highlighted target.";
            case "TP" -> "Teleport near this target. Preview the destination before travelling.";
            case "Return" -> "Return to your position before the last hotspot teleport.";
            case "Copy" -> "Copy the selected result and its children as text.";
            case "Export" -> "Save this report as HTML, JSON or CSV.";
            case "Save JSON" -> "Save the full report, including capture details.";
            case "Save CSV" -> "Save a table you can open in a spreadsheet.";
            case "Hottest" -> "Select and centre the most expensive captured chunk in this dimension.";
            case "Fit" -> "Zoom out to show this dimension’s captured chunks, up to the map’s zoom limit.";
            case "Call paths" -> "See which methods called each other in the CPU samples.";
            case "< X" -> "Pan west by three chunks.";
            case "X >" -> "Pan east by three chunks.";
            case "^ Z" -> "Pan north by three chunks.";
            case "Z v" -> "Pan south by three chunks.";
            case "Baseline" -> "Select one profile, then choose another to compare. Changes always run earlier to later.";
            case "Compare" -> "Compare the selected profiles, earlier to later, using cost per captured tick.";
            case "Selected" -> "Clear this comparison baseline.";
            case "Reset defaults" -> "Restore defaults. Server capture settings require Apply.";
            case "By mod" -> "Add up the results for each mod.";
            case "By action" -> "Show separate results for each piece of mod code.";
            case "Reload" -> "Request the current saved-profile list.";
            case "CPU activity" -> "See which code was running when CPU samples were taken. Percentages show each result’s share of those samples.";
            case "Mod activity" -> "Show CPU samples for this mod across the server rather than just this target.";
            case "Event timings" -> "Measure individual event handlers, such as tick updates. Turn on Detailed events in Advanced settings before capturing.";
            default -> null;
        };
    }
    public static String field(String label) {
        return switch (label) {
            case "Timer interval in minutes" -> "Minutes between automatic profile captures (1–1440).";
            case "Timer capture seconds" -> "Duration of each automatic profile capture (5–120 seconds).";
            case "TPS alert threshold; zero disables" -> "Start a profile if TPS stays below this value. Choose 1–20 below your server’s usual TPS, or 0 to turn this off.";
            case "Seconds below TPS threshold" -> "How long TPS must stay below the threshold: 1–120 seconds.";
            case "Tick-work alert milliseconds; zero disables" -> "Start a profile after consecutive ticks take at least this many milliseconds. Set to 0 to turn off this threshold.";
            case "Consecutive slow work ticks" -> "Required consecutive ticks above the tick-work threshold: 1–100.";
            case "Follow-up capture seconds" -> "Profile duration after spike detection (5–120 seconds).";
            case "Repeat cooldown after capture, seconds" -> "Minimum delay after a spike profile finishes before another can start: 30–86400 seconds.";
            case "Maximum profiles" -> "Keep 1–50 server profiles. Oldest saves are removed when either retention limit is exceeded.";
            case "Disk limit in MiB" -> "Server history disk budget: 8–256 MiB. Oldest saves are removed to stay within it.";
            default -> label;
        };
    }
    static String discordTab(String name){return switch(name){
        case "Connection"->"Configure Discord webhook notifications.";
        case "Timer"->"Run profiles at a fixed interval.";
        case "Spikes"->"Start a follow-up profile when a configured threshold is reached.";
        case "Manual"->"Configure notifications for manually started profiles.";
        default->null;
    };}
}
