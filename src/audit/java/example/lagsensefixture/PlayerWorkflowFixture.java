package example.lagsensefixture;

import net.sprocketgames.lagsense.*;

import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Actual command/archive/permission workflow in the disposable audit server. */
public final class PlayerWorkflowFixture {
    private static int stage,clock,wait;
    private static final List<CustomPacketPayload> packets=new ArrayList<>();
    private static boolean owns=true;
    private static ProfilerUi ui,oldUi;private static ArchiveService archive,oldArchive;private static ReportDelivery oldDelivery;private static Report oldLatest;
    private static TestPlayer player,operator;
    private static Report source;private static ArmorStand entity;private static Path areas;
    private static final long CAPTURE=8001;
    static final class TestPlayer extends FakePlayer {
        boolean op;
        TestPlayer(MinecraftServer server,String name,boolean op){super(server.overworld(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),name));this.op=op;}
        @Override public PermissionSet permissions(){return op?LevelBasedPermissionSet.GAMEMASTER:PermissionSet.NO_PERMISSIONS;}
    }
    private static void check(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    private static Report.Entry entry(String type,String dim,int x,int z){return new Report.Entry("Block",type,dim,x,80,z,10000,20);}
    private static ProfilerStatePayload.State state(){return packets.reversed().stream().filter(ProfilerStatePayload.class::isInstance).map(ProfilerStatePayload.class::cast).map(p->Profiler.JSON.fromJson(p.json(),ProfilerStatePayload.State.class)).findFirst().orElseThrow();}
    private static List<ReportPayload> reports(){return packets.stream().filter(ReportPayload.class::isInstance).map(ReportPayload.class::cast).toList();}
    private static Report report(){return Profiler.JSON.fromJson(reports().getLast().json(),Report.class);}
    private static int execute(MinecraftServer server,TestPlayer p,String command){try{return server.getCommands().getDispatcher().execute(command,p.createCommandSourceStack());}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){return 0;}}
    private static ProfilerUi create(MinecraftServer server,java.util.function.Function<UUID,ServerPlayer> lookup,java.util.function.BiConsumer<ServerPlayer,CustomPacketPayload> transport,java.util.function.BiFunction<ServerPlayer,List<PublicArea>,BaseAccess> access)throws Exception{
        var constructor=ProfilerUi.class.getDeclaredConstructor(MinecraftServer.class,java.util.function.Function.class,java.util.function.BiConsumer.class,java.util.function.BiFunction.class);constructor.setAccessible(true);return constructor.newInstance(server,lookup,transport,access);
    }
    private static void mark(ServerPlayer p,long capture,int index)throws Exception{var method=ProfilerUi.class.getDeclaredMethod("mark",ServerPlayer.class,long.class,int.class);method.setAccessible(true);method.invoke(ui,p,capture,index);}
    public static boolean run(MinecraftServer server)throws Exception{
        if(stage==99)return true;if(++clock>500)throw new AssertionError("Player workflow fixture timed out at stage "+stage);
        if(stage==0){
            oldUi=Profiler.players;oldArchive=Profiler.archives;oldDelivery=Profiler.reportDelivery;oldLatest=Profiler.latest;
            player=new TestPlayer(server,"ScopedViewer",false);operator=new TestPlayer(server,"ScopedOperator",true);
            var directory=Path.of("player-workflow-fixture");Files.createDirectories(directory);areas=directory.resolve("areas.json");
            Files.writeString(areas,Profiler.JSON.toJson(List.of(new PublicArea("workshop","minecraft:the_nether",81,81,82,82))));Profiler.areas.load(directory);
            server.overworld().setChunkForced(0,0,true);server.overworld().setChunkForced(3,3,true);server.overworld().getChunk(0,0);server.overworld().getChunk(3,3);
            entity=new ArmorStand(server.overworld(),4,81,4);entity.setNoGravity(true);server.overworld().addFreshEntity(entity);
            var targets=List.of(entry("fixture:owned","minecraft:overworld",1,1),entry("fixture:public","minecraft:the_nether",81,81),entry("fixture:private","minecraft:overworld",49,49),entry("fixture:outside_public","minecraft:the_nether",83,83),new Report.Entry("Entity","minecraft:armor_stand","minecraft:overworld",4,81,4,50000,20,entity.getUUID()));
            source=new Report(CAPTURE,1000,20,123,true,false,targets,false,0,List.of(),8,new Report.Diagnostics("Private trigger",100,200,List.of(new Report.Task("private","Private#method",5)),List.of("private")));
            ui=create(server,id->id.equals(player.getUUID())?player:id.equals(operator.getUUID())?operator:null,(p,packet)->packets.add(packet),(p,publicAreas)->new BaseAccess(publicAreas,List.of(k->owns&&p==player&&k.dimension().equals("minecraft:overworld")&&k.x()==0&&k.z()==0?BaseAccess.Decision.ALLOW:BaseAccess.Decision.DENY)));
            archive=new ArchiveService(server,directory,new ProfileArchive.Settings(true,10,32));Profiler.players=ui;Profiler.archives=archive;Profiler.reportDelivery=null;
            archive.capture(source);archive.capture(new Report(8002,2000,20,999,true,false,List.of(targets.get(2))));Profiler.latest=null;
            var root=server.getCommands().getDispatcher().getRoot().getChild("lagsense");
            check(root.getChildren().stream().map(n->n.getName()).collect(java.util.stream.Collectors.toSet()).equals(Set.of("report","profile","stop","status","area","tp","tpchunk","return","schedule","spikes","reload","discord")),"Final registered command tree has no obsolete GUI/base commands");
            var ordinary=server.getCommands().getDispatcher().getSmartUsage(root,player.createCommandSourceStack());
            check(ordinary.size()==1&&ordinary.keySet().iterator().next().getName().equals("report"),"Ordinary-player autocomplete exposes only report");
            for(String command:List.of("lagsense profile","lagsense stop","lagsense area pos1","lagsense tp location minecraft:overworld 0 80 0","lagsense tp chunk minecraft:overworld 0 0","lagsense return","lagsense status","lagsense schedule off","lagsense spikes off","lagsense reload","lagsense discord test"))check(execute(server,player,command)==0,"Non-operator cannot execute "+command);
            check(execute(server,player,"lagsense report")==1&&!Profiler.busy(),"Report opens a read-only workflow and never starts a capture");stage=1;return false;
        }
        if(stage==1){
            if(reports().isEmpty())return false;
            var scoped=report();check(scoped.id()==CAPTURE&&scoped.entries().size()==3&&!scoped.admin()&&scoped.mspt()==0&&scoped.tps()==0&&scoped.diagnostics()==null,"Latest permitted persisted capture is restored after login, skipping a newer private-only capture");
            check(scoped.entries().stream().noneMatch(e->e.type().contains("private"))&&scoped.chunks().size()==2,"Chunk totals contain no unrelated data or public-area overreach");
            for(int i=0;i<scoped.entries().size();i++){mark(player,CAPTURE,i);check(state().marker()!=null,"Mark works for measured owned/public entries");}
            for(int index:new int[]{-1,scoped.entries().size(),Integer.MAX_VALUE}){mark(player,CAPTURE,index);check(state().marker()==null,"Forged indexes do not select private targets");}
            mark(player,CAPTURE-1,0);check(state().marker()==null,"Stale report IDs cannot mark targets");
            String json=Profiler.JSON.toJson(scoped);check(!json.contains("fixture:private")&&!json.contains("Private#method"),"Scoped JSON exports contain no private diagnostics/targets");
            Path html=areas.getParent().resolve("scoped-export.html");Files.deleteIfExists(html);HtmlReportExport.write(html,scoped, net.sprocketgames.lagsense.client.UiTheme.DEFAULT);check(!ReportExport.csv(scoped).contains("fixture:private")&&!Files.readString(html).contains("fixture:private"),"CSV/HTML exports contain only scoped retained records");
            ui.forget(player.getUUID());ui.request(player,new ProfilerRequest("toggle"));check(!Profiler.busy(),"Ordinary Ctrl+F10 protocol opens results without starting or stopping profiling");
            ui.history(player,new ArchiveRequestPayload("list",0,0,false,10,32));stage=2;return false;
        }
        if(stage==2){
            var payload=packets.stream().filter(ArchivePayload.class::isInstance).map(ArchivePayload.class::cast).findFirst();if(payload.isEmpty())return false;
            var list=Profiler.JSON.fromJson(payload.get().json(),ProfileArchive.State.class);check(list.profiles().size()==1&&list.profiles().getFirst().id()==CAPTURE&&list.profiles().getFirst().targets()==3&&list.profiles().getFirst().mspt()==0&&list.profiles().getFirst().bytes()==0,"History lists only currently permitted captures and redacts all server-wide summary fields");
            wait=clock+22;stage=3;return false;
        }
        if(stage==3){if(clock<wait)return false;packets.clear();ui.history(player,new ArchiveRequestPayload("open",CAPTURE,0,false,10,32));stage=4;return false;}
        if(stage==4){
            if(reports().isEmpty())return false;check(reports().getLast().archived()&&report().entries().size()==3,"Scoped history opens actual archived data with fresh redaction");
            int index=report().entries().indexOf(report().entries().stream().filter(e->e.entityId()!=null).findFirst().orElseThrow());
            check(server.overworld().getEntity(entity.getUUID())==entity&&!entity.isRemoved(),"Moving-entity fixture remains loaded and UUID-resolvable");
            entity.snapTo(49,81,49,0,0);mark(player,CAPTURE,index);check(state().marker()==null,"A UUID-matched entity outside the permitted area cannot be marked");entity.snapTo(4,81,4,0,0);mark(player,CAPTURE,index);check(state().marker()!=null,"UUID marker works when its current location is permitted");
            entity.snapTo(49,81,49,0,0);for(int i=0;i<20;i++)ui.tick();check(state().markerChanged()&&state().marker()==null,"Marker refresh revokes moving-entity access");
            owns=false;for(int i=0;i<20;i++)ui.tick();check(state().clear()&&ui.view(player,CAPTURE)==null,"Ownership changes invalidate cached reports and markers");mark(player,CAPTURE,0);check(state().marker()==null,"Stale cached grants cannot bypass revoked ownership");
            owns=true;ui.forget(player.getUUID());packets.clear();ui.open(player);stage=5;return false;
        }
        if(stage==5){
            if(reports().isEmpty())return false;check(report().id()==CAPTURE&&!Profiler.busy(),"Relogging restores saved permitted results without any new capture");
            Files.writeString(areas,"[]");Profiler.areas.load(areas.getParent());for(int i=0;i<20;i++)ui.tick();check(state().clear(),"Deleting a public area invalidates old report grants");
            ui.forget(player.getUUID());packets.clear();ui.grant(operator,source.view(true,e->true));check(state().admin(),"Operator retains full UI authority");operator.op=false;for(int i=0;i<20;i++)ui.tick();check(!state().admin()&&state().clear(),"Operator revocation clears cached unrestricted data");
            var historySettings=Profiler.settings.history();ui.forget(player.getUUID());packets.clear();archive.request(player,new ArchiveRequestPayload("configure",0,0,true,50,256));check(state().message().equals("Operator access required.")&&Profiler.settings.history().equals(historySettings),"Forged history configuration cannot modify server retention");
            ui.forget(player.getUUID());packets.clear();archive.request(player,new ArchiveRequestPayload("compare",CAPTURE,8002,false,10,32));check(state().message().equals("Operator access required.")&&packets.stream().noneMatch(ComparisonPayload.class::isInstance),"Forged comparison requests cannot release server diagnostics");
            ui.forget(player.getUUID());packets.clear();archive.request(player,new ArchiveRequestPayload("open",8002,0,false,10,32));stage=6;return false;
        }
        if(stage==6){
            if(packets.stream().filter(ProfilerStatePayload.class::isInstance).map(ProfilerStatePayload.class::cast).map(p->Profiler.JSON.fromJson(p.json(),ProfilerStatePayload.State.class)).noneMatch(s->s.message().equals("No permitted results in that profile.")))return false;
            check(reports().isEmpty(),"Forged unrelated archive IDs do not transmit private capture data");
            archive.close();ui.close();Profiler.players=oldUi;Profiler.archives=oldArchive;Profiler.reportDelivery=oldDelivery;Profiler.latest=oldLatest;Profiler.areas.load(Profiler.directory);entity.discard();player.discard();operator.discard();stage=99;
            System.out.println("Player workflow fixture passed: final command/autocomplete tree, read-only users/shortcut protocol, persisted-history fallback/relogin, scoped archives/exports, owned/public/private boundaries, server-side Mark/UUID/revocation, forged archive/configuration/comparison requests and stale indexes, and admin-only capture/TP/configuration.");return true;
        }
        return false;
    }
}
