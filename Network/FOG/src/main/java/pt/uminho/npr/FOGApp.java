package pt.uminho.npr;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.objects.road.IConnection;
import org.eclipse.mosaic.lib.objects.road.INode;
import org.eclipse.mosaic.lib.objects.road.IRoadPosition;
import org.eclipse.mosaic.lib.objects.v2x.GenericV2xMessage;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.lib.util.scheduling.EventProcessor;
import org.eclipse.mosaic.rti.TIME;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FOGApp extends AbstractApplication<ServerOperatingSystem> implements CommunicationApplication {

    private final long MsgDelay = 200 * TIME.MILLI_SECOND;
    private final int Power = 50;
    private final double Distance = 140.0;
    private final Map<String, String> emergencyVehiclesPosition = new HashMap<>(); // Vehid -> IconnectId

    @Override
    public void onStartup() {
        getOs().getCellModule().enable();

        getLog().infoSimTime(this, "Setup FOG server {} at time {}", getOs().getId(), getOs().getSimulationTime());

        // getOs().getEventManager().addEvent(getOs().getSimulationTime() + MsgDelay,
        // this);
    }

    @Override
    public void processEvent(Event arg0) throws Exception {
        getLog().infoSimTime(this, "processEvent");

        getOs().getEventManager().addEvent(getOs().getSimulationTime() + MsgDelay, this);
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

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedMsg) {
        if (receivedMsg.getMessage() instanceof VehInfoMessage) {
            VehInfoMessage msg = (VehInfoMessage) receivedMsg.getMessage();

            String senderId = msg.getSenderName();
            String type = senderId.split("_")[0];
            String id = senderId.split("_")[2];
            getLog().infoSimTime(this, "Received Info msg: " + msg.toString());

            // TODO : need to have a sense of old messages, if a message is received
            // that has an older timestamp than a message i already have from that vehicle
            // need to treat it differently, ignore it for instance
            //
            // maybe mensagens com um delay superior a x ignorar, tipo aging factor
            IRoadPosition road = getOs().getRoutingModule()
                    .getClosestRoadPosition(msg.getSenderPosition().toGeoPoint());

            INode next_turn = road.getUpcomingNode();

            IConnection connection = road.getConnection();

            if (type.equals("Default")) {
                getLog().infoSimTime(this, "Default Detected");

                Mode mode;
                if (msg.getNumberOfHops() == 0)
                    mode = Mode.DIRECT;
                else
                    mode = Mode.SEARCH;

                if (emergencyVehiclesPosition.containsValue(connection.getId())) {

                    sendStopMessage(msg.getFwrdId(), mode, msg.getSenderName());

                } else {
                    if (!emergencyVehiclesPosition.isEmpty())
                        sendResumeMessage(msg.getFwrdId(), mode, msg.getSenderName());
                }

            } else if (type.equals("Emergency")) {
                getLog().infoSimTime(this, "Emergency Detected on road: " + connection.getId());

                emergencyVehiclesPosition.put(id, connection.getId());

            }
            getLog().infoSimTime(this, "Emergency map: " + emergencyVehiclesPosition.toString());

            List<String> outgoing_ids = connection.getOutgoingConnections()
                    .stream()
                    .map(IConnection::getId)
                    .collect(Collectors.toList());

            List<String> incoming_ids = connection.getOutgoingConnections()
                    .stream()
                    .map(IConnection::getId)
                    .collect(Collectors.toList());

            getLog().infoSimTime(this, "Upcoming Node: " + next_turn.getId());
            getLog().infoSimTime(this, "Outgoing Connections: " + outgoing_ids);
            getLog().infoSimTime(this, "Incoming Connections: " + incoming_ids);

            double speedLimit = 30;
            if (msg.getSpeed() > speedLimit + 5) {// 5km de respiro
                Mode mode;
                if (msg.getNumberOfHops() == 0)
                    mode = Mode.DIRECT;
                else
                    mode = Mode.SEARCH;

                sendSlowMessage(msg.getFwrdId(), mode, msg.getSenderName(), (float) speedLimit);
            }

        }
    }

    private void sendStopMessage(String rsu, Mode mode, String destination) {
        long time = getOs().getSimulationTime();

        MessageRouting routing = getOs().getCellModule().createMessageRouting()
                .tcp()
                .destination(rsu)
                .topological()
                .build();

        StopMessage message = new StopMessage(routing, mode, time, getOs().getId(), rsu, destination);

        getOs().getCellModule().sendV2xMessage(message);

        getLog().infoSimTime(this, "Sent StopMessage: " + message.toString());
    }

    private void sendResumeMessage(String rsu, Mode mode, String destination) {
        long time = getOs().getSimulationTime();

        MessageRouting routing = getOs().getCellModule().createMessageRouting()
                .tcp()
                .destination(rsu)
                .topological()
                .build();

        ResumeMessage message = new ResumeMessage(routing, mode, time, getOs().getId(), rsu, destination);

        getOs().getCellModule().sendV2xMessage(message);

        getLog().infoSimTime(this, "Sent ResumeMessage: " + message.toString());
    }

    private void sendSlowMessage(String rsu, Mode mode, String destination, float targetSpeed) {
        long time = getOs().getSimulationTime();

        MessageRouting routing = getOs().getCellModule().createMessageRouting()
                .tcp()
                .destination(rsu)
                .topological()
                .build();

        SlowMessage message = new SlowMessage(routing, mode, time, getOs().getId(), rsu, destination,
                targetSpeed);

        getOs().getCellModule().sendV2xMessage(message);

        getLog().infoSimTime(this, "Sent SlowMessage: " + message.toString());
    }

}