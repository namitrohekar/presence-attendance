package com.presence.attendance.session.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.presence.attendance.testutil.Contracts;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Runs {@code contracts/geofence-vectors.json} through the app's math. The rules suite runs the
 * same file through {@code firestore.rules}, so the client pre-check and the verifier agree.
 */
public final class GeofenceTest {

    private static final int MIN_VECTORS = 30;
    private static final double COS_TOLERANCE = 1e-12;

    private static List<Map<String, String>> vectors() throws IOException {
        return Contracts.flatObjects(Contracts.read("geofence-vectors.json"));
    }

    private static double number(Map<String, String> vector, String key) {
        return Double.parseDouble(vector.get(key));
    }

    @Test
    public void everySharedVectorGivesTheExpectedResult() throws IOException {
        final List<Map<String, String>> vectors = vectors();
        assertTrue("vector file not loaded", vectors.size() >= MIN_VECTORS);

        final List<String> wrong = new ArrayList<>();
        for (Map<String, String> v : vectors) {
            final Geofence geofence =
                    new Geofence(
                            number(v, "lat0"),
                            number(v, "lng0"),
                            number(v, "radiusM"),
                            number(v, "cosLat0"));
            final boolean actual =
                    geofence.contains(number(v, "lat"), number(v, "lng"), number(v, "accuracyM"));
            if (actual != Boolean.parseBoolean(v.get("pass"))) {
                wrong.add(v.get("name"));
            }
        }
        assertEquals("vectors with the wrong result", new ArrayList<String>(), wrong);
    }

    @Test
    public void aroundPrecomputesTheCosineStoredInTheVectors() throws IOException {
        for (Map<String, String> v : vectors()) {
            final Geofence geofence =
                    Geofence.around(number(v, "lat0"), number(v, "lng0"), number(v, "radiusM"));
            assertEquals(v.get("name"), number(v, "cosLat0"), geofence.getCosLat(), COS_TOLERANCE);
        }
    }

    @Test
    public void unusableFixesAreOutside() {
        final Geofence geofence = Geofence.around(19.076, 72.8777, 100);
        assertTrue(geofence.contains(19.076, 72.8777, 10));
        assertFalse(geofence.contains(Double.NaN, 72.8777, 10));
        assertFalse(geofence.contains(19.076, Double.NaN, 10));
        assertFalse(geofence.contains(19.076, 72.8777, Double.NaN));
        assertFalse(geofence.contains(Double.POSITIVE_INFINITY, 72.8777, 10));
        assertFalse(geofence.contains(19.076, 72.8777, Double.POSITIVE_INFINITY));
    }
}
