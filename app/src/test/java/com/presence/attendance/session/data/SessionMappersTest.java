package com.presence.attendance.session.data;

import static org.junit.Assert.assertEquals;

import com.presence.attendance.session.domain.Geofence;
import com.presence.attendance.testutil.DocumentContract;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.TreeSet;
import org.junit.Test;

/** The session documents the app writes must have exactly the fields the rules whitelist. */
public final class SessionMappersTest {

    @Test
    public void aNewSessionHasExactlyTheContractFields() throws IOException {
        final Geofence area = Geofence.around(19.076, 72.8777, 120);
        final Map<String, Object> session =
                SessionMappers.newSession(
                        "courseA", "teacherA", new Date(1_700_000_000_000L), area);
        assertEquals(DocumentContract.fields("sessions", false), new TreeSet<>(session.keySet()));
        assertEquals("a session always starts ACTIVE", "ACTIVE", session.get("status"));

        final Map<?, ?> geofence = (Map<?, ?>) session.get("geofence");
        final TreeSet<String> keys = new TreeSet<>();
        for (Object key : geofence.keySet()) {
            keys.add(String.valueOf(key));
        }
        assertEquals(DocumentContract.fields("sessions.geofence", false), keys);
        assertEquals(120.0, (Double) geofence.get("radiusM"), 0);
        assertEquals(Math.cos(Math.toRadians(19.076)), (Double) geofence.get("cosLat"), 1e-12);
    }

    @Test
    public void aTokenDocumentHoldsOnlyItsServerTime() throws IOException {
        assertEquals(
                DocumentContract.fields("tokens", false),
                new TreeSet<>(SessionMappers.newToken().keySet()));
    }
}
