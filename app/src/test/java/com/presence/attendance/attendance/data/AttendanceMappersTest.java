package com.presence.attendance.attendance.data;

import static org.junit.Assert.assertEquals;

import com.google.firebase.Timestamp;
import com.presence.attendance.attendance.domain.PendingMark;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.location.LocationFix;
import com.presence.attendance.session.domain.Geofence;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.testutil.DocumentContract;
import com.presence.attendance.testutil.Fixtures;
import java.io.IOException;
import java.util.Map;
import java.util.TreeSet;
import org.junit.Test;

/** A student's mark must have exactly the fields the rules whitelist, with server-true ids. */
public final class AttendanceMappersTest {

    @Test
    public void aLiveMarkHasExactlyTheContractFields() throws IOException {
        final Session session =
                new Session(
                        "sessA",
                        "courseA",
                        "teacherA",
                        Session.Status.ACTIVE,
                        0,
                        1,
                        Geofence.around(19.076, 72.8777, 100));
        final Map<String, Object> mark =
                AttendanceMappers.liveMark(
                        Fixtures.STUDENT,
                        session,
                        "AbCdEfGhIjKlMnOpQrSt_-",
                        new LocationFix(19.0761, 72.8778, 12.5f, false, 0));
        assertEquals(
                DocumentContract.fields("attendance.live", false), new TreeSet<>(mark.keySet()));
        assertEquals("the course and teacher are the session's", "courseA", mark.get("courseId"));
        assertEquals("teacherA", mark.get("teacherId"));
        assertEquals("studentA", mark.get("studentId"));
        assertEquals("PRESENT", mark.get("status"));
        assertEquals("LIVE", mark.get("source"));
        assertEquals(12.5, (Double) mark.get("accuracyM"), 0);
    }

    @Test
    public void aQueuedMarkHasExactlyTheContractFields() throws IOException {
        final Session session =
                new Session(
                        "sessA",
                        "courseA",
                        "teacherA",
                        Session.Status.ACTIVE,
                        0,
                        1,
                        Geofence.around(19.076, 72.8777, 100));
        final Map<String, Object> mark =
                AttendanceMappers.queuedMark(
                        session,
                        new PendingMark(
                                "sessA",
                                "studentA",
                                "AbCdEfGhIjKlMnOpQrSt_-",
                                19.0761,
                                72.8778,
                                12.5f,
                                1_700_000_000_000L,
                                PendingMark.State.PENDING,
                                null));
        assertEquals(
                DocumentContract.fields("attendance.queued", false), new TreeSet<>(mark.keySet()));
        assertEquals("LATE_SYNC", mark.get("status"));
        assertEquals("QUEUED", mark.get("source"));
        assertEquals("studentA", mark.get("studentId"));
        assertEquals("teacherA", mark.get("teacherId"));
        assertEquals(
                "the device clock at the scan",
                1_700_000_000L,
                ((Timestamp) mark.get("scannedAt")).getSeconds());
    }

    @Test
    public void aQueueRowSurvivesTheRoundTripAndUnknownValuesFailClosed() {
        final PendingMark rejected =
                new PendingMark(
                        "s",
                        "u",
                        "",
                        0,
                        0,
                        0,
                        5,
                        PendingMark.State.REJECTED,
                        AppError.OUTSIDE_AREA);
        final PendingMark back = PendingMarkMapper.fromEntity(PendingMarkMapper.toEntity(rejected));
        assertEquals(PendingMark.State.REJECTED, back.getState());
        assertEquals(AppError.OUTSIDE_AREA, back.getReason());
        assertEquals(5, back.getScannedAtMillis());

        final PendingMark odd =
                PendingMarkMapper.fromEntity(
                        new PendingMarkEntity("s", "u", "t", 1, 2, 3, 4, "FROM_THE_FUTURE", null));
        assertEquals("never shown as waiting", PendingMark.State.REJECTED, odd.getState());
        assertEquals(AppError.UNKNOWN, odd.getReason());
    }
}
