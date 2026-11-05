package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Reuses Mark, TravelScreen and the server's archive-backed target teleport path. */
final class TargetActions {
    static ComparisonTargets.Destination recorded(Report report,Report.Entry target,int index,boolean chunk){
        return index>=0&&HistoricalTargets.positioned(target)?new ComparisonTargets.Destination(report.id(),report.capturedAt(),index,target,report.ticks(),chunk):null;
    }
    static Report.Entry chunk(Report.Chunk chunk){var mc=Minecraft.getInstance();return HistoricalTargets.chunk(chunk,mc!=null&&mc.player!=null?mc.player.getBlockY():null);}
    static boolean permitted(boolean chunk){var mc=Minecraft.getInstance();return ClientAccess.admin()&&mc!=null&&mc.player!=null&&mc.getConnection()!=null&&permitted(mc.getConnection().getCommands(),chunk);}
    static boolean permitted(com.mojang.brigadier.CommandDispatcher<?> commands,boolean chunk){return commands.findNode(java.util.List.of("lagsense",chunk?"tpchunk":"tp"))!=null;}
    static boolean canReturn(){return ClientAccess.canReturn()&&permitted(false);}
    private static Entity live(Report.Entry target){return EntityMarkers.current(target);}
    static boolean marked(Report.Entry target){
        var current=LagSenseClient.waypoint;if(current==null||target==null||!current.kind().equals(target.kind()))return false;
        if(current.entityId()!=null||target.entityId()!=null)return current.entityId()!=null&&current.entityId().equals(target.entityId())&&current.type().equals(target.type());
        if(!current.dimension().equals(target.dimension()))return false;
        return target.kind().equals("Chunk")?(current.x()>>4)==(target.x()>>4)&&(current.z()>>4)==(target.z()>>4):current.type().equals(target.type())&&current.x()==target.x()&&current.y()==target.y()&&current.z()==target.z();
    }
    static void unmark(){LagSenseClient.waypoint=null;LagSenseClient.tracked(null);ClientAccess.request("unmark");}
    static UiResults.Action markAction(Report.Entry target,Runnable apply){return new UiResults.Action("Mark",()->{if(marked(target))unmark();else apply.run();}).markState(target);}
    static boolean canTravel(ComparisonTargets.Destination destination){
        return destination!=null&&HistoricalTargets.positioned(destination.target())&&permitted(destination.chunk());
    }
    static UiResults.Action[] actions(LagScreen parent,ComparisonTargets.Destination destination){
        return new UiResults.Action[]{
            markAction(destination==null?null:destination.target(),()->{if(destination==null)return;if(!ClientAccess.admin()){if(destination.chunk())ClientAccess.request(new ProfilerRequest("mark_chunk",destination.capture(),destination.index()));else ClientAccess.mark(destination.capture(),destination.target());}else LagSenseClient.mark(destination);}).enabledWhen(()->destination!=null),
            new UiResults.Action("TP",()->teleport(parent,destination)).enabledWhen(()->canTravel(destination)).visibleWhen(()->destination!=null&&permitted(destination.chunk()))
        };
    }
    static void teleport(LagScreen parent,ComparisonTargets.Destination destination){
        if(!canTravel(destination))return;var target=destination.target();boolean entity=target.kind().equals("Entity");
        boolean currentLocation=false;
        if(entity){var current=live(target);if(current!=null){var pos=current.blockPosition();var resolved=ComparisonTargets.current(target,new ComparisonTargets.Live(current.level().dimension().identifier().toString(),net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(current.getType()).toString(),pos.getX(),pos.getY(),pos.getZ(),current.isRemoved()));if(resolved!=null){target=resolved;currentLocation=true;}}}
        var mc=Minecraft.getInstance();mc.setScreen(new TravelScreen(parent,target,()->{if(canTravel(destination))mc.player.connection.sendCommand("lagsense "+(destination.chunk()?"tpchunk":"tp")+" "+destination.capture()+" "+destination.index());},currentLocation?"Current entity location":"Recorded location"));
    }
    private TargetActions(){}
}
