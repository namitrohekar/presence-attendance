package com.presence.attendance.attendance.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.AttendanceRepository;
import com.presence.attendance.attendance.domain.AttendanceStats;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.core.AppError;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.session.domain.SessionRepository;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** Student: their own attendance in one course, session by session, with the overall figure. */
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
        @NonNull public final List<Session> sessions;
        @NonNull public final Map<String, AttendanceRecord> recordBySession;
        @Nullable public final AttendanceStats stats;
        public final long loadedAtMillis;
        @Nullable public final AppError error;

        State(
                @NonNull Status status,
                @NonNull List<Session> sessions,
                @NonNull Map<String, AttendanceRecord> recordBySession,
                @Nullable AttendanceStats stats,
                long loadedAtMillis,
                @Nullable AppError error) {
            this.status = status;
            this.sessions = Collections.unmodifiableList(sessions);
            this.recordBySession = Collections.unmodifiableMap(recordBySession);
            this.stats = stats;
            this.loadedAtMillis = loadedAtMillis;
            this.error = error;
        }
    }

    private final AuthRepository auth;
    private final SessionRepository sessions;
    private final AttendanceRepository attendance;
    private final String courseId;
    private final LongSupplier wallClockMs;
    private final MutableLiveData<State> state = new MutableLiveData<>();

    public HistoryViewModel(
            @NonNull AuthRepository auth,
            @NonNull SessionRepository sessions,
            @NonNull AttendanceRepository attendance,
            @NonNull String courseId,
            @NonNull LongSupplier wallClockMs) {
        this.auth = auth;
        this.sessions = sessions;
        this.attendance = attendance;
        this.courseId = courseId;
        this.wallClockMs = wallClockMs;
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
        set(Status.LOADING, Collections.emptyList(), Collections.emptyMap(), null, null);
        auth.loadUser(user -> {
            if (!user.isSuccess()) {
                fail(user.getError());
                return;
            }
            final String studentId = user.getValue().getUid();
            sessions.loadEnrolledSessions(
                    courseId,
                    held -> {
                        if (!held.isSuccess()) {
                            fail(held.getError());
                        } else if (held.getValue().isEmpty()) {
                            set(
                                    Status.EMPTY,
                                    Collections.emptyList(),
                                    Collections.emptyMap(),
                                    null,
                                    null);
                        } else {
                            loadRecords(studentId, held.getValue());
                        }
                    });
        });
    }

    private void loadRecords(@NonNull String studentId, @NonNull List<Session> held) {
        attendance.loadStudentRecords(
                studentId,
                courseId,
                records -> {
                    if (!records.isSuccess()) {
                        fail(records.getError());
                        return;
                    }
                    final Map<String, AttendanceRecord> bySession = new HashMap<>();
                    for (AttendanceRecord record : records.getValue()) {
                        bySession.put(record.getSessionId(), record);
                    }
                    final AttendanceStats stats =
                            AttendanceStats.of(
                                    AttendanceStats.ended(held, wallClockMs.getAsLong()),
                                    records.getValue());
                    set(Status.CONTENT, held, bySession, stats, null);
                });
    }

    private void fail(@Nullable AppError error) {
        set(
                Status.ERROR,
                Collections.emptyList(),
                Collections.emptyMap(),
                null,
                error != null ? error : AppError.UNKNOWN);
    }

    private void set(
            @NonNull Status status,
            @NonNull List<Session> held,
            @NonNull Map<String, AttendanceRecord> bySession,
            @Nullable AttendanceStats stats,
            @Nullable AppError error) {
        state.setValue(new State(status, held, bySession, stats, wallClockMs.getAsLong(), error));
    }
}
