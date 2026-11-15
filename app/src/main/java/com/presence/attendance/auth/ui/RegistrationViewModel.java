package com.presence.attendance.auth.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.core.AppError;
import com.presence.attendance.roster.domain.StudentIdentity;

/**
 * Registration screen: the student types their roll number once. The address is the signed-in
 * account's and cannot be edited. The roster decides whether the two belong together.
 */
public final class RegistrationViewModel extends ViewModel {

    public enum Step {
        FORM,
        REGISTERED,
        SIGNED_OUT
    }

    /** Immutable screen state. */
    public static final class State {
        @NonNull public final Step step;
        @Nullable public final String email;
        public final boolean online;
        public final boolean rollNoInvalid;
        public final boolean busy;
        @Nullable public final AppError error;

        State(
                @NonNull Step step,
                @Nullable String email,
                boolean online,
                boolean rollNoInvalid,
                boolean busy,
                @Nullable AppError error) {
            this.step = step;
            this.email = email;
            this.online = online;
            this.rollNoInvalid = rollNoInvalid;
            this.busy = busy;
            this.error = error;
        }
    }

    private final AuthRepository auth;
    private final MediatorLiveData<State> state;

    public RegistrationViewModel(@NonNull AuthRepository auth, @NonNull LiveData<Boolean> online) {
        this.auth = auth;
        final Step start = auth.isSignedIn() ? Step.FORM : Step.SIGNED_OUT;
        state =
                new MediatorLiveData<>(
                        new State(start, auth.signedInEmail(), false, false, false, null));
        state.addSource(
                online,
                isOnline -> {
                    final State s = current();
                    state.setValue(
                            new State(
                                    s.step,
                                    s.email,
                                    Boolean.TRUE.equals(isOnline),
                                    s.rollNoInvalid,
                                    s.busy,
                                    s.error));
                });
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    public void submit(@NonNull String typedRollNo) {
        final String rollNo = typedRollNo.trim();
        final State s = current();
        if (s.busy || s.step != Step.FORM) {
            return;
        }
        if (!StudentIdentity.isRollNo(rollNo)) {
            set(Step.FORM, true, false, null);
            return;
        }
        // Registration never queues: it must reach the server now or not happen.
        if (!s.online) {
            set(Step.FORM, false, false, AppError.OFFLINE);
            return;
        }
        set(Step.FORM, false, true, null);
        auth.register(
                rollNo,
                outcome -> {
                    if (outcome.isSuccess()) {
                        set(Step.REGISTERED, false, false, null);
                    } else if (outcome.getError() == AppError.UNAUTHENTICATED) {
                        set(Step.SIGNED_OUT, false, false, null);
                    } else {
                        set(Step.FORM, false, false, outcome.getError());
                    }
                });
    }

    /** The student wants to use another Google account. */
    public void signOut() {
        if (!current().busy) {
            auth.signOut();
            set(Step.SIGNED_OUT, false, false, null);
        }
    }

    private void set(
            @NonNull Step step, boolean rollNoInvalid, boolean busy, @Nullable AppError error) {
        final State s = current();
        state.setValue(new State(step, s.email, s.online, rollNoInvalid, busy, error));
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
