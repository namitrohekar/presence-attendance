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
import com.presence.attendance.roster.domain.StudentDetails;
import com.presence.attendance.roster.domain.StudentEmoji;
import com.presence.attendance.roster.domain.StudentIdentity;

/** Add-student sheet: strict validation, a connection, then one atomic write. */
public final class AddStudentViewModel extends ViewModel {

    /** What is wrong with each field; all false when the form is valid. Immutable. */
    public static final class Invalid {
        public final boolean email;
        public final boolean rollNo;
        public final boolean name;
        public final boolean cohort;
        public final boolean batch;

        Invalid(boolean email, boolean rollNo, boolean name, boolean cohort, boolean batch) {
            this.email = email;
            this.rollNo = rollNo;
            this.name = name;
            this.cohort = cohort;
            this.batch = batch;
        }

        boolean any() {
            return email || rollNo || name || cohort || batch;
        }

        static final Invalid NONE = new Invalid(false, false, false, false, false);
    }

    /** Immutable sheet state. {@code conflict} is EMAIL_EXISTS or ROLL_TAKEN, never ADDED. */
    public static final class State {
        public final boolean online;
        @NonNull public final Invalid invalid;
        public final boolean busy;
        @Nullable public final AddOutcome conflict;
        @Nullable public final AppError error;
        public final boolean added;

        State(
                boolean online,
                @NonNull Invalid invalid,
                boolean busy,
                @Nullable AddOutcome conflict,
                @Nullable AppError error,
                boolean added) {
            this.online = online;
            this.invalid = invalid;
            this.busy = busy;
            this.conflict = conflict;
            this.error = error;
            this.added = added;
        }
    }

    private final AuthRepository auth;
    private final RosterRepository roster;
    private final MediatorLiveData<State> state =
            new MediatorLiveData<>(new State(false, Invalid.NONE, false, null, null, false));

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
                                    s.invalid,
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

    /**
     * @param typedBatch the batch as typed; blank for none
     * @param emoji the emoji picked, or null to use the one for the roll number
     */
    public void submit(
            @NonNull String typedEmail,
            @NonNull String typedRollNo,
            @NonNull String typedName,
            @NonNull String typedCohort,
            @NonNull String typedBatch,
            @Nullable StudentEmoji emoji) {
        final State s = current();
        if (s.busy || s.added) {
            return;
        }
        final String email = StudentIdentity.normaliseEmail(typedEmail);
        final String rollNo = typedRollNo.trim();
        final String name = StudentIdentity.normaliseName(typedName);
        final String cohort = StudentIdentity.normaliseCohort(typedCohort);
        final int batch = StudentIdentity.parseBatch(typedBatch);
        final Invalid invalid =
                new Invalid(
                        !StudentIdentity.isStudentEmail(email),
                        !StudentIdentity.isRollNo(rollNo),
                        !StudentIdentity.isName(name),
                        !StudentIdentity.isCohort(cohort),
                        batch == StudentIdentity.INVALID_BATCH);
        if (invalid.any()) {
            state.setValue(new State(s.online, invalid, false, null, null, false));
            return;
        }
        if (!s.online) {
            set(false, null, AppError.OFFLINE, false);
            return;
        }
        final StudentDetails details =
                new StudentDetails(
                        name,
                        batch == StudentIdentity.NO_BATCH ? null : batch,
                        emoji != null ? emoji : StudentEmoji.forRollNo(rollNo));
        set(true, null, null, false);
        auth.loadUser(user -> {
            if (user.isSuccess()) {
                roster.addStudent(user.getValue(), email, rollNo, cohort, details, this::onOutcome);
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
        state.setValue(new State(current().online, Invalid.NONE, busy, conflict, error, added));
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
