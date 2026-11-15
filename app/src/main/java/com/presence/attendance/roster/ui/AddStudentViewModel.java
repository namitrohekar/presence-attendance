package com.presence.attendance.roster.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.roster.domain.RosterRepository;
import com.presence.attendance.roster.domain.RosterRepository.AddOutcome;
import com.presence.attendance.roster.domain.StudentIdentity;

/** Add-student sheet: strict validation, a connection, then one atomic write. */
public final class AddStudentViewModel extends ViewModel {

    /** Immutable sheet state. {@code conflict} is EMAIL_EXISTS or ROLL_TAKEN, never ADDED. */
    public static final class State {
        public final boolean online;
        public final boolean emailInvalid;
        public final boolean rollNoInvalid;
        public final boolean cohortInvalid;
        public final boolean busy;
        @Nullable public final AddOutcome conflict;
        @Nullable public final AppError error;
        public final boolean added;

        State(
                boolean online,
                boolean emailInvalid,
                boolean rollNoInvalid,
                boolean cohortInvalid,
                boolean busy,
                @Nullable AddOutcome conflict,
                @Nullable AppError error,
                boolean added) {
            this.online = online;
            this.emailInvalid = emailInvalid;
            this.rollNoInvalid = rollNoInvalid;
            this.cohortInvalid = cohortInvalid;
            this.busy = busy;
            this.conflict = conflict;
            this.error = error;
            this.added = added;
        }
    }

    private final AuthRepository auth;
    private final RosterRepository roster;
    private final MediatorLiveData<State> state =
            new MediatorLiveData<>(new State(false, false, false, false, false, null, null, false));

    public AddStudentViewModel(
            @NonNull AuthRepository auth,
            @NonNull RosterRepository roster,
            @NonNull LiveData<Boolean> online) {
        this.auth = auth;
        this.roster = roster;
        state.addSource(
                online,
                isOnline -> {
                    final State s = current();
                    state.setValue(
                            new State(
                                    Boolean.TRUE.equals(isOnline),
                                    s.emailInvalid,
                                    s.rollNoInvalid,
                                    s.cohortInvalid,
                                    s.busy,
                                    s.conflict,
                                    s.error,
                                    s.added));
                });
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    public void submit(
            @NonNull String typedEmail, @NonNull String typedRollNo, @NonNull String typedCohort) {
        final State s = current();
        if (s.busy || s.added) {
            return;
        }
        final String email = StudentIdentity.normaliseEmail(typedEmail);
        final String rollNo = typedRollNo.trim();
        final String cohort = StudentIdentity.normaliseCohort(typedCohort);
        final boolean emailInvalid = !StudentIdentity.isStudentEmail(email);
        final boolean rollNoInvalid = !StudentIdentity.isRollNo(rollNo);
        final boolean cohortInvalid = !StudentIdentity.isCohort(cohort);
        if (emailInvalid || rollNoInvalid || cohortInvalid) {
            state.setValue(
                    new State(
                            s.online,
                            emailInvalid,
                            rollNoInvalid,
                            cohortInvalid,
                            false,
                            null,
                            null,
                            false));
            return;
        }
        if (!s.online) {
            set(false, null, AppError.OFFLINE, false);
            return;
        }
        set(true, null, null, false);
        auth.loadUser(user -> {
            if (user.isSuccess()) {
                roster.addStudent(user.getValue(), email, rollNo, cohort, this::onOutcome);
            } else {
                set(false, null, user.getError(), false);
            }
        });
    }

    private void onOutcome(@NonNull Outcome<AddOutcome> outcome) {
        if (!outcome.isSuccess()) {
            set(false, null, outcome.getError(), false);
        } else if (outcome.getValue() == AddOutcome.ADDED) {
            set(false, null, null, true);
        } else {
            set(false, outcome.getValue(), null, false);
        }
    }

    private void set(
            boolean busy, @Nullable AddOutcome conflict, @Nullable AppError error, boolean added) {
        state.setValue(
                new State(current().online, false, false, false, busy, conflict, error, added));
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
