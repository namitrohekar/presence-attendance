package com.presence.attendance.attendance.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.presence.attendance.attendance.domain.AttendanceRecord.Status;
import com.presence.attendance.attendance.domain.SessionReview.Flag;
import com.presence.attendance.attendance.domain.SessionReview.Reason;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;

public final class SessionReviewTest {

    private static AttendanceRecord at(String student, Status status, Double lat, Double lng) {
        return new AttendanceRecord("s", student, status, 1, true, null, lat, lng, 0);
    }

    @Test
    public void studentsAtExactlyTheSamePlaceAreFlaggedAgainstEachOther() {
        final Map<String, Flag> flags =
                SessionReview.flags(
                        Arrays.asList(
                                at("c", Status.PRESENT, 19.07612, 72.87771),
                                at("a", Status.PRESENT, 19.07612, 72.87771),
                                at("b", Status.PRESENT, 19.07612, 72.87771),
                                at("d", Status.PRESENT, 19.076121, 72.87771)));
        assertEquals(3, flags.size());
        assertEquals(Reason.SAME_PLACE, flags.get("a").reason);
        assertEquals("the first other, in a stable order", "b", flags.get("a").otherStudentId);
        assertEquals("a", flags.get("b").otherStudentId);
        assertEquals("a", flags.get("c").otherStudentId);
        assertFalse("a metre away is a real phone", flags.containsKey("d"));
    }

    @Test
    public void aLateSendIsFlaggedButTheSamePlaceTakesPrecedence() {
        final Map<String, Flag> flags =
                SessionReview.flags(
                        Arrays.asList(
                                at("a", Status.LATE_SYNC, 1.0, 2.0),
                                at("b", Status.PRESENT, 1.0, 2.0),
                                at("c", Status.LATE_SYNC, 3.0, 4.0)));
        assertEquals(Reason.SAME_PLACE, flags.get("a").reason);
        assertEquals(Reason.SENT_LATE, flags.get("c").reason);
        assertNull(flags.get("c").otherStudentId);
    }

    @Test
    public void marksThatAlreadyDoNotCountAndHandMadeRecordsAreNotFlagged() {
        final Map<String, Flag> flags =
                SessionReview.flags(
                        Arrays.asList(
                                at("a", Status.REVOKED, 1.0, 2.0),
                                at("b", Status.PRESENT, 1.0, 2.0),
                                at("c", Status.MANUAL_ABSENT, 1.0, 2.0),
                                at("d", Status.MANUAL_PRESENT, null, null),
                                at("e", Status.MANUAL_PRESENT, null, null)));
        assertTrue(flags.isEmpty());
        assertTrue(SessionReview.flags(Collections.emptyList()).isEmpty());
    }
}
