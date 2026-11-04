package audio.sprocket.lagsense;

import com.google.gson.Gson;
import java.util.*;

/** Keep unusually long mod identifiers/handler names inside the existing packet budget. */
final class ReportWire {
    static String json(Report view, Gson gson) {
        if(view.diagnostics()!=null&&(view.diagnostics().background()!=null||view.diagnostics().callbacks().size()>128)){
            var d=view.diagnostics();var limited=limited(new Report.Diagnostics(d.trigger(),d.bookkeepingNanos(),d.snapshotNanos(),d.tasks(),d.notes(),d.callbacks().stream().limit(128).toList(),d.callbackState(),d.cpu().summary(),d.coverage(),null,d.performance()),"Result presentation limited: update LagSense to receive the complete diagnostics.");
            view=new Report(view.id(),view.capturedAt(),view.ticks(),view.mspt(),view.admin(),true,view.entries(),view.recording(),view.secondsRemaining(),view.chunks(),view.tps(),limited);
        }
        boolean preview=view.entries().size()>128||view.chunks()!=null&&view.chunks().size()>128;
        if(preview)view=new Report(view.id(),view.capturedAt(),view.ticks(),view.mspt(),view.admin(),true,view.entries().stream().limit(128).toList(),view.recording(),view.secondsRemaining(),view.chunks()==null?List.of():view.chunks().stream().limit(128).toList(),view.tps(),limited(view.diagnostics(),"Result presentation limited: this client received a preview of the full report saved on the server."));
        if (view.diagnostics() != null) view = new Report(view.id(), view.capturedAt(), view.ticks(), view.mspt(), view.admin(), view.truncated(), view.entries(), view.recording(), view.secondsRemaining(), view.chunks(), view.tps(), view.diagnostics().summary());
        String json = gson.toJson(view);
        for (int attempt = 0; json.length() > 200000 && attempt < 8; attempt++) {
            var d = view.diagnostics();
            if (d != null) d = limited(new Report.Diagnostics(d.trigger(), d.bookkeepingNanos(), d.snapshotNanos(), half(d.tasks()), d.notes(), half(d.callbacks()), d.callbackState(), d.cpu().summary(),d.coverage(),null,d.performance()),"Result presentation limited: the full report was too large to send to this client and remains on the server.");
            view = new Report(view.id(), view.capturedAt(), view.ticks(), view.mspt(), view.admin(), true, half(view.entries()), view.recording(), view.secondsRemaining(), half(view.chunks()), view.tps(), d);
            json = gson.toJson(view);
        }
        if (json.length() > 200000) throw new IllegalArgumentException("Report metadata exceeds packet limit");
        return json;
    }
    private static <T> List<T> half(List<T> values) { return values == null || values.isEmpty() ? List.of() : values.subList(0, Math.max(1, values.size() / 2)); }
    private static Report.Diagnostics limited(Report.Diagnostics d,String reason){if(d==null)return null;var notes=new ArrayList<>(d.notes());if(!notes.contains(reason))notes.add(reason);return new Report.Diagnostics(d.trigger(),d.bookkeepingNanos(),d.snapshotNanos(),d.tasks(),notes,d.callbacks(),d.callbackState(),d.cpu(),d.coverage(),d.background(),d.performance());}
}
