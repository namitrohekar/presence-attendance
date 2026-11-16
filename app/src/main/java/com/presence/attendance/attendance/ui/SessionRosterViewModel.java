package com.presence.attendance.attendance.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.AttendanceRepository;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.domain.CourseRepository;
import com.presence.attendance.course.domain.Member;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Faculty: everyone enrolled in the course against one session's records. The records are a live
 * listener, so a correction shows as soon as the server has it.
 */
public final class SessionRosterViewModel extends ViewModel {

    public enum Status {
        LOADING,
        CONTENT,
        EMPTY,
        ERROR
    }

    /** Immutable screen state. A member with no record is absent. */
    public static final class State {
        @NonNull public final Status status;
        @NonNull public final List<Member> members;
        @NonNull public final Map<String, AttendanceRecord> recordByStudent;
        @Nullable public final AppError error;

        State(
                @NonNull Status status,
                @NonNull List<Member> members,
                @NonNull Map<String, AttendanceRecord> recordByStudent,
                @Nullable AppError error) {
            this.status = status;
            this.members = Collections.unmodifiableList(members);
            this.recordByStudent = Collections.unmodifiableMap(recordByStudent);
            this.error = error;
        }

        /** How many enrolled students count as present. */
        public int presentCount() {
            int present = 0;
            for (Member member : members) {
                final AttendanceRecord record = recordByStudent.get(member.getStudentId());
                if (record != null && record.getStatus().countsAsPresent()) {
                    present++;
                }
            }
            return present;
        }
    }

    private final AuthRepository auth;
    private final CourseRepository courses;
    private final AttendanceRepository attendance;
    private final String courseId;
    private final String sessionId;
    private final MediatorLiveData<State> state = new MediatorLiveData<>();
    @Nullable private LiveData<Outcome<List<AttendanceRecord>>> records;

    public SessionRosterViewModel(
            @NonNull AuthRepository auth,
            @NonNull CourseRepository courses,
            @NonNull AttendanceRepository attendance,
            @NonNull String courseId,
            @NonNull String sessionId) {
        this.auth = auth;
        this.courses = courses;
        this.attendance = attendance;
        this.courseId = courseId;
        this.sessionId = sessionId;
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
        state.setValue(
                new State(Status.LOADING, Collections.emptyList(), Collections.emptyMap(), null));
        auth.loadUser(user -> {
            if (user.isSuccess()) {
                loadMembers(user.getValue());
            } else {
                fail(user.getError());
            }
        });
    }

    private void loadMembers(@NonNull User teacher) {
        courses.loadMembers(
                teacher.getUid(),
                courseId,
                members -> {
                    if (!members.isSuccess()) {
                        fail(members.getError());
                    } else if (members.getValue().isEmpty()) {
                        state.setValue(
                                new State(
                                        Status.EMPTY,
                                        Collections.emptyList(),
                                        Collections.emptyMap(),
                                        null));
                    } else {
                        observeRecords(teacher.getUid(), members.getValue());
                    }
                });
    }

    private void observeRecords(@NonNull String teacherId, @NonNull List<Member> members) {
        if (records != null) {
            state.removeSource(records);
        }
        records = attendance.observeSession(sessionId, teacherId);
        state.addSource(
                records,
                outcome -> {
                    if (!outcome.isSuccess()) {
                        fail(outcome.getError());
                        return;
                    }
                    final Map<String, AttendanceRecord> byStudent = new HashMap<>();
                    for (AttendanceRecord record : outcome.getValue()) {
                        byStudent.put(record.getStudentId(), record);
                    }
                    state.setValue(new State(Status.CONTENT, members, byStudent, null));
                });
    }

    private void fail(@Nullable AppError error) {
        state.setValue(
                new State(
                        Status.ERROR,
                        Collections.emptyList(),
                        Collections.emptyMap(),
                        error != null ? error : AppError.UNKNOWN));
    }
}
