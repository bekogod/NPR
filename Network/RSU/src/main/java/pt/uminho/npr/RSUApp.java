package pt.uminho.npr;

import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.lib.util.scheduling.EventBuilder;
import org.eclipse.mosaic.fed.application.app.api.os.RoadSideUnitOperatingSystem;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

import pt.uminho.npr.VehInfoMessage;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class RSUApp extends AbstractApplication<RoadSideUnitOperatingSystem>
        implements CommunicationApplication

{
    private final long MsgDelayAdHoc = 200 * TIME.MILLI_SECOND;
    private final long MsgDelayCell = 0 * TIME.MILLI_SECOND; // 0 porque o federate do Cell ja mete delay
    private final int Power = 50;
    private final double Distance = 140.0;

    @Override
    public void onStartup() {
        getOs().getAdHocModule().enable(new AdHocModuleConfiguration()
                .addRadio()
                .channel(AdHocChannel.CCH)
                .power(Power)
                .distance(Distance)
                .create());

        getOs().getCellModule().enable();

        getLog().infoSimTime(this, "=== Starting RSU ===");
        getOs().getEventManager().addEvent(getOs().getSimulationTime() + MsgDelayAdHoc, this);
    }

    @Override
    public void processEvent(Event event) throws Exception {

        Object resource = event.getResource();
        if (resource != null) {
            getLog().infoSimTime(this, "Sent : " + resource.toString());

            if (resource instanceof Message) {
                Message message = (Message) resource;

                if (isNetworkMessage(message)) {
                    getOs().getCellModule().sendV2xMessage(message);
                } else if (isFogMessage(message)) {
                    getOs().getAdHocModule().sendV2xMessage(message);

                }
            } else if (resource instanceof BeaconMsg) {
                BeaconMsg message = (BeaconMsg) resource;
                getOs().getAdHocModule().sendV2xMessage(message);

            }

        } else {
            // getLog().infoSimTime(this, "Event Tick: ");

            // Send a Awareness message every 1 second
            if (getOs().getSimulationTime() % (1 * TIME.SECOND) == 0) {
                // getLog().infoSimTime(this, "Scheduling Beacon Message: ");
                sendBeaconMsg();
            }
            getOs().getEventManager().addEvent(getOs().getSimulationTime() + MsgDelayAdHoc, this);
        }

    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Shutdown application");
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {

    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgement) {

    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {
    }

    private boolean isFogMessage(Message msg) {
        return msg instanceof SlowMessage || msg instanceof StopMessage || msg instanceof ResumeMessage;
    }

    private boolean isNetworkMessage(Message msg) {
        return msg instanceof VehInfoMessage; // || msg instanceof FastMessage;
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedMsg) {

        if (receivedMsg.getMessage() instanceof Message) {
            Message msg = (Message) receivedMsg.getMessage();
            long currentTime = getOs().getSimulationTime();

            if (isNetworkMessage(msg)) {
                // only forward the messages that i am the forwardId of
                // VehInfoMessage vehMsg = (VehInfoMessage) msg;

                if (msg.getFwrdId() == getOs().getId() || msg.getMode() == Mode.SEARCH) {

                    getLog().infoSimTime(this, "Received msg: " + msg.toString());

                    msg.setFwrdId(getOs().getId()); // if it is Mode Search i want it to be rsu_id so that fog can
                                                    // traceback the message

                    MessageRouting routing = getOs().getCellModule().createMessageRouting()
                            .tcp()
                            .destination("server_0")
                            .topological()
                            .build();

                    EventBuilder enviarMsg = getOs().getEventManager().newEvent(currentTime + MsgDelayCell, this);
                    enviarMsg.withResource(msg.clone(routing));
                    enviarMsg.schedule();

                    // getOs().getCellModule().sendV2xMessage((V2xMessage) msg.clone(routing));
                }
            } else if (isFogMessage(msg)) {

                if (msg.getFwrdId() == getOs().getId()) {

                    getLog().infoSimTime(this, "Received msg: " + msg.toString());

                    MessageRouting routing = getOs().getAdHocModule()
                            .createMessageRouting()
                            .channel(AdHocChannel.CCH)
                            .topological().broadcast()
                            .build();

                    EventBuilder enviarMsg = getOs().getEventManager().newEvent(currentTime + MsgDelayAdHoc, this);
                    enviarMsg.withResource(msg.clone(routing));
                    enviarMsg.schedule();

                    // getOs().getAdHocModule().sendV2xMessage((V2xMessage) msg.clone(routing));
                }
            } else {

                getLog().infoSimTime(this, "Undefined behavior for " + msg.toString());

            }
        }
    }

    private void sendBeaconMsg() {
        long currentTime = getOs().getSimulationTime();

        MessageRouting routing = getOs().getAdHocModule()
                .createMessageRouting()
                .channel(AdHocChannel.CCH)
                .topological().broadcast()
                .build();

        long time = getOs().getSimulationTime();
        String rsuId = getOs().getId();

        BeaconMsg message = new BeaconMsg(
                routing,
                time,
                rsuId,
                new Position(getOs().getPosition()));

        EventBuilder enviarMsg = getOs().getEventManager().newEvent(currentTime + MsgDelayAdHoc, this);
        enviarMsg.withResource(message);
        enviarMsg.schedule();
    }

}