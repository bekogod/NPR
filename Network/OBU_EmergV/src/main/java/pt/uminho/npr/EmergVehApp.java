package pt.uminho.npr;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.enums.VehicleStopMode;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.lib.util.scheduling.EventBuilder;
import org.eclipse.mosaic.rti.TIME;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class EmergVehApp extends AbstractApplication<VehicleOperatingSystem>
        implements VehicleApplication, CommunicationApplication {
    private final long MsgDelay = 200 * TIME.MILLI_SECOND;
    private final int Power = 50;
    private final double Distance = 140.0;
    private static final double DECELERATION_NORMAL = 4.0; // m/s²
    private static final double DECELERATION_EMERGENCY = 9.0; // m/s²

    private final Map<String, NeighborInfo> knownVehicleNeighbors = new HashMap<>();
    private final Map<String, NeighborInfo> knownRsuNeighbors = new HashMap<>();

    private int msgIdCounter = 0;
    private double vehHeading;
    private double vehSpeed;
    private int vehLane;

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "onShutdown");
        getOs().getAdHocModule().disable();
    }

    @Override
    public void onStartup() {
        getOs().getAdHocModule().enable(new AdHocModuleConfiguration()
                .addRadio()
                .channel(AdHocChannel.CCH)
                .power(Power)
                .distance(Distance)
                .create());

        getLog().infoSimTime(this, "=== Starting Emergency Vehicle ===");
        getOs().getEventManager().addEvent(getOs().getSimulationTime() + MsgDelay, this);
    }

    @Override
    public void processEvent(Event event) throws Exception {
        cleanupOldNeighbors();
        Object resource = event.getResource();
        if (resource != null) {
            if (resource instanceof Message) {
                Message message = (Message) resource;
                getOs().getAdHocModule().sendV2xMessage(message);
                getLog().infoSimTime(this, "Sent : " + message.toString());

            }
        } else {
            // getLog().infoSimTime(this, "Event Tick: ");

            // Send a Awareness message every 1 second
            if (getOs().getSimulationTime() % (1 * TIME.SECOND) == 0) {
                // getLog().infoSimTime(this, "Scheduling Info Message: ");
                sendVehInfoMsg();
            }
            getOs().getEventManager().addEvent(getOs().getSimulationTime() + MsgDelay, this);

        }

    }

    /**
     * Calculates the time in nanoseconds required to decelerate from an initial
     * speed to a final speed.
     *
     * @param startingSpeed Initial speed in km/h
     * @param finalSpeed    Final speed in km/h
     * @param deceleration  Deceleration in m/s²
     * @return Deceleration time in nanoseconds (long)
     * 
     */

    public long slowDownTime(double startingSpeed, double finalSpeed, double deceleration) {
        if (startingSpeed < finalSpeed) {
            getLog().infoSimTime(this, "Starting speed must be greater than or equal to final speed.");
        }

        double vStart = startingSpeed / 3.6; // m/s
        double vEnd = finalSpeed / 3.6; // m/s

        double timeSeconds = (vStart - vEnd) / deceleration; // seconds

        return (long) (timeSeconds * 1_000_000_000); // nanoseconds
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedMessage) {
        getLog().infoSimTime(this, "onMessageReceived");

        long currentTime = getOs().getSimulationTime();

        if (receivedMessage.getMessage() instanceof SlowMessage) {

            SlowMessage slowMsg = (SlowMessage) receivedMessage.getMessage();
            String veh_id = "Emergency_" + getOs().getId();
           
            getLog().infoSimTime(this, "Received message: " + slowMsg.toString());

            if (veh_id.equals(slowMsg.getReceiverName())) {

                float targetSpeed = (float) (slowMsg.getTargetSpeed() / 3.6); // m/s

                getOs().slowDown(targetSpeed, slowDownTime(this.vehSpeed, targetSpeed, DECELERATION_NORMAL));

                getLog().infoSimTime(this, "Vehicle is slowing due to received SLOW command.");

            } else if (slowMsg.getMode() == Mode.DIRECT) {

                if (veh_id.equals(slowMsg.getFwrdId())) {

                    String destId = slowMsg.getReceiverName();
                    NeighborInfo destInfo = findNeighbor(destId);

                    if (destInfo != null) { // existe o vixinho no meu mapa
                        getLog().infoSimTime(this, "Forwarding Message: " + slowMsg.toString());

                        MessageRouting routing = getOs().getAdHocModule()
                                .createMessageRouting()
                                .channel(AdHocChannel.CCH) // Send on Control Channel
                                .topological().broadcast()
                                .build(); // Broadcast to nearby vehicles

                        // Change Mode to Direct so that the message backtraces
                        // the network to the Destination
                        SlowMessage message = new SlowMessage(
                                routing,
                                Mode.DIRECT,
                                slowMsg.getTime(),
                                slowMsg.getSenderName(),
                                destInfo.getPublisher(),
                                slowMsg.getReceiverName(),
                                slowMsg.getTargetSpeed());

                        EventBuilder enviarMsg = getOs().getEventManager().newEvent(currentTime + MsgDelay, this);
                        enviarMsg.withResource(message);
                        enviarMsg.schedule();

                    } // se nao existe nao faco nada

                }

            } else if (slowMsg.getMode() == Mode.SEARCH) {

                String destId = slowMsg.getReceiverName();
                NeighborInfo destInfo = findNeighbor(destId);

                if (destInfo != null) {
                    getLog().infoSimTime(this, "Forwarding Message: " + slowMsg.toString());

                    MessageRouting routing = getOs().getAdHocModule()
                            .createMessageRouting()
                            .channel(AdHocChannel.CCH) // Send on Control Channel
                            .topological().broadcast()
                            .build(); // Broadcast to nearby vehicles

                    // Change Mode to Direct so that the message backtraces
                    // the network to the Destination
                    SlowMessage message = new SlowMessage(
                            routing,
                            Mode.DIRECT,
                            slowMsg.getTime(),
                            slowMsg.getSenderName(),
                            destInfo.getPublisher(),
                            slowMsg.getReceiverName(),
                            slowMsg.getTargetSpeed());

                    EventBuilder enviarMsg = getOs().getEventManager().newEvent(currentTime + MsgDelay, this);
                    enviarMsg.withResource(message);
                    enviarMsg.schedule();
                }
            }

            // TODO: Reverter desaceleração e paragem

        } else if (receivedMessage.getMessage() instanceof StopMessage) {

            StopMessage stopMsg = (StopMessage) receivedMessage.getMessage();
            getLog().infoSimTime(this, "Received STOP message from " + stopMsg.getSenderName());

            // getOs().stopNow(VehicleStopMode.PARK_ON_ROADSIDE, slowDownTime(this.vehSpeed, 0, DECELERATION_EMERGENCY));
            // getLog().infoSimTime(this, "Vehicle is stopping due to received STOP command.");

        } else if (receivedMessage.getMessage() instanceof BeaconMsg) {

            BeaconMsg beaconMsg = (BeaconMsg) receivedMessage.getMessage();
            String senderId = beaconMsg.getSenderName();

            getLog().infoSimTime(this, "Received Beacon message from " + beaconMsg.getSenderName());

            NeighborInfo neighbor = new NeighborInfo(
                    senderId,
                    beaconMsg.getSenderPosition(),
                    0.0,
                    0,
                    0.0,
                    0.0,
                    -1,
                    currentTime,
                    senderId);

            getLog().infoSimTime(this, "Adding RSU: " + neighbor.toString() + " To Network Map");

            knownRsuNeighbors.put(senderId, neighbor);

        } else if (receivedMessage.getMessage() instanceof VehInfoMessage) {

            // Receive Info message
            VehInfoMessage fwdMsg = (VehInfoMessage) receivedMessage.getMessage();
            String senderId = fwdMsg.getSenderName();
            String veh_id = "Emergency_" + getOs().getId();

            if (!senderId.equals(veh_id)) { // only react if message is not mine, this avoid loopbacks

                // Atualizar vizinhança
                NeighborInfo neighbor = new NeighborInfo(
                        senderId,
                        fwdMsg.getSenderPosition(),
                        fwdMsg.getDistanceToRsu(),
                        fwdMsg.getNumberOfHops(),
                        fwdMsg.getHeading(),
                        fwdMsg.getSpeed(),
                        fwdMsg.getLaneId(),
                        currentTime,
                        fwdMsg.getPublisher());

                getLog().infoSimTime(this, "Adding Veh: " + neighbor.toString() + " To Network Map");

                // TODO: so mudar a info dos vizinhos se o caminho for melhor ou o mais atual
                knownVehicleNeighbors.put(senderId, neighbor);
                /**
                 * If Mode is Search, the sender doesnt know about any RSU's
                 * I should Apply the forwarding logic here
                 * 1. try to find RSU
                 * 2. if not find veh with closest
                 * 3. if not do nothing to avoid loopbacks
                 */
                // Forward Info message if my id is equal to fwrdID of msg
                if (veh_id.equals(fwdMsg.getFwrdId()) || fwdMsg.getMode() == Mode.SEARCH) {
                    getLog().infoSimTime(this, "Forwarding Message: " + fwdMsg.toString());

                    String forwarderId = bestNeighbor();

                    if (forwarderId != "BROADCAST") {

                        MessageRouting routing = getOs().getAdHocModule()
                                .createMessageRouting()
                                .channel(AdHocChannel.CCH) // Send on Control Channel
                                .topological().broadcast()
                                .build(); // Broadcast to nearby vehicles

                        // Create the VehInfoMsg that is a copy of the message received, only changing
                        // the forwarder Id
                        VehInfoMessage message = new VehInfoMessage(
                                routing,
                                fwdMsg.getMessageId(),
                                fwdMsg.getTime(),
                                fwdMsg.getSenderName(),
                                fwdMsg.getSenderPosition(),
                                fwdMsg.getHeading(),
                                fwdMsg.getSpeed(),
                                fwdMsg.getLaneId(),
                                fwdMsg.getDistanceToRsu(),
                                fwdMsg.getNumberOfHops(),
                                Mode.DIRECT,
                                forwarderId,
                                veh_id);

                        EventBuilder enviarMsg = getOs().getEventManager().newEvent(currentTime + MsgDelay, this);
                        enviarMsg.withResource(message);
                        enviarMsg.schedule();
                    } // else i dont know any rsu's so i dont BROADCAST

                }
            }
        }

    }

    private String bestNeighbor() {
        String forwarderId = "BROADCAST";

        // Check closest RSU
        Position myPosition = new Position(getOs().getPosition()); // Vehicle's current position
        NeighborInfo receiver = findClosestRsu(myPosition);

        if (receiver != null) {
            forwarderId = receiver.getId();
        } else { // search for neiborh Closest to RSU
            receiver = findClosestNeighborToRsu();
            if (receiver != null) {
                forwarderId = receiver.getId();
            }
        }
        // change the Id of forwarder to next best neighbor (be RSU or Vehicle)
        return forwarderId;
    }

    private void cleanupOldNeighbors() {
        long currentTime = getOs().getSimulationTime();
        long threshold = 2 * TIME.SECOND;

        knownVehicleNeighbors.values().removeIf(n -> (currentTime - n.lastSeen) > threshold);
        knownRsuNeighbors.values().removeIf(n -> (currentTime - n.lastSeen) > threshold);
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission arg0) {
        // getLog().infoSimTime(this, "onMessageTransmitted");
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        // getLog().infoSimTime(this, "onVehicleUpdated");

        this.vehHeading = updatedVehicleData.getHeading().doubleValue();
        double speedKmh = updatedVehicleData.getSpeed() * 3.6;
        this.vehSpeed = Double.parseDouble(String.format("%.2f", speedKmh));
        this.vehLane = updatedVehicleData.getRoadPosition().getLaneIndex();
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement arg0) {
        getLog().infoSimTime(this, "onAcknowledgementReceived");

    }

    @Override
    public void onCamBuilding(CamBuilder arg0) {
        getLog().infoSimTime(this, "onCamBuilding");
    }

    private void sendVehInfoMsg() {

        MessageRouting routing = getOs().getAdHocModule()
                .createMessageRouting()
                .channel(AdHocChannel.CCH) // Send on Control Channel
                .topological().broadcast()
                .build();

        long currentTime = getOs().getSimulationTime();
        String carId = "Emergency_" + getOs().getId();
        String messageId = carId + "_" + msgIdCounter++;

        /**
         * Default values if no RSU info exists,
         * this would mean that OBU as no info about Network map
         */

        double distanceToRsu = Double.MAX_VALUE;
        int numberOfHops = -1;
        String forwarderId = "BROADCAST";
        Mode mode = Mode.SEARCH;

        // Check closest RSU
        Position myPosition = new Position(getOs().getPosition()); // Vehicle's current position

        NeighborInfo receiver = findClosestRsu(myPosition);

        if (receiver != null) {
            distanceToRsu = myPosition.distanceTo(receiver.position); // Calculate distance to the closest RSU
            numberOfHops = 0;
            forwarderId = receiver.getId();
            mode = Mode.DIRECT;
        } else { // search for neiborh Closest to RSU
            receiver = findClosestNeighborToRsu();

            if (receiver != null) {
                double distanceToVeh = myPosition.distanceTo(receiver.position); // Calculate distance to the vehicle
                distanceToRsu = distanceToVeh + receiver.getDistanceToRsu();
                numberOfHops = receiver.getNumberOfHops() + 1;
                forwarderId = receiver.getId();
                mode = Mode.DIRECT;
            }
        }

        if (receiver == null) {

            distanceToRsu = -1;
            numberOfHops = -1;
            forwarderId = "BROADCAST";
            mode = Mode.SEARCH;

        }

        // Create the VehInfoMsg with the closest RSU details
        VehInfoMessage message = new VehInfoMessage(
                routing,
                messageId,
                currentTime,
                carId,
                new Position(getOs().getPosition()),
                this.vehHeading,
                this.vehSpeed,
                this.vehLane,
                distanceToRsu,
                numberOfHops,
                mode,
                forwarderId,
                carId);

        EventBuilder enviarMsg = getOs().getEventManager().newEvent(currentTime + MsgDelay, this);
        enviarMsg.withResource(message);
        enviarMsg.schedule();
        // getOs().getAdHocModule().sendV2xMessage(message);
        // getLog().infoSimTime(this, "Sent VehInfoMessage: " + message.toString());
    }

    private NeighborInfo findNeighbor(String DestId) {
        // If the map is empty, return null
        if (knownVehicleNeighbors.isEmpty()) {
            getLog().infoSimTime(this, "No Vehicles known.");
            return null;
        }

        // Try to get the neighbor with the specified DestId from the map
        NeighborInfo neighbor = knownVehicleNeighbors.get(DestId);

        // If no neighbor is found, return null
        if (neighbor == null) {
            getLog().infoSimTime(this, "No neighbor found with DestId: " + DestId);
        }

        return neighbor;
    }

    private NeighborInfo findClosestRsu(Position myPosition) {
        if (knownRsuNeighbors.isEmpty()) {
            getLog().infoSimTime(this, "No RSUs known.");
            return null;
        }

        NeighborInfo closestRsu = null;
        double minDistance = Double.MAX_VALUE;

        for (NeighborInfo rsu : knownRsuNeighbors.values()) {
            double distance = myPosition.distanceTo(rsu.position);
            if (distance < minDistance) {
                minDistance = distance;
                closestRsu = rsu;
            }
        }
        return closestRsu;
    }

    private NeighborInfo findClosestNeighborToRsu() {
        // If there are no neighbors, return null
        if (knownVehicleNeighbors.isEmpty()) {
            getLog().infoSimTime(this, "No Vehicles known.");
            return null;
        }

        NeighborInfo closestNeighbor = null;
        double minDistance = Double.MAX_VALUE; // Initially set to maximum possible value
        int minHops = Integer.MAX_VALUE; // Initially set to maximum possible value

        // Loop through all neighbors
        for (NeighborInfo neighbor : knownVehicleNeighbors.values()) {

            // cehck if distance is reserved value -1 ou seja doesnt know of any rsu's
            if (neighbor.distanceToRsu != -1) {

                // Check if the neighbor is closer and has fewer hops
                if (neighbor.distanceToRsu < minDistance ||
                        (neighbor.distanceToRsu == minDistance && neighbor.numberOfHops < minHops)) {
                    minDistance = neighbor.distanceToRsu;
                    minHops = neighbor.numberOfHops;
                    if (minHops < 0)
                        closestNeighbor = null; // If the closest neihbor doesnt know of any RSU then chose no neighbor
                    else
                        closestNeighbor = neighbor; // Update the closest neighbor
                }
            }
        }

        return closestNeighbor; // Return the closest neighbor with fewer hops
    }

}
