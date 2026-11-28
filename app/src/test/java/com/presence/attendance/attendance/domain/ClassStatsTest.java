package com.presence.attendance.attendance.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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

public final class ClassStatsTest {

    private static final Geofence AREA = Geofence.around(19.076, 72.8777, 100);

    private static Session session(String id) {
        return new Session(id, "courseA", "teacherA", Session.Status.ENDED, 0, 1, AREA);
    }

    @Test
    public void theAverageWeighsEveryStudentsSessionsAndTurnoutIsOldestFirst() {
        // Newest first, as the course lists them; s7 is still running.
        final List<Session> held = new ArrayList<>();
        for (int i = 7; i >= 1; i--) {
            held.add(session("s" + i));
        }
        final Set<String> ended = new HashSet<>(Arrays.asList("s1", "s2", "s3", "s4", "s5", "s6"));
        final List<AttendanceRecord> records =
                Arrays.asList(
                        new AttendanceRecord("s6", "a", Status.PRESENT, 1),
                        new AttendanceRecord("s6", "b", Status.MANUAL_PRESENT, 1),
                        new AttendanceRecord("s5", "a", Status.LATE_SYNC, 1),
                        new AttendanceRecord("s5", "b", Status.REVOKED, 1),
                        new AttendanceRecord("s4", "b", Status.MANUAL_ABSENT, 1),
                        new AttendanceRecord("s7", "a", Status.PRESENT, 1));
        final AttendanceStats a = AttendanceStats.of(ended, records.subList(0, 1));
        final ClassStats figures =
                ClassStats.of(
                        Arrays.asList(
                                AttendanceStats.of(
                                        ended, Arrays.asList(records.get(0), records.get(2))),
                                AttendanceStats.of(
                                        ended, Arrays.asList(records.get(1), records.get(3)))),
                        held,
                        ended,
                        records);
        // a: 2 of 6; b: 1 present minus a one-session penalty, 0 of 6. 2 of 12 is 17%.
        assertEquals(17, figures.averagePercent());
        assertEquals(
                "s2 to s6, the running s7 left out",
                Arrays.asList(0, 0, 0, 1, 2),
                figures.recentTurnout);
    }

    @Test
    public void beforeAnySessionIsOverThereIsNoAverage() {
        final ClassStats figures =
                ClassStats.of(
                        Collections.singletonList(
                                AttendanceStats.of(
                                        Collections.emptySet(), Collections.emptyList())),
                        Collections.singletonList(session("s1")),
                        Collections.emptySet(),
                        Collections.emptyList());
        assertFalse(figures.hasSessions());
        assertEquals(0, figures.averagePercent());
        assertEquals(Collections.emptyList(), figures.recentTurnout);
    }

    @Test
    public void aCountedCourseIsAPercentOfEveryPossibleMark() {
        assertEquals(85, new CourseCount(10, 18, 153).percent());
        assertEquals("never above 100", 100, new CourseCount(1, 2, 3).percent());
        assertFalse(new CourseCount(0, 18, 0).hasSessions());
        assertFalse(new CourseCount(4, 0, 0).hasSessions());
    }
}
