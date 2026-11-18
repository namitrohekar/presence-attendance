package com.presence.attendance.attendance.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.AttendanceRepository;
import com.presence.attendance.attendance.domain.SessionReview;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Constants;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.domain.CourseRepository;
import com.presence.attendance.course.domain.Member;
import com.presence.attendance.session.domain.SessionRepository;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Faculty: the summary of one session. Everyone enrolled against the session's records, the
 * headcount the teacher entered, and the marks worth a second look. The records are a live
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

        /** Enrolled students whose mark is worth a look, with why. */
        @NonNull public final Map<String, SessionReview.Flag> flags;

        @Nullable public final Integer headcount;
        public final boolean headcountSaving;
        @Nullable public final AppError headcountError;

        /** Whether the full list of students is shown. */
        public final boolean expanded;

        @Nullable public final AppError error;

        State(
                @NonNull Status status,
                @NonNull List<Member> members,
                @NonNull Map<String, AttendanceRecord> recordByStudent,
                @NonNull Map<String, SessionReview.Flag> flags,
                @Nullable Integer headcount,
                boolean headcountSaving,
                @Nullable AppError headcountError,
                boolean expanded,
                @Nullable AppError error) {
            this.status = status;
            this.members = Collections.unmodifiableList(members);
            this.recordByStudent = Collections.unmodifiableMap(recordByStudent);
            this.flags = Collections.unmodifiableMap(flags);
            this.headcount = headcount;
            this.headcountSaving = headcountSaving;
            this.headcountError = headcountError;
            this.expanded = expanded;
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

        /**
         * Marks minus people counted: above zero, more marks than people (proxies are likely);
         * below zero, people in the room who have not marked. Null until a headcount is entered.
         */
        @Nullable
        public Integer difference() {
            return headcount == null ? null : presentCount() - headcount;
        }
    }

    private final AuthRepository auth;
    private final CourseRepository courses;
    private final SessionRepository sessions;
    private final AttendanceRepository attendance;
    private final String courseId;
    private final String sessionId;
    private final MediatorLiveData<State> state = new MediatorLiveData<>();
    @Nullable private LiveData<Outcome<List<AttendanceRecord>>> records;

    public SessionRosterViewModel(
            @NonNull AuthRepository auth,
            @NonNull CourseRepository courses,
            @NonNull SessionRepository sessions,
            @NonNull AttendanceRepository attendance,
            @NonNull String courseId,
            @NonNull String sessionId) {
        this.auth = auth;
        this.courses = courses;
        this.sessions = sessions;
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

    public void toggleExpanded() {
        final State s = current();
        set(s, s.headcount, s.headcountSaving, s.headcountError, !s.expanded);
    }

    /**
     * A headcount as typed, or null if it is not a whole number from 0 to {@link
     * Constants#HEADCOUNT_MAX}.
     */
    @Nullable
    public static Integer parseHeadcount(@NonNull String raw) {
        final String digits = raw.trim();
        if (digits.isEmpty()
                || digits.length() > 4
                || !digits.chars().allMatch(Character::isDigit)) {
            return null;
        }
        final int value = Integer.parseInt(digits);
        return value <= Constants.HEADCOUNT_MAX ? value : null;
    }

    /** Stores the headcount. The summary compares it with the marks once the server has it. */
    public void saveHeadcount(int headcount) {
        final State s = current();
        if (s.headcountSaving) {
            return;
        }
        set(s, s.headcount, true, null, s.expanded);
        sessions.setHeadcount(
                sessionId,
                headcount,
                saved -> {
                    final State now = current();
                    if (saved.isSuccess()) {
                        set(now, saved.getValue(), false, null, now.expanded);
                    } else {
                        set(now, now.headcount, false, saved.getError(), now.expanded);
                    }
                });
    }

    private void load() {
        state.setValue(
                new State(
                        Status.LOADING,
                        Collections.emptyList(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        null,
                        false,
                        null,
                        false,
                        null));
        auth.loadUser(user -> {
            if (user.isSuccess()) {
                loadSession(user.getValue());
            } else {
                fail(user.getError());
            }
        });
    }

    private void loadSession(@NonNull User teacher) {
        sessions.loadSession(
                sessionId,
                session -> {
                    if (session.isSuccess()) {
                        loadMembers(teacher, session.getValue().getHeadcount());
                    } else {
                        fail(session.getError());
                    }
                });
    }

    private void loadMembers(@NonNull User teacher, @Nullable Integer headcount) {
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
                                        Collections.emptyMap(),
                                        headcount,
                                        false,
                                        null,
                                        false,
                                        null));
                    } else {
                        observeRecords(teacher.getUid(), members.getValue(), headcount);
                    }
                });
    }

    private void observeRecords(
            @NonNull String teacherId, @NonNull List<Member> members, @Nullable Integer headcount) {
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
                    // Only enrolled students: someone who has left the course is not reviewed.
                    final Map<String, AttendanceRecord> byStudent = new HashMap<>();
                    for (AttendanceRecord record : outcome.getValue()) {
                        byStudent.put(record.getStudentId(), record);
                    }
                    final Map<String, AttendanceRecord> enrolled = new HashMap<>();
                    for (Member member : members) {
                        final AttendanceRecord record = byStudent.get(member.getStudentId());
                        if (record != null) {
                            enrolled.put(member.getStudentId(), record);
                        }
                    }
                    final State s = current();
                    final boolean first = s.status != Status.CONTENT;
                    state.setValue(
                            new State(
                                    Status.CONTENT,
                                    members,
                                    enrolled,
                                    SessionReview.flags(enrolled.values()),
                                    first ? headcount : s.headcount,
                                    !first && s.headcountSaving,
                                    first ? null : s.headcountError,
                                    !first && s.expanded,
                                    null));
                });
    }

    private void set(
            @NonNull State s,
            @Nullable Integer headcount,
            boolean saving,
            @Nullable AppError headcountError,
            boolean expanded) {
        state.setValue(
                new State(
                        s.status,
                        s.members,
                        s.recordByStudent,
                        s.flags,
                        headcount,
                        saving,
                        headcountError,
                        expanded,
                        s.error));
    }

    private void fail(@Nullable AppError error) {
        state.setValue(
                new State(
                        Status.ERROR,
                        Collections.emptyList(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        null,
                        false,
                        null,
                        false,
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
