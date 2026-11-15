package com.presence.attendance.session.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.core.AppError;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.session.domain.SessionRepository;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;

/** A course as its teacher sees it: its sessions, and whether one is live right now. */
public final class CourseViewModel extends ViewModel {

    public enum Status {
        LOADING,
        CONTENT,
        ERROR
    }

    /** Immutable screen state. {@code live} is the running session, if there is one. */
    public static final class State {
        @NonNull public final Status status;
        @NonNull public final List<Session> sessions;
        @Nullable public final Session live;
        @Nullable public final AppError error;

        State(
                @NonNull Status status,
                @NonNull List<Session> sessions,
                @Nullable Session live,
                @Nullable AppError error) {
            this.status = status;
            this.sessions = Collections.unmodifiableList(sessions);
            this.live = live;
            this.error = error;
        }
    }

    private final AuthRepository auth;
    private final SessionRepository sessionRepository;
    private final String courseId;
    private final LongSupplier wallClockMs;
    private final MutableLiveData<State> state =
            new MutableLiveData<>(new State(Status.LOADING, Collections.emptyList(), null, null));

    public CourseViewModel(
            @NonNull AuthRepository auth,
            @NonNull SessionRepository sessionRepository,
            @NonNull String courseId,
            @NonNull LongSupplier wallClockMs) {
        this.auth = auth;
        this.sessionRepository = sessionRepository;
        this.courseId = courseId;
        this.wallClockMs = wallClockMs;
        refresh();
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** Reloads the sessions: after a failure, and whenever the screen comes back into view. */
    public void refresh() {
        auth.loadUser(user -> {
            if (!user.isSuccess()) {
                fail(user.getError());
                return;
            }
            sessionRepository.loadCourseSessions(
                    user.getValue().getUid(),
                    courseId,
                    outcome -> {
                        if (!outcome.isSuccess()) {
                            fail(outcome.getError());
                            return;
                        }
                        final List<Session> sessions = outcome.getValue();
                        state.setValue(
                                new State(Status.CONTENT, sessions, firstLive(sessions), null));
                    });
        });
    }

    @Nullable
    private Session firstLive(@NonNull List<Session> sessions) {
        final long now = wallClockMs.getAsLong();
        for (Session session : sessions) {
            if (session.isLive(now)) {
                return session;
            }
        }
        return null;
    }

    private void fail(@Nullable AppError error) {
        state.setValue(new State(Status.ERROR, Collections.emptyList(), null, error));
    }
}
