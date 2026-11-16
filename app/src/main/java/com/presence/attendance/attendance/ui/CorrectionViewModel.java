package com.presence.attendance.attendance.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.attendance.domain.AttendanceRepository;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Constants;

/**
 * Faculty: marks one student present or absent for one session by hand. A note saying why is
 * required, and the rules require it too.
 */
public final class CorrectionViewModel extends ViewModel {

    /** Immutable sheet state. */
    public static final class State {
        public final boolean online;
        public final boolean saving;
        public final boolean noteMissing;
        public final boolean done;
        @Nullable public final AppError error;

        State(
                boolean online,
                boolean saving,
                boolean noteMissing,
                boolean done,
                @Nullable AppError error) {
            this.online = online;
            this.saving = saving;
            this.noteMissing = noteMissing;
            this.done = done;
            this.error = error;
        }

        public boolean canSave() {
            return online && !saving && !done;
        }
    }

    private final AuthRepository auth;
    private final AttendanceRepository attendance;
    private final String courseId;
    private final String sessionId;
    private final String studentId;
    private final boolean hasRecord;
    private final MediatorLiveData<State> state = new MediatorLiveData<>();

    public CorrectionViewModel(
            @NonNull AuthRepository auth,
            @NonNull AttendanceRepository attendance,
            @NonNull LiveData<Boolean> online,
            @NonNull String courseId,
            @NonNull String sessionId,
            @NonNull String studentId,
            boolean hasRecord) {
        this.auth = auth;
        this.attendance = attendance;
        this.courseId = courseId;
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.hasRecord = hasRecord;
        state.setValue(new State(true, false, false, false, null));
        state.addSource(
                online,
                isOnline -> {
                    final State s = current();
                    state.setValue(
                            new State(
                                    Boolean.TRUE.equals(isOnline),
                                    s.saving,
                                    s.noteMissing,
                                    s.done,
                                    s.error));
                });
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** Records the student as present or absent, with the reason. */
    public void save(boolean present, @NonNull String rawNote) {
        final State s = current();
        if (!s.canSave()) {
            return;
        }
        final String note = rawNote.trim();
        if (note.isEmpty() || note.length() > Constants.NOTE_MAX_LEN) {
            state.setValue(new State(s.online, false, true, false, null));
            return;
        }
        state.setValue(new State(s.online, true, false, false, null));
        auth.loadUser(user -> {
            if (!user.isSuccess()) {
                finish(user.getError());
                return;
            }
            attendance.correct(
                    user.getValue().getUid(),
                    courseId,
                    sessionId,
                    studentId,
                    hasRecord,
                    present,
                    note,
                    saved -> finish(saved.getError()));
        });
    }

    private void finish(@Nullable AppError error) {
        state.setValue(new State(current().online, false, false, error == null, error));
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
