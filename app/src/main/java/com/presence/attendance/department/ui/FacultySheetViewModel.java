package com.presence.attendance.department.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.ui.Monogram;
import com.presence.attendance.department.domain.FacultyMember;
import com.presence.attendance.department.domain.FacultyRepository;
import com.presence.attendance.department.domain.FacultyRepository.AddOutcome;
import com.presence.attendance.department.domain.FacultyRepository.ChangeOutcome;
import com.presence.attendance.roster.domain.StudentIdentity;

/**
 * HOD: add someone to the faculty list, or correct or remove them. Initials left empty are taken
 * from the name, without titles: "Dr. Archana G. Pai" is AP.
 */
public final class FacultySheetViewModel extends ViewModel {

    /** What is wrong with the form, or what happened. */
    public enum Problem {
        NAME_INVALID,
        INITIALS_INVALID,
        EMAIL_INVALID,
        /** The address is on the list already. */
        EXISTS,
        /** Someone else removed them while the sheet was open. */
        GONE,
        /** The first tap on Remove: the next one removes them. */
        CONFIRM_REMOVE
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
    private final FacultyRepository faculty;
    @Nullable private final FacultyMember editing;
    private final MediatorLiveData<State> state =
            new MediatorLiveData<>(new State(false, false, null, null, false));

    /**
     * @param editing the member being changed, or null to add one
     */
    public FacultySheetViewModel(
            @NonNull AuthRepository auth,
            @NonNull FacultyRepository faculty,
            @NonNull LiveData<Boolean> online,
            @Nullable FacultyMember editing) {
        this.auth = auth;
        this.faculty = faculty;
        this.editing = editing;
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

    /** Adds the member, or saves the changes; the email is ignored when changing a member. */
    public void save(
            @NonNull String typedName, @NonNull String typedInitials, @NonNull String typedEmail) {
        final State s = current();
        if (s.busy || s.done) {
            return;
        }
        final String name = StudentIdentity.normaliseName(typedName);
        final String typed = FacultyMember.normaliseInitials(typedInitials);
        final String initials = typed.isEmpty() ? Monogram.of(name) : typed;
        final String email = FacultyMember.normaliseEmail(typedEmail);
        final Problem problem;
        if (!StudentIdentity.isName(name)) {
            problem = Problem.NAME_INVALID;
        } else if (!FacultyMember.isInitials(initials)) {
            problem = Problem.INITIALS_INVALID;
        } else if (editing == null && !FacultyMember.isStaffEmail(email)) {
            problem = Problem.EMAIL_INVALID;
        } else {
            problem = null;
        }
        if (problem != null) {
            set(false, problem, null, false);
            return;
        }
        if (!s.online) {
            set(false, null, AppError.OFFLINE, false);
            return;
        }
        set(true, null, null, false);
        if (editing != null) {
            faculty.updateFaculty(editing.getEmail(), name, initials, this::onChanged);
            return;
        }
        auth.loadUser(user -> {
            if (!user.isSuccess()) {
                set(false, null, user.getError(), false);
                return;
            }
            faculty.addFaculty(
                    user.getValue(),
                    email,
                    name,
                    initials,
                    outcome -> {
                        if (!outcome.isSuccess()) {
                            set(false, null, outcome.getError(), false);
                        } else if (outcome.getValue() == AddOutcome.EXISTS) {
                            set(false, Problem.EXISTS, null, false);
                        } else {
                            set(false, null, null, true);
                        }
                    });
        });
    }

    /** The first call asks for confirmation; the second removes them. */
    public void remove() {
        final State s = current();
        if (editing == null || s.busy || s.done) {
            return;
        }
        if (s.problem != Problem.CONFIRM_REMOVE) {
            set(false, Problem.CONFIRM_REMOVE, null, false);
            return;
        }
        if (!s.online) {
            set(false, Problem.CONFIRM_REMOVE, AppError.OFFLINE, false);
            return;
        }
        set(true, Problem.CONFIRM_REMOVE, null, false);
        faculty.removeFaculty(editing.getEmail(), this::onChanged);
    }

    private void onChanged(@NonNull Outcome<ChangeOutcome> outcome) {
        if (!outcome.isSuccess()) {
            set(false, null, outcome.getError(), false);
        } else if (outcome.getValue() == ChangeOutcome.NOT_FOUND) {
            set(false, Problem.GONE, null, false);
        } else {
            set(false, null, null, true);
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
