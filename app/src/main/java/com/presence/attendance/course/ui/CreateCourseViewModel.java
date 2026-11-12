package com.presence.attendance.course.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.domain.Course;
import com.presence.attendance.course.domain.CourseRepository;

/** Create-course sheet: validates the form, requires a connection, then creates the course. */
public final class CreateCourseViewModel extends ViewModel {

    /** Immutable sheet state. {@code created} is set once and ends the form. */
    public static final class State {
        public final boolean online;
        public final boolean nameMissing;
        public final boolean codeMissing;
        public final boolean submitting;
        @Nullable public final AppError error;
        @Nullable public final Course created;

        State(
                boolean online,
                boolean nameMissing,
                boolean codeMissing,
                boolean submitting,
                @Nullable AppError error,
                @Nullable Course created) {
            this.online = online;
            this.nameMissing = nameMissing;
            this.codeMissing = codeMissing;
            this.submitting = submitting;
            this.error = error;
            this.created = created;
        }
    }

    private final AuthRepository auth;
    private final CourseRepository courseRepository;
    private final MediatorLiveData<State> state =
            new MediatorLiveData<>(new State(false, false, false, false, null, null));

    public CreateCourseViewModel(
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
                                    Boolean.TRUE.equals(isOnline),
                                    s.nameMissing,
                                    s.codeMissing,
                                    s.submitting,
                                    s.error,
                                    s.created));
                });
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    public void submit(@NonNull String rawName, @NonNull String rawCode) {
        final String name = rawName.trim();
        final String code = rawCode.trim();
        final State s = current();
        if (s.submitting || s.created != null) {
            return;
        }
        if (name.isEmpty() || code.isEmpty()) {
            set(name.isEmpty(), code.isEmpty(), false, null, null);
            return;
        }
        // Creating a course never queues: it must reach the server now or not happen.
        if (!s.online) {
            set(false, false, false, AppError.OFFLINE, null);
            return;
        }
        set(false, false, true, null, null);
        auth.loadUser(user -> {
            if (user.isSuccess()) {
                courseRepository.createCourse(user.getValue(), name, code, this::onCreated);
            } else {
                set(false, false, false, user.getError(), null);
            }
        });
    }

    private void onCreated(@NonNull Outcome<Course> outcome) {
        if (outcome.isSuccess()) {
            set(false, false, false, null, outcome.getValue());
        } else {
            set(false, false, false, outcome.getError(), null);
        }
    }

    private void set(
            boolean nameMissing,
            boolean codeMissing,
            boolean submitting,
            @Nullable AppError error,
            @Nullable Course created) {
        state.setValue(
                new State(current().online, nameMissing, codeMissing, submitting, error, created));
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
