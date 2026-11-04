package net.sprocketgames.lagsense;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;

/** Consistent chat output: grey labels, readable values, gold warnings and red errors. */
public final class CommandOutput {
    private CommandOutput() {}
    public static MutableComponent format(String text) {
        var result = Component.empty(); boolean first = true;
        for (String line : text.split("\n")) {
            if (!first) result.append("\n"); first = false;
            int split = line.indexOf(": ");
            if (split < 0) result.append(Component.literal(line).withStyle(ChatFormatting.WHITE));
            else result.append(Component.literal(line.substring(0, split + 2)).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(line.substring(split + 2)).withStyle(valueColor(line.substring(split + 2))));
        }
        return result;
    }
    private static ChatFormatting valueColor(String value) {
        return value.equals("Off") || value.equals("Idle") ? ChatFormatting.GRAY : value.equals("On") ? ChatFormatting.GREEN : value.startsWith("Running") || value.startsWith("Recording") || value.startsWith("Paused") ? ChatFormatting.GOLD : ChatFormatting.WHITE;
    }
    public static MutableComponent table(String title, List<StatusText.Line> lines) {
        var result = Component.literal(title).withStyle(ChatFormatting.GREEN);
        for (var line : lines) result.append("\n").append(format(line.label() + ": " + line.value()));
        return result;
    }
    public static int send(CommandSourceStack source, String text) { source.sendSuccess(() -> format(text), false); return 1; }
    public static int table(CommandSourceStack source, String title, List<StatusText.Line> lines) { source.sendSuccess(() -> table(title, lines), false); return 1; }
    public static int fail(CommandSourceStack source, String text) { source.sendFailure(Component.literal(text)); return 0; }
    public static void warn(CommandSourceStack source, String text) { source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GOLD), false); }
    public static void tell(ServerPlayer player, String text) { player.sendSystemMessage(format(text)); }
    public static void error(ServerPlayer player, String text) { player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.RED)); }
    public static void ready(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Profile ready. ").withStyle(ChatFormatting.WHITE)
            .append(Component.literal("[Open]").withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true).withClickEvent(new ClickEvent.RunCommand("/lagsense report")))));
    }
}
