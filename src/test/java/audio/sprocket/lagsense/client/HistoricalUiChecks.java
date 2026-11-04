package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.*;
import java.util.*;

final class HistoricalUiChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static void run()throws Exception{
        var e=new Report.Entry("Block","fixture:machine","minecraft:the_nether",-20,-40,55,3000,10);var report=new Report(1,100,100,1,true,false,List.of(e)).view(true,List.of());
        var append=ReportScreen.class.getDeclaredMethod("appendTarget",UiResults.class,ProfileResults.Target.class,boolean.class,Object.class,int.class);append.setAccessible(true);
        for(int scale:List.of(2,3)){
            var screen=new ReportScreen(null,report,true);screen.width=1920/scale;screen.height=1080/scale;
            var out=new UiResults(screen,140,76,false);append.invoke(screen,out,new ProfileResults.Target(0,e),true,"target",24);
            var list=new UiScrollList(screen.margin(),140,screen.contentWidth(),24,new ListScroll());list.virtual(out.entries,null);
            var row=(UiRow)list.children().getFirst();var mark=(UiButton)list.children().get(1);var tp=(UiButton)list.children().get(2);
            check(row.getHeight()==22&&mark.getMessage().getString().equals("Mark")&&tp.getMessage().getString().equals("TP"),"Nested saved targets preserve compact row and original Mark/TP styling");
            check(mark.getX()>row.columns().nameX()+row.columns().nameWidth()&&tp.getX()+tp.getWidth()<=screen.width-screen.margin(),"Historical actions stay outside the fixed timing column");
            check(mark.active&&!tp.active&&!tp.visible,"Mark remains available while disconnected/non-authorised TP is hidden");
            var branches=new ChunkBranches(screen,report,true);check(branches.actions(e,0).length==2,"Saved chunk contributors share the same eligible actions");
            var base=new Report(1,100,100,0,false,false,List.of(e));check(new ChunkBranches(screen,base,false).actions(e,0).length==1,"Base Health never exposes TP");
            var bad=new Report.Entry("Block",e.type(),"bad dimension",0,64,0,3000,10);check(branches.actions(bad,0).length==0,"Missing/invalid historical positions have no unusable actions");
            var dialog=new TravelScreen(screen,e,()->{},"Recorded location");dialog.width=screen.width;dialog.height=screen.height;dialog.init();
            check(dialog.panelBottom()-dialog.panelTop()==148&&Math.abs(dialog.panelTop()+dialog.panelBottom()-dialog.height)<=1&&dialog.width-2*dialog.panelMargin()==320,"Teleport confirmation uses a small centred popup rather than a full result panel");
            var lines=(List<?>)field(dialog,"lines");check(!lines.isEmpty()&&dialog.panelTop()+52+(lines.size()-1)*13+9<dialog.footer(),"Destination and Return hint fit above the compact footer");
            for(var child:dialog.children())if(child instanceof UiButton button)check(button.getY()==dialog.footer()&&button.getX()>=dialog.margin()&&button.getX()+button.getWidth()<=dialog.width-dialog.margin(),"Confirm/Cancel stay inside the small panel");
        }
        permissions();System.out.println("Historical UI checks passed at scales 2/3 (nested actions, fixed columns, invalid positions, Base Health isolation, permission refresh and compact teleport confirmation).");
    }
    private static void permissions(){
        var commands=new com.mojang.brigadier.CommandDispatcher<Object>();check(!TargetActions.permitted(commands,false)&&!TargetActions.permitted(commands,true),"Client without server-authorised commands cannot expose TP");
        commands.register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("lagsense").then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("tp")).then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("tpchunk")));
        check(TargetActions.permitted(commands,false)&&TargetActions.permitted(commands,true),"Operator-advertised normal and chunk commands enable their existing actions");
        boolean[] permission={true};int[] calls={0};var button=new UiButton(0,0,36,20,"TP",()->calls[0]++).enabledWhen(()->permission[0]).visibleWhen(()->permission[0]);
        permission[0]=false;button.refreshAvailability();button.onPress(null);check(!button.visible&&!button.active&&calls[0]==0,"Revoking permissions hides and blocks a mounted TP immediately");
        permission[0]=true;button.refreshAvailability();button.onPress(null);check(button.visible&&button.active&&calls[0]==1,"Restoring permissions restores an existing action without changing its geometry");
    }
}
