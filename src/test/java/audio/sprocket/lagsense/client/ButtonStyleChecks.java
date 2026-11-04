package audio.sprocket.lagsense.client;

final class ButtonStyleChecks {
    static void run() {
        for(var role:UiButton.Role.values())for(boolean on:new boolean[]{false,true})for(boolean selected:new boolean[]{false,true}) {
            var normal=UiButton.surface(role,true,on,selected,false,false);
            var hover=UiButton.surface(role,true,on,selected,true,false);
            var pressed=UiButton.surface(role,true,on,selected,true,true);
            if(hover.topLeft()!=UiTheme.ICE||hover.bottomRight()!=UiTheme.ICE)throw new AssertionError("Every active button role uses ice hover boundaries");
            if(pressed.topLeft()!=normal.bottomRight()||pressed.bottomRight()!=normal.topLeft())throw new AssertionError("Pressed borders reverse the normal bevel");
            if(UiButton.surface(role,false,on,selected,true,true).fill()!=UiTheme.DISABLED)throw new AssertionError("Hover/press cannot enable a disabled button's appearance");
            if(normal!=UiButton.surface(role,true,on,selected,false,false))throw new AssertionError("Shared button surfaces must not allocate during rendering");
        }
        var selected=UiButton.surface(UiButton.Role.TAB,true,false,true,false,false);
        if(selected.fill()!=UiTheme.SELECTED||selected.topLeft()==selected.bottomRight())throw new AssertionError("Selected tabs preserve their fill and gain a defined boundary");
        var normal=UiButton.surface(UiButton.Role.SECONDARY,true,false,false,false,false);
        if(normal.fill()==UiTheme.ROW||normal.fill()==UiTheme.HOVER||normal.fill()==UiTheme.SELECTED)throw new AssertionError("Action buttons remain distinct from normal, hovered and selected rows");
        System.out.println("Button style checks passed (shared roles, hover borders, reversed press bevels, selected and disabled states).");
    }
}
