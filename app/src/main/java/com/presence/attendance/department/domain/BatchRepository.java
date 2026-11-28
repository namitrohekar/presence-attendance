package com.presence.attendance.department.domain;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.Callback;
import java.util.List;

/** The department's batches. Only the HOD may write them; everyone signed in may read them. */
public interface BatchRepository {

    enum CreateOutcome {
        CREATED,
        /** A batch of that programme and year exists already; it is not overwritten. */
        EXISTS
    }

    enum UpdateOutcome {
        DONE,
        NOT_FOUND
    }

    /** Every batch, the newest first. */
    @MainThread
    void loadBatches(@NonNull Callback<List<Batch>> callback);

    /** One batch; fails with {@code BATCH_NOT_FOUND} if it does not exist. */
    @MainThread
    void loadBatch(@NonNull String batchId, @NonNull Callback<Batch> callback);

    /** Creates a batch in its first semester. Inputs must already be valid. */
    @MainThread
    void createBatch(
            @NonNull User hod,
            @NonNull String programme,
            int startYear,
            int years,
            int intake,
            int labGroups,
            @NonNull Callback<CreateOutcome> callback);

    /** Changes the programme length, semester, intake and lab groups. Inputs must be valid. */
    @MainThread
    void updateBatch(
            @NonNull String batchId,
            int years,
            int semester,
            int intake,
            int labGroups,
            @NonNull Callback<UpdateOutcome> callback);

    /**
     * The timetables that exist among these ids ({@link Timetable#idOf}); missing ones are left
     * out.
     */
    @MainThread
    void loadTimetables(@NonNull List<String> ids, @NonNull Callback<List<Timetable>> callback);

    /** HOD: saves a batch's week for one semester, replacing the one before. */
    @MainThread
    void saveTimetable(
            @NonNull User hod, @NonNull Timetable timetable, @NonNull Callback<Timetable> callback);
}
