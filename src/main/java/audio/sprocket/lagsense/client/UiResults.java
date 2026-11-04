package audio.sprocket.lagsense.client;

import audio.sprocket.lagsense.UiMetrics;
import java.util.*;
import net.minecraft.client.gui.Font;

/** Lightweight row descriptions. Native widgets are only created for the visible window. */
final class UiResults {
    record Action(String label, Runnable run, boolean selected,java.util.function.BooleanSupplier enabled,java.util.function.BooleanSupplier visible) {
        Action(String label, Runnable run) { this(label, run, false,null,null); }
        Action(String label, Runnable run,boolean selected) { this(label,run,selected,null,null); }
        Action enabledWhen(java.util.function.BooleanSupplier enabled){return new Action(label,run,selected,enabled,visible);}
        Action visibleWhen(java.util.function.BooleanSupplier visible){return new Action(label,run,selected,enabled,visible);}
    }
    private final int left, width, reserved, clipTop, clipBottom;
    private final Font font;
    private UiResults(UiResults source){left=source.left;width=source.width;reserved=source.reserved;clipTop=source.clipTop;clipBottom=source.clipBottom;font=source.font;compact=source.compact;countColumn=source.countColumn;valueColumn=source.valueColumn;nameOnlyIndent=source.nameOnlyIndent;diagnosticHints=source.diagnosticHints;selection=source.selection;}
    private final boolean compact;
    private int countColumn, valueColumn;
    private boolean nameOnlyIndent;
    private boolean diagnosticHints;
    private ResultSelection selection;
    private java.util.function.Supplier<audio.sprocket.lagsense.CopyTree.Node> copySource;
    final List<UiScrollList.Entry> entries = new ArrayList<>();
    UiResults(LagScreen screen, int top, int reserved, boolean footnote) {
        this(screen,top,reserved,footnote,true);
    }
    UiResults(LagScreen screen, int top, int reserved, boolean footnote, boolean compact) {
        left=screen.margin(); width=screen.contentWidth(); this.reserved=reserved; font=screen.profileFont();
        clipTop=top; clipBottom=screen.footer()-(footnote?32:12);
        this.compact=compact;
    }
    UiResults samples(long maximum) {
        countColumn=Math.max(72,UiFonts.width(UiFonts.mono(),maximum+" samples"));
        valueColumn=Math.max(48,UiFonts.width(UiFonts.mono(),"100.0%"));
        return this;
    }
    UiResults nameOnlyIndent() { nameOnlyIndent=true;return this; }
    UiResults diagnosticHints(){diagnosticHints=true;return this;}
    UiResults selectable(ResultSelection value){selection=value;return this;}
    UiResults copy(java.util.function.Supplier<audio.sprocket.lagsense.CopyTree.Node> value){copySource=value;return this;}
    void lazy(Object key,java.util.function.Consumer<UiResults> build){entries.add(new UiScrollList.Entry(key,compact?24:UiMetrics.ROW,(list,y)->{var row=new UiResults(this);build.accept(row);row.entries.getFirst().build().accept(list,y);}));}
    void row(Object key, String name, String secondary, String value, int valueColor, int indent, boolean selected, String help, Runnable run, Action... actions) {
        row(key,name,secondary,"",value,valueColor,indent,selected,help,run,actions);
    }
    void row(Object key, String name, String secondary, String count, String value, int valueColor, int indent, boolean selected, String help, Runnable run, Action... actions) {
        int rowHeight=compact?24:UiMetrics.ROW;
        String measurement=audio.sprocket.lagsense.MeasurementHelp.value(value);
        String hint=diagnosticHints?help:String.join("\n",java.util.stream.Stream.of(compact?name:"",compact?secondary:"",help,measurement).filter(s->s!=null&&!s.isBlank()).distinct().toList());
        var theme=UiTheme.current();var source=copySource;copySource=null;Runnable clicked=selection!=null&&source!=null?()->{selection.select(key,source);if(run!=null)run.run();}:run;
        entries.add(new UiScrollList.Entry(key, rowHeight, (list,y)->{
            var row=new UiRow(left+(nameOnlyIndent?0:indent),y,width-(nameOnlyIndent?0:indent),rowHeight-2,name,secondary,value,theme.palette().text(),reserved,nameOnlyIndent?indent:0,selected,clicked).valueColor(valueColor).theme(theme);
            if(selection!=null&&source!=null)row.selection(()->selection.is(key));
            if(compact)row.compact(count);
            if(countColumn>0)row.sampleColumns(countColumn,valueColumn);
            list.add(UiHints.hint(row,hint));
            int x=left+width-reserved+4;
            for(var action:actions) {
                int w=actions.length==1?reserved-12:action.label().equals("TP")?(compact?28:32):action.label().equals("Mark")?(compact?36:40):action.label().equals("View")?42:76;
                var button=list.add(UiHints.control(new UiButton(x,y+(compact?3:6),w,compact?16:20,action.label(),action.run()),action.label()));
                if(action.enabled()!=null)button.enabledWhen(action.enabled());
                if(action.visible()!=null)button.visibleWhen(action.visible());
                if(action.selected())button.role(UiButton.Role.TAB).selected(true);
                x+=w+4;
            }
        }));
    }
    void details(Object key, List<String> lines, int indent) {
        var layout=UiDetails.wrap(font,width-indent,lines);
        entries.add(new UiScrollList.Entry(key,layout.height(),(list,y)->list.add(new UiDetails(font,left+indent,y,width-indent,layout,clipTop,clipBottom))));
    }
    static String cost(double micros) { return String.format(Locale.ROOT, micros>=1000?"%.2f ms/t":"%.2f µs/t",micros>=1000?micros/1000:micros); }
    static String share(long count,long total) { return String.format(Locale.ROOT,"%.1f%%",count*100.0/Math.max(1,total)); }
    static String branch(String label, boolean expanded) { return (expanded?"− ":"+ ")+label; }
}
