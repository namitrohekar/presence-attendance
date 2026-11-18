package com.presence.attendance.attendance.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.AttendanceRepository;
import com.presence.attendance.attendance.domain.AttendanceStats;
import com.presence.attendance.attendance.domain.MonthGrid;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.session.domain.SessionRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * One student's attendance in one course: the overall figure, a month at a time on a calendar, and
 * the classes behind it. The student sees their own; a teacher can open any of their students'.
 */
public final class HistoryViewModel extends ViewModel {

    public enum Status {
        LOADING,
        CONTENT,
        EMPTY,
        ERROR
    }

    /** Immutable screen state. A session with no record is one the student missed. */
    public static final class State {
        @NonNull public final Status status;

        /** Every class of the course, newest first. */
        @NonNull public final List<Session> sessions;

        @NonNull public final Map<String, AttendanceRecord> recordBySession;
        @Nullable public final AttendanceStats stats;
        public final long loadedAtMillis;
        @Nullable public final AppError error;

        /** The month on the calendar; null until there is content. */
        @Nullable public final YearMonth month;

        @NonNull public final List<MonthGrid.Day> days;
        @Nullable public final AttendanceStats monthStats;
        public final boolean hasEarlierMonth;
        public final boolean hasLaterMonth;

        /** A day picked on the calendar; the list shows only its classes. */
        @Nullable public final LocalDate day;

        State(
                @NonNull Status status,
                @NonNull List<Session> sessions,
                @NonNull Map<String, AttendanceRecord> recordBySession,
                @Nullable AttendanceStats stats,
                long loadedAtMillis,
                @Nullable AppError error,
                @Nullable YearMonth month,
                @NonNull List<MonthGrid.Day> days,
                @Nullable AttendanceStats monthStats,
                boolean hasEarlierMonth,
                boolean hasLaterMonth,
                @Nullable LocalDate day) {
            this.status = status;
            this.sessions = Collections.unmodifiableList(sessions);
            this.recordBySession = Collections.unmodifiableMap(recordBySession);
            this.stats = stats;
            this.loadedAtMillis = loadedAtMillis;
            this.error = error;
            this.month = month;
            this.days = Collections.unmodifiableList(days);
            this.monthStats = monthStats;
            this.hasEarlierMonth = hasEarlierMonth;
            this.hasLaterMonth = hasLaterMonth;
            this.day = day;
        }
    }

    private final AuthRepository auth;
    private final SessionRepository sessions;
    private final AttendanceRepository attendance;
    private final String courseId;
    @Nullable private final String studentId;
    private final LongSupplier wallClockMs;
    private final ZoneId zone;
    private final DayOfWeek firstDayOfWeek;
    private final MutableLiveData<State> state = new MutableLiveData<>();

