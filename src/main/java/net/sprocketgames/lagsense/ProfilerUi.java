package net.sprocketgames.lagsense;

import java.util.*;
import java.util.function.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Shared read-only player workflow. Captures, teleportation and configuration remain operator-only. */
public final class ProfilerUi implements AutoCloseable {
    private static final int LEASE_TICKS=100;
    private static final class Session {
        boolean admin;Report view;ScopedReports.Evidence evidence,history;Report.Entry mark;boolean listing;long job;
        Session(boolean admin){this.admin=admin;}
    }
    private final MinecraftServer server;
    private final Function<UUID,ServerPlayer> lookup;
    private final BiConsumer<ServerPlayer,CustomPacketPayload> transport;
    private final BiFunction<ServerPlayer,List<PublicArea>,BaseAccess> access;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final RequestGate requests=new RequestGate(250_000_000),historyRequests=new RequestGate(1_000_000_000);
    private boolean closed,requireClient;private int ticks;
    public ProfilerUi(MinecraftServer server){this(server,id->server.getPlayerList().getPlayer(id),(p,packet)->{if(p.connection!=null&&NetworkRegistry.hasChannel(p.connection,packet.type().id()))PacketDistributor.sendToPlayer(p,packet);}, net.sprocketgames.lagsense.compat.ClaimCompatibility::forPlayer);requireClient=true;}
    ProfilerUi(MinecraftServer server,Function<UUID,ServerPlayer> lookup,BiConsumer<ServerPlayer,CustomPacketPayload> transport,BiFunction<ServerPlayer,List<PublicArea>,BaseAccess> access){this.server=server;this.lookup=lookup;this.transport=transport;this.access=access;}
    private boolean admin(ServerPlayer p){return LagSense.isAdmin(p.createCommandSourceStack());}
    private Session session(ServerPlayer p){return sessions.computeIfAbsent(p.getUUID(),id->new Session(admin(p)));}
    public BaseAccess access(ServerPlayer p){return access.apply(p,Profiler.areas.areas());}
    public void open(ServerPlayer p){request(p,new ProfilerRequest("open"));}
    public void request(ServerPlayer p,ProfilerRequest request){
        if(closed||!requests.allow(p.getUUID(),System.nanoTime()))return;
        switch(request.action()){
            case "open","capture","toggle"->openNow(p);
            case "settings"->{renew(p);transport.accept(p,SettingsPayload.view(admin(p),Profiler.settings,Profiler.delivery==null?"Off":Profiler.delivery.summary()));}
            case "captures"->{if(admin(p))Profiler.openSettings(p,"");else error(p,"Operator access required.");}
            case "mark"->mark(p,request.report(),request.index());
            case "unmark"->{var s=session(p);s.mark=null;state(p,s,false,false,true,"");}
            default->error(p,"Unknown profiler action.");
        }
    }
    private void openNow(ServerPlayer p){
        var s=session(p);s.job++;s.listing=false;
        if(admin(p)){
            if(Profiler.latest!=null){deliver(p,Profiler.latest,true,false);return;}
            latestSaved(p,s,true);return;
        }
        if(Profiler.latest!=null&&!Profiler.latest.entries().isEmpty()&&!Profiler.latest.view(false,access(p)).entries().isEmpty()){deliver(p,Profiler.latest,true,false);return;}
        latestSaved(p,s,false);
    }
    private void latestSaved(ServerPlayer p,Session s,boolean global){
        if(Profiler.archives==null){empty(p,s);return;}
        long job=++s.job;s.listing=true;
        Profiler.archives.listForUi((list,error)->server.execute(()->{
            if(!current(p,s,job))return;
            if(error!=null){empty(p,s);return;}
            latest(p,s,job,list.profiles(),0,global);
        }));
    }
    private boolean current(ServerPlayer p,Session s,long job){return !closed&&lookup.apply(p.getUUID())==p&&sessions.get(p.getUUID())==s&&s.job==job;}
    private void latest(ServerPlayer p,Session s,long job,List<ProfileArchive.Summary> list,int offset,boolean global){
        if(!current(p,s,job))return;
        if(offset>=list.size()){s.listing=false;empty(p,s);return;}
        Profiler.archives.readInvestigation(list.get(offset).id(),(source,error)->server.execute(()->{
            if(!current(p,s,job))return;
            if(error==null&&source!=null&&((global&&admin(p))||!source.view(false,access(p)).entries().isEmpty())){s.listing=false;deliver(p,source,true,true);}
            else latest(p,s,job,list,offset+1,global);
        }));
    }
    private void empty(ServerPlayer p,Session s){s.listing=false;s.view=null;s.mark=null;s.evidence=null;s.history=null;s.admin=admin(p);state(p,s,true,true,true,"");}
    private void deliver(ServerPlayer p,Report source,boolean open,boolean archived){
        if(requireClient&&p.connection!=null&&!Profiler.hasClient(p)){CommandOutput.tell(p,"Install LagSense on your client to open the profiler.");return;}
        if(Profiler.reportDelivery!=null){Profiler.reportDelivery.send(p,source,admin(p),open,archived);return;}
        // Fallback delivery applies the same access filtering and report binding.
        var view=source.view(admin(p),access(p));grant(p,view);transport.accept(p,new ReportPayload(ReportWire.json(view,Profiler.JSON),open,archived));
    }
    public void grant(ServerPlayer p,Report view){
        var s=session(p);boolean changed=s.admin!=admin(p);s.admin=admin(p);s.view=view;s.mark=null;
        s.evidence=s.admin?null:ScopedReports.evidence(view,Profiler.areas.areas());
        state(p,s,changed,false,true,"");
    }
    public boolean permitted(ServerPlayer p,Report view){
        if(view.admin()&&!admin(p))return false;
        var s=sessions.get(p.getUUID());var evidence=s!=null&&s.view==view?s.evidence:ScopedReports.evidence(view,Profiler.areas.areas());
        if(evidence==null)return admin(p);var owned=access.apply(p,List.of());
        return evidence.valid(Profiler.areas.areas(),k->owned.contains(k.dimension(),k.x()<<4,k.z()<<4));
    }
    private Report.Entry currentMarker(ServerPlayer p,Report.Entry recorded){
        if(recorded==null||!access(p).test(recorded))return null;
        if(recorded.entityId()!=null)for(var level:server.getAllLevels()){
            var entity=level.getEntity(recorded.entityId());if(entity==null||entity.isRemoved())continue;
            String type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();if(!type.equals(recorded.type()))return null;
            if(!access(p).contains(level.dimension().identifier().toString(),entity.getBlockX(),entity.getBlockZ()))return null;
        }
        return recorded;
    }
    void mark(ServerPlayer p,long report,int index){
        var s=sessions.get(p.getUUID());if(s!=null&&s.admin!=admin(p)){state(p,s,true,false,true,"Permissions changed. Open the profiler again.");return;}
        var entry=s==null?null:ScopedReports.target(s.view,report,index);
        if(entry==null||!admin(p)&&currentMarker(p,entry)==null){if(s!=null){s.mark=null;state(p,s,false,false,true,"Target access changed. Open the report again.");}return;}
        s.mark=entry;state(p,s,false,false,true,"");
    }
    private void state(ServerPlayer p,Session s,boolean clear,boolean open,boolean markerChanged,String message){
        boolean role=admin(p);if(role!=s.admin){s.admin=role;s.view=null;s.evidence=null;s.history=null;s.mark=null;s.job++;clear=true;markerChanged=true;}
        transport.accept(p,new ProfilerStatePayload(new ProfilerStatePayload.State(s.admin,clear,open,s.view==null?0:s.view.id(),LEASE_TICKS,markerChanged,s.mark,s.view==null?0:s.view.ticks(),message)));
    }
    void renew(ServerPlayer p){state(p,session(p),false,false,false,"");}
    private void error(ServerPlayer p,String message){state(p,session(p),false,false,false,message);}
    public void tick(){
        if(sessions.isEmpty()||++ticks%20!=0)return;
        var iterator=sessions.entrySet().iterator();
        while(iterator.hasNext()){
            var item=iterator.next();var p=lookup.apply(item.getKey());if(p==null){iterator.remove();continue;}var s=item.getValue();
            boolean role=admin(p);boolean invalid=role!=s.admin;
            if(!role){var owned=access.apply(p,List.of());for(var evidence:new ScopedReports.Evidence[]{s.evidence,s.history})if(evidence!=null)invalid|=!evidence.valid(Profiler.areas.areas(),k->owned.contains(k.dimension(),k.x()<<4,k.z()<<4));}
            if(invalid){
                s.admin=role;s.view=null;s.evidence=null;s.history=null;s.mark=null;s.job++;s.listing=false;
                if(Profiler.reportDelivery!=null)Profiler.reportDelivery.forget(p.getUUID());if(Profiler.investigations!=null)Profiler.investigations.forget(p.getUUID());
                state(p,s,true,false,true,"Area access changed. Open the profiler again.");continue;
            }
            boolean clearMark=!role&&s.mark!=null&&currentMarker(p,s.mark)==null;if(clearMark)s.mark=null;
            state(p,s,false,false,clearMark,"");
        }
    }
    public void history(ServerPlayer p,ArchiveRequestPayload request){
        if(closed||!historyRequests.allow(p.getUUID(),System.nanoTime()))return;
        var s=session(p);
        state(p,s,false,false,false,"");
        if(!request.action().equals("list")&&!request.action().equals("open")){error(p,"Operator access required.");return;}
        if(Profiler.archives==null){showHistory(p,List.of());return;}
        long job=++s.job;s.listing=true;
        if(request.action().equals("open")){
            Profiler.archives.readInvestigation(request.first(),(source,error)->server.execute(()->{
                if(!current(p,s,job))return;s.listing=false;
                if(error!=null||source==null||source.view(false,access(p)).entries().isEmpty()){error(p,"No permitted results in that profile.");return;}
                deliver(p,source,true,true);
            }));return;
        }
        Profiler.archives.listForUi((list,error)->server.execute(()->{
            if(!current(p,s,job))return;
            if(error!=null){s.listing=false;error(p,"History unavailable.");return;}
            historyNext(p,s,job,list.profiles(),0,new ArrayList<>(),new HashSet<>(),List.copyOf(Profiler.areas.areas()));
        }));
    }
    private void historyNext(ServerPlayer p,Session s,long job,List<ProfileArchive.Summary> list,int offset,List<ProfileArchive.Summary> allowed,Set<BaseAccess.ChunkKey> evidence,List<PublicArea> areas){
        if(!current(p,s,job))return;
        if(offset>=list.size()){
            s.listing=false;var owned=access.apply(p,List.of());
            if(!new ScopedReports.Evidence(areas,evidence).valid(Profiler.areas.areas(),k->owned.contains(k.dimension(),k.x()<<4,k.z()<<4))){error(p,"Area access changed. Reload history.");return;}
            s.history=new ScopedReports.Evidence(areas,Set.copyOf(evidence));showHistory(p,allowed);return;
        }
        Profiler.archives.readInvestigation(list.get(offset).id(),(source,error)->server.execute(()->{
            if(!current(p,s,job))return;
            if(!areas.equals(Profiler.areas.areas())){s.listing=false;error(p,"Area access changed. Reload history.");return;}
            if(error==null&&source!=null){var view=source.view(false,access(p));if(!view.entries().isEmpty()){allowed.add(ScopedReports.summary(view));evidence.addAll(ScopedReports.evidence(view,areas).claims());}}
            historyNext(p,s,job,list,offset+1,allowed,evidence,areas);
        }));
    }
    private void showHistory(ServerPlayer p,List<ProfileArchive.Summary> profiles){
        state(p,session(p),false,false,false,"");
        transport.accept(p,new ArchivePayload(Profiler.JSON.toJson(new ProfileArchive.State(ProfileArchive.Settings.defaults(),List.copyOf(profiles))),""));
    }
    public Report view(ServerPlayer p,long id){var s=sessions.get(p.getUUID());return s!=null&&s.view!=null&&s.view.id()==id&&permitted(p,s.view)?s.view:null;}
    public void forget(UUID id){sessions.remove(id);requests.forget(id);historyRequests.forget(id);}
    @Override public void close(){closed=true;sessions.clear();requests.clear();historyRequests.clear();}
}
