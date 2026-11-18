package com.presence.attendance.attendance.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.presence.attendance.attendance.domain.AttendanceRecord.Status;
import com.presence.attendance.session.domain.Geofence;
import com.presence.attendance.session.domain.Session;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public final class AttendanceStatsTest {

    private static final long NOW = 1_000_000;

    private static Session session(String id, Session.Status status, long endsAt) {
        return new Session(
                id,
                "courseA",
                "teacherA",
                status,
                0,
                endsAt,
                Geofence.around(19.076, 72.8777, 100));
    }

    private static AttendanceRecord record(String sessionId, Status status) {
        return new AttendanceRecord(sessionId, "studentA", status, 1);
    }

    private static Set<String> ids(String... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    /** {@code present} attended out of {@code total} ended sessions. */
    private static AttendanceStats stats(int present, int total) {
        final Set<String> ended = new HashSet<>();
        final List<AttendanceRecord> records = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            ended.add("s" + i);
            if (i < present) {
                records.add(record("s" + i, Status.PRESENT));
            }
        }
        return AttendanceStats.of(ended, records);
    }

    @Test
    public void onlySessionsThatAreOverAreCounted() {
        final Set<String> ended =
                AttendanceStats.ended(
                        Arrays.asList(
                                session("live", Session.Status.ACTIVE, NOW + 1),
                                session("timedOut", Session.Status.ACTIVE, NOW - 1),
                                session("endedEarly", Session.Status.ENDED, NOW + 1)),
                        NOW);
        assertEquals(ids("timedOut", "endedEarly"), ended);
    }

    @Test
    public void everyKindOfPresentCountsAndAManualAbsentDoesNot() {
        final AttendanceStats stats =
                AttendanceStats.of(
                        ids("a", "b", "c", "d", "e"),
                        Arrays.asList(
                                record("a", Status.PRESENT),
                                record("b", Status.LATE_SYNC),
                                record("c", Status.MANUAL_PRESENT),
                                record("d", Status.MANUAL_ABSENT)));
        assertEquals(3, stats.getPresent());
        assertEquals("the session with no record is a miss", 5, stats.getTotal());
        assertEquals(60, stats.percent());
    }

    @Test
    public void aMarkInASessionStillInProgressIsNotCountedYet() {
        final AttendanceStats stats =
                AttendanceStats.of(
                        ids("a"), Collections.singletonList(record("live", Status.PRESENT)));
        assertEquals(0, stats.getPresent());
        assertEquals(1, stats.getTotal());
    }

    @Test
    public void aCourseWithNoSessionsHasNoPercentageAndNobodyIsAtRisk() {
        final AttendanceStats stats = stats(0, 0);
        assertFalse(stats.hasSessions());
        assertEquals(0, stats.percent());
        assertFalse(stats.isBelow(75));
    }

    @Test
    public void percentagesRoundHalvesUp() {
        assertEquals(33, stats(1, 3).percent());
        assertEquals(67, stats(2, 3).percent());
        assertEquals(13, stats(1, 8).percent());
        assertEquals(100, stats(7, 7).percent());
        assertEquals(0, stats(0, 4).percent());
    }

    @Test
    public void theThresholdIsComparedExactlyNotOnTheRoundedFigure() {
        assertFalse("exactly 75%", stats(3, 4).isBelow(75));
        assertTrue("50%", stats(2, 4).isBelow(75));
        // 100 of 134 is 74.6%: it displays as 75 but is below the requirement.
        assertEquals(75, stats(100, 134).percent());
        assertTrue(stats(100, 134).isBelow(75));
        assertFalse("a threshold of 1 is met by a single session", stats(1, 100).isBelow(1));
        assertTrue(stats(0, 100).isBelow(1));
    }

    @Test
    public void aRevokedMarkIsAbsentAndCostsOneMoreAttendedSession() {
        final AttendanceStats stats =
                AttendanceStats.of(
                        ids("a", "b", "c", "d", "e"),
                        Arrays.asList(
                                record("a", Status.PRESENT),
                                record("b", Status.PRESENT),
                                record("c", Status.LATE_SYNC),
                                record("d", Status.REVOKED)));
        assertEquals(1, stats.getStrikes());
        assertEquals("three attended, one taken off for the strike", 2, stats.getPresent());
        assertEquals(40, stats.percent());
        assertTrue(stats.isBelow(75));
    }

    @Test
    public void thePenaltyNeverTakesTheFigureBelowZero() {
        final AttendanceStats stats =
                AttendanceStats.of(
                        ids("a", "b", "c"),
                        Arrays.asList(record("a", Status.REVOKED), record("b", Status.REVOKED)));
        assertEquals(2, stats.getStrikes());
        assertEquals(0, stats.getPresent());
    }

    @Test
    public void aRevokeInASessionStillRunningIsNotCountedYet() {
        final AttendanceStats stats =
                AttendanceStats.of(
                        ids("a"),
                        Arrays.asList(record("a", Status.PRESENT), record("live", Status.REVOKED)));
        assertEquals(0, stats.getStrikes());
        assertEquals(1, stats.getPresent());
    }
}
