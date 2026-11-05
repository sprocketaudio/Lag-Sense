package net.sprocketgames.lagsense;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod("lagsense")
public final class LagSense {
    public static final Logger LOG = LogUtils.getLogger();
    private record ReturnPoint(ServerLevel level, double x, double y, double z, float yaw, float pitch) {}
    private static final Map<UUID, ReturnPoint> returns = new HashMap<>();
    public LagSense(IEventBus bus) {
        bus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("13").optional()
            .playToClient(ProfilerStatePayload.TYPE,ProfilerStatePayload.CODEC)
            .playToServer(ProfilerRequest.TYPE,ProfilerRequest.CODEC,(packet,context)->{if(context.player() instanceof ServerPlayer player)uiRequest(player,packet);})
            .playToClient(SettingsPayload.TYPE, SettingsPayload.CODEC)
            .playToClient(ServerSettingsPayload.TYPE, ServerSettingsPayload.CODEC)
            .playToServer(ServerSettingsRequest.TYPE, ServerSettingsRequest.CODEC, (packet, context) -> {
                if(context.player() instanceof ServerPlayer player)ServerSettingsForms.request(player,packet,context::reply);
            })
            .playToClient(InvestigationPayload.TYPE, InvestigationPayload.CODEC)
            .playToClient(InvestigationPart.TYPE,InvestigationPart.CODEC)
            .playToServer(InvestigationRequest.TYPE, InvestigationRequest.CODEC, (packet, context) -> {
                if (context.player() instanceof ServerPlayer p && Profiler.investigations != null) Profiler.investigations.request(p, packet);
            })
            .playToServer(ChunkBatchRequest.TYPE, ChunkBatchRequest.CODEC, (packet, context) -> {
                if(context.player() instanceof ServerPlayer p && Profiler.investigations != null)
                    Profiler.investigations.request(p,packet.context(),packet.category(),packet.offset());
            })
            .playToClient(ReportPayload.TYPE, ReportPayload.CODEC)
            .playToClient(FullReportPart.TYPE,FullReportPart.CODEC)
            .playToClient(AreaPayload.TYPE, AreaPayload.CODEC)
            .playToClient(ArchivePayload.TYPE, ArchivePayload.CODEC)
            .playToClient(ComparisonPayload.TYPE, ComparisonPayload.CODEC)
            .playToClient(ComparisonPart.TYPE,ComparisonPart.CODEC)
            .playToClient(CaptureSettingsPayload.TYPE, CaptureSettingsPayload.CODEC)
            .playToServer(UpdateCaptureSettingsPayload.TYPE, UpdateCaptureSettingsPayload.CODEC, (packet, context) -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                if (!isAdmin(player.createCommandSourceStack())) { context.reply(new CaptureSettingsPayload("", "Operator access required.")); return; }
                try {
                    var value = Profiler.JSON.fromJson(packet.json(), CaptureSettings.class);
                    if (value == null) throw new IllegalArgumentException("Missing settings.");
                    Profiler.editCaptures(value); Profiler.openSettings(player, "Settings applied.");
                } catch (java.io.IOException error) { Profiler.openSettings(player, "Could not save the configuration, so your settings were not changed."); }
                catch (RuntimeException error) { Profiler.openSettings(player, error instanceof IllegalArgumentException ? error.getMessage() : "The settings are invalid and were not applied."); }
            })
            .playToServer(ArchiveRequestPayload.TYPE, ArchiveRequestPayload.CODEC, (packet, context) -> {
                if (context.player() instanceof ServerPlayer player && Profiler.archives != null) Profiler.archives.request(player, packet);
            }));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> Profiler.load(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> { Profiler.reset(); returns.clear(); });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer player)) return;
            returns.remove(player.getUUID()); Profiler.areas.forget(player.getUUID()); Profiler.forget(player.getUUID());if(Profiler.players!=null)Profiler.players.forget(player.getUUID());
            if (Profiler.investigations != null) Profiler.investigations.forget(player.getUUID());
            if (Profiler.archives != null) Profiler.archives.forget(player.getUUID());
        });
        NeoForge.EVENT_BUS.addListener(this::commands);
    }
    public static boolean isAdmin(CommandSourceStack source) { return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source); }
    public static boolean canReturn(ServerPlayer player){return isAdmin(player.createCommandSourceStack())&&returns.containsKey(player.getUUID());}
    private static void syncReturn(ServerPlayer player){if(Profiler.players!=null&&Profiler.hasClient(player))Profiler.players.renew(player);}
    private int message(CommandSourceStack source, String text) { return CommandOutput.send(source, text); }
    private int fail(CommandSourceStack source, String text) { return CommandOutput.fail(source, text); }
    private int help(CommandSourceStack source) {
        if (!isAdmin(source)) return message(source, "Open profiler: /lagsense report");
        return CommandOutput.table(source,"LagSense",List.of(new StatusText.Line("Results","/lagsense report"),new StatusText.Line("Capture","/lagsense profile [seconds] | stop"),new StatusText.Line("Public areas","/lagsense area"),new StatusText.Line("Status","/lagsense status")));
    }
    private void uiRequest(ServerPlayer player,ProfilerRequest request){
        if((request.action().equals("capture")||request.action().equals("toggle"))&&isAdmin(player.createCommandSourceStack())){
            try{if(request.action().equals("toggle"))toggleProfile(player.createCommandSourceStack());else start(player.createCommandSourceStack(),30,true);}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){CommandOutput.error(player,"Could not start the profile.");}
        }else if(Profiler.players!=null)Profiler.players.request(player,request);
    }
    private void commands(RegisterCommandsEvent event) {
        var root = event.getDispatcher().register(Commands.literal("lagsense")
            .executes(c -> help(c.getSource()))
            .then(Profiler.areas.commands())
            .then(Commands.literal("status").requires(LagSense::isAdmin).executes(c -> status(c.getSource())))
            .then(Commands.literal("reload").requires(LagSense::isAdmin).executes(c -> {
                String result = Profiler.reload(); return result.startsWith("Reload failed") ? fail(c.getSource(), result) : message(c.getSource(), result);
            }))
            .then(Commands.literal("discord").requires(LagSense::isAdmin)
                .executes(c -> message(c.getSource(), Profiler.delivery.status()))
                .then(Commands.literal("test").executes(c -> {
                    if (!Profiler.settings.discord().enabled()) return fail(c.getSource(), "Discord is off.");
                    return Profiler.delivery.test() ? message(c.getSource(), "Discord test queued.") : fail(c.getSource(), "Wait 30s between Discord tests.");
                }))
                .then(Commands.literal("send").executes(c -> {
                    if (Profiler.latest == null) return fail(c.getSource(), "No profile to send.");
                    if (Profiler.latest.diagnostics()!=null&&Profiler.latest.diagnostics().cpu().state().equals("pending"))return fail(c.getSource(),"CPU analysis is still running. Try again when it finishes.");
                    if (!Profiler.settings.discord().enabled()) return fail(c.getSource(), "Discord is off.");
                    if (!Profiler.settings.discord().manual().enabled()) return fail(c.getSource(), "Manual Discord sending is off.");
                    Profiler.delivery.capture(Profiler.latest, Profiler.detector.reason(), "manual"); return message(c.getSource(), "Discord delivery requested.");
                })))
            .then(Commands.literal("schedule").requires(LagSense::isAdmin)
                .executes(c -> message(c.getSource(), Profiler.scheduleStatus()))
                .then(Commands.literal("off").executes(c -> change(c.getSource(), () -> Profiler.setTimer(false, Profiler.settings.timer().intervalMinutes(), Profiler.settings.timer().captureSeconds()))))
                .then(Commands.literal("every").then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                    .executes(c -> change(c.getSource(), () -> Profiler.setTimer(true, IntegerArgumentType.getInteger(c, "minutes"), 30)))
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 120)).executes(c -> change(c.getSource(), () -> Profiler.setTimer(true, IntegerArgumentType.getInteger(c, "minutes"), IntegerArgumentType.getInteger(c, "seconds"))))))))
            .then(Commands.literal("spikes").requires(LagSense::isAdmin)
                .executes(c -> message(c.getSource(), Profiler.spikeStatus()))
                .then(Commands.literal("off").executes(c -> change(c.getSource(), () -> Profiler.setSpikes(false, null, null))))
                .then(Commands.literal("on").executes(c -> change(c.getSource(), () -> Profiler.setSpikes(true, null, null)))
                    .then(Commands.argument("thresholdMs", IntegerArgumentType.integer(1, 10000)).executes(c -> change(c.getSource(), () -> Profiler.setSpikes(true, (double)IntegerArgumentType.getInteger(c, "thresholdMs"), null)))
                        .then(Commands.argument("cooldownSeconds", IntegerArgumentType.integer(30, 86400)).executes(c -> change(c.getSource(), () -> Profiler.setSpikes(true, (double)IntegerArgumentType.getInteger(c, "thresholdMs"), IntegerArgumentType.getInteger(c, "cooldownSeconds"))))))))
            .then(Commands.literal("profile").requires(LagSense::isAdmin)
                .executes(c -> start(c.getSource(), 30))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 120)).executes(c -> start(c.getSource(), IntegerArgumentType.getInteger(c, "seconds")))))
            .then(Commands.literal("stop").requires(LagSense::isAdmin).executes(c->stopProfile(c.getSource())))
            .then(Commands.literal("report").executes(c->{
                if(c.getSource().getEntity() instanceof ServerPlayer player){if(Profiler.players!=null)Profiler.players.open(player);return 1;}
                if(!isAdmin(c.getSource()))return fail(c.getSource(),"Join a world to open the profiler.");
                return message(c.getSource(),Profiler.latest==null?"No saved profile.":"Latest profile: "+Profiler.latest.entries().size()+" targets | "+Profiler.latest.ticks()+" ticks");
            }))
            .then(Commands.literal("tp").requires(LagSense::isAdmin)
                .then(Commands.literal("location").then(Commands.argument("dimension",net.minecraft.commands.arguments.IdentifierArgument.id())
                    .then(Commands.argument("x",IntegerArgumentType.integer(-30000000,30000000)).then(Commands.argument("y",IntegerArgumentType.integer(-20000000,20000000)).then(Commands.argument("z",IntegerArgumentType.integer(-30000000,30000000))
                        .executes(c->teleportLocation(c.getSource(),net.minecraft.commands.arguments.IdentifierArgument.getId(c,"dimension").toString(),IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"y"),IntegerArgumentType.getInteger(c,"z"))))))))
                .then(Commands.literal("chunk").then(Commands.argument("dimension",net.minecraft.commands.arguments.IdentifierArgument.id()).then(Commands.argument("x",IntegerArgumentType.integer(-1875000,1874999)).then(Commands.argument("z",IntegerArgumentType.integer(-1875000,1874999))
                    .executes(c->teleportChunkLocation(c.getSource(),net.minecraft.commands.arguments.IdentifierArgument.getId(c,"dimension").toString(),IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"z")))))))
                .then(Commands.argument("report", LongArgumentType.longArg()).then(Commands.argument("index", IntegerArgumentType.integer(0, CaptureBudget.MAX_ENTRIES-1))
                    .executes(c -> teleport(c.getSource().getPlayerOrException(), LongArgumentType.getLong(c, "report"), IntegerArgumentType.getInteger(c, "index"))))))
            .then(Commands.literal("tpchunk").requires(LagSense::isAdmin)
                .then(Commands.argument("report", LongArgumentType.longArg()).then(Commands.argument("index", IntegerArgumentType.integer(0, CaptureBudget.MAX_ENTRIES-1))
                    .executes(c -> {
                        var player = c.getSource().getPlayerOrException();
                        return teleportChunk(player,LongArgumentType.getLong(c,"report"),IntegerArgumentType.getInteger(c,"index"));
                    }))))
            .then(Commands.literal("return").requires(LagSense::isAdmin).executes(c -> {
                var player = c.getSource().getPlayerOrException(); if(!isAdmin(c.getSource()))return fail(c.getSource(),"Operator access required.");var point = returns.remove(player.getUUID());syncReturn(player);
                if (point == null) return fail(c.getSource(), "No return position saved.");
                player.teleportTo(point.level, point.x, point.y, point.z, Set.of(), point.yaw, point.pitch, true); return message(c.getSource(), "Returned.");
            })));
        event.getDispatcher().register(Commands.literal("ls").redirect(root));
    }
    @FunctionalInterface private interface Edit { void run() throws java.io.IOException; }
    private int change(CommandSourceStack source, Edit edit) {
        try {
            var before = Profiler.settings; edit.run();
            if (!before.timer().equals(Profiler.settings.timer())) return message(source, "Timer: " + StatusText.timer(Profiler.settings.timer()));
            if (!before.spikes().equals(Profiler.settings.spikes())) return message(source, "Spike capture: " + StatusText.onOff(Profiler.settings.spikes().enabled()));
            return message(source, "Settings unchanged.");
        }
        catch (IllegalArgumentException error) { return fail(source, error.getMessage()); }
        catch (java.io.IOException error) { return fail(source, "Could not save settings."); }
    }
    private int start(CommandSourceStack source, int seconds) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return start(source,seconds,false);
    }
    private int start(CommandSourceStack source,int seconds,boolean brief)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        if (Profiler.busy()) return brief?captureFeedback(source,"A profile is already running."):fail(source, "A profile is already running.");
        Profiler.start(source.getEntity() instanceof ServerPlayer player ? player : null, seconds, "Manual");
        if (!Profiler.settings.backups().mode().equals("ignore") && (Profiler.backups.blocked(new BackupMonitor.Settings("defer_automatic", Profiler.settings.backups().graceSeconds()), System.nanoTime())))
            CommandOutput.warn(source, Profiler.backups.active().isEmpty() ? "Recent backup may affect results." : "Backup running. Results may be affected.");
        return brief?captureFeedback(source,"Profiling started · "+seconds+"s"):message(source, "Profiling… " + seconds + "s.");
    }
    private int stopProfile(CommandSourceStack source){
        if(!isAdmin(source))return fail(source,"Operator access required.");
        if(!Profiler.busy())return fail(source,"No profile is running.");
        return captureFeedback(source,Profiler.stopEarly()?"Stopping profile…":"Finishing profile…");
    }
    private int toggleProfile(CommandSourceStack source)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        if(!isAdmin(source))return fail(source,"Operator access required.");
        if(Profiler.active)return captureFeedback(source,Profiler.stopEarly()?"Stopping profile…":"Finishing profile…");
        if(Profiler.busy())return captureFeedback(source,"Finishing profile…");
        return start(source,30,true);
    }
    private int captureFeedback(CommandSourceStack source,String text){if(source.getEntity() instanceof ServerPlayer p)p.sendOverlayMessage(Component.literal(text));else message(source,text);return 1;}
    private int status(CommandSourceStack source) {
        Profiler.refreshBackups();
        return CommandOutput.table(source, "LagSense", StatusText.summary(Profiler.settings, Profiler.captureStatus(), Profiler.backups.status(Profiler.settings.backups(), System.nanoTime()), Profiler.delivery.summary(), Profiler.backups.blocked(Profiler.settings.backups(), System.nanoTime())));
    }
    private int teleport(ServerPlayer player,long report,int index) {
        if(!isAdmin(player.createCommandSourceStack()))return fail(player.createCommandSourceStack(),"Operator access required.");
        if(Profiler.latest!=null&&Profiler.latest.id()==report){
            if(index<0||index>=Profiler.latest.entries().size())return fail(player.createCommandSourceStack(),"Target unavailable.");
            return travel(player,Profiler.latest.entries().get(index));
        }
        if(Profiler.archives==null)return fail(player.createCommandSourceStack(),"Saved profile unavailable.");
        Profiler.archives.target(player,report,index,false,entry->travel(player,entry));return 1;
    }
    private int teleportChunk(ServerPlayer player,long report,int index){
        if(!isAdmin(player.createCommandSourceStack()))return fail(player.createCommandSourceStack(),"Operator access required.");
        if(Profiler.latest!=null&&Profiler.latest.id()==report){
            var chunks=HistoricalTargets.chunks(Profiler.latest);
            if(index<0||index>=chunks.size())return fail(player.createCommandSourceStack(),"Chunk unavailable.");
            return travel(player,HistoricalTargets.chunk(chunks.get(index),player.getBlockY()));
        }
        if(Profiler.archives==null)return fail(player.createCommandSourceStack(),"Saved profile unavailable.");
        Profiler.archives.target(player,report,index,true,entry->travel(player,entry));return 1;
    }
    private int teleportLocation(CommandSourceStack source,String dimension,int x,int y,int z)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        if(!isAdmin(source))return fail(source,"Operator access required.");
        var target=TeleportLocation.target(dimension,x,y,z,false);if(target==null)return fail(source,"Invalid target coordinates.");
        return travel(source.getPlayerOrException(),target,true);
    }
    private int teleportChunkLocation(CommandSourceStack source,String dimension,int x,int z)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        if(!isAdmin(source))return fail(source,"Operator access required.");
        if(TeleportLocation.chunkCommand(dimension,x,z)==null)return fail(source,"Invalid chunk coordinates.");
        var player=source.getPlayerOrException();var level=player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(dimension)));
        if(level==null)return fail(source,"Dimension unavailable.");
        int y=Math.clamp(player.getBlockY(),level.getMinY()+1,level.getMaxY()-3);
        return travel(player,TeleportLocation.target(dimension,x*16+8,y,z*16+8,true),true);
    }
    private int travel(ServerPlayer player,Report.Entry e){
        if(!isAdmin(player.createCommandSourceStack()))return fail(player.createCommandSourceStack(),"Operator access required.");
        return travel(player,e,false);
    }
    private int travel(ServerPlayer player, Report.Entry e,boolean requireSafe) {
        if(!isAdmin(player.createCommandSourceStack()))return fail(player.createCommandSourceStack(),"Operator access required.");
        if(!HistoricalTargets.positioned(e))return fail(player.createCommandSourceStack(),"Target location unavailable.");
        if(e.kind().equals("Entity")&&e.entityId()!=null){
            net.minecraft.world.entity.Entity live=null;
            for(var candidate:player.level().getServer().getAllLevels()){var found=candidate.getEntity(e.entityId());if(found!=null&&!found.isRemoved()){live=found;break;}}
            if(live!=null){
                var position=live.blockPosition();var current=ComparisonTargets.current(e,new ComparisonTargets.Live(live.level().dimension().identifier().toString(),net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(live.getType()).toString(),position.getX(),position.getY(),position.getZ(),live.isRemoved()));
                if(current!=null)e=current;
            }
        }
        var level = player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(e.dimension())));
        if (level == null) return fail(player.createCommandSourceStack(), "Dimension unavailable.");
        var pos=new BlockPos(e.x(),e.y(),e.z());
        boolean chunk=e.kind().equals("Chunk");
        if(!chunk&&!level.getWorldBorder().isWithinBounds(pos))return fail(player.createCommandSourceStack(),"Target is outside the world border.");
        if(!chunk&&(e.y()<level.getMinY()||e.y()>=level.getMaxY()))return fail(player.createCommandSourceStack(),"Target is outside the dimension's height limits.");
        if(chunk){
            var border=level.getWorldBorder();int left=(e.x()>>4)*16,top=(e.z()>>4)*16;
            if(left>=border.getMaxX()||left+16<=border.getMinX()||top>=border.getMaxZ()||top+16<=border.getMinZ())return fail(player.createCommandSourceStack(),"Chunk is outside the world border.");
        }
        var dimensions=player.getDimensions(net.minecraft.world.entity.Pose.STANDING);
        java.util.function.Predicate<LocalTeleport.Position> canStand=p->{
            var feet=BlockPos.containing(p.x(),p.y(),p.z());
            if(p.y()<level.getMinY()||p.y()+dimensions.height()>=level.getMaxY()||!level.getWorldBorder().isWithinBounds(feet))return false;
            double half=dimensions.width()/2.0;
            var box=new net.minecraft.world.phys.AABB(p.x()-half,p.y(),p.z()-half,p.x()+half,p.y()+dimensions.height(),p.z()+half);
            return level.noCollision(player,box)&&!level.containsAnyLiquid(box)&&level.getFluidState(feet.below()).isEmpty()
                &&!level.getBlockState(feet.below()).getCollisionShape(level,feet.below()).isEmpty();
        };
        var landing=chunk?chunkLanding(player,level,e.x()>>4,e.z()>>4,canStand):LocalTeleport.choose(e,canStand);
        if(landing==null)return fail(player.createCommandSourceStack(),"No safe position found in this chunk.");
        if(requireSafe&&!canStand.test(landing))return fail(player.createCommandSourceStack(),"No safe position found near the recorded location.");
        returns.put(player.getUUID(),new ReturnPoint(player.level(),player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot()));
        player.teleportTo(level,landing.x(),landing.y(),landing.z(),Set.of(),player.getYRot(),player.getXRot(),true);
        syncReturn(player);
        return message(player.createCommandSourceStack(), "Teleported to hotspot.\nReturn: /lagsense return");
    }
    private static LocalTeleport.Position chunkLanding(ServerPlayer player,net.minecraft.server.level.ServerLevel level,int x,int z,java.util.function.Predicate<LocalTeleport.Position> standing){
        var chunk=level.getChunk(x,z);
        int top=level.dimensionType().hasCeiling()?Math.min(level.getMaxY(),level.getMinY()+level.dimensionType().logicalHeight()):level.getMaxY();
        var terrain=new ChunkTeleport.Terrain(){
            public int minimumY(){return level.getMinY();}
            public int maximumY(){return top;}
            public int surfaceY(int bx,int bz){return chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,bx&15,bz&15)+1;}
        };
        var feet=new BlockPos.MutableBlockPos();
        return ChunkTeleport.choose(x,z,terrain,p->{
            feet.set((int)Math.floor(p.x()),(int)p.y(),(int)Math.floor(p.z()));
            var floor=feet.below();
            if(!level.getBlockState(floor).isFaceSturdy(level,floor,net.minecraft.core.Direction.UP))return false;
            if(unsafeFloor(level.getBlockState(floor))||unsafeFloor(level.getBlockState(feet))||unsafeFloor(level.getBlockState(feet.above())))return false;
            return standing.test(p);
        });
    }
    private static boolean unsafeFloor(net.minecraft.world.level.block.state.BlockState state){
        return state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)||state.is(net.minecraft.world.level.block.Blocks.CACTUS)
            ||state.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)||state.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
            ||state.is(net.minecraft.world.level.block.Blocks.FIRE)||state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
            ||state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)||state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
            ||state.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)||state.is(net.minecraft.world.level.block.Blocks.POINTED_DRIPSTONE)
            ||state.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)||state.is(net.minecraft.world.level.block.Blocks.END_PORTAL);
    }
}
