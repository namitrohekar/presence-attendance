package com.presence.attendance.roster.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.core.AppError;
import com.presence.attendance.department.domain.Batch;
import com.presence.attendance.department.domain.BatchRepository;
import com.presence.attendance.department.domain.LabGroups;
import com.presence.attendance.roster.domain.RosterEntry;
import com.presence.attendance.roster.domain.RosterRepository;
import com.presence.attendance.roster.domain.RosterRepository.CorrectOutcome;
import com.presence.attendance.roster.domain.StudentDetails;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One batch as the HOD sees it: the batch itself, its students, and the two changes made from the
 * batch screen, moving the semester on and applying lab groups. Shared by the batch screen, its
 * sheets and the import screen, so any of them can refresh it.
 */
public final class RosterViewModel extends ViewModel {

    public enum Status {
        LOADING,
        CONTENT,
        ERROR
    }

    /** A change in progress or just finished. */
    public enum Task {
        SEMESTER,
        LAB_GROUPS
    }

    /** The state of a change made from the batch screen. Immutable. */
    public static final class Work {
        @NonNull public final Task task;
        public final int done;
        public final int total;
        public final boolean finished;
        @Nullable public final AppError error;

        Work(@NonNull Task task, int done, int total, boolean finished, @Nullable AppError error) {
            this.task = task;
            this.done = done;
            this.total = total;
            this.finished = finished;
            this.error = error;
        }
    }

    /** Immutable screen state. The batch is known in CONTENT. */
    public static final class State {
        @NonNull public final Status status;
        @Nullable public final Batch batch;
        @NonNull public final List<RosterEntry> entries;
        @Nullable public final AppError error;
        public final boolean online;

        /** Null when no change has been started since the last one was seen through. */
        @Nullable public final Work work;

        State(
                @NonNull Status status,
                @Nullable Batch batch,
                @NonNull List<RosterEntry> entries,
                @Nullable AppError error,
                boolean online,
                @Nullable Work work) {
            this.status = status;
            this.batch = batch;
            this.entries = Collections.unmodifiableList(entries);
            this.error = error;
            this.online = online;
            this.work = work;
        }
    }

    private final RosterRepository roster;
    private final BatchRepository batches;
    private final String batchId;
    private final MediatorLiveData<State> state =
            new MediatorLiveData<>(
                    new State(Status.LOADING, null, Collections.emptyList(), null, false, null));

