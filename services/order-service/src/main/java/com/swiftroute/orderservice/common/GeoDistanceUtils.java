package com.swiftroute.orderservice.common;

/**
 * Geographic distance utilities using the spherical Haversine formula.
 */
public final class GeoDistanceUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistanceUtils() {
    }

    /**
     * Computes great-circle distance in kilometers between two WGS-84 coordinates.
     */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double sinLat = Math.sin(dLat / 2.0);
        double sinLng = Math.sin(dLng / 2.0);
        double a = sinLat * sinLat
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinLng * sinLng;
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_KM * c;
    }
}
