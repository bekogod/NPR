package pt.uminho.npr;

import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

public abstract class Message extends V2xMessage {

    private Mode mode;
    private String forwarderId;

    public Message(final MessageRouting routing, Mode mode, String fid) {
        super(routing);
        this.mode = mode;
        this.forwarderId = fid;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getFwrdId() {
        return forwarderId;
    }

    public void setFwrdId(String fid) {
        this.forwarderId = fid;
    }

    abstract Message clone(final MessageRouting routing);

}
