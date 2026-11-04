package net.sprocketgames.lagsense.client;

import net.sprocketgames.lagsense.*;
import java.util.*;

final class HistoryComparisonUiChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static void field(Object o,String n,Object v)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    private static void call(Object o,String name)throws Exception{var m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(o);}
    private static UiButton button(LagScreen s,String text){return s.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast).filter(b->b.getMessage().getString().equals(text)).findFirst().orElseThrow();}
    static void run()throws Exception{
        for(int scale:List.of(2,3)){
            int width=1920/scale,height=1080/scale;
            var state=new ProfileArchive.State(ProfileArchive.Settings.defaults(),List.of());var history=new HistoryScreen(null,state,"");history.width=width;history.height=height;history.init();
            UiButton clear=button(history,"Clear baseline"),reload=button(history,"Reload"),apply=button(history,"Apply");
            check(clear.getY()==reload.getY()&&clear.getX()+clear.getWidth()+8==reload.getX()&&reload.getX()+reload.getWidth()==width-history.margin(),"History groups Clear baseline and Reload at the right edge");
            var count=(net.minecraft.client.gui.components.EditBox)field(history,"count");var limit=(net.minecraft.client.gui.components.EditBox)field(history,"limit");
            int fieldTop=count.getRectangle().top();
            check(fieldTop-14>=history.panelTop()+UiMetrics.PROFILE_DIVIDER-12+8&&reload.getY()>=fieldTop+32,"History leaves clear spacing after its header divider and between form/tool rows");
            check(!apply.active,"History Apply is disabled for an unchanged draft");count.setValue("12");check(apply.active,"Editing a retention field enables Apply immediately");count.setValue("10");check(!apply.active,"Restoring the saved field disables Apply");
            limit.setValue("16");check(apply.active,"Disk limit edits require explicit Apply");limit.setValue("32");
            field(history,"enabled",true);call(history,"updateApply");check(apply.active,"History enable edits are pending server settings");
            field(history,"pendingDesired",new ProfileArchive.Settings(true,10,32));call(history,"updateApply");check(!apply.active&&!count.active&&!limit.active,"Pending Apply cannot be repeated or overwritten by field edits");
            field(history,"pendingDesired",null);count.setValue("12");call(history,"discardDraft");check(!(boolean)field(history,"enabled")&&field(history,"countDraft").equals("10"),"Discard restores confirmed history settings");
            int[] actions={0,0};var dialog=new UnsavedScreen(history,()->actions[1]++,()->actions[0]++);dialog.width=width;dialog.height=height;dialog.init();dialog.setInitialFocus();
            check(dialog.panelBottom()-dialog.panelTop()==128&&Math.abs(dialog.panelTop()+dialog.panelBottom()-height)<=1&&width-2*dialog.panelMargin()==320,"Unsaved warning uses a small centred panel, independent of the editor size");
            var keep=button(dialog,"Keep editing");check(dialog.getFocused()==keep,"Keep editing is the warning's default keyboard action");
            var controls=dialog.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast).toList();for(var b:controls)check(b.getX()>=dialog.margin()&&b.getX()+b.getWidth()<=width-dialog.margin()&&b.getY()>dialog.panelTop()+60&&b.getY()+b.getHeight()<dialog.panelBottom(),"Warning buttons stay inside the compact panel");
            button(dialog,"Apply").onPress(null);button(dialog,"Discard").onPress(null);check(Arrays.equals(actions,new int[]{1,1}),"Compact warning preserves the distinct Apply and Discard callbacks");
            comparison(width,height);
        }
        System.out.println("History/comparison UI checks passed at scales 2/3 (header/form spacing, aligned actions, dirty Apply, compact warning, nested comparisons and fixed timing columns).");
    }
    @SuppressWarnings("unchecked") private static void comparison(int width,int height)throws Exception{
        var capture=ComparisonDetailChecks.fixture();var index=new ComparisonIndex(capture);var screen=new ComparisonScreen(null,index);screen.width=width;screen.height=height;var oldReport=LagSenseClient.report;screen.init();
        check(LagSenseClient.report==oldReport,"Opening a comparison cannot replace the active profile or its HUD source");
        check(button(screen,"Types").getY()==screen.headerControlsTop()+52&&screen.headerControlsTop()>=screen.panelTop()+44&&button(screen,"All dimensions").getY()>button(screen,"Types").getY(),"Comparison separates title and summary with the standard divider/gap, then leaves room for both capture dates before navigation");
        check(button(screen,"Copy")!=null&&button(screen,"Export")!=null&&button(screen,"Close")!=null,"Comparison preserves Copy, Export and Close");
        check(screen.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast).noneMatch(b->List.of("Largest change","Later cost","Most costly","By Instance","By Type").contains(b.getMessage().getString())),"Comparison navigation has no sorting control or extra grouping toggle");
        var expanded=(Set<ComparisonIndex.Key>)field(screen,"expanded");var append=ComparisonScreen.class.getDeclaredMethod("append",UiResults.class);append.setAccessible(true);
        var roots=index.all.chunks();field(screen,"visible",roots);expand(roots,expanded);
        var out=new UiResults(screen,140,88,true,false).diagnosticHints();append.invoke(screen,out);
        var list=new UiScrollList(screen.margin(),140,screen.contentWidth(),34,new ListScroll());list.virtual(out.entries,null);var root=(UiRow)list.children().getFirst();
        int leaf=-1;for(int i=0;i<out.entries.size();i++)if(out.entries.get(i).key() instanceof ComparisonIndex.Key k&&k.scope().equals("chunkTarget")){leaf=i;break;}
        check(leaf>=0,"Chunk comparison expands through category/type to targets");list.restore(leaf*34);var target=(UiRow)list.children().getFirst();
        check(target.getHeight()==32&&target.columns().nameX()==root.columns().nameX()+36&&target.columns().valueRight()==root.columns().valueRight()&&target.getX()==root.getX()+36&&target.getX()+target.getWidth()==root.getX()+root.getWidth()&&list.widgetCount()==3,"Three-level indentation preserves the original two-line row height and fixed timing column");
        String hint=TooltipCleanupChecks.hint(target);check(hint.contains("UUID:")&&hint.contains("Earlier:")&&hint.contains("Later:")&&!hint.contains("percentage of total"),"Target differences retain actual identity/location/measurement diagnostics");
        list.restore(Integer.MAX_VALUE);check(list.visible(out.entries.getLast().key()),"The end of an expanded comparison is reachable without nested scrollbars");
        var first=roots.getFirst();int before=out.entries.size();expanded.remove(first.key());var collapsed=new UiResults(screen,140,88,true,false).diagnosticHints();append.invoke(screen,collapsed);check(collapsed.entries.size()<before&&roots.stream().skip(1).allMatch(n->expanded.contains(n.key())),"Collapsing one chunk preserves other independent expansions");
        var entries=new ArrayList<Report.Entry>();var later=new ArrayList<Report.Entry>();for(int i=0;i<2000;i++){entries.add(new Report.Entry("Block","fixture:machine","minecraft:overworld",i,64,0,100000,100));later.add(new Report.Entry("Block","fixture:machine","minecraft:overworld",i,64,0,400000,200));}
        var large=ProfileComparison.between(new Report(1,100,100,1,true,false,List.copyOf(entries)),new Report(2,200,200,1,true,false,List.copyOf(later)));var data=new ComparisonIndex(large);var largeScreen=new ComparisonScreen(null,data);largeScreen.width=width;largeScreen.height=height;largeScreen.init();((Set<ComparisonIndex.Key>)field(largeScreen,"expanded")).add(data.all.types().getFirst().key());
        var rows=new UiResults(largeScreen,140,88,true,false).diagnosticHints();append.invoke(largeScreen,rows);check(rows.entries.size()==2001,"Comparison expansion retains thousands of individual contributors");
        var virtual=new UiScrollList(largeScreen.margin(),140,largeScreen.contentWidth(),68,new ListScroll());virtual.virtual(rows.entries,null);virtual.restore(Integer.MAX_VALUE);check(virtual.widgetCount()<=9&&virtual.visible(rows.entries.getLast().key()),"Large comparisons materialise only the visible rows and reach the final target");
    }
    private static void expand(List<ComparisonIndex.Node> nodes,Set<ComparisonIndex.Key> expanded){for(var n:nodes){if(!n.children().isEmpty()){expanded.add(n.key());expand(n.children(),expanded);}}}
}
