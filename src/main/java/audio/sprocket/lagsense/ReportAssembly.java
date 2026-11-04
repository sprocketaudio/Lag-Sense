package audio.sprocket.lagsense;

/** A single, contiguous bounded report transfer. Newer reports supersede older incomplete ones. */
public final class ReportAssembly {
    public record Complete(long serial,byte[] bytes,boolean open,boolean archived) {}
    private long serial;
    private byte[] pending;
    private int received;
    private boolean open,archived;
    public long serial(){return serial;}
    public void clear(){serial=0;pending=null;received=0;}
    public Complete accept(FullReportPart part) {
        if(part.transfer()<serial)return null;
        if(part.transfer()>serial){if(part.offset()!=0)return null;serial=part.transfer();pending=new byte[part.total()];received=0;open=part.open();archived=part.archived();}
        if(pending==null)return null;
        if(pending.length!=part.total()||received!=part.offset()||open!=part.open()||archived!=part.archived()){pending=null;throw new IllegalArgumentException("Report segments changed or arrived out of order");}
        System.arraycopy(part.bytes(),0,pending,received,part.bytes().length);received+=part.bytes().length;
        if(received!=pending.length)return null;
        var bytes=pending;pending=null;return new Complete(serial,bytes,open,archived);
    }
}
