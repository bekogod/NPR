package pt.uminho.npr;

public class NeighborInfo {
    public String id;
    public Position position;
    final double distanceToRsu;
    final int numberOfHops;
    public double heading;
    public double speed;
    public int lane;
    public long lastSeen;
    public String publisher;

    public NeighborInfo(String id, Position position, double distanceToRsu, int numberOfHops, double heading,
            double speed, int lane, long lastSeen, String publisher) {
        this.id = id;
        this.position = position;
        this.distanceToRsu = distanceToRsu;
        this.numberOfHops = numberOfHops;
        this.heading = heading;
        this.speed = speed;
        this.lane = lane;
        this.lastSeen = lastSeen;
        this.publisher = publisher;
    }

    public String getId() {
        return id;
    }

    public String getPublisher() {
        return publisher;
    }

    public double getDistanceToRsu() {
        return distanceToRsu;
    }

    public int getNumberOfHops() {
        return numberOfHops;
    }

    @Override
    public String toString() {
        return String.format(
                "NeighborInfo {id='%s', position=%s, distanceToRsu=%.2f, numberOfHops=%d, heading=%.2f, speed=%.2f, lane=%d, lastSeen=%d, publisher=%s}",
                id,
                position != null ? position.toString() : "null", // Handling null for position
                distanceToRsu,
                numberOfHops,
                heading,
                speed,
                lane,
                lastSeen,
                publisher);
    }

}
