package com.presence.attendance.testutil;

import androidx.annotation.NonNull;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.department.domain.Batch;
import com.presence.attendance.department.domain.BatchRepository;
import com.presence.attendance.department.domain.Timetable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** In-memory {@link BatchRepository}. Answers synchronously and records every write. */
public final class FakeBatchRepository implements BatchRepository {

    /** The batches as stored. Writes change it, as the real one would. */
    public final List<Batch> batches = new ArrayList<>();

    public final List<String> calls = new ArrayList<>();

    /** When set, every call fails with it. */
    public AppError failure;

    @Override
    public void loadBatches(@NonNull Callback<List<Batch>> callback) {
        callback.onResult(
                failure != null
                        ? Outcome.failure(failure)
                        : Outcome.success(new ArrayList<>(batches)));
    }

    @Override
    public void loadBatch(@NonNull String batchId, @NonNull Callback<Batch> callback) {
        if (failure != null) {
            callback.onResult(Outcome.failure(failure));
            return;
        }
        final Batch batch = find(batchId);
        callback.onResult(
                batch != null ? Outcome.success(batch) : Outcome.failure(AppError.BATCH_NOT_FOUND));
    }

    @Override
    public void createBatch(
            @NonNull User hod,
            @NonNull String programme,
            int startYear,
            int years,
            int intake,
            int labGroups,
            @NonNull Callback<CreateOutcome> callback) {
        calls.add(
                "create:"
                        + Batch.idOf(programme, startYear)
                        + ":"
                        + years
                        + ":"
                        + intake
                        + ":"
                        + labGroups);
        if (failure != null) {
            callback.onResult(Outcome.failure(failure));
            return;
        }
        if (find(Batch.idOf(programme, startYear)) != null) {
            callback.onResult(Outcome.success(CreateOutcome.EXISTS));
            return;
        }
        batches.add(new Batch(programme, startYear, years, 1, intake, labGroups));
        callback.onResult(Outcome.success(CreateOutcome.CREATED));
    }

    @Override
    public void updateBatch(
            @NonNull String batchId,
            int years,
            int semester,
            int intake,
            int labGroups,
            @NonNull Callback<UpdateOutcome> callback) {
        calls.add(
                "update:" + batchId + ":" + years + ":" + semester + ":" + intake + ":"
                        + labGroups);
        if (failure != null) {
            callback.onResult(Outcome.failure(failure));
            return;
        }
        final Batch before = find(batchId);
        if (before == null) {
            callback.onResult(Outcome.success(UpdateOutcome.NOT_FOUND));
            return;
        }
        batches.remove(before);
        batches.add(
                new Batch(
                        before.getProgramme(),
                        before.getStartYear(),
                        years,
                        semester,
                        intake,
                        labGroups));
        callback.onResult(Outcome.success(UpdateOutcome.DONE));
    }

    /** The timetables as stored, by id. */
    public final Map<String, Timetable> timetables = new HashMap<>();

    @Override
    public void loadTimetables(
            @NonNull List<String> ids, @NonNull Callback<List<Timetable>> callback) {
        calls.add("timetables:" + String.join(",", ids));
        if (failure != null) {
            callback.onResult(Outcome.failure(failure));
            return;
        }
        final List<Timetable> found = new ArrayList<>();
        for (String id : ids) {
            if (timetables.containsKey(id)) {
                found.add(timetables.get(id));
            }
        }
        callback.onResult(Outcome.success(found));
    }

    @Override
    public void saveTimetable(
            @NonNull User hod,
            @NonNull Timetable timetable,
            @NonNull Callback<Timetable> callback) {
        calls.add("saveTimetable:" + timetable.getId() + ":" + timetable.getSlots().size());
        if (failure != null) {
            callback.onResult(Outcome.failure(failure));
            return;
        }
        timetables.put(timetable.getId(), timetable);
        callback.onResult(Outcome.success(timetable));
    }

    private Batch find(@NonNull String batchId) {
        for (Batch batch : batches) {
            if (batch.getId().equals(batchId)) {
                return batch;
            }
        }
        return null;
    }
}
