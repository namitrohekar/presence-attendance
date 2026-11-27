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
import com.presence.attendance.roster.domain.StudentDetails;
import com.presence.attendance.roster.domain.StudentEmoji;
import com.presence.attendance.roster.domain.StudentIdentity;

/**
 * Sheet for one roster entry. The name, batch and emoji are saved as they are, with no reason, and
 * the student stays registered. Every other action is one audited, atomic change and needs a
 * reason, which is kept in the audit log.
 */
public final class RosterEntryViewModel extends ViewModel {

    /** What is wrong with the form, if anything. */
    public enum Problem {
        NAME_INVALID,
        BATCH_INVALID,
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
    @Nullable private final StudentDetails details;
    private final MediatorLiveData<State> state =
            new MediatorLiveData<>(new State(false, false, null, null, false));

    public RosterEntryViewModel(
            @NonNull AuthRepository auth,
            @NonNull RosterRepository roster,
            @NonNull LiveData<Boolean> online,
            @NonNull String email,
            @NonNull String rollNo,
            @NonNull String cohort,
            @Nullable StudentDetails details) {
        this.auth = auth;
        this.roster = roster;
        this.email = email;
        this.rollNo = rollNo;
        this.cohort = cohort;
        this.details = details;
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

    /**
     * Saves the name, batch and emoji. Saving them unchanged closes the sheet without a write.
     *
     * @param typedBatch the batch as typed; blank for none
     * @param picked the emoji picked, or null to use the one for the roll number
     */
    public void saveDetails(
            @NonNull String typedName, @NonNull String typedBatch, @Nullable StudentEmoji picked) {
        final State s = current();
        if (s.busy || s.done) {
            return;
        }
        final String name = StudentIdentity.normaliseName(typedName);
        final int batch = StudentIdentity.parseBatch(typedBatch);
        if (!StudentIdentity.isName(name)) {
            set(false, Problem.NAME_INVALID, null, false);
            return;
        }
        if (batch == StudentIdentity.INVALID_BATCH) {
            set(false, Problem.BATCH_INVALID, null, false);
            return;
        }
        final StudentDetails changed =
                new StudentDetails(
                        name,
                        batch == StudentIdentity.NO_BATCH ? null : batch,
                        picked != null ? picked : StudentEmoji.forRollNo(rollNo));
        if (changed.equals(details)) {
            set(false, null, null, true);
            return;
        }
        if (!s.online) {
            set(false, null, AppError.OFFLINE, false);
            return;
        }
        set(true, null, null, false);
        roster.updateDetails(email, changed, this::onOutcome);
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
