package com.presence.attendance.attendance.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.presence.attendance.attendance.domain.AttendanceRecord.Status;
import com.presence.attendance.attendance.domain.MonthGrid.Day;
import com.presence.attendance.attendance.domain.MonthGrid.Mark;
import com.presence.attendance.session.domain.Geofence;
import com.presence.attendance.session.domain.Session;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class MonthGridTest {

    private static final ZoneId MUMBAI = ZoneId.of("Asia/Kolkata");
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final long HOUR = 3_600_000L;

    private final List<Session> sessions = new ArrayList<>();
    private final Map<String, AttendanceRecord> records = new HashMap<>();
    private long now = millis(2026, 12, 1, 9, 0);

    private static long millis(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).atZone(MUMBAI).toInstant().toEpochMilli();
    }

    /** A one-hour class starting at the given local time; recorded with the status, if any. */
    private void held(String id, int month, int day, int hour, Status status) {
        final long start = millis(2026, month, day, hour, 0);
        sessions.add(
                new Session(
                        id,
                        "courseA",
                        "teacherA",
                        Session.Status.ACTIVE,
                        start,
                        start + HOUR,
                        Geofence.around(19.0, 72.8, 100)));
        if (status != null) {
            records.put(id, new AttendanceRecord(id, "studentA", status, start));
        }
    }

    private Map<LocalDate, Day> grid() {
        final Map<LocalDate, Day> byDate = new HashMap<>();
        for (Day day : MonthGrid.days(NOVEMBER, DayOfWeek.MONDAY, MUMBAI, sessions, records, now)) {
            byDate.put(day.date, day);
        }
        return byDate;
    }

    @Test
    public void theGridIsWholeWeeksFromTheFirstDayOfTheWeek() {
        // 1 November 2026 is a Sunday.
        final List<Day> monday =
                MonthGrid.days(NOVEMBER, DayOfWeek.MONDAY, MUMBAI, sessions, records, now);
        assertEquals(LocalDate.of(2026, 10, 26), monday.get(0).date);
        assertFalse(monday.get(0).inMonth);
        assertEquals(LocalDate.of(2026, 12, 6), monday.get(monday.size() - 1).date);
        assertEquals(42, monday.size());
        final List<Day> sunday =
                MonthGrid.days(NOVEMBER, DayOfWeek.SUNDAY, MUMBAI, sessions, records, now);
        assertEquals(LocalDate.of(2026, 11, 1), sunday.get(0).date);
        assertEquals(35, sunday.size());
        // February 2026 starts on a Sunday and fills exactly four Sunday-first weeks.
        assertEquals(
                28,
                MonthGrid.days(
                                YearMonth.of(2026, 2),
                                DayOfWeek.SUNDAY,
                                MUMBAI,
                                sessions,
                                records,
                                now)
                        .size());
    }

    @Test
    public void eachDayIsMarkedByItsClasses() {
        held("a", 11, 2, 9, Status.PRESENT);
        held("b", 11, 3, 9, Status.PRESENT);
        held("c", 11, 3, 14, null);
        held("d", 11, 4, 9, null);
        held("e", 11, 5, 9, Status.MANUAL_ABSENT);
        held("f", 11, 6, 9, Status.PRESENT);
        held("g", 11, 6, 11, Status.REVOKED);
        held("h", 11, 9, 9, Status.LATE_SYNC);
        final Map<LocalDate, Day> grid = grid();
        assertEquals(Mark.ATTENDED, grid.get(LocalDate.of(2026, 11, 2)).mark);
        assertEquals("one of two", Mark.PARTLY, grid.get(LocalDate.of(2026, 11, 3)).mark);
        assertEquals(Mark.MISSED, grid.get(LocalDate.of(2026, 11, 4)).mark);
        assertEquals(Mark.MISSED, grid.get(LocalDate.of(2026, 11, 5)).mark);
        assertEquals("a revoke outranks", Mark.REVOKED, grid.get(LocalDate.of(2026, 11, 6)).mark);
        assertEquals(Mark.ATTENDED, grid.get(LocalDate.of(2026, 11, 9)).mark);
        assertEquals(Mark.NONE, grid.get(LocalDate.of(2026, 11, 10)).mark);
    }

    @Test
    public void aRunningClassIsRunningUntilMarked() {
        now = millis(2026, 11, 18, 9, 30);
        held("live", 11, 18, 9, null);
        assertEquals(Mark.RUNNING, grid().get(LocalDate.of(2026, 11, 18)).mark);
        records.put("live", new AttendanceRecord("live", "studentA", Status.PRESENT, now));
        assertEquals(
                "marked, but not yet counted as held",
                Mark.NONE,
                grid().get(LocalDate.of(2026, 11, 18)).mark);
    }

    @Test
    public void theLocalDayDecidesNotTheUtcDay() {
        // 00:30 on 1 November in Mumbai is still 31 October in UTC.
        held("early", 11, 1, 0, Status.PRESENT);
        held("late", 10, 31, 23, Status.PRESENT);
        assertEquals(Mark.ATTENDED, grid().get(LocalDate.of(2026, 11, 1)).mark);
        assertEquals(1, MonthGrid.inMonth(sessions, NOVEMBER, MUMBAI).size());
        assertEquals(NOVEMBER.minusMonths(1), MonthGrid.monthOf(sessions.get(1), MUMBAI));
        assertTrue(MonthGrid.monthsWithClasses(sessions, MUMBAI).contains(NOVEMBER));
    }

    @Test
    public void theMonthsFigureCountsOnlyThatMonthWithThePenalty() {
        held("oct", 10, 30, 9, null);
        held("a", 11, 2, 9, Status.PRESENT);
        held("b", 11, 3, 9, Status.PRESENT);
        held("c", 11, 4, 9, Status.PRESENT);
        held("d", 11, 5, 9, Status.REVOKED);
        final AttendanceStats november =
                MonthGrid.stats(sessions, records.values(), NOVEMBER, MUMBAI, now);
        assertEquals(4, november.getTotal());
        assertEquals("three attended, one off for the strike", 2, november.getPresent());
        assertEquals(1, november.getStrikes());
        final AttendanceStats october =
                MonthGrid.stats(sessions, records.values(), NOVEMBER.minusMonths(1), MUMBAI, now);
        assertEquals(1, october.getTotal());
        assertEquals(0, october.getPresent());
        assertEquals(
                0,
                MonthGrid.stats(sessions, Arrays.asList(), YearMonth.of(2026, 9), MUMBAI, now)
                        .getTotal());
    }
}
