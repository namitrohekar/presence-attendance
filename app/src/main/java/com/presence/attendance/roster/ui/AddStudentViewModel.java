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
import com.presence.attendance.roster.domain.StudentIdentity;

/** Add-student sheet for one batch: strict validation, a connection, then one atomic write. */
public final class AddStudentViewModel extends ViewModel {

    /** What is wrong with each field; all false when the form is valid. Immutable. */
    public static final class Invalid {
        public final boolean email;
        public final boolean rollNo;
        public final boolean name;
        public final boolean labGroup;

        Invalid(boolean email, boolean rollNo, boolean name, boolean labGroup) {
            this.email = email;
            this.rollNo = rollNo;
            this.name = name;
            this.labGroup = labGroup;
        }

        boolean any() {
            return email || rollNo || name || labGroup;
        }

        static final Invalid NONE = new Invalid(false, false, false, false);
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
    private final String batchId;
    private final int labGroups;
    private final MediatorLiveData<State> state =
            new MediatorLiveData<>(new State(false, Invalid.NONE, false, null, null, false));

    public AddStudentViewModel(
            @NonNull AuthRepository auth,
            @NonNull RosterRepository roster,
            @NonNull LiveData<Boolean> online,
            @NonNull String batchId,
            int labGroups) {
        this.auth = auth;
        this.roster = roster;
        this.batchId = batchId;
        this.labGroups = labGroups;
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
     * @param typedLabGroup the lab group as typed; blank for none yet
     */
    public void submit(
            @NonNull String typedEmail,
            @NonNull String typedRollNo,
            @NonNull String typedName,
            @NonNull String typedLabGroup) {
        final State s = current();
        if (s.busy || s.added) {
            return;
        }
        final String email = StudentIdentity.normaliseEmail(typedEmail);
        final String rollNo = typedRollNo.trim();
        final String name = StudentIdentity.normaliseName(typedName);
        final int labGroup = StudentIdentity.parseLabGroup(typedLabGroup, labGroups);
        final Invalid invalid =
                new Invalid(
                        !StudentIdentity.isStudentEmail(email),
                        !StudentIdentity.isRollNo(rollNo),
                        !StudentIdentity.isName(name),
                        labGroup == StudentIdentity.INVALID_GROUP);
        if (invalid.any()) {
            state.setValue(new State(s.online, invalid, false, null, null, false));
            return;
        }
        if (!s.online) {
            set(false, null, AppError.OFFLINE, false);
            return;
        }
        final StudentDetails details =
                new StudentDetails(name, labGroup == StudentIdentity.NO_GROUP ? null : labGroup);
        set(true, null, null, false);
        auth.loadUser(user -> {
            if (user.isSuccess()) {
                roster.addStudent(
                        user.getValue(), email, rollNo, batchId, details, this::onOutcome);
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