    public RosterViewModel(
            @NonNull RosterRepository roster,
            @NonNull BatchRepository batches,
            @NonNull LiveData<Boolean> online,
            @NonNull String batchId) {
        this.roster = roster;
        this.batches = batches;
        this.batchId = batchId;
        state.addSource(
                online,
                isOnline -> {
                    final State s = current();
                    state.setValue(
                            new State(
                                    s.status,
                                    s.batch,
                                    s.entries,
                                    s.error,
                                    Boolean.TRUE.equals(isOnline),
                                    s.work));
                });
        refresh();
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** The batch's students as last loaded; empty until the first load finishes. */
    @NonNull
    public List<RosterEntry> entries() {
        return current().entries;
    }

    /** The batch as last loaded, or null before it is. */
    @Nullable
    public Batch batch() {
        return current().batch;
    }

    /** Reloads the batch and its students: after a failure, and after any change. */
    public void refresh() {
        final State s = current();
        // Keep showing the batch during a refresh; only a first load shows the spinner.
        if (s.batch == null) {
            set(Status.LOADING, null, Collections.emptyList(), null);
        }
        batches.loadBatch(
                batchId,
                loaded -> {
                    if (!loaded.isSuccess()) {
                        set(Status.ERROR, null, Collections.emptyList(), loaded.getError());
                        return;
                    }
                    roster.loadBatchRoster(
                            batchId,
                            students -> {
                                if (students.isSuccess()) {
                                    set(
                                            Status.CONTENT,
                                            loaded.getValue(),
                                            students.getValue(),
                                            null);
                                } else {
                                    set(
                                            Status.ERROR,
                                            loaded.getValue(),
                                            Collections.emptyList(),
                                            students.getError());
                                }
                            });
                });
    }

    /** Moves the batch on to its next semester. Nothing happens in the last one. */
    public void moveSemester() {
        final State s = current();
        final Batch batch = s.batch;
        if (batch == null || batch.isInLastSemester() || isWorking()) {
            return;
        }
        if (!s.online) {
            work(new Work(Task.SEMESTER, 0, 1, true, AppError.OFFLINE));
            return;
        }
        work(new Work(Task.SEMESTER, 0, 1, false, null));
        batches.updateBatch(
                batch.getId(),
                batch.getYears(),
                batch.getSemester() + 1,
                batch.getIntake(),
                batch.getLabGroups(),
                outcome -> {
                    final AppError error =
                            !outcome.isSuccess()
                                    ? outcome.getError()
                                    : outcome.getValue() == BatchRepository.UpdateOutcome.DONE
                                            ? null
                                            : AppError.BATCH_NOT_FOUND;
                    work(new Work(Task.SEMESTER, 1, 1, true, error));
                    refresh();
                });
    }

    /**
     * Writes the planned lab group of every student whose group changes, one at a time, in
     * roll-number order. A failure stops there: what was written stays, and applying the same plan
     * again writes only the rest.
     */
    public void applyLabGroups(@NonNull LabGroups.Plan plan) {
        final State s = current();
        if (s.batch == null || isWorking() || plan.changes.isEmpty()) {
            return;
        }
        if (!s.online) {
            work(new Work(Task.LAB_GROUPS, 0, plan.changes.size(), true, AppError.OFFLINE));
            return;
        }
        final List<RosterEntry> changes = new ArrayList<>(plan.changes);
        work(new Work(Task.LAB_GROUPS, 0, changes.size(), false, null));
        applyNext(plan, changes, 0);
    }

    private void applyNext(
            @NonNull LabGroups.Plan plan, @NonNull List<RosterEntry> changes, int index) {
        if (index == changes.size()) {
            work(new Work(Task.LAB_GROUPS, index, changes.size(), true, null));
            refresh();
            return;
        }
        final RosterEntry student = changes.get(index);
        final String name = student.getName();
        if (name == null) {
            // An entry added before names were kept cannot be given a group until it has one.
            applyNext(plan, changes, index + 1);
            return;
        }
        final Integer group = plan.groupByEmail.get(student.getEmail());
        roster.updateDetails(
                student.getEmail(),
                new StudentDetails(name, group),
                outcome -> {
                    if (!outcome.isSuccess()) {
                        work(
                                new Work(
                                        Task.LAB_GROUPS,
                                        index,
                                        changes.size(),
                                        true,
                                        outcome.getError()));
                        refresh();
                        return;
                    }
                    if (outcome.getValue() == CorrectOutcome.NOT_FOUND) {
                        // Removed meanwhile: nothing to group.
                        applyNext(plan, changes, index + 1);
                        return;
                    }
                    work(new Work(Task.LAB_GROUPS, index + 1, changes.size(), false, null));
                    applyNext(plan, changes, index + 1);
                });
    }

    /** The sheet that started the change has shown how it ended. */
    public void onWorkSeen() {
        final State s = current();
        if (s.work != null && s.work.finished) {
            state.setValue(new State(s.status, s.batch, s.entries, s.error, s.online, null));
        }
    }

    private boolean isWorking() {
        final Work work = current().work;
        return work != null && !work.finished;
    }

    private void work(@NonNull Work work) {
        final State s = current();
        state.setValue(new State(s.status, s.batch, s.entries, s.error, s.online, work));
    }

    private void set(
            @NonNull Status status,
            @Nullable Batch batch,
            @NonNull List<RosterEntry> entries,
            @Nullable AppError error) {
        final State s = current();
        state.setValue(new State(status, batch, entries, error, s.online, s.work));
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
