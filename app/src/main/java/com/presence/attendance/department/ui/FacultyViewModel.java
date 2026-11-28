package com.presence.attendance.department.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.core.AppError;
import com.presence.attendance.department.domain.FacultyMember;
import com.presence.attendance.department.domain.FacultyRepository;
import java.util.Collections;
import java.util.List;

/** HOD: the faculty list. */
public final class FacultyViewModel extends ViewModel {

    public enum Status {
        LOADING,
        CONTENT,
        EMPTY,
        ERROR
    }

    /** Immutable screen state. */
    public static final class State {
        @NonNull public final Status status;
        @NonNull public final List<FacultyMember> members;
        @Nullable public final AppError error;

        State(
                @NonNull Status status,
                @NonNull List<FacultyMember> members,
                @Nullable AppError error) {
            this.status = status;
            this.members = Collections.unmodifiableList(members);
            this.error = error;
        }
    }

    private final FacultyRepository faculty;
    private final MutableLiveData<State> state =
            new MutableLiveData<>(new State(Status.LOADING, Collections.emptyList(), null));

    public FacultyViewModel(@NonNull FacultyRepository faculty) {
        this.faculty = faculty;
        refresh();
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** Everyone on the list as last loaded. */
    @NonNull
    public List<FacultyMember> members() {
        final State value = state.getValue();
        return value != null ? value.members : Collections.emptyList();
    }

    /** Reloads after a failure or after an add, edit or removal. */
    public void refresh() {
        if (members().isEmpty()) {
            state.setValue(new State(Status.LOADING, Collections.emptyList(), null));
        }
        faculty.loadFaculty(outcome -> {
            if (!outcome.isSuccess()) {
                state.setValue(
                        new State(Status.ERROR, Collections.emptyList(), outcome.getError()));
                return;
            }
            final List<FacultyMember> members = outcome.getValue();
            state.setValue(
                    new State(members.isEmpty() ? Status.EMPTY : Status.CONTENT, members, null));
        });
    }
}
