package pt.uminho.npr;

import java.io.DataOutputStream;
import java.io.IOException;

import org.eclipse.mosaic.lib.geo.GeoPoint;

public class Position {

    private final GeoPoint geoPoint;

    public Position(GeoPoint geoPoint) {
        this.geoPoint = geoPoint;
    }

    public Position(Position senderPos) {
        this.geoPoint = senderPos.toGeoPoint();
    }

    public double getLatitude() {
        return geoPoint.getLatitude();
    }

    public double getLongitude() {
        return geoPoint.getLongitude();
    }

    public double getAltitude() {
        return geoPoint.getAltitude(); // optional, can be zero
    }

    public double distanceTo(Position other) {
        return this.geoPoint.distanceTo(other.toGeoPoint());
    }

    public GeoPoint toGeoPoint() {
        return this.geoPoint;
    }

    @Override
    public Position clone() {
        return new Position(geoPoint);
    }

    public void encode(DataOutputStream dos) throws IOException {
        dos.writeDouble(getLatitude());
        dos.writeDouble(getLongitude());
        dos.writeDouble(getAltitude());
    }

    /**
     * Calcula o ângulo (bearing) entre esta posição e outra.
     * Resultado em graus (0 a 360), onde 0 é Norte, 90 é Este, etc.
     */
    public double bearingTo(Position other) {
        double lat1 = Math.toRadians(this.getLatitude());
        double lon1 = Math.toRadians(this.getLongitude());
        double lat2 = Math.toRadians(other.getLatitude());
        double lon2 = Math.toRadians(other.getLongitude());

        double dLon = lon2 - lon1;

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        double bearingRad = Math.atan2(y, x);
        double bearingDeg = Math.toDegrees(bearingRad);

        return (bearingDeg + 360.0) % 360.0;
    }

    /**
     * Verifica se esta posição está dentro de um raio de X metros da outra.
     */
    public boolean equalsWithin(Position other, double thresholdMeters) {
        return this.distanceTo(other) <= thresholdMeters;
    }

    @Override
    public String toString() {
        return "Position{" +
                "lat=" + getLatitude() +
                ", lon=" + getLongitude() +
                ", alt=" + getAltitude() +
                '}';
    }
}
