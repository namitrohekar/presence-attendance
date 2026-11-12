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
import java.util.Collections;
import java.util.List;

/** Home screen state: the signed-in user and the courses they teach or have joined. */
public final class HomeViewModel extends ViewModel {

    public enum Status {
        LOADING,
        CONTENT,
        EMPTY,
        ERROR,
        SIGNED_OUT
    }

    /** Immutable screen state. {@code user} is known in every state reached after loading it. */
    public static final class State {
        @NonNull public final Status status;
        @Nullable public final User user;
        @NonNull public final List<Course> courses;
        @Nullable public final AppError error;

        State(
                @NonNull Status status,
                @Nullable User user,
                @NonNull List<Course> courses,
                @Nullable AppError error) {
            this.status = status;
            this.user = user;
            this.courses = Collections.unmodifiableList(courses);
            this.error = error;
        }
    }

    private final AuthRepository auth;
    private final CourseRepository courseRepository;
    private final MutableLiveData<State> state =
            new MutableLiveData<>(new State(Status.LOADING, null, Collections.emptyList(), null));

    public HomeViewModel(@NonNull AuthRepository auth, @NonNull CourseRepository courseRepository) {
        this.auth = auth;
        this.courseRepository = courseRepository;
        load();
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** Reloads the courses, after a failure or after the user created or joined one. */
    public void refresh() {
        load();
    }

    public void signOut() {
        auth.signOut();
        state.setValue(new State(Status.SIGNED_OUT, null, Collections.emptyList(), null));
    }

    private void load() {
        final State current = current();
        // Keep showing the list during a refresh; only an empty screen shows the spinner.
        if (current.status != Status.CONTENT) {
            state.setValue(new State(Status.LOADING, current.user, current.courses, null));
        }
        auth.loadUser(this::onUser);
    }

    private void onUser(@NonNull Outcome<User> outcome) {
        if (!outcome.isSuccess()) {
            final AppError error = outcome.getError();
            final boolean signedOut =
                    error == AppError.UNAUTHENTICATED || error == AppError.ACCOUNT_NOT_ALLOWED;
            state.setValue(
                    new State(
                            signedOut ? Status.SIGNED_OUT : Status.ERROR,
                            null,
                            Collections.emptyList(),
                            error));
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
            state.setValue(
                    new State(Status.ERROR, user, Collections.emptyList(), outcome.getError()));
            return;
        }
        final List<Course> courses = outcome.getValue();
        state.setValue(
                new State(courses.isEmpty() ? Status.EMPTY : Status.CONTENT, user, courses, null));
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
