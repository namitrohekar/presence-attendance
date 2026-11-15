package com.presence.attendance.core.location;

/** One location reading. Immutable. Coordinates are never logged. */
public final class LocationFix {

    private final double lat;
    private final double lng;
    private final float accuracyM;
    private final boolean mock;
    private final long elapsedRealtimeNanos;

    public LocationFix(
            double lat, double lng, float accuracyM, boolean mock, long elapsedRealtimeNanos) {
        this.lat = lat;
        this.lng = lng;
        this.accuracyM = accuracyM;
        this.mock = mock;
        this.elapsedRealtimeNanos = elapsedRealtimeNanos;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    /** Radius, in metres, within which the true position lies with 68% confidence. */
    public float getAccuracyM() {
        return accuracyM;
    }

    /** True if the reading came from a mock location provider. */
    public boolean isMock() {
        return mock;
    }

    /** When the reading was taken, on the monotonic clock, for judging its age. */
    public long getElapsedRealtimeNanos() {
        return elapsedRealtimeNanos;
    }
}
