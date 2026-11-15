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
 * teacher a code resolves to, and the roll number they will join with, before anything is written.
 * The roll number is the one from their registration; it is not typed here.
 */
public final class JoinCourseViewModel extends ViewModel {

    public enum Step {
        ENTER_CODE,
        CONFIRM,
        JOINED
    }

    /** Immutable sheet state. {@code course} and {@code rollNo} are set from CONFIRM onwards. */
    public static final class State {
        @NonNull public final Step step;
        public final boolean online;
        public final boolean codeInvalid;
        public final boolean busy;
        @Nullable public final AppError error;
        @Nullable public final JoinCodeInfo course;
        @Nullable public final String rollNo;

        State(
                @NonNull Step step,
                boolean online,
                boolean codeInvalid,
                boolean busy,
                @Nullable AppError error,
                @Nullable JoinCodeInfo course,
                @Nullable String rollNo) {
            this.step = step;
            this.online = online;
            this.codeInvalid = codeInvalid;
            this.busy = busy;
            this.error = error;
            this.course = course;
            this.rollNo = rollNo;
        }
    }

    private final AuthRepository auth;
    private final CourseRepository courseRepository;
    private final MediatorLiveData<State> state =
            new MediatorLiveData<>(
                    new State(Step.ENTER_CODE, false, false, false, null, null, null));

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
                                    s.course,
                                    s.rollNo));
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
            set(Step.ENTER_CODE, true, false, null, null, null);
            return;
        }
        if (!current().online) {
            set(Step.ENTER_CODE, false, false, AppError.OFFLINE, null, null);
            return;
        }
        set(Step.ENTER_CODE, false, true, null, null, null);
        auth.loadUser(user -> {
            if (user.isSuccess()) {
                courseRepository.findJoinCode(code, found -> onFound(user.getValue(), found));
            } else {
                set(Step.ENTER_CODE, false, false, user.getError(), null, null);
            }
        });
    }

    private void onFound(@NonNull User user, @NonNull Outcome<JoinCodeInfo> found) {
        if (!found.isSuccess()) {
            set(Step.ENTER_CODE, false, false, found.getError(), null, null);
        } else if (found.getValue().getTeacherId().equals(user.getUid())) {
            // The rules deny this too; checking here gives the reason.
            set(Step.ENTER_CODE, false, false, AppError.OWN_COURSE, null, null);
        } else if (user.getRollNo() == null) {
            // Only a registered student can enrol. Faculty never reach this sheet.
            set(Step.ENTER_CODE, false, false, AppError.REGISTRATION_REQUIRED, null, null);
        } else {
            set(Step.CONFIRM, false, false, null, found.getValue(), user.getRollNo());
        }
    }

    /** Step 2: join the course shown on the confirm step. */
    public void join() {
        final State s = current();
        final JoinCodeInfo course = s.course;
        if (s.step != Step.CONFIRM || s.busy || course == null) {
            return;
        }
        if (!s.online) {
            set(Step.CONFIRM, false, false, AppError.OFFLINE, course, s.rollNo);
            return;
        }
        set(Step.CONFIRM, false, true, null, course, s.rollNo);
        auth.loadUser(user -> {
            if (user.isSuccess()) {
                courseRepository.joinCourse(
                        user.getValue(), course, joined -> onJoined(course, joined));
            } else {
                set(Step.CONFIRM, false, false, user.getError(), course, s.rollNo);
            }
        });
    }

    private void onJoined(@NonNull JoinCodeInfo course, @NonNull Outcome<JoinCodeInfo> joined) {
        final String rollNo = current().rollNo;
        if (joined.isSuccess()) {
            set(Step.JOINED, false, false, null, course, rollNo);
        } else {
            set(Step.CONFIRM, false, false, joined.getError(), course, rollNo);
        }
    }

    /** Back from the confirm step to type another code. */
    public void useDifferentCode() {
        if (!current().busy) {
            set(Step.ENTER_CODE, false, false, null, null, null);
        }
    }

    private void set(
            @NonNull Step step,
            boolean codeInvalid,
            boolean busy,
            @Nullable AppError error,
            @Nullable JoinCodeInfo course,
            @Nullable String rollNo) {
        state.setValue(new State(step, current().online, codeInvalid, busy, error, course, rollNo));
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
