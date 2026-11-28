package com.presence.attendance.department.domain;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class TimetableTest {

    @Test
    public void idsNameTheBatchSemesterCodeAndGroup() {
        assertEquals("MCA26S1", Timetable.idOf("MCA26", 1));
        assertEquals("MCA26S1R5MC5013T", Timetable.subjectId("MCA26", 1, "R5MC5013T", 0));
        assertEquals("MCA26S2CS201G3", Timetable.subjectId("MCA26", 2, "cs-201", 3));
    }

    @Test
    public void aDaysSlotsForTheGivenCoursesComeEarliestFirst() {
        final TimetableSlot late =
                new TimetableSlot(1, "14:30", "15:25", "a", TimetableSlot.Type.LECTURE, "", "");
        final TimetableSlot early =
                new TimetableSlot(1, "09:30", "10:25", "b", TimetableSlot.Type.LECTURE, "", "");
        final TimetableSlot tuesday =
                new TimetableSlot(2, "09:30", "10:25", "a", TimetableSlot.Type.LECTURE, "", "");
        final TimetableSlot notMine =
                new TimetableSlot(1, "08:30", "09:25", "c", TimetableSlot.Type.LECTURE, "", "");
        final Timetable week =
                new Timetable("MCA26", 1, Arrays.asList(late, early, tuesday, notMine));
        assertEquals(
                Arrays.asList(early, late),
                Timetable.on(Collections.singletonList(week), 1, Arrays.asList("a", "b")));
    }

    @Test
    public void minutesAreCountedFromMidnight() {
        assertEquals(13 * 60 + 30, TimetableSlot.minutesOf("13:30"));
        assertEquals(-1, TimetableSlot.minutesOf("9:30"));
        assertEquals(-1, TimetableSlot.minutesOf("24:00"));
    }
}
