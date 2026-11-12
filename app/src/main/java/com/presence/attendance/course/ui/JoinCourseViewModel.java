package com.presence.attendance.course.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.domain.CourseRepository;
import com.presence.attendance.course.domain.JoinCodeInfo;
import com.presence.attendance.course.domain.JoinCodes;

/**
 * Join-course sheet state machine: ENTER_CODE -> CONFIRM -> JOINED. The student sees the course and
 * teacher a code resolves to before anything is written.
 */
public final class JoinCourseViewModel extends ViewModel {

    public enum Step {
        ENTER_CODE,
        CONFIRM,
        JOINED
    }

    /** Immutable sheet state. {@code course} is set from CONFIRM onwards. */
    public static final class State {
        @NonNull public final Step step;
        public final boolean online;
        public final boolean codeInvalid;
        public final boolean busy;
        @Nullable public final AppError error;
        @Nullable public final JoinCodeInfo course;

        State(
                @NonNull Step step,
                boolean online,
                boolean codeInvalid,
                boolean busy,
                @Nullable AppError error,
                @Nullable JoinCodeInfo course) {
            this.step = step;
            this.online = online;
            this.codeInvalid = codeInvalid;
            this.busy = busy;
            this.error = error;
            this.course = course;
        }
    }

    private final AuthRepository auth;
    private final CourseRepository courseRepository;
    private final MediatorLiveData<State> state =
            new MediatorLiveData<>(new State(Step.ENTER_CODE, false, false, false, null, null));

    public JoinCourseViewModel(
            @NonNull AuthRepository auth,
            @NonNull CourseRepository courseRepository,
            @NonNull LiveData<Boolean> online) {
        this.auth = auth;
        this.courseRepository = courseRepository;
        state.addSource(
                online,
                isOnline -> {
                    final State s = current();
                    state.setValue(
                            new State(
                                    s.step,
                                    Boolean.TRUE.equals(isOnline),
                                    s.codeInvalid,
                                    s.busy,
                                    s.error,
                                    s.course));
                });
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** Step 1: resolve the typed code to a course. */
    public void lookUp(@NonNull String typedCode) {
        final String code = JoinCodes.normalise(typedCode);
        if (current().busy) {
            return;
        }
        if (!JoinCodes.isValid(code)) {
            set(Step.ENTER_CODE, true, false, null, null);
            return;
        }
        if (!current().online) {
            set(Step.ENTER_CODE, false, false, AppError.OFFLINE, null);
            return;
        }
        set(Step.ENTER_CODE, false, true, null, null);
        auth.loadUser(user -> {
            if (user.isSuccess()) {
                courseRepository.findJoinCode(code, found -> onFound(user.getValue(), found));
            } else {
                set(Step.ENTER_CODE, false, false, user.getError(), null);
            }
        });
    }

    private void onFound(@NonNull User user, @NonNull Outcome<JoinCodeInfo> found) {
        if (!found.isSuccess()) {
            set(Step.ENTER_CODE, false, false, found.getError(), null);
        } else if (found.getValue().getTeacherId().equals(user.getUid())) {
            // The rules deny this too; checking here gives the reason.
            set(Step.ENTER_CODE, false, false, AppError.OWN_COURSE, null);
        } else {
            set(Step.CONFIRM, false, false, null, found.getValue());
        }
    }

    /** Step 2: join the course shown on the confirm step. */
    public void join(@NonNull String typedRollNo) {
        final State s = current();
        final JoinCodeInfo course = s.course;
        if (s.step != Step.CONFIRM || s.busy || course == null) {
            return;
        }
        if (!s.online) {
            set(Step.CONFIRM, false, false, AppError.OFFLINE, course);
            return;
        }
        final String trimmed = typedRollNo.trim();
        final String rollNo = trimmed.isEmpty() ? null : trimmed;
        set(Step.CONFIRM, false, true, null, course);
        auth.loadUser(user -> {
            if (user.isSuccess()) {
                courseRepository.joinCourse(
                        user.getValue(), course, rollNo, joined -> onJoined(course, joined));
            } else {
                set(Step.CONFIRM, false, false, user.getError(), course);
            }
        });
    }

    private void onJoined(@NonNull JoinCodeInfo course, @NonNull Outcome<JoinCodeInfo> joined) {
        if (joined.isSuccess()) {
            set(Step.JOINED, false, false, null, course);
        } else {
            set(Step.CONFIRM, false, false, joined.getError(), course);
        }
    }

    /** Back from the confirm step to type another code. */
    public void useDifferentCode() {
        if (!current().busy) {
            set(Step.ENTER_CODE, false, false, null, null);
        }
    }

    private void set(
            @NonNull Step step,
            boolean codeInvalid,
            boolean busy,
            @Nullable AppError error,
            @Nullable JoinCodeInfo course) {
        state.setValue(new State(step, current().online, codeInvalid, busy, error, course));
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
