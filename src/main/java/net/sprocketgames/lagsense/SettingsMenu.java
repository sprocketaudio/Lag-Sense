package net.sprocketgames.lagsense;

import java.util.List;

public final class SettingsMenu {
    public enum Category {
        HUD("Display"), CAPTURES("Automatic Captures"), DISCORD("Discord notifications"), ADVANCED("Advanced");
        public final String label;
        Category(String label){this.label=label;}
    }
    public static List<Category> categories(boolean admin) {
        return admin?List.of(Category.HUD,Category.CAPTURES,Category.DISCORD,Category.ADVANCED):List.of(Category.HUD);
    }
    private SettingsMenu(){}
}
