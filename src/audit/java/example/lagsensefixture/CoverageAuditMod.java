package example.lagsensefixture;

import net.sprocketgames.lagsense.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.*;

/** Controlled server workloads in a disposable world. No access to the user's modpack/world. */
@Mod("lagsense_audit")
public final class CoverageAuditMod {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("lagsense_audit");
    private static final DeferredBlock<CountingBlock> RANDOM=BLOCKS.register("observed",id->new CountingBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK,id)).randomTicks()));
    private static final Map<BlockPos,Long> dispatched=new HashMap<>(),random=new HashMap<>(),scheduled=new HashMap<>();
    private static final BlockPos FURNACE=new BlockPos(1,80,1),CUSTOM=new BlockPos(3,80,1),WRAPPED=new BlockPos(5,80,1),PASSIVE=new BlockPos(7,80,1);
    private static BlockPos RANDOM_A=new BlockPos(1,82,4),RANDOM_B=new BlockPos(2,82,4),UNOBSERVED=new BlockPos(3,82,4);
    private static int cycles,pre,post;
    private static long preNanos,postNanos;
    private static UUID entity;
    private static long entityCalls;
    private static boolean started,done,randomForced;
    private static long netherCalls;
    private static final java.util.concurrent.atomic.AtomicBoolean workersRunning=new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean temporaryRunning=new java.util.concurrent.atomic.AtomicBoolean();
    private static final List<Thread> workers=new ArrayList<>();
    private static Thread temporary;
    private static boolean eventsForced,workersStopped;
    private static boolean shortcutStopped;
    private static long shortcutDeadline;
    private static int enrichmentTicks;
    private static long dynamicCalls,nestedOuter,nestedInner;
    private static final java.util.concurrent.atomic.AtomicLong workerEvents=new java.util.concurrent.atomic.AtomicLong();
    private static volatile long blackhole;
    private static final java.util.concurrent.atomic.AtomicReference<String> discordHtml=new java.util.concurrent.atomic.AtomicReference<>();
    public static class Pulse extends Event {final int round;Pulse(int round){this.round=round;}}
    public static class NestedOuter extends Event {}
    public static class NestedInner extends Event {}
    public static class WorkerPulse extends Event {}
    public static class Compat extends Event {}
    public static class CompatChild extends Compat {}
    public static class Cancelled extends Event implements ICancellableEvent {private boolean cancelled;public boolean isCanceled(){return cancelled;}public void setCanceled(boolean value){cancelled=value;}}
    public static final class LateExpensive implements java.util.function.Consumer<Pulse>{public void accept(Pulse event){work();}}
    private static void busyWorker(java.util.concurrent.atomic.AtomicBoolean running){var event=new WorkerPulse();while(running.get()){long value=blackhole;for(int i=0;i<200000;i++)value=(value*1664525+1013904223)^i;blackhole=value;NeoForge.EVENT_BUS.post(event);java.util.concurrent.locks.LockSupport.parkNanos(250000);}}
    private static void stopWorkers()throws InterruptedException{workersRunning.set(false);temporaryRunning.set(false);for(var worker:workers)worker.join(2000);if(temporary!=null)temporary.join(2000);workersStopped=true;}

    public CoverageAuditMod(IEventBus bus){
        BLOCKS.register(bus);
        for(int i=0;i<9000;i++){final int id=i;NeoForge.EVENT_BUS.addListener(Pulse.class,event->{if(event.round==1&&id==0)NeoForge.EVENT_BUS.addListener(Pulse.class,late->{dynamicCalls++;});});}
        NeoForge.EVENT_BUS.addListener(Pulse.class,new LateExpensive());
        NeoForge.EVENT_BUS.addListener(NestedOuter.class,event->{nestedOuter++;NeoForge.EVENT_BUS.post(new NestedInner());});
        NeoForge.EVENT_BUS.addListener(NestedInner.class,event->{nestedInner++;work();});
        NeoForge.EVENT_BUS.addListener(WorkerPulse.class,event->workerEvents.incrementAndGet());
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,ServerTickEvent.Pre.class,this::pre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,ServerTickEvent.Post.class,this::post);
    }
    private void pre(ServerTickEvent.Pre event){if(Profiler.active){pre++;preNanos+=work();
        if(pre==10){temporaryRunning.set(true);temporary=Thread.ofPlatform().name("fixture-short-lived").start(()->busyWorker(temporaryRunning));}
        if(pre==55)temporaryRunning.set(false);
    }}
    private void post(ServerTickEvent.Post event){
        if(Profiler.active){post++;postNanos+=work();if(!randomForced){try{controlledRandom(event.getServer().overworld());randomForced=true;}catch(Exception error){throw new RuntimeException(error);}}}
        if(Profiler.active&&!eventsForced){eventsForced=true;NeoForge.EVENT_BUS.post(new Pulse(1));NeoForge.EVENT_BUS.post(new Pulse(2));NeoForge.EVENT_BUS.post(new NestedOuter());}
        if(done)return;
        var server=event.getServer();
        try{
            if(!started&&++cycles>=10){
                if(!PlayerWorkflowFixture.run(server))return;
                if(!EntityTrackingFixture.ready(server))return;
                prepare(server);started=true;
                require(server.getCommands().getDispatcher().execute("lagsense profile",server.createCommandSourceStack())==1&&Profiler.active,"Shortcut command starts the normal manual capture");
                shortcutDeadline=deadline();
                try{server.getCommands().getDispatcher().execute("lagsense stop",server.createCommandSourceStack().withPermission(net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS));throw new AssertionError("A non-operator stopped the capture");}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){}
                var stop=Profiler.class.getDeclaredField("stopRequested");stop.setAccessible(true);require(!stop.getBoolean(null)&&deadline()==shortcutDeadline,"Denied stop leaves the running capture untouched");
                workersRunning.set(true);for(int i=0;i<2;i++)workers.add(Thread.ofPlatform().name("fixture-worker").start(()->busyWorker(workersRunning)));return;
            }
            if(started&&!shortcutStopped&&pre>=160){
                var dispatcher=server.getCommands().getDispatcher();var source=server.createCommandSourceStack();
                require(dispatcher.execute("lagsense stop",source)==1&&Profiler.active,"Stop waits for the normal end-of-tick completion");
                require(deadline()==shortcutDeadline&&!Profiler.stopEarly(),"Stop preserves capture identity and repeated requests do not restart it");
                require(dispatcher.execute("lagsense stop",source)==1&&deadline()==shortcutDeadline,"Already-finishing requests remain harmless");shortcutStopped=true;
            }
            if(started&&!Profiler.busy()&&Profiler.latest!=null){
                if(!workersStopped)stopWorkers();
                if(Profiler.latest.diagnostics().cpu().state().equals("pending")&&++enrichmentTicks<300)return;
                if(discordHtml.get()==null&&++enrichmentTicks<400)return;
                verify(server);done=true;server.halt(false);
            }
        }catch(Throwable error){
            workersRunning.set(false);temporaryRunning.set(false);done=true;try{Files.writeString(Path.of("audit-failure.txt"),error.toString()+"\n"+Arrays.toString(error.getStackTrace()));}catch(Exception ignored){}
            error.printStackTrace();server.halt(false);
        }
    }
    private static long deadline()throws Exception{var field=Profiler.class.getDeclaredField("deadline");field.setAccessible(true);return field.getLong(null);}
    private static void dispatchCompatibility()throws Exception{
        require(NeoForge.EVENT_BUS instanceof ProfilingEventBus,"The actual game bus is wrapped before registrations");
        var order=new ArrayList<String>();int[] errors={0};var failure=new IllegalStateException("expected fixture exception");
        var bus=ProfilingEventBus.wrap(BusBuilder.builder().setExceptionHandler((b,e,listeners,index,error)->{require(error==failure,"Original failure reaches configured bus handler");errors[0]++;}).build());
        java.util.function.Consumer<Compat> high=e->order.add("high");bus.addListener(EventPriority.HIGH,Compat.class,high);bus.addListener(Compat.class,e->order.add("normal"));
        bus.addListener(Cancelled.class,e->order.add("uncancelled"));bus.addListener(true,Cancelled.class,e->order.add("cancelled"));
        bus.post(new CompatChild());var expected=List.copyOf(order);order.clear();
        try(var timing=new CallbackTimings(bus,Thread.currentThread(),c->"fixture")){
            bus.post(new CompatChild());require(order.equals(expected)&&timing.snapshot().stream().mapToLong(Report.Callback::calls).sum()==2,"Real hook preserves inherited listener order and records each invocation once");
            order.clear();var cancelled=new Cancelled();cancelled.setCanceled(true);bus.post(cancelled);require(order.equals(List.of("cancelled")),"Cancellation filters remain unchanged");
            bus.unregister(high);bus.addListener(Compat.class,e->order.add("late"));order.clear();bus.post(new CompatChild());require(order.equals(List.of("normal","late")),"Dynamic registration/removal updates do not wait for a profiling refresh");
            bus.addListener(Compat.class,e->{throw failure;});try{bus.post(new CompatChild());throw new AssertionError("Expected callback failure");}catch(IllegalStateException caught){require(caught==failure&&errors[0]==1,"Original exception and bus exception handler preserved");}
        }
        long before=System.nanoTime();var event=new CompatChild();bus=ProfilingEventBus.wrap(BusBuilder.builder().build());bus.addListener(Compat.class,e->blackhole++);for(int i=0;i<200000;i++)bus.post(event);
        System.out.println("Inactive dispatch fixture: "+((System.nanoTime()-before)/200000.0)+" ns/post (isolated smoke measurement)");
    }
    /** Exercise the real Brigadier tree and the same server-side landing checks as in-game TP. */
    private static void teleportCommands(MinecraftServer server)throws Exception{
        var level=server.overworld();var nether=server.getLevel(Level.NETHER);
        class CommandPlayer extends net.neoforged.neoforge.common.util.FakePlayer {
            boolean operator;
            CommandPlayer(){super(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"HtmlTpAudit"));}
            @Override public boolean teleportTo(net.minecraft.server.level.ServerLevel target,double x,double y,double z,Set<net.minecraft.world.entity.Relative> relative,float yaw,float pitch,boolean resetCamera){
                boolean moved=super.teleportTo(target,x,y,z,relative,yaw,pitch,resetCamera);
                // FakePlayer's packet listener discards same-level teleports instead of updating its pose.
                if(moved)snapTo(x,y,z,yaw,pitch);
                return moved;
            }
            @Override public net.minecraft.server.permissions.PermissionSet permissions(){return operator?net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER:net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS;}
        }
        var player=new CommandPlayer();player.snapTo(80.5,81,80.5,0,0);
        var dispatcher=server.getCommands().getDispatcher();
        java.util.function.Function<String,Integer> run=command->{try{return dispatcher.execute(command,player.createCommandSourceStack());}catch(com.mojang.brigadier.exceptions.CommandSyntaxException rejected){return 0;}};
        require(run.apply("lagsense tp location minecraft:overworld 80 81 80")==0,"Non-operator cannot use coordinate TP");
        require(run.apply("lagsense tp chunk minecraft:overworld 5 5")==0,"Non-operator cannot use chunk TP");
        require(run.apply("lagsense stop")==0&&!Profiler.busy(),"Non-operator cannot start or stop a capture through the shortcut command");
        var replies=new ArrayList<ServerSettingsPayload>();
        ServerSettingsForms.request(player,new ServerSettingsRequest("advanced",""),replies::add);
        require(replies.getLast().json().isEmpty(),"Non-operators cannot read Advanced or effective budgets");
        player.operator=true;
        ServerSettingsForms.request(player,new ServerSettingsRequest("advanced",""),replies::add);
        require(!replies.getLast().json().isEmpty()&&Profiler.JSON.fromJson(replies.getLast().json(),ServerSettingsForms.View.class).advanced().limits().mode().equals("adaptive"),"Operators receive the validated profiling limit settings");
        player.operator=false;
        ServerSettingsForms.request(player,new ServerSettingsRequest("advanced",Profiler.JSON.toJson(new ServerSettingsForms.Edit(ServerSettingsForms.view(Profiler.settings),ServerSettingsForms.view(Profiler.settings),""))),replies::add);
        require(replies.getLast().json().isEmpty()&&!replies.getLast().saved(),"Fresh operator revocation prevents editing or reading Advanced settings");
        player.operator=true;
        for(var dimension:List.of(level,nether)){
            dimension.getChunk(-2,-3);dimension.getChunk(5,5);
            for(var centre:List.of(new BlockPos(-17,81,-33),new BlockPos(88,81,88))){
                for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=-1;y<=3;y++)dimension.setBlockAndUpdate(centre.offset(x,y,z),(y==-1?Blocks.STONE:Blocks.AIR).defaultBlockState());
            }
        }
        require(!LagSense.canReturn(player),"Return starts unavailable before a successful hotspot teleport");
        require(run.apply("lagsense tp location fixture:missing -17 81 -33")==0,"Missing dimension rejected");
        require(run.apply("lagsense tp location minecraft:overworld 30000001 81 0")==0,"Out-of-range arguments rejected");
        require(run.apply("lagsense tp location minecraft:overworld -17 20000000 -33")==0,"Dimension height checked on server");
        require(run.apply("lagsense tp location minecraft:overworld -17 81 -33")==1,"Capture-independent negative-coordinate TP succeeds near recorded Y");
        require(LagSense.canReturn(player),"Successful hotspot TP makes the server return destination available");
        require(run.apply("lagsense return")==1&&!LagSense.canReturn(player),"Using Return consumes the stored destination");
        require(run.apply("lagsense return")==0&&!LagSense.canReturn(player),"Missing destinations keep Return unavailable");

        require(run.apply("lagsense tp location minecraft:the_nether -17 81 -33")==1,"Coordinate TP resolves the recorded dimension");
        require(player.level()==nether,"Cross-dimension TP uses the requested level");
        player.snapTo(player.getX(),81,player.getZ(),0,0);
        require(run.apply("lagsense tp chunk minecraft:overworld 5 5")==1,"Chunk-centre fallback uses safe local standing space");
        player.snapTo(15.25,86.5,-4.75,37.5f,-13.25f);
        var originalLevel=player.level();double oldX=player.getX(),oldY=player.getY(),oldZ=player.getZ();float oldYaw=player.getYRot(),oldPitch=player.getXRot();
        for(var dimension:List.of(level,nether,server.getLevel(Level.END))){
            dimension.getChunk(6,6);
            for(int x=96;x<112;x++)for(int z=96;z<112;z++){
                dimension.setBlockAndUpdate(new BlockPos(x,200,z),Blocks.STONE.defaultBlockState());
                dimension.setBlockAndUpdate(new BlockPos(x,201,z),Blocks.AIR.defaultBlockState());
                dimension.setBlockAndUpdate(new BlockPos(x,202,z),Blocks.AIR.defaultBlockState());
            }
            if(dimension!=nether){
                for(int x=103;x<=104;x++)for(int z=103;z<=104;z++)dimension.setBlockAndUpdate(new BlockPos(x,200,z),Blocks.MAGMA_BLOCK.defaultBlockState());
                require(run.apply("lagsense tp chunk "+dimension.dimension().identifier()+" 6 6")==1,"Surface chunk teleport works in Overworld and End");
                require(player.level()==dimension&&player.getY()==201&&player.getBlockX()>=96&&player.getBlockX()<112&&player.getBlockZ()>=96&&player.getBlockZ()<112,"Chunk surface destination: expected "+dimension.dimension().identifier()+" Y 201, got "+player.level().dimension().identifier()+" "+player.position());
                require(!dimension.getBlockState(player.blockPosition().below()).is(Blocks.MAGMA_BLOCK),"Unsafe central magma is skipped for another surface position");
                require(run.apply("lagsense return")==1&&player.level()==originalLevel&&player.getX()==oldX&&player.getY()==oldY&&player.getZ()==oldZ&&player.getYRot()==oldYaw&&player.getXRot()==oldPitch,"Chunk Return restores exact coordinates, dimension, yaw and pitch");
            }
        }
        require(run.apply("lagsense tp chunk minecraft:the_nether 5 5")==1&&player.level()==nether&&player.getY()<128,"Nether chunk TP uses an interior floor below the bedrock roof");
        require(run.apply("lagsense return")==1,"Nether chunk Return restores the previous dimension");
        var blocked=new BlockPos(80,81,80);for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-2;y<=4;y++)level.setBlockAndUpdate(blocked.offset(x,y,z),Blocks.STONE.defaultBlockState());
        require(run.apply("lagsense tp location minecraft:overworld 80 81 80")==0,"No safe landing fails instead of teleporting into solid blocks");
        player.operator=false;
        require(!LagSense.canReturn(player),"Operator revocation hides any saved return destination");
        require(run.apply("lagsense tp location minecraft:overworld -17 81 -33")==0,"Permission changes are checked freshly");
        player.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        System.out.println("HTML TP command fixture passed: operator-only, fresh permissions, negative coordinates, dimensions, bounds, chunk fallback and unsafe-landing rejection.");
    }
    private static long work(){long start=System.nanoTime();while(System.nanoTime()-start<1_000_000)Thread.onSpinWait();return System.nanoTime()-start;}
    private static void prepare(MinecraftServer server)throws Exception{
        dispatchCompatibility();
        teleportCommands(server);
        EntityTrackingFixture.run(server);
        var defaults=Profiler.settings;Profiler.settings=new ServerSettings(defaults.timer(),defaults.spikes(),false,defaults.history(),defaults.discord(),defaults.backups(),true,true);
        if(Profiler.delivery!=null)Profiler.delivery.close();
        var section=new ServerSettings.Delivery(true,0,5,0,0,"either","target","html","","degraded");
        var discord=new ServerSettings.Discord(true,"https://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwx","Audit server",section,section,section,128);
        var sleeper=Class.forName("net.sprocketgames.lagsense.DiscordDelivery$Sleeper");
        var constructor=DiscordDelivery.class.getDeclaredConstructor(ServerSettings.Discord.class,DiscordDelivery.Transport.class,sleeper,java.util.function.Consumer.class);constructor.setAccessible(true);
        var noSleep=java.lang.reflect.Proxy.newProxyInstance(sleeper.getClassLoader(),new Class<?>[]{sleeper},(p,m,args)->null);
        Profiler.delivery=constructor.newInstance(discord,(DiscordDelivery.Transport)(uri,body)->{discordHtml.set(new String(body.bytes(),java.nio.charset.StandardCharsets.UTF_8));return new DiscordDelivery.Response(204,0);},noSleep,(java.util.function.Consumer<String>)System.out::println);
        var level=server.overworld();level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED,0,server);level.setChunkForced(0,0,true);level.getChunk(0,0);
        for(var pos:List.of(FURNACE,CUSTOM,WRAPPED,PASSIVE,RANDOM_A,RANDOM_B,UNOBSERVED))level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(FURNACE,Blocks.FURNACE.defaultBlockState());level.setBlockAndUpdate(CUSTOM,Blocks.CHEST.defaultBlockState());level.setBlockAndUpdate(WRAPPED,Blocks.CHEST.defaultBlockState());
        // Only actual random callbacks are counted; eligible but unselected positions must stay absent.
        level.setBlockAndUpdate(RANDOM_A,RANDOM.get().defaultBlockState());level.setBlockAndUpdate(RANDOM_B,RANDOM.get().defaultBlockState());level.setBlockAndUpdate(UNOBSERVED,RANDOM.get().defaultBlockState());
        var nether=server.getLevel(Level.NETHER);nether.setChunkForced(0,0,true);nether.getChunk(0,0);nether.setBlockAndUpdate(CUSTOM,Blocks.CHEST.defaultBlockState());nether.addBlockEntityTicker(new TickingBlockEntity(){public void tick(){if(Profiler.active)netherCalls++;}public boolean isRemoved(){return false;}public BlockPos getPos(){return CUSTOM;}public String getType(){return "minecraft:chest";}});
        level.addBlockEntityTicker(ticker(level,CUSTOM,false));level.addBlockEntityTicker(ticker(level,WRAPPED,true));
        var stand=new ArmorStand(level,9,81,1){@Override public void tick(){if(Profiler.active)entityCalls++;super.tick();}};stand.setNoGravity(true);entity=stand.getUUID();level.addFreshEntity(stand);
        // Scheduled callbacks have a deterministic execution count, unlike random selection.
        level.scheduleTick(RANDOM_A,RANDOM.get(),5);level.scheduleTick(RANDOM_B,RANDOM.get(),6);
        var water=new BlockPos(10,80,10);level.setBlockAndUpdate(water,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(water,Blocks.WATER.defaultBlockState());level.scheduleTick(water,net.minecraft.world.level.material.Fluids.WATER,5);
    }
    private static void controlledRandom(ServerLevel level)throws Exception{
        var chunk=level.getChunk(2,2);level.setBlockAndUpdate(new BlockPos(33,82,33),RANDOM.get().defaultBlockState());
        int seed=0;BlockPos chosen=null;
        for(int i=0;i<chunk.getSections().length;i++)if(chunk.getSections()[i].isRandomlyTicking()){
            seed=seed*3+1013904223;int val=seed>>2;
            if(chunk.getSectionYFromSectionIndex(i)==5)chosen=new BlockPos(32+(val&15),80+(val>>16&15),32+(val>>8&15));
        }
        require(chosen!=null,"Controlled random section exists");RANDOM_A=chosen;RANDOM_B=chosen.offset(chosen.getX()%16==15?-1:1,0,0);UNOBSERVED=chosen.offset(0,0,chosen.getZ()%16==15?-1:1);
        for(var pos:List.of(RANDOM_A,RANDOM_B,UNOBSERVED))level.setBlockAndUpdate(pos,RANDOM.get().defaultBlockState());
        var randomField=Level.class.getDeclaredField("random");randomField.setAccessible(true);var random=(RandomSource)randomField.get(level);
        var seedField=Level.class.getDeclaredField("randValue");seedField.setAccessible(true);
        require(RandomSource.create(42).nextInt(48)!=0,"No precipitation side selection alters the deterministic random-position sequence");
        for(int i=0;i<2;i++){seedField.setInt(level,0);random.setSeed(42);level.tickChunk(chunk,1);}
    }
    private static TickingBlockEntity ticker(ServerLevel level,BlockPos pos,boolean wrapped){
        Runnable body=()->{if(Profiler.active)dispatched.merge(pos,1L,Long::sum);};Runnable run=wrapped?()->{body.run();}:body;
        return new TickingBlockEntity(){public void tick(){run.run();}public boolean isRemoved(){return false;}public BlockPos getPos(){return pos;}public String getType(){return new String("minecraft:chest");}};
    }
    private static void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    private static void verify(MinecraftServer server)throws Exception{
        var report=Profiler.latest;var entries=report.entries();require(report.ticks()>20,"Capture has multiple real server ticks");
        var performance=report.diagnostics().performance();require(performance!=null&&performance.valid()&&performance.measuredTicks()==report.ticks()&&Math.abs(report.tps()-performance.averageTps())<1e-9,"Actual tick-period counters survive JFR enrichment without changing the measurement interval");
        require(performance.peakNanos()/1e6>=report.mspt()&&performance.windows()>0&&performance.durationSeconds()>0,"Real capture records peak work, valid TPS windows and actual duration");
        String sent=discordHtml.get();require(sent!=null&&sent.contains("<!doctype html>")&&sent.contains("LagSense | Manual Capture")&&sent.contains(Long.toString(report.id()))&&!sent.contains("CPU sample analysis pending."),"Automatic delivery waits for the actual completed CPU-enriched capture and attaches its full HTML");
        String marker="<script id=\"report-data\" type=\"application/json\">";int first=sent.indexOf(marker)+marker.length(),last=sent.indexOf("</script>",first);
        var document=com.google.gson.JsonParser.parseString(sent.substring(first,last)).getAsJsonObject();var exported=Profiler.JSON.fromJson(document.get("data"),Report.class);
        require(exported.id()==report.id()&&exported.entries().equals(report.entries())&&exported.diagnostics().callbacks().equals(report.diagnostics().callbacks())&&exported.diagnostics().cpu().equals(report.diagnostics().cpu())&&exported.diagnostics().background().equals(report.diagnostics().background())&&exported.diagnostics().performance().equals(performance),"The actual Discord attachment contains every retained target, event handler, server/background call path and exact performance interval");
        System.out.println("Discord server integration passed: measured interval/peak/window counters, sampler enrichment, shared automatic delivery, same capture identity and complete offline HTML; mock transport only.");
        require(shortcutStopped&&!report.recording()&&report.secondsRemaining()==0&&report.id()==shortcutDeadline&&deadline()==shortcutDeadline,"Early stop produces a completed normal report without replacing its capture identity");
        var disk=Profiler.JSON.fromJson(Files.readString(Profiler.directory.resolve("latest.json")),Report.class);require(disk.id()==report.id()&&disk.ticks()==report.ticks()&&!disk.recording(),"Early completion persists the full final report through the normal save path");
        System.out.println("Shortcut capture fixture passed: server permissions, manual start, early stop, repeated finishing requests and normal completed report/history pipeline.");
        for(var pos:List.of(CUSTOM,WRAPPED)){
            var found=entries.stream().filter(e->e.kind().equals("Block")&&e.x()==pos.getX()&&e.y()==pos.getY()&&e.z()==pos.getZ()&&e.dimension().equals("minecraft:overworld")).toList();
            require(found.size()==1&&found.getFirst().calls()==dispatched.getOrDefault(pos,0L)&&found.getFirst().calls()>20,"Custom/delegated ticker captured once per real dispatch at "+pos);
        }
        require(entries.stream().filter(e->e.kind().equals("Block")&&e.dimension().equals("minecraft:the_nether")&&e.x()==CUSTOM.getX()&&e.y()==CUSTOM.getY()&&e.z()==CUSTOM.getZ()).mapToLong(Report.Entry::calls).sum()==netherCalls&&netherCalls>20,"Same coordinates in another loaded dimension retain independent work");
        require(entries.stream().anyMatch(e->e.kind().equals("Block")&&e.type().equals("minecraft:furnace")&&e.x()==FURNACE.getX()),"Vanilla bound ticker remains captured");
        require(entries.stream().noneMatch(e->e.x()==PASSIVE.getX()&&e.y()==PASSIVE.getY()&&e.z()==PASSIVE.getZ()),"Passive stone creates no fake zero entry");
        require(entries.stream().filter(e->entity.equals(e.entityId())).mapToLong(Report.Entry::calls).sum()==entityCalls&&entityCalls>20,"Entity work retains actual UUID and call count");
        for(var pos:List.of(new BlockPos(1,82,4),new BlockPos(2,82,4)))require(entries.stream().filter(e->e.kind().equals("Scheduled block")&&e.x()==pos.getX()&&e.y()==pos.getY()&&e.z()==pos.getZ()).mapToLong(Report.Entry::calls).sum()==scheduled.getOrDefault(pos,0L),"Scheduled callback count matches executed work");
        require(entries.stream().anyMatch(e->e.kind().equals("Scheduled fluid")),"Scheduled flowing-fluid callbacks are captured");
        for(var pos:List.of(RANDOM_A,RANDOM_B,UNOBSERVED))require(entries.stream().filter(e->e.kind().equals("Random block")&&e.x()==pos.getX()&&e.y()==pos.getY()&&e.z()==pos.getZ()).mapToLong(Report.Entry::calls).sum()==random.getOrDefault(pos,0L),"Only genuinely observed random callbacks become positioned results");
        require(random.getOrDefault(RANDOM_A,0L)==2&&random.getOrDefault(RANDOM_B,0L)==0&&random.getOrDefault(UNOBSERVED,0L)==0,"Exactly one of three similar eligible blocks received two deterministic random updates");
        var events=report.diagnostics().callbacks();
        var pulse=events.stream().filter(e->e.event().equals(Pulse.class.getName())).toList();
        require(pulse.size()==9002&&pulse.stream().mapToLong(Report.Callback::calls).sum()==18003,"First-use and dynamically registered event handlers are captured completely above all previous cutoffs");
        require(pulse.stream().map(Report.Callback::handlerId).distinct().count()==9002,"Registration identities remain distinct for identical handler labels");
        require(pulse.stream().anyMatch(e->e.method().contains("LateExpensive")&&e.nanos()>=2000000),"Expensive handler beyond old cutoffs is retained");
        require(dynamicCalls==1&&nestedOuter==1&&nestedInner==1,"Dynamic registration and nested execution counts remain unchanged");
        var outer=events.stream().filter(e->e.event().equals(NestedOuter.class.getName())).findFirst().orElseThrow();var inner=events.stream().filter(e->e.event().equals(NestedInner.class.getName())).findFirst().orElseThrow();
        require(outer.calls()==1&&inner.calls()==1&&outer.nanos()>=inner.nanos(),"Nested handler timings remain inclusive without duplicate instrumentation");
        require(events.stream().noneMatch(e->e.event().equals(WorkerPulse.class.getName()))&&workerEvents.get()>0,"Background dispatches are not added to direct server event timings");
        var background=report.diagnostics().background();require(background!=null&&background.state().equals("ready")&&report.diagnostics().cpu().samples()>0,"Actual JFR capture contains server and background execution samples");
        for(var worker:workers)require(background.thread(worker.threadId())!=null&&background.thread(worker.threadId()).cpu().samples()>0,"Duplicate-name worker has its own retained sampled stack");
        require(temporary!=null&&!temporary.isAlive()&&background.thread(temporary.threadId())!=null,"A worker created and stopped during capture retains its actual identity");
        require(background.threads().stream().noneMatch(w->w.name().startsWith("LagSense ")),"Analysis/reader threads excluded");
        require(events.stream().filter(e->e.method().contains("CoverageAuditMod#")&&e.event().endsWith("ServerTickEvent$Pre")).mapToLong(Report.Callback::calls).sum()==pre,"All early Pre callbacks are measured");
        require(events.stream().filter(e->e.method().contains("CoverageAuditMod#")&&e.event().endsWith("ServerTickEvent$Post")).mapToLong(Report.Callback::calls).sum()==post,"All late Post callbacks are measured before finalization");
        require(report.mspt()*report.ticks()*1_000_000>=preNanos+postNanos,"Whole-tick MSPT includes early and late shared-handler work");
        var coverage=report.diagnostics().coverage();require(coverage!=null,"Capture retains overlap-aware coverage");
        require(Math.abs(coverage.totalNanos()-report.mspt()*report.ticks()*1_000_000)<1,"Coverage denominator equals measured MSPT window");
        require(coverage.targetNanos()>0&&coverage.eventNanos()>=preNanos+postNanos&&coverage.overlapNanos()>0,"Targets and early/late events are covered with nested overlap deducted");
        require(coverage.outsideNanos()==coverage.totalNanos()-coverage.targetNanos()-coverage.eventNanos()+coverage.overlapNanos(),"No double-counted coverage");
        var view=report.view(true,List.of());var transferred=FullReportWire.decode(FullReportWire.encode(view));require(view.equals(transferred),"Controlled work survives full transfer");
        var result=new LinkedHashMap<String,Object>();result.put("result","passed");result.put("ticks",report.ticks());result.put("targets",entries.size());result.put("customCalls",dispatched);result.put("entityCalls",entityCalls);result.put("netherCalls",netherCalls);result.put("pre",pre);result.put("post",post);result.put("mspt",report.mspt());result.put("coverage",coverage);result.put("eventHandlers",events.size());result.put("pulseHandlers",pulse.size());result.put("background",background);result.put("randomObserved",random);result.put("scheduledObserved",scheduled);
        Files.writeString(Path.of("audit-result.json"),Profiler.JSON.toJson(result));
    }
    public static final class CountingBlock extends Block {
        CountingBlock(BlockBehaviour.Properties properties){super(properties);}
        @Override public void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource rng){if(Profiler.active)random.merge(pos.immutable(),1L,Long::sum);}
        @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource rng){if(Profiler.active)scheduled.merge(pos.immutable(),1L,Long::sum);}
    }
}