    /**
     * @param studentId the student to show, when a teacher opens them; null for the signed-in
     *     student's own attendance
     * @param zone the time zone whose days and months the calendar shows
     * @param firstDayOfWeek the first column of the calendar, from the locale
     */
    public HistoryViewModel(
            @NonNull AuthRepository auth,
            @NonNull SessionRepository sessions,
            @NonNull AttendanceRepository attendance,
            @NonNull String courseId,
            @Nullable String studentId,
            @NonNull LongSupplier wallClockMs,
            @NonNull ZoneId zone,
            @NonNull DayOfWeek firstDayOfWeek) {
        this.auth = auth;
        this.sessions = sessions;
        this.attendance = attendance;
        this.courseId = courseId;
        this.studentId = studentId;
        this.wallClockMs = wallClockMs;
        this.zone = zone;
        this.firstDayOfWeek = firstDayOfWeek;
        load();
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    public void retry() {
        load();
    }

    public void previousMonth() {
        final State s = current();
        if (s.month != null && s.hasEarlierMonth) {
            show(s, s.month.minusMonths(1), null);
        }
    }

    public void nextMonth() {
        final State s = current();
        if (s.month != null && s.hasLaterMonth) {
            show(s, s.month.plusMonths(1), null);
        }
    }

    /** Shows only the classes of a day of the month on view; picking it again shows all. */
    public void selectDay(@NonNull LocalDate day) {
        final State s = current();
        if (s.month != null && YearMonth.from(day).equals(s.month)) {
            show(s, s.month, day.equals(s.day) ? null : day);
        }
    }

    public void clearDay() {
        final State s = current();
        if (s.month != null && s.day != null) {
            show(s, s.month, null);
        }
    }

    private void load() {
        state.setValue(empty(Status.LOADING, null));
        auth.loadUser(user -> {
            if (!user.isSuccess()) {
                fail(user.getError());
                return;
            }
            final String viewer = user.getValue().getUid();
            final Callback<List<Session>> onSessions =
                    held -> {
                        if (!held.isSuccess()) {
                            fail(held.getError());
                        } else if (held.getValue().isEmpty()) {
                            state.setValue(empty(Status.EMPTY, null));
                        } else {
                            loadRecords(viewer, held.getValue());
                        }
                    };
            if (studentId == null) {
                sessions.loadEnrolledSessions(courseId, onSessions);
            } else {
                sessions.loadCourseSessions(viewer, courseId, onSessions);
            }
        });
    }

    private void loadRecords(@NonNull String viewer, @NonNull List<Session> held) {
        final Callback<List<AttendanceRecord>> onRecords =
                records -> {
                    if (!records.isSuccess()) {
                        fail(records.getError());
                        return;
                    }
                    final Map<String, AttendanceRecord> bySession = new HashMap<>();
                    for (AttendanceRecord record : records.getValue()) {
                        bySession.put(record.getSessionId(), record);
                    }
                    final long now = wallClockMs.getAsLong();
                    final State loaded =
                            new State(
                                    Status.CONTENT,
                                    held,
                                    bySession,
                                    AttendanceStats.of(
                                            AttendanceStats.ended(held, now), records.getValue()),
                                    now,
                                    null,
                                    null,
                                    Collections.emptyList(),
                                    null,
                                    false,
                                    false,
                                    null);
                    // Sessions come newest first: open on the month of the latest class.
                    show(loaded, MonthGrid.monthOf(held.get(0), zone), null);
                };
        if (studentId == null) {
            attendance.loadStudentRecords(viewer, courseId, onRecords);
        } else {
            attendance.loadStudentRecordsForTeacher(viewer, studentId, courseId, onRecords);
        }
    }

    /** The loaded data with another month (and possibly a day) on view. */
    private void show(@NonNull State s, @NonNull YearMonth month, @Nullable LocalDate day) {
        // From the first month with a class to this month, or the last class if that is later.
        YearMonth earliest = month;
        YearMonth latest = YearMonth.from(Instant.ofEpochMilli(s.loadedAtMillis).atZone(zone));
        for (YearMonth withClass : MonthGrid.monthsWithClasses(s.sessions, zone)) {
            earliest = withClass.isBefore(earliest) ? withClass : earliest;
            latest = withClass.isAfter(latest) ? withClass : latest;
        }
        state.setValue(
                new State(
                        s.status,
                        s.sessions,
                        s.recordBySession,
                        s.stats,
                        s.loadedAtMillis,
                        s.error,
                        month,
                        MonthGrid.days(
                                month,
                                firstDayOfWeek,
                                zone,
                                s.sessions,
                                s.recordBySession,
                                s.loadedAtMillis),
                        MonthGrid.stats(
                                s.sessions,
                                new ArrayList<>(s.recordBySession.values()),
                                month,
                                zone,
                                s.loadedAtMillis),
                        month.isAfter(earliest),
                        month.isBefore(latest),
                        day));
    }

    private void fail(@Nullable AppError error) {
        state.setValue(empty(Status.ERROR, error != null ? error : AppError.UNKNOWN));
    }

    @NonNull
    private State empty(@NonNull Status status, @Nullable AppError error) {
        return new State(
                status,
                Collections.emptyList(),
                Collections.emptyMap(),
                null,
                wallClockMs.getAsLong(),
                error,
                null,
                Collections.emptyList(),
                null,
                false,
                false,
                null);
    }

    @NonNull
    private State current() {
        final State value = state.getValue();
        if (value == null) {
            throw new IllegalStateException("State is always set");
        }
        return value;
    }
}
