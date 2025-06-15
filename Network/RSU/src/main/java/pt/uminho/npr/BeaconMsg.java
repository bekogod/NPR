package pt.uminho.npr;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.annotation.Nonnull;

import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

/*
 * timestamp
 * sendeID
 * sender position
 * sender heading
 * sender speed
 * sender lane
 */

public class BeaconMsg extends V2xMessage {

    private final EncodedPayload payload;
    private final long timeStamp;
    private final String senderName;
    private final Position senderPos;

    public BeaconMsg(
            final MessageRouting routing,
            final long time,
            final String name,
            final Position pos) {

        super(routing);
        this.timeStamp = time;
        this.senderName = name;
        this.senderPos = pos;

        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeLong(timeStamp);
            dos.writeUTF(senderName);
            senderPos.encode(dos); // substitui o encodeGeoPoint

            payload = new EncodedPayload(baos.toByteArray(), baos.size());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    @Override
    public EncodedPayload getPayload() {
        return payload;
    }

    public long getTime() {
        return timeStamp;
    }

    public String getSenderName() {
        return senderName;
    }

    public Position getSenderPosition() {
        return new Position(senderPos);
    }

    @Override
    public String toString() {
        return String.format(
                "BeaconMsg {timeStamp=%d, senderName='%s', senderPosition=%s}",
                timeStamp,
                senderName,
                senderPos != null ? senderPos.toString() : "null" // Handling null for senderPos
        );
    }

}
