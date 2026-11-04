package net.sprocketgames.lagsense;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class AreaService {
    private final Map<UUID, PublicArea.Selection> selections = new HashMap<>();
    private final Set<UUID> display = new HashSet<>();
    private List<PublicArea> areas = List.of();
    private int ticks;
    public List<PublicArea> areas() { return areas; }
    public void load(Path directory) {
        clear(); Path file = directory.resolve("areas.json");
        try {
            if (Files.exists(file)) {
                if (Files.size(file) > 64 * 1024) throw new IOException("Area file exceeds size limit.");
                PublicArea[] saved = Profiler.JSON.fromJson(Files.readString(file), PublicArea[].class);
                if (saved == null) throw new IllegalArgumentException("Missing areas.");
                areas = PublicArea.validated(Arrays.asList(saved));
            }
        } catch (IOException | RuntimeException error) { areas = List.of(); LagSense.LOG.warn("Could not load public areas; public access remains closed", error); }
        // Individual grants must never silently become public during an upgrade.
        if (Files.exists(directory.resolve("regions.json"))) LagSense.LOG.info("Legacy per-player regions.json is no longer used. Define public areas with /lagsense area pos1, pos2 and create.");
    }
    public void clear() { areas = List.of(); selections.clear(); display.clear(); ticks = 0; }
    public void forget(UUID player) { selections.remove(player); display.remove(player); }
    public LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("area").requires(LagSense::isAdmin)
            .executes(c -> CommandOutput.table(c.getSource(), "Public areas", List.of(new StatusText.Line("Corner 1", "/lagsense area pos1"), new StatusText.Line("Corner 2", "/lagsense area pos2"),
                new StatusText.Line("Create", "/lagsense area create <name>"), new StatusText.Line("Delete", "/lagsense area delete [name]"), new StatusText.Line("Boundaries", "/lagsense area show | hide"),
                new StatusText.Line("List", "/lagsense area list"), new StatusText.Line("Cancel", "/lagsense area cancel"))))
            .then(corner("pos1", true)).then(corner("pos2", false))
            .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word()).executes(c -> create(c.getSource(), StringArgumentType.getString(c, "name")))))
            .then(Commands.literal("cancel").executes(c -> {
                var player = c.getSource().getPlayerOrException(); selections.remove(player.getUUID()); sync(player);
                return say(c.getSource(), "Selection cancelled.");
            }))
            .then(Commands.literal("show").executes(c -> display(c.getSource(), true)))
            .then(Commands.literal("hide").executes(c -> display(c.getSource(), false)))
            .then(Commands.literal("list").executes(c -> list(c.getSource())))
            .then(Commands.literal("delete").executes(c -> delete(c.getSource(), null))
                .then(Commands.argument("name", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(areas.stream().map(PublicArea::name), builder))
                    .executes(c -> delete(c.getSource(), StringArgumentType.getString(c, "name")))));
    }
    private LiteralArgumentBuilder<CommandSourceStack> corner(String command, boolean first) {
        return Commands.literal(command).executes(c -> select(c.getSource(), first, BlockPos.containing(c.getSource().getPosition())))
            .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> select(c.getSource(), first, BlockPosArgument.getBlockPos(c, "pos"))));
    }
    private int select(CommandSourceStack source, boolean first, BlockPos pos) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException(); String dimension = source.getLevel().dimension().identifier().toString();
        try {
            var point = new PublicArea.Point(pos.getX(), pos.getZ());
            if (first) selections.put(player.getUUID(), new PublicArea.Selection(dimension, point, null));
            else {
                var selected = selections.get(player.getUUID());
                if (selected == null) return fail(source, "Set corner 1 with /lagsense area pos1 first.");
                if (!selected.dimension().equals(dimension)) return fail(source, "Wrong dimension. Set corner 1 here first.");
                selections.put(player.getUUID(), new PublicArea.Selection(dimension, selected.first(), point));
            }
            sync(player);
            return say(source, "Corner " + (first ? "1" : "2") + ": " + point.x() + ", " + point.z() + "\nNext: /lagsense area " + (first ? "pos2" : "create <name>"));
        } catch (IllegalArgumentException error) { return fail(source, error.getMessage()); }
    }
    private int create(CommandSourceStack source, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException(); var selection = selections.get(player.getUUID());
        if (selection == null) return fail(source, "Set both corners with area pos1 and pos2 first.");
        if (!selection.dimension().equals(player.level().dimension().identifier().toString())) return fail(source, "Return to your selection's dimension first.");
        try {
            var area = selection.area(name);
            var changed = new ArrayList<>(areas); changed.add(area); areas = PublicArea.validated(changed);
            Profiler.save("areas.json", areas); selections.remove(player.getUUID()); display.add(player.getUUID()); broadcast(source.getServer());
            return say(source, "Public area created: " + name + "\nSize: " + area.width() + " × " + area.depth() + " blocks, full height");
        } catch (IllegalArgumentException error) { return fail(source, error.getMessage()); }
    }
    private int delete(CommandSourceStack source, String name) {
        String dimension = source.getLevel().dimension().identifier().toString(); var pos = BlockPos.containing(source.getPosition());
        var matches = name == null ? areas.stream().filter(a -> a.contains(dimension, pos.getX(), pos.getZ())).toList() : areas.stream().filter(a -> a.name().equals(name)).toList();
        if (matches.isEmpty()) return fail(source, name == null ? "You are not inside a public area." : "No public area named '" + name + "'.");
        if (matches.size() > 1) return fail(source, "Overlapping areas. Use /lagsense area delete <name>.");
        var area = matches.getFirst(); areas = areas.stream().filter(a -> a != area).toList();
        Profiler.save("areas.json", areas); broadcast(source.getServer());
        return say(source, "Public area '" + area.name() + "' deleted.");
    }
    private int list(CommandSourceStack source) {
        say(source, "Public areas: " + areas.size());
        for (var area : areas) say(source, area.name() + ": " + area.minX() + ", " + area.minZ() + " → " + area.maxX() + ", " + area.maxZ() + "\nDimension: " + area.dimension());
        return 1;
    }
    private int display(CommandSourceStack source, boolean show) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (show && !NetworkRegistry.hasChannel(player.connection, AreaPayload.TYPE.id())) return fail(source, "Install LagSense on your client to see boundaries.");
        if (show) display.add(player.getUUID()); else display.remove(player.getUUID()); sync(player);
        return say(source, "Area boundaries: " + StatusText.onOff(show));
    }
    private void broadcast(MinecraftServer server) {
        for (var player : server.getPlayerList().getPlayers()) if (display.contains(player.getUUID()) || selections.containsKey(player.getUUID())) sync(player);
    }
    private void sync(ServerPlayer player) {
        if (!NetworkRegistry.hasChannel(player.connection, AreaPayload.TYPE.id())) return;
        boolean admin = LagSense.isAdmin(player.createCommandSourceStack());
        var state = new AreaPayload.State(admin && display.contains(player.getUUID()) ? areas : List.of(), admin ? selections.get(player.getUUID()) : null, admin && display.contains(player.getUUID()));
        PacketDistributor.sendToPlayer(player, new AreaPayload(Profiler.JSON.toJson(state)));
    }
    public void tick(MinecraftServer server) {
        if (display.isEmpty() && selections.isEmpty()) return;
        if (++ticks % 20 != 0) return;
        Set<UUID> watching = new HashSet<>(display); watching.addAll(selections.keySet());
        for (UUID id : watching) {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) { forget(id); continue; }
            if (!LagSense.isAdmin(player.createCommandSourceStack())) { forget(id); sync(player); }
        }
    }
    private int say(CommandSourceStack source, String message) { return CommandOutput.send(source, message); }
    private int fail(CommandSourceStack source, String message) { return CommandOutput.fail(source, message); }
}
