package com.presence.attendance.roster.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.department.domain.Batch;
import com.presence.attendance.roster.domain.RosterCsv;
import com.presence.attendance.roster.domain.RosterRepository;
import com.presence.attendance.roster.domain.RosterRepository.AddOutcome;
import com.presence.attendance.roster.domain.RosterRepository.CorrectOutcome;
import com.presence.attendance.roster.domain.StudentDetails;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * CSV import state machine: IDLE -> PREVIEW -> IMPORTING -> DONE.
 *
 * <p>The whole file is validated before anything is written. Each accepted row is then written in
 * its own transaction, one after another: the transaction re-checks for conflicts, so nothing is
 * overwritten, and one row per write keeps far inside Firestore's batch and rule lookup limits. A
 * new student is added; a student already on the roster with a different name, batch or emoji gets
 * those updated and stays registered. If the import is interrupted, importing the same file again
 * skips the rows that are already there.
 */
public final class RosterImportViewModel extends ViewModel {

    public enum Phase {
        IDLE,
        PREVIEW,
        IMPORTING,
        DONE
    }

    /** What happened to one written row. */
    public enum Result {
        IMPORTED,
        /** An existing student's name, batch or emoji was changed. */
        UPDATED,
        EMAIL_EXISTS,
        ROLL_TAKEN,
        FAILED
    }

    /** Immutable screen state. {@code results} is keyed by the row's line number. */
    public static final class State {
        @NonNull public final Phase phase;
        public final boolean online;
        @NonNull public final List<RosterCsv.Row> rows;
        @NonNull public final Map<Integer, Result> results;
        public final int total;

        /** Set when the chosen file could not be read. */
        @Nullable public final AppError fileError;

        /** Set when the file was read but cannot be used: no header, no rows, or too many. */
        @Nullable public final RosterCsv.FileProblem fileProblem;

        /** One-shot: the roster screen should open the preview. */
        public final boolean openPreview;

        State(
                @NonNull Phase phase,
                boolean online,
                @NonNull List<RosterCsv.Row> rows,
                @NonNull Map<Integer, Result> results,
                int total,
                @Nullable AppError fileError,
                @Nullable RosterCsv.FileProblem fileProblem,
                boolean openPreview) {
            this.phase = phase;
            this.online = online;
            this.rows = Collections.unmodifiableList(rows);
            this.results = Collections.unmodifiableMap(results);
            this.total = total;
            this.fileError = fileError;
            this.fileProblem = fileProblem;
            this.openPreview = openPreview;
        }

        /** Rows with this check. */
        public int count(@NonNull RosterCsv.Check check) {
            int count = 0;
            for (RosterCsv.Row row : rows) {
                if (row.check == check) {
                    count++;
                }
            }
            return count;
        }

        public int count(@NonNull Result result) {
            int count = 0;
            for (Result value : results.values()) {
                if (value == result) {
                    count++;
                }
            }
            return count;
        }
    }

    private final AuthRepository auth;
    private final RosterRepository roster;

    /** The batch the file being previewed or imported is for. */
    @NonNull private String batchId = "";

    private final MediatorLiveData<State> state = new MediatorLiveData<>(idle(false, null, null));

