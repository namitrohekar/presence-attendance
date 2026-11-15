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
import com.presence.attendance.roster.domain.RosterCsv;
import com.presence.attendance.roster.domain.RosterEntry;
import com.presence.attendance.roster.domain.RosterRepository;
import com.presence.attendance.roster.domain.RosterRepository.AddOutcome;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV import state machine: IDLE -> PREVIEW -> IMPORTING -> DONE.
 *
 * <p>The whole file is validated before anything is written. Each accepted row is then written in
 * its own transaction, one after another: the transaction re-checks for conflicts, so nothing is
 * overwritten, and one row per write keeps far inside Firestore's batch and rule lookup limits. If
 * the import is interrupted, importing the same file again skips the rows that are already there.
 */
public final class RosterImportViewModel extends ViewModel {

    public enum Phase {
        IDLE,
        PREVIEW,
        IMPORTING,
        DONE
    }

    /** What happened to one READY row when it was written. */
    public enum Result {
        IMPORTED,
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

        /** Set when the chosen file could not be used at all. */
        @Nullable public final AppError fileError;

        public final boolean invalidFile;

        /** One-shot: the roster screen should open the preview. */
        public final boolean openPreview;

        State(
                @NonNull Phase phase,
                boolean online,
                @NonNull List<RosterCsv.Row> rows,
                @NonNull Map<Integer, Result> results,
                int total,
                @Nullable AppError fileError,
                boolean invalidFile,
                boolean openPreview) {
            this.phase = phase;
            this.online = online;
            this.rows = Collections.unmodifiableList(rows);
            this.results = Collections.unmodifiableMap(results);
            this.total = total;
            this.fileError = fileError;
            this.invalidFile = invalidFile;
            this.openPreview = openPreview;
        }

        /** Rows that will be, or were, written. */
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
    private final MediatorLiveData<State> state = new MediatorLiveData<>(idle(false, null, false));

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
                                    s.invalidFile,
                                    s.openPreview));
                });
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /**
     * A file was read. Validates all of it against itself and the current roster.
     *
     * @param text the file's contents, or null if it could not be read or was too large
     */
    public void onFile(@Nullable String text, @NonNull List<RosterEntry> currentRoster) {
        if (current().phase == Phase.IMPORTING) {
            return;
        }
        final boolean online = current().online;
        if (text == null) {
            state.setValue(idle(online, AppError.FILE_UNREADABLE, false));
            return;
        }
        final List<RosterCsv.Row> rows;
        try {
            rows = RosterCsv.checkAgainst(RosterCsv.parse(text), currentRoster);
        } catch (IllegalArgumentException emptyOrTooLarge) {
            state.setValue(idle(online, null, true));
            return;
        }
        final int ready =
                new State(Phase.PREVIEW, online, rows, new HashMap<>(), 0, null, false, false)
                        .count(RosterCsv.Check.READY);
        state.setValue(
                new State(Phase.PREVIEW, online, rows, new HashMap<>(), ready, null, false, true));
    }

    /** The roster screen has navigated to the preview. */
    public void onPreviewOpened() {
        final State s = current();
        state.setValue(
                new State(s.phase, s.online, s.rows, s.results, s.total, null, false, false));
    }

    /** Writes every READY row, one transaction at a time. */
    public void startImport() {
        final State s = current();
        if (s.phase != Phase.PREVIEW || s.total == 0 || !s.online) {
            return;
        }
        final List<RosterCsv.Row> ready = new ArrayList<>();
        for (RosterCsv.Row row : s.rows) {
            if (row.check == RosterCsv.Check.READY) {
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
        roster.addStudent(
                teacher,
                row.email,
                row.rollNo,
                row.cohort,
                outcome -> {
                    final Map<Integer, Result> results = new HashMap<>(current().results);
                    results.put(row.line, result(outcome));
                    set(Phase.IMPORTING, results);
                    importNext(teacher, ready, index + 1);
                });
    }

    @NonNull
    private static Result result(@NonNull Outcome<AddOutcome> outcome) {
        if (!outcome.isSuccess()) {
            return Result.FAILED;
        }
        return switch (outcome.getValue()) {
            case ADDED -> Result.IMPORTED;
            case EMAIL_EXISTS -> Result.EMAIL_EXISTS;
            case ROLL_TAKEN -> Result.ROLL_TAKEN;
        };
    }

    /** Leaves the import screen: forgets the file. */
    public void reset() {
        if (current().phase != Phase.IMPORTING) {
            state.setValue(idle(current().online, null, false));
        }
    }

    private void set(@NonNull Phase phase, @NonNull Map<Integer, Result> results) {
        final State s = current();
        state.setValue(new State(phase, s.online, s.rows, results, s.total, null, false, false));
    }

    @NonNull
    private static State idle(boolean online, @Nullable AppError fileError, boolean invalidFile) {
        return new State(
                Phase.IDLE,
                online,
                Collections.emptyList(),
                new HashMap<>(),
                0,
                fileError,
                invalidFile,
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
