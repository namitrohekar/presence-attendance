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
import com.presence.attendance.course.domain.CourseRepository;
import com.presence.attendance.course.domain.Member;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.session.domain.SessionRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Faculty: every enrolled student's attendance in one course, and who is below the course's
 * required percentage.
 */
public final class CourseStudentsViewModel extends ViewModel {

    public enum Status {
        LOADING,
        CONTENT,
        EMPTY,
        ERROR
    }

    /** One student's line. */
    public static final class Row {
        @NonNull public final Member member;
        @NonNull public final AttendanceStats stats;
        public final boolean atRisk;

        Row(@NonNull Member member, @NonNull AttendanceStats stats, boolean atRisk) {
            this.member = member;
            this.stats = stats;
            this.atRisk = atRisk;
        }
    }

    /** Immutable screen state. Rows are in roll-number order. */
    public static final class State {
        @NonNull public final Status status;
        @NonNull public final List<Row> rows;
        public final int atRiskCount;
        @Nullable public final AppError error;

        /** The month the figures cover, or null for the whole course. */
        @Nullable public final YearMonth month;

        State(@NonNull Status status, @NonNull List<Row> rows, @Nullable AppError error) {
            this(status, rows, error, null);
        }

        State(
                @NonNull Status status,
                @NonNull List<Row> rows,
                @Nullable AppError error,
                @Nullable YearMonth month) {
            this.month = month;
            this.status = status;
            this.rows = Collections.unmodifiableList(rows);
            int atRisk = 0;
            for (Row row : rows) {
                if (row.atRisk) {
                    atRisk++;
                }
            }
            this.atRiskCount = atRisk;
            this.error = error;
        }
    }

    private final AuthRepository auth;
    private final CourseRepository courses;
    private final SessionRepository sessions;
    private final AttendanceRepository attendance;
    private final String courseId;
    private final int threshold;
    private final LongSupplier wallClockMs;
    private final ZoneId zone;
    private final MutableLiveData<State> state = new MutableLiveData<>();
    // What was loaded, so switching between overall and this month needs no new reads.
    private List<Member> members = Collections.emptyList();
    private List<Session> held = Collections.emptyList();
    private List<AttendanceRecord> records = Collections.emptyList();
    private boolean thisMonth;

    public CourseStudentsViewModel(
            @NonNull AuthRepository auth,
            @NonNull CourseRepository courses,
            @NonNull SessionRepository sessions,
            @NonNull AttendanceRepository attendance,
            @NonNull String courseId,
            int threshold,
            @NonNull LongSupplier wallClockMs,
            @NonNull ZoneId zone) {
        this.auth = auth;
        this.courses = courses;
        this.sessions = sessions;
        this.attendance = attendance;
        this.courseId = courseId;
        this.threshold = threshold;
        this.wallClockMs = wallClockMs;
        this.zone = zone;
        load();
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    public void retry() {
        load();
    }

    private void load() {
        state.setValue(new State(Status.LOADING, Collections.emptyList(), null));
        auth.loadUser(user -> {
            if (!user.isSuccess()) {
                fail(user.getError());
                return;
            }
            final String teacherId = user.getValue().getUid();
            courses.loadMembers(
                    teacherId,
                    courseId,
                    members -> {
                        if (!members.isSuccess()) {
                            fail(members.getError());
                        } else if (members.getValue().isEmpty()) {
                            state.setValue(new State(Status.EMPTY, Collections.emptyList(), null));
                        } else {
                            loadSessions(teacherId, members.getValue());
                        }
                    });
        });
    }

    private void loadSessions(@NonNull String teacherId, @NonNull List<Member> members) {
        sessions.loadCourseSessions(
                teacherId,
                courseId,
                held -> {
                    if (!held.isSuccess()) {
                        fail(held.getError());
                        return;
                    }
                    attendance.loadCourseRecords(
                            teacherId,
                            courseId,
                            records -> {
                                if (records.isSuccess()) {
                                    show(members, held.getValue(), records.getValue());
                                } else {
                                    fail(records.getError());
                                }
                            });
                });
    }

    /** Switches the figures between the whole course and the current month. */
    public void toggleMonth() {
        if (current().status == Status.CONTENT) {
            thisMonth = !thisMonth;
            show(members, held, records);
        }
    }

    private void show(
            @NonNull List<Member> members,
            @NonNull List<Session> held,
            @NonNull List<AttendanceRecord> records) {
        this.members = members;
        this.held = held;
        this.records = records;
        final long now = wallClockMs.getAsLong();
        final YearMonth month =
                thisMonth ? YearMonth.from(Instant.ofEpochMilli(now).atZone(zone)) : null;
        final Set<String> ended =
                AttendanceStats.ended(
                        month != null ? MonthGrid.inMonth(held, month, zone) : held, now);
        final Map<String, List<AttendanceRecord>> byStudent = new HashMap<>();
        for (AttendanceRecord record : records) {
            byStudent.computeIfAbsent(record.getStudentId(), id -> new ArrayList<>()).add(record);
        }
        final List<Row> rows = new ArrayList<>(members.size());
        for (Member member : members) {
            final AttendanceStats stats =
                    AttendanceStats.of(
                            ended,
                            byStudent.getOrDefault(member.getStudentId(), Collections.emptyList()));
            rows.add(new Row(member, stats, stats.isBelow(threshold)));
        }
        state.setValue(new State(Status.CONTENT, rows, null, month));
    }

    private void fail(@Nullable AppError error) {
        state.setValue(
                new State(
                        Status.ERROR,
                        Collections.emptyList(),
                        error != null ? error : AppError.UNKNOWN));
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
