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
import com.presence.attendance.roster.domain.RosterRepository.CorrectOutcome;
import com.presence.attendance.roster.domain.RosterRepository.CorrectionAction;
import com.presence.attendance.roster.domain.StudentIdentity;

/**
 * Correction sheet for one roster entry. Each action is one audited, atomic change and needs a
 * reason, which is kept in the audit log.
 */
public final class RosterEntryViewModel extends ViewModel {

    /** What is wrong with the form, if anything. */
    public enum Problem {
        ROLL_NO_INVALID,
        COHORT_INVALID,
        REASON_MISSING,
        UNCHANGED,
        ROLL_TAKEN,
        /** Someone else removed the entry while this sheet was open. */
        GONE
    }

    /** Immutable sheet state. */
    public static final class State {
        public final boolean online;
        public final boolean busy;
        @Nullable public final Problem problem;
        @Nullable public final AppError error;
        public final boolean done;

        State(
                boolean online,
                boolean busy,
                @Nullable Problem problem,
                @Nullable AppError error,
                boolean done) {
            this.online = online;
            this.busy = busy;
            this.problem = problem;
            this.error = error;
            this.done = done;
        }
    }

    private final AuthRepository auth;
    private final RosterRepository roster;
    private final String email;
    private final String rollNo;
    private final String cohort;
    private final MediatorLiveData<State> state =
            new MediatorLiveData<>(new State(false, false, null, null, false));

    public RosterEntryViewModel(
            @NonNull AuthRepository auth,
            @NonNull RosterRepository roster,
            @NonNull LiveData<Boolean> online,
            @NonNull String email,
            @NonNull String rollNo,
            @NonNull String cohort) {
        this.auth = auth;
        this.roster = roster;
        this.email = email;
        this.rollNo = rollNo;
        this.cohort = cohort;
        state.addSource(
                online,
                isOnline -> {
                    final State s = current();
                    state.setValue(
                            new State(
                                    Boolean.TRUE.equals(isOnline),
                                    s.busy,
                                    s.problem,
                                    s.error,
                                    s.done));
                });
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** Changes the roll number and/or cohort. */
    public void save(
            @NonNull String typedRollNo, @NonNull String typedCohort, @NonNull String reason) {
        final String newRollNo = typedRollNo.trim();
        final String newCohort = StudentIdentity.normaliseCohort(typedCohort);
        if (!StudentIdentity.isRollNo(newRollNo)) {
            set(false, Problem.ROLL_NO_INVALID, null, false);
        } else if (!StudentIdentity.isCohort(newCohort)) {
            set(false, Problem.COHORT_INVALID, null, false);
        } else if (newRollNo.equals(rollNo) && newCohort.equals(cohort)) {
            set(false, Problem.UNCHANGED, null, false);
        } else {
            run(CorrectionAction.UPDATE, reason, newRollNo, newCohort);
        }
    }

    /** Frees the registration so the student can register again. */
    public void release(@NonNull String reason) {
        run(CorrectionAction.RELEASE, reason, null, null);
    }

    public void remove(@NonNull String reason) {
        run(CorrectionAction.REMOVE, reason, null, null);
    }

    private void run(
            @NonNull CorrectionAction action,
            @NonNull String typedReason,
            @Nullable String newRollNo,
            @Nullable String newCohort) {
        final State s = current();
        final String reason = typedReason.trim();
        if (s.busy || s.done) {
            return;
        }
        if (reason.isEmpty()) {
            set(false, Problem.REASON_MISSING, null, false);
            return;
        }
        if (!s.online) {
            set(false, null, AppError.OFFLINE, false);
            return;
        }
        set(true, null, null, false);
        auth.loadUser(user -> {
            if (user.isSuccess()) {
                roster.correct(
                        user.getValue(),
                        email,
                        action,
                        reason,
                        newRollNo,
                        newCohort,
                        this::onOutcome);
            } else {
                set(false, null, user.getError(), false);
            }
        });
    }

    private void onOutcome(@NonNull Outcome<CorrectOutcome> outcome) {
        if (!outcome.isSuccess()) {
            set(false, null, outcome.getError(), false);
            return;
        }
        switch (outcome.getValue()) {
            case DONE -> set(false, null, null, true);
            case ROLL_TAKEN -> set(false, Problem.ROLL_TAKEN, null, false);
            case NOT_FOUND -> set(false, Problem.GONE, null, false);
        }
    }

    private void set(
            boolean busy, @Nullable Problem problem, @Nullable AppError error, boolean done) {
        state.setValue(new State(current().online, busy, problem, error, done));
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
