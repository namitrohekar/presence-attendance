package com.presence.attendance.course.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.auth.domain.Role;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.domain.Course;
import com.presence.attendance.course.domain.CourseRepository;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.session.domain.SessionRepository;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** Home screen state: the signed-in user and the courses they teach or have joined. */
public final class HomeViewModel extends ViewModel {

    public enum Status {
        LOADING,
        CONTENT,
        EMPTY,
        ERROR,
        SIGNED_OUT,
        /** A signed-in student address that has not linked a roll number yet. */
        REGISTRATION_REQUIRED
    }

    /**
     * Immutable screen state. {@code user} is known in every state reached after loading it. {@code
     * liveSessionByCourse} maps a course id to its running session, for faculty.
     */
    public static final class State {
        @NonNull public final Status status;
        @Nullable public final User user;
        @NonNull public final List<Course> courses;
        @NonNull public final Map<String, String> liveSessionByCourse;
        @Nullable public final AppError error;

        State(
                @NonNull Status status,
                @Nullable User user,
                @NonNull List<Course> courses,
                @NonNull Map<String, String> liveSessionByCourse,
                @Nullable AppError error) {
            this.status = status;
            this.user = user;
            this.courses = Collections.unmodifiableList(courses);
            this.liveSessionByCourse = Collections.unmodifiableMap(liveSessionByCourse);
            this.error = error;
        }
    }

    private final AuthRepository auth;
    private final CourseRepository courseRepository;
    private final SessionRepository sessionRepository;
    private final LongSupplier wallClockMs;
    private final MutableLiveData<State> state =
            new MutableLiveData<>(
                    new State(
                            Status.LOADING, null, Collections.emptyList(), new HashMap<>(), null));

    public HomeViewModel(
            @NonNull AuthRepository auth,
            @NonNull CourseRepository courseRepository,
            @NonNull SessionRepository sessionRepository,
            @NonNull LongSupplier wallClockMs) {
        this.auth = auth;
        this.courseRepository = courseRepository;
        this.sessionRepository = sessionRepository;
        this.wallClockMs = wallClockMs;
        load();
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** Reloads: after a failure, after a change, and whenever the screen comes back into view. */
    public void refresh() {
        load();
    }

    public void signOut() {
        auth.signOut();
        set(Status.SIGNED_OUT, null, Collections.emptyList(), null);
    }

    private void load() {
        final State current = current();
        // Keep showing the list during a refresh; only an empty screen shows the spinner.
        if (current.status != Status.CONTENT) {
            set(Status.LOADING, current.user, current.courses, null);
        }
        auth.loadUser(this::onUser);
    }

    private void onUser(@NonNull Outcome<User> outcome) {
        if (!outcome.isSuccess()) {
            final AppError error = outcome.getError();
            final Status status;
            if (error == AppError.REGISTRATION_REQUIRED) {
                status = Status.REGISTRATION_REQUIRED;
            } else if (error == AppError.UNAUTHENTICATED || error == AppError.NOT_VJTI_ACCOUNT) {
                status = Status.SIGNED_OUT;
            } else {
                status = Status.ERROR;
            }
            set(status, null, Collections.emptyList(), error);
            return;
        }
        final User user = outcome.getValue();
        final Callback<List<Course>> onCourses = courses -> onCourses(user, courses);
        if (user.getRole() == Role.FACULTY) {
            courseRepository.loadTeacherCourses(user.getUid(), onCourses);
        } else {
            courseRepository.loadStudentCourses(user.getUid(), onCourses);
        }
    }

    private void onCourses(@NonNull User user, @NonNull Outcome<List<Course>> outcome) {
        if (!outcome.isSuccess()) {
            set(Status.ERROR, user, Collections.emptyList(), outcome.getError());
            return;
        }
        final List<Course> courses = outcome.getValue();
        set(courses.isEmpty() ? Status.EMPTY : Status.CONTENT, user, courses, null);
        if (user.getRole() == Role.FACULTY && !courses.isEmpty()) {
            sessionRepository.loadActiveSessions(user.getUid(), this::onActiveSessions);
        }
    }

    /** Marks the courses that have a session running. A failure just leaves them unmarked. */
    private void onActiveSessions(@NonNull Outcome<List<Session>> outcome) {
        final State s = current();
        if (!outcome.isSuccess() || s.status != Status.CONTENT) {
            return;
        }
        final long now = wallClockMs.getAsLong();
        final Map<String, String> live = new HashMap<>();
        for (Session session : outcome.getValue()) {
            if (session.isLive(now)) {
                live.put(session.getCourseId(), session.getId());
            }
        }
        state.setValue(new State(s.status, s.user, s.courses, live, s.error));
    }

    private void set(
            @NonNull Status status,
            @Nullable User user,
            @NonNull List<Course> courses,
            @Nullable AppError error) {
        final Map<String, String> live =
                status == Status.CONTENT ? current().liveSessionByCourse : new HashMap<>();
        state.setValue(new State(status, user, courses, live, error));
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
