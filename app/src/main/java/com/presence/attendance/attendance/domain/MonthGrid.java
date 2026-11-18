package com.presence.attendance.attendance.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.session.domain.Session;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One student's month: a calendar of whole weeks, each day marked by what happened in the classes
 * held that day, and the month's own figure. Days and months are the student's local ones.
 */
public final class MonthGrid {

    /** What a day looks like on the calendar. */
    public enum Mark {
        /** No class that day. */
        NONE,
        /** Every class held that day attended. */
        ATTENDED,
        /** Some attended, some missed. */
        PARTLY,
        /** Classes held, none attended. */
        MISSED,
        /** A mark that day was revoked as a proxy. Outranks everything else. */
        REVOKED,
        /** A class is still running and has not been marked. */
        RUNNING
    }

    /** One square of the calendar. */
    public static final class Day {
        @NonNull public final LocalDate date;

        /** False for the days of the previous and next month that fill the first and last week. */
        public final boolean inMonth;

        @NonNull public final Mark mark;

        Day(@NonNull LocalDate date, boolean inMonth, @NonNull Mark mark) {
            this.date = date;
            this.inMonth = inMonth;
            this.mark = mark;
        }
    }

    private MonthGrid() {}

    /** The date a session belongs to: the local day it started. */
    @NonNull
    public static LocalDate dayOf(@NonNull Session session, @NonNull ZoneId zone) {
        return Instant.ofEpochMilli(session.getStartedAtMillis()).atZone(zone).toLocalDate();
    }

    @NonNull
    public static YearMonth monthOf(@NonNull Session session, @NonNull ZoneId zone) {
        return YearMonth.from(dayOf(session, zone));
    }

    /** The sessions that started in the month, in the order given. */
    @NonNull
    public static List<Session> inMonth(
            @NonNull Collection<Session> sessions, @NonNull YearMonth month, @NonNull ZoneId zone) {
        final List<Session> found = new ArrayList<>();
        for (Session session : sessions) {
            if (monthOf(session, zone).equals(month)) {
                found.add(session);
            }
        }
        return found;
    }

    /** The month's figure: the same rules as the overall one, over that month's classes only. */
    @NonNull
    public static AttendanceStats stats(
            @NonNull Collection<Session> sessions,
            @NonNull Collection<AttendanceRecord> records,
            @NonNull YearMonth month,
            @NonNull ZoneId zone,
            long nowMillis) {
        return AttendanceStats.of(
                AttendanceStats.ended(inMonth(sessions, month, zone), nowMillis), records);
    }

    /**
     * Whole weeks covering the month, starting on {@code firstDay}: 28 to 42 days.
     *
     * @param recordBySession the student's records, keyed by session id
     */
    @NonNull
    public static List<Day> days(
            @NonNull YearMonth month,
            @NonNull DayOfWeek firstDay,
            @NonNull ZoneId zone,
            @NonNull Collection<Session> sessions,
            @NonNull Map<String, AttendanceRecord> recordBySession,
            long nowMillis) {
        final LocalDate first = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDay));
        final LocalDate last =
                month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(firstDay.minus(1)));
        final List<Day> days = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            days.add(
                    new Day(
                            date,
                            YearMonth.from(date).equals(month),
                            mark(date, zone, sessions, recordBySession, nowMillis)));
        }
        return days;
    }

    @NonNull
    private static Mark mark(
            @NonNull LocalDate date,
            @NonNull ZoneId zone,
            @NonNull Collection<Session> sessions,
            @NonNull Map<String, AttendanceRecord> recordBySession,
            long nowMillis) {
        int held = 0;
        int attended = 0;
        boolean revoked = false;
        boolean running = false;
        for (Session session : sessions) {
            if (!dayOf(session, zone).equals(date)) {
                continue;
            }
            @Nullable final AttendanceRecord record = recordBySession.get(session.getId());
            final boolean counts = record != null && record.getStatus().countsAsPresent();
            if (session.isLive(nowMillis)) {
                // A running class counts once it is marked; until then it is only "running".
                running |= !counts;
                continue;
            }
            held++;
            if (counts) {
                attended++;
            } else if (record != null && record.getStatus() == AttendanceRecord.Status.REVOKED) {
                revoked = true;
            }
        }
        if (revoked) {
            return Mark.REVOKED;
        }
        if (held == 0) {
            return running ? Mark.RUNNING : Mark.NONE;
        }
        if (attended == held) {
            return Mark.ATTENDED;
        }
        return attended == 0 ? Mark.MISSED : Mark.PARTLY;
    }

    /** The months that have a class, as a set: what the calendar can step through. */
    @NonNull
    public static Set<YearMonth> monthsWithClasses(
            @NonNull Collection<Session> sessions, @NonNull ZoneId zone) {
        final Set<YearMonth> months = new HashSet<>();
        for (Session session : sessions) {
            months.add(monthOf(session, zone));
        }
        return months;
    }
}
