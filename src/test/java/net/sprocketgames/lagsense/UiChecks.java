package net.sprocketgames.lagsense;

import java.util.*;

final class UiChecks {
    private static void require(boolean condition, String why) { if (!condition) throw new AssertionError(why); }
    static void run() {
        var delay = new HoverDelay();
        require(!delay.visit("a", 0) && !delay.visit("a", 399) && delay.visit("a", 400), "Initial tooltip waits 400ms, without resetting on the same control");
        require(!delay.visit("b", 410) && !delay.visit("b", 559) && delay.visit("b", 560), "Recent tooltips switch after 150ms");
        require(!delay.visit(null, 561) && !delay.visit("c", 562) && !delay.visit(null, 600), "Leaving dismisses and cancels pending help");
        require(!delay.visit("d", 2000) && !delay.visit("d", 2399) && delay.visit("d", 2400), "The initial delay returns outside the interaction window");
        delay.clear(); require(!delay.visit("d", 2410) && !delay.visit("d", 2809) && delay.visit("d", 2810), "Opening another screen resets the hover session");
        for (int width : new int[]{320, 427, 480, 640, 960}) {
            int left = UiMetrics.left(width) + 8, right = width - left;
            var tabs = new UiMetrics.Flow(left, right, 44);
            var rectangles = new ArrayList<UiMetrics.Rect>();
            for (int size : new int[]{30, 52, 46, 34, 48, 32, 42}) rectangles.add(tabs.next(size));
            var filters = new UiMetrics.Flow(left, right, tabs.bottom() + 4);
            for (int size : new int[]{94, 64, 76}) rectangles.add(filters.next(size));
            filters.group(164); var a = filters.next(58); var b = filters.next(50); var c = filters.next(48);
            rectangles.addAll(List.of(a, b, c)); require(a.y() == b.y() && b.y() == c.y(), "Utility actions wrap as a group");
            require(rectangles.stream().allMatch(r -> r.x() >= left && r.right() <= right), "Report tabs and controls stay inside logical viewport at width " + width);
            for (int i = 0; i < rectangles.size(); i++) for (int j = i + 1; j < rectangles.size(); j++) {
                var p = rectangles.get(i); var q = rectangles.get(j);
                require(p.right() <= q.x() || q.right() <= p.x() || p.bottom() <= q.y() || q.bottom() <= p.y(), "Wrapped control rectangles never overlap");
            }
            for (int height : new int[]{240, 270, 360, 540}) {
                int top = filters.bottom() + 8, rows = UiMetrics.rows(height, top, UiMetrics.ROW);
                require(Math.min(top + rows * UiMetrics.ROW, height - 80) <= UiMetrics.pagerY(height) - 4 && UiMetrics.footer(height) + 20 <= height - 24, "The clipped result viewport clears pagination and inset footer across GUI scales");
            }
        }
        for (int mask = 0; mask < 32; mask++) {
            boolean enabled = (mask & 1) != 0, report = (mask & 2) != 0, player = (mask & 4) != 0,
                hidden = (mask & 8) != 0, screen = (mask & 16) != 0;
            require(ClientReportPolicy.showHud(enabled, report, player, hidden, screen, true), "Position preview bypasses normal visibility and missing capture/player gates");
            require(ClientReportPolicy.showHud(enabled, report, player, hidden, screen, false) == (enabled && report && player && !hidden && !screen),
                "Gameplay HUD remains suppressed behind screens, when disabled or without data");
        }
        for (int width : new int[]{320, 640, 1280}) for (int height : new int[]{240, 360, 684}) for (float scale : new float[]{.5f, 1, 2}) {
            for (boolean marked : new boolean[]{false, true}) {
                var start = HudPlacement.at(width, height, 8, 8, scale, marked);
                require(start.width() == (int)(240 * scale) && start.height() == (int)((marked ? 86 : 73) * scale), "Real and preview HUD dimensions include scale and marked-target rows");
                var outside = HudPlacement.at(width, height, 4096, 4096, scale, marked);
                require(outside.x() == Math.max(0, width - outside.width()) && outside.y() == Math.max(0, height - outside.height()), "Both rendering paths use the same viewport clamp after resizing");
                var moved = HudPlacement.at(width, height, outside.x() - 8, outside.y() - 8, scale, marked);
                require(moved.x() == Math.max(0, outside.x() - 8) && moved.y() == Math.max(0, outside.y() - 8), "Movement starts from the visible position and retains eight-pixel increments");
                require(start.overlaps(start.x(), start.y(), 20, 20) && !start.overlaps(start.x() + start.width(), start.y(), 20, 20), "Only actual HUD/control overlap raises a positioning control");
            }
        }
        var hud=HudPlacement.at(640,360,8,8,1,false);
        require(List.of(hud.corner(640,360,false,false),hud.corner(640,360,true,false),hud.corner(640,360,false,true),hud.corner(640,360,true,true)).equals(List.of(
            new HudPlacement(8,8,240,73),new HudPlacement(392,8,240,73),new HudPlacement(8,279,240,73),new HudPlacement(392,279,240,73))), "The four corner shortcuts keep an eight-pixel edge margin");
        require(HudPlacement.at(640,360,8,8,1,true).corner(640,360,false,true).equals(new HudPlacement(8,266,240,86)), "Bottom positioning includes the extra marked-target row");
        require(HudPlacement.at(320,240,8,8,2,true).corner(320,240,true,true).equals(new HudPlacement(0,60,480,172)), "Oversized scaled HUDs clamp horizontally while retaining the bottom margin");
        var capture = new FrameProfile.Capture(); for (int i = 0; i < 100; i++) capture.frame(2_000_000); var quick = capture.finish();
        require(FrameGraph.ceiling(quick) < 8.3 && FrameGraph.ceiling(quick) >= quick.worstMs(), "Low frame times use available graph height");
        capture.frame(800_000_000); var spike = capture.finish();
        require(FrameGraph.ceiling(spike) >= 800 && FrameGraph.firstFrame(spike, 0) == 1 && FrameGraph.lastFrame(spike, spike.graphMs().size() - 1) == spike.frames(), "Unusual spikes stay visible and bucket frame ranges remain accurate");
        System.out.println("UI checks passed (responsive layout, tooltip timing/cancellation, frame graph bounds, HUD visibility and live placement).");
    }
}
