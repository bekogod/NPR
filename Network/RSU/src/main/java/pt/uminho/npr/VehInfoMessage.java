package pt.uminho.npr;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.annotation.Nonnull;

import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;

public class VehInfoMessage extends Message {

    private final EncodedPayload payload;
    private final String messageId;
    private final long timeStamp;
    private final String senderName;
    private final Position senderPos;
    private final double senderHeading;
    private final double senderSpeed;
    private final int senderLaneId;
    private final double distanceToRsu; // Distance to nearest RSU
    private final int numberOfHops; // Number of hops for the message
    private final String publisher;

    public VehInfoMessage(
            final MessageRouting routing,
            final String messageId,
            final long time,
            final String name,
            final Position pos,
            final double heading,
            final double speed,
            final int laneId,
            final double distanceToRsu,
            final int numberOfHops,
            Mode mode,
            String forwarderId,
            String publisher) {

        super(routing, mode, forwarderId);
        this.messageId = messageId;
        this.timeStamp = time;
        this.senderName = name;
        this.senderPos = pos;
        this.senderHeading = heading;
        this.senderSpeed = speed;
        this.senderLaneId = laneId;
        this.distanceToRsu = distanceToRsu;
        this.numberOfHops = numberOfHops;
        this.publisher = publisher;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final DataOutputStream dos = new DataOutputStream(baos)) {
            // Write existing fields to the output stream
            dos.writeUTF(messageId);
            dos.writeLong(timeStamp);
            dos.writeUTF(senderName);
            senderPos.encode(dos); // encode the sender position
            dos.writeDouble(senderHeading);
            dos.writeDouble(senderSpeed);
            dos.writeInt(senderLaneId);
            dos.writeDouble(distanceToRsu);
            dos.writeInt(numberOfHops);
            dos.writeUTF(publisher);

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

    public String getMessageId() {
        return messageId;
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

    public double getHeading() {
        return senderHeading;
    }

    public double getSpeed() {
        return senderSpeed;
    }

    public int getLaneId() {
        return senderLaneId;
    }

    public double getDistanceToRsu() {
        return distanceToRsu;
    }

    public int getNumberOfHops() {
        return numberOfHops;
    }

    public String getPublisher() {
        return publisher;
    }

    public VehInfoMessage clone(final MessageRouting routing) {
        return new VehInfoMessage(
                routing,
                messageId, // ID original
                timeStamp,
                senderName, senderPos, senderHeading, senderSpeed, senderLaneId, distanceToRsu, numberOfHops,
                super.getMode(), super.getFwrdId(), publisher);
    }

    @Override
    public String toString() {
        return String.format(
                "VehInfoMsg {messageId='%s', timeStamp=%d, senderName='%s', senderPosition=%s, senderHeading=%.2f, senderSpeed=%.2f, senderLaneId=%d, distanceToRsu=%.2f, numberOfHops=%d, mode='%s',forwarderId='%s'}",
                messageId,
                timeStamp,
                senderName,
                senderPos != null ? senderPos.toString() : "null", // Handling null for senderPos
                senderHeading,
                senderSpeed,
                senderLaneId,
                distanceToRsu,
                numberOfHops,
                super.getMode(),
                super.getFwrdId());
    }

}
