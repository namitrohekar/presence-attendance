package com.presence.attendance.session.domain;

import androidx.annotation.NonNull;
import com.presence.attendance.core.Constants;

/** The circular area a student must be inside to mark attendance. Immutable. */
public final class Geofence {

    private static final double MIN_LAT = -90;
    private static final double MAX_LAT = 90;
    private static final double MIN_LNG = -180;
    private static final double MAX_LNG = 180;

    private final double lat;
    private final double lng;
    private final double radiusM;
    private final double cosLat;

    /**
     * @param cosLat cosine of {@code lat}, stored with the session because the security rules
     *     cannot do trigonometry
     */
    public Geofence(double lat, double lng, double radiusM, double cosLat) {
        this.lat = lat;
        this.lng = lng;
        this.radiusM = radiusM;
        this.cosLat = cosLat;
    }

    /** Creates a geofence around a point, precomputing {@code cosLat}. */
    @NonNull
    public static Geofence around(double lat, double lng, double radiusM) {
        return new Geofence(lat, lng, radiusM, Math.cos(Math.toRadians(lat)));
    }

    /**
     * Whether a location fix counts as inside the geofence.
     *
     * <p>The fix passes when its distance from the centre is at most the radius plus the fix's own
     * accuracy. Anything unusable (out-of-range coordinates, NaN, accuracy that is zero, negative
     * or worse than {@link Constants#MAX_ACCURACY_M}) is outside.
     *
     * <p>The arithmetic and its order must stay identical to {@code inGeofence()} in {@code
     * firestore.rules}; both are checked against {@code contracts/geofence-vectors.json}.
     */
    public boolean contains(double lat, double lng, double accuracyM) {
        // Written as positive conditions so that NaN fails them.
        final boolean validPosition =
                lat >= MIN_LAT && lat <= MAX_LAT && lng >= MIN_LNG && lng <= MAX_LNG;
        final boolean validAccuracy = accuracyM > 0 && accuracyM <= Constants.MAX_ACCURACY_M;
        if (!validPosition || !validAccuracy) {
            return false;
        }
        final double dy = (lat - this.lat) * Constants.METERS_PER_DEGREE;
        final double dx = (lng - this.lng) * Constants.METERS_PER_DEGREE * cosLat;
        final double reach = radiusM + accuracyM;
        return dx * dx + dy * dy <= reach * reach;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public double getRadiusM() {
        return radiusM;
    }

    public double getCosLat() {
        return cosLat;
    }
}
