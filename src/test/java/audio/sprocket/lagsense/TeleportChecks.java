package audio.sprocket.lagsense;

import java.util.*;
import java.lang.reflect.Proxy;
import net.minecraft.commands.*;
import net.minecraft.server.permissions.PermissionSet;

public final class TeleportChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void run()throws Exception{
        var e=new Report.Entry("Block","fixture:machine","minecraft:overworld",15,-40,20,3000,10);
        var exact=LocalTeleport.choose(e,p->true);check(exact.equals(new LocalTeleport.Position(15.5,-40,20.5)),"Underground target stays at its recorded Y");
        var adjacent=LocalTeleport.choose(e,p->p.x()==16.5&&p.y()==-40&&p.z()==20.5);check(adjacent.x()==16.5&&adjacent.y()==-40,"An adjacent empty space is preferred to collision");
        var above=LocalTeleport.choose(e,p->p.x()==15.5&&p.y()==-39&&p.z()==20.5);check(above.y()==-39,"One local step above is allowed without searching for another floor");
        var attempts=new ArrayList<LocalTeleport.Position>();var enclosed=LocalTeleport.choose(e,p->{attempts.add(p);return false;});
        check(enclosed.equals(exact)&&attempts.size()==75&&attempts.stream().allMatch(p->Math.abs(p.x()-exact.x())<=2&&Math.abs(p.z()-exact.z())<=2&&Math.abs(p.y()-exact.y())<=1),"Fully enclosed targets fall back to the intended position after a fixed small search");
        var chunk=HistoricalTargets.chunk(new Report.Chunk(e.dimension(),-2,4,3000,1,e));var destination=LocalTeleport.choose(chunk,p->true);
        check(destination.x()==-24&&destination.z()==72&&destination.y()==e.y(),"Chunk uses its centre and recorded underground hotspot Y");
        var missing=new Report.Chunk("minecraft:the_nether",2,3,0,0,null);var fallback=HistoricalTargets.chunk(missing,55);
        check(fallback.y()==55&&fallback.x()==40&&fallback.z()==56&&fallback.dimension().equals(missing.dimension()),"Chunk without a hotspot uses the explicitly supplied viewer Y, never a surface scan");
        serverPermissions();
        System.out.println("Teleport checks passed (underground/adjacent/above/enclosed positions, bounded local search, chunk centres/fallback heights and compiled server permission boundaries).");
    }
    private static void serverPermissions()throws Exception{
        var node=new org.objectweb.asm.tree.ClassNode();try(var in=TeleportChecks.class.getResourceAsStream("/audio/sprocket/lagsense/LagSense.class")){new org.objectweb.asm.ClassReader(in).accept(node,0);}
        for(String name:List.of("teleport","teleportChunk","travel","teleportLocation","teleportChunkLocation")){
            var method=node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();boolean guard=false,returned=false;
            for(var instruction:method.instructions){
                if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call){
                    if(call.owner.equals("audio/sprocket/lagsense/LagSense")&&call.name.equals("isAdmin"))guard=true;
                    if(call.name.equals("getLevel")||call.name.equals("teleportTo")||call.name.equals("target"))check(guard&&returned,"Operator rejection precedes all destination lookups/teleports in "+name);
                }
                if(instruction instanceof org.objectweb.asm.tree.FieldInsnNode f&&f.owner.equals("audio/sprocket/lagsense/Profiler"))check(guard&&returned,"Forged requests cannot read capture/archive state before the permission guard in "+name);
                if(instruction.getOpcode()==org.objectweb.asm.Opcodes.IRETURN&&guard)returned=true;
            }check(guard&&returned,"Every TP execution path has an explicit server permission rejection");
        }
        var authority=node.methods.stream().filter(m->m.name.equals("isAdmin")).findFirst().orElseThrow();boolean nativePermission=false;
        for(var instruction:authority.instructions)if(instruction instanceof org.objectweb.asm.tree.FieldInsnNode f&&f.name.equals("LEVEL_GAMEMASTERS"))nativePermission=true;
        check(nativePermission,"Teleport guards reuse Minecraft's existing operator authority");
        var commands=node.methods.stream().filter(m->m.name.equals("commands")).findFirst().orElseThrow();
        for(String name:List.of("tp","tpchunk","return")){
            boolean found=false;
            for(var instruction:commands.instructions)if(instruction instanceof org.objectweb.asm.tree.LdcInsnNode text&&name.equals(text.cst)){
                boolean admin=false;
                for(var next=instruction.getNext();next!=null;next=next.getNext()){
                    if(next instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode dynamic)for(var arg:dynamic.bsmArgs)if(arg instanceof org.objectweb.asm.Handle handle&&handle.getName().equals("isAdmin"))admin=true;
                    if(next instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.name.equals("requires")){check(admin,"Only operator-authorised "+name+" is advertised to clients");found=true;break;}
                }
            }check(found,"Existing TP/TPchunk/Back routes remain permission-gated");
        }
    }
}
