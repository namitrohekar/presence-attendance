package com.presence.attendance.roster.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.core.AppError;
import com.presence.attendance.roster.domain.RosterEntry;
import com.presence.attendance.roster.domain.RosterRepository;
import java.util.Collections;
import java.util.List;

/** The roster list. Shared by the roster and import screens so an import can refresh it. */
public final class RosterViewModel extends ViewModel {

    public enum Status {
        LOADING,
        CONTENT,
        EMPTY,
        ERROR
    }

    /** Immutable screen state. */
    public static final class State {
        @NonNull public final Status status;
        @NonNull public final List<RosterEntry> entries;
        @Nullable public final AppError error;

        State(
                @NonNull Status status,
                @NonNull List<RosterEntry> entries,
                @Nullable AppError error) {
            this.status = status;
            this.entries = Collections.unmodifiableList(entries);
            this.error = error;
        }
    }

    private final RosterRepository roster;
    private final MutableLiveData<State> state =
            new MutableLiveData<>(new State(Status.LOADING, Collections.emptyList(), null));

    public RosterViewModel(@NonNull RosterRepository roster) {
        this.roster = roster;
        refresh();
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** The entries as last loaded; empty until the first load finishes. */
    @NonNull
    public List<RosterEntry> entries() {
        final State value = state.getValue();
        return value != null ? value.entries : Collections.emptyList();
    }

    /** Reloads after a failure or after an add, import or correction. */
    public void refresh() {
        // Keep showing the list during a refresh; only an empty screen shows the spinner.
        if (entries().isEmpty()) {
            state.setValue(new State(Status.LOADING, Collections.emptyList(), null));
        }
        roster.loadRoster(outcome -> {
            if (!outcome.isSuccess()) {
                state.setValue(
                        new State(Status.ERROR, Collections.emptyList(), outcome.getError()));
                return;
            }
            final List<RosterEntry> entries = outcome.getValue();
            state.setValue(
                    new State(entries.isEmpty() ? Status.EMPTY : Status.CONTENT, entries, null));
        });
    }
}
