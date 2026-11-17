package edu.thapar.campusnav.algorithm;

/** Geodesic helpers: Haversine distance, bearings and compass directions. */
public final class GeoUtils {

    public static final double EARTH_RADIUS_METERS = 6_371_000;
    /** Average adult walking speed used for time estimates. */
    public static final double WALKING_SPEED_MPS = 1.4;

    private static final String[] DIRECTIONS = {
            "north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"
    };

    private GeoUtils() {
    }

    /** Great-circle distance in meters between two WGS-84 coordinates. */
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }

    /** Initial bearing in degrees (0 = north, clockwise) from point 1 to point 2. */
    public static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLambda = Math.toRadians(lon2 - lon1);
        double x = Math.sin(dLambda) * Math.cos(phi2);
        double y = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda);
        return (Math.toDegrees(Math.atan2(x, y)) + 360) % 360;
    }

    /** Converts a bearing into a compass word such as "north-east". */
    public static String compassDirection(double bearingDegrees) {
        int index = (int) Math.round(bearingDegrees / 45.0) % 8;
        return DIRECTIONS[index];
    }

    public static String formatDistance(double meters) {
        if (meters < 1000) {
            return Math.round(meters) + " m";
        }
        return String.format("%.2f km", meters / 1000.0);
    }

    public static String formatDuration(double seconds) {
        if (seconds < 60) {
            return "< 1 min";
        }
        return (long) Math.ceil(seconds / 60.0) + " min";
    }
}