    public RosterImportViewModel(
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
                                    s.phase,
                                    Boolean.TRUE.equals(isOnline),
                                    s.rows,
                                    s.results,
                                    s.total,
                                    s.fileError,
                                    s.fileProblem,
                                    s.openPreview));
                });
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /**
     * A file was read for a batch. Validates all of it against itself, then against the whole
     * roster as it is now: an address may be in only one batch.
     *
     * @param text the file's contents, or null if it could not be read or was too large
     */
    public void onFile(@Nullable String text, @NonNull Batch batch) {
        if (current().phase == Phase.IMPORTING) {
            return;
        }
        final boolean online = current().online;
        if (text == null) {
            state.setValue(idle(online, AppError.FILE_UNREADABLE, null));
            return;
        }
        final List<RosterCsv.Row> parsed;
        try {
            parsed = RosterCsv.parse(text, batch.getId(), batch.getLabGroups());
        } catch (RosterCsv.FileException unusable) {
            state.setValue(idle(online, null, unusable.problem));
            return;
        }
        batchId = batch.getId();
        roster.loadRoster(loaded -> {
            if (!loaded.isSuccess()) {
                state.setValue(idle(current().online, loaded.getError(), null));
                return;
            }
            final List<RosterCsv.Row> rows =
                    RosterCsv.checkAgainst(parsed, batch.getId(), loaded.getValue());
            int writes = 0;
            for (RosterCsv.Row row : rows) {
                writes += row.writes() ? 1 : 0;
            }
            state.setValue(
                    new State(
                            Phase.PREVIEW,
                            current().online,
                            rows,
                            new HashMap<>(),
                            writes,
                            null,
                            null,
                            true));
        });
    }

    /** The roster screen has navigated to the preview. */
    public void onPreviewOpened() {
        final State s = current();
        state.setValue(new State(s.phase, s.online, s.rows, s.results, s.total, null, null, false));
    }

    /** Writes every new student and every changed set of details, one transaction at a time. */
    public void startImport() {
        final State s = current();
        if (s.phase != Phase.PREVIEW || s.total == 0 || !s.online) {
            return;
        }
        final List<RosterCsv.Row> ready = new ArrayList<>();
        for (RosterCsv.Row row : s.rows) {
            if (row.writes()) {
                ready.add(row);
            }
        }
        set(Phase.IMPORTING, s.results);
        auth.loadUser(user -> {
            if (user.isSuccess()) {
                importNext(user.getValue(), ready, 0);
            } else {
                // Nothing was written; every row is reported as not imported.
                final Map<Integer, Result> failed = new HashMap<>();
                for (RosterCsv.Row row : ready) {
                    failed.put(row.line, Result.FAILED);
                }
                set(Phase.DONE, failed);
            }
        });
    }

    private void importNext(@NonNull User teacher, @NonNull List<RosterCsv.Row> ready, int index) {
        if (index == ready.size()) {
            set(Phase.DONE, current().results);
            return;
        }
        final RosterCsv.Row row = ready.get(index);
        final StudentDetails details = row.details;
        if (details == null) {
            throw new IllegalStateException("A row that writes has its details");
        }
        final Consumer<Result> next =
                result -> {
                    final Map<Integer, Result> results = new HashMap<>(current().results);
                    results.put(row.line, result);
                    set(Phase.IMPORTING, results);
                    importNext(teacher, ready, index + 1);
                };
        if (row.check == RosterCsv.Check.DETAILS_CHANGED) {
            roster.updateDetails(row.email, details, outcome -> next.accept(updated(outcome)));
        } else {
            roster.addStudent(
                    teacher,
                    row.email,
                    row.rollNo,
                    batchId,
                    details,
                    outcome -> next.accept(added(outcome)));
        }
    }

    @NonNull
    private static Result added(@NonNull Outcome<AddOutcome> outcome) {
        if (!outcome.isSuccess()) {
            return Result.FAILED;
        }
        return switch (outcome.getValue()) {
            case ADDED -> Result.IMPORTED;
            case EMAIL_EXISTS -> Result.EMAIL_EXISTS;
            case ROLL_TAKEN -> Result.ROLL_TAKEN;
        };
    }

    /** A student removed meanwhile cannot be updated; that row is reported as not imported. */
    @NonNull
    private static Result updated(@NonNull Outcome<CorrectOutcome> outcome) {
        return outcome.isSuccess() && outcome.getValue() == CorrectOutcome.DONE
                ? Result.UPDATED
                : Result.FAILED;
    }

    /** Leaves the import screen: forgets the file. */
    public void reset() {
        if (current().phase != Phase.IMPORTING) {
            state.setValue(idle(current().online, null, null));
        }
    }

    private void set(@NonNull Phase phase, @NonNull Map<Integer, Result> results) {
        final State s = current();
        state.setValue(new State(phase, s.online, s.rows, results, s.total, null, null, false));
    }

    @NonNull
    private static State idle(
            boolean online,
            @Nullable AppError fileError,
            @Nullable RosterCsv.FileProblem fileProblem) {
        return new State(
                Phase.IDLE,
                online,
                Collections.emptyList(),
                new HashMap<>(),
                0,
                fileError,
                fileProblem,
                false);
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
