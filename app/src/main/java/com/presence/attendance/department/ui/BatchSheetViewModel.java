package com.presence.attendance.department.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Constants;
import com.presence.attendance.department.domain.Batch;
import com.presence.attendance.department.domain.BatchRepository;
import com.presence.attendance.department.domain.BatchRepository.CreateOutcome;
import com.presence.attendance.department.domain.BatchRepository.UpdateOutcome;

/**
 * HOD: create a batch, or change one. A new batch starts in semester 1; the programme and start
 * year make its id and are fixed from then on.
 */
public final class BatchSheetViewModel extends ViewModel {

    /** What is wrong with each field; all false when the form is valid. Immutable. */
    public static final class Invalid {
        public final boolean programme;
        public final boolean startYear;
        public final boolean years;
        public final boolean semester;
        public final boolean intake;
        public final boolean labGroups;

        Invalid(
                boolean programme,
                boolean startYear,
                boolean years,
                boolean semester,
                boolean intake,
                boolean labGroups) {
            this.programme = programme;
            this.startYear = startYear;
            this.years = years;
            this.semester = semester;
            this.intake = intake;
            this.labGroups = labGroups;
        }

        boolean any() {
            return programme || startYear || years || semester || intake || labGroups;
        }

        static final Invalid NONE = new Invalid(false, false, false, false, false, false);
    }

    /** Immutable sheet state. */
    public static final class State {
        public final boolean online;
        @NonNull public final Invalid invalid;
        public final boolean busy;

        /** A batch of that programme and year exists already. */
        public final boolean exists;

        @Nullable public final AppError error;
        public final boolean done;

        State(
                boolean online,
                @NonNull Invalid invalid,
                boolean busy,
                boolean exists,
                @Nullable AppError error,
                boolean done) {
            this.online = online;
            this.invalid = invalid;
            this.busy = busy;
            this.exists = exists;
            this.error = error;
            this.done = done;
        }
    }

    private final AuthRepository auth;
    private final BatchRepository batches;
    @Nullable private final Batch editing;
    private final MediatorLiveData<State> state =
            new MediatorLiveData<>(new State(false, Invalid.NONE, false, false, null, false));

    /**
     * @param editing the batch being changed, or null to create one
     */
    public BatchSheetViewModel(
            @NonNull AuthRepository auth,
            @NonNull BatchRepository batches,
            @NonNull LiveData<Boolean> online,
            @Nullable Batch editing) {
        this.auth = auth;
        this.batches = batches;
        this.editing = editing;
        state.addSource(
                online,
                isOnline -> {
                    final State s = current();
                    state.setValue(
                            new State(
                                    Boolean.TRUE.equals(isOnline),
                                    s.invalid,
                                    s.busy,
                                    s.exists,
                                    s.error,
                                    s.done));
                });
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /**
     * Validates and writes. When changing a batch the programme and start year are ignored; when
     * creating one the semester is.
     */
    public void submit(
            @NonNull String typedProgramme,
            @NonNull String typedStartYear,
            @NonNull String typedYears,
            @NonNull String typedSemester,
            @NonNull String typedIntake,
            @NonNull String typedLabGroups) {
        final State s = current();
        if (s.busy || s.done) {
            return;
        }
        final String programme = Batch.normaliseProgramme(typedProgramme);
        final int startYear = number(typedStartYear);
        final int years = number(typedYears);
        final int semester = number(typedSemester);
        final int intake = number(typedIntake);
        final int labGroups = number(typedLabGroups);
        final boolean creating = editing == null;
        final Invalid invalid =
                new Invalid(
                        creating && !Batch.isProgramme(programme),
                        creating && !Batch.isStartYear(startYear),
                        !Batch.isYears(years),
                        !creating
                                && Batch.isYears(years)
                                && (semester < 1
                                        || semester > years * Constants.SEMESTERS_PER_YEAR),
                        !Batch.isIntake(intake),
                        !Batch.isLabGroups(labGroups));
        if (invalid.any()) {
            state.setValue(new State(s.online, invalid, false, false, null, false));
            return;
        }
        if (!s.online) {
            set(false, false, AppError.OFFLINE, false);
            return;
        }
        set(true, false, null, false);
        if (editing != null) {
            batches.updateBatch(
                    editing.getId(),
                    years,
                    semester,
                    intake,
                    labGroups,
                    outcome -> {
                        if (!outcome.isSuccess()) {
                            set(false, false, outcome.getError(), false);
                        } else if (outcome.getValue() == UpdateOutcome.NOT_FOUND) {
                            set(false, false, AppError.BATCH_NOT_FOUND, false);
                        } else {
                            set(false, false, null, true);
                        }
                    });
            return;
        }
        auth.loadUser(user -> {
            if (!user.isSuccess()) {
                set(false, false, user.getError(), false);
                return;
            }
            batches.createBatch(
                    user.getValue(),
                    programme,
                    startYear,
                    years,
                    intake,
                    labGroups,
                    outcome -> {
                        if (!outcome.isSuccess()) {
                            set(false, false, outcome.getError(), false);
                        } else if (outcome.getValue() == CreateOutcome.EXISTS) {
                            set(false, true, null, false);
                        } else {
                            set(false, false, null, true);
                        }
                    });
        });
    }

    /** The whole number typed, or -1 if it is not one. */
    private static int number(@NonNull String typed) {
        final String text = typed.trim();
        if (text.isEmpty() || text.length() > 4 || !text.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        return Integer.parseInt(text);
    }

    private void set(boolean busy, boolean exists, @Nullable AppError error, boolean done) {
        state.setValue(new State(current().online, Invalid.NONE, busy, exists, error, done));
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
