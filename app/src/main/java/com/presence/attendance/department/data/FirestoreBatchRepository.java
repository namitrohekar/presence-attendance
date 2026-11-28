package com.presence.attendance.department.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Log;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.data.FirebaseErrors;
import com.presence.attendance.department.domain.Batch;
import com.presence.attendance.department.domain.BatchRepository;
import com.presence.attendance.department.domain.Timetable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** {@link BatchRepository} backed by Cloud Firestore; the rules let only the HOD write. */
public final class FirestoreBatchRepository implements BatchRepository {

    private static final String TAG = "BatchRepository";
    private static final String BATCHES = "batches";
    private static final String TIMETABLES = "timetables";

    private final FirebaseFirestore firestore;

    public FirestoreBatchRepository(@NonNull FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void loadBatches(@NonNull Callback<List<Batch>> callback) {
        batches()
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        fail(callback, task.getException());
                        return;
                    }
                    final List<Batch> batches = new ArrayList<>();
                    for (DocumentSnapshot document : task.getResult().getDocuments()) {
                        final Batch batch = DepartmentMappers.batch(document);
                        if (batch == null) {
                            Log.w(TAG, "Skipping a malformed batch", null);
                        } else {
                            batches.add(batch);
                        }
                    }
                    batches.sort(
                            Comparator.comparingInt(Batch::getStartYear)
                                    .reversed()
                                    .thenComparing(Batch::getProgramme));
                    callback.onResult(Outcome.success(batches));
                });
    }

    @Override
    public void loadBatch(@NonNull String batchId, @NonNull Callback<Batch> callback) {
        batches()
                .document(batchId)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        fail(callback, task.getException());
                        return;
                    }
                    final Batch batch = DepartmentMappers.batch(task.getResult());
                    callback.onResult(
                            batch != null
                                    ? Outcome.success(batch)
                                    : Outcome.failure(AppError.BATCH_NOT_FOUND));
                });
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
        final DocumentReference ref = batches().document(Batch.idOf(programme, startYear));
        firestore
                .runTransaction(transaction -> {
                    // Read first so an existing batch is reported, never overwritten.
                    if (transaction.get(ref).exists()) {
                        return CreateOutcome.EXISTS;
                    }
                    transaction.set(
                            ref,
                            DepartmentMappers.newBatch(
                                    programme, startYear, years, intake, labGroups, hod.getUid()));
                    return CreateOutcome.CREATED;
                })
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onResult(Outcome.success(task.getResult()));
                    } else {
                        fail(callback, task.getException());
                    }
                });
    }

    @Override
    public void updateBatch(
            @NonNull String batchId,
            int years,
            int semester,
            int intake,
            int labGroups,
            @NonNull Callback<UpdateOutcome> callback) {
        final DocumentReference ref = batches().document(batchId);
        firestore
                .runTransaction(transaction -> {
                    if (!transaction.get(ref).exists()) {
                        return UpdateOutcome.NOT_FOUND;
                    }
                    transaction.update(
                            ref, DepartmentMappers.batchUpdate(years, semester, intake, labGroups));
                    return UpdateOutcome.DONE;
                })
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onResult(Outcome.success(task.getResult()));
                    } else {
                        fail(callback, task.getException());
                    }
                });
    }

    private static <T> void fail(@NonNull Callback<T> callback, @Nullable Exception error) {
        callback.onResult(Outcome.failure(FirebaseErrors.toAppError(error)));
    }

    @Override
    public void loadTimetables(
            @NonNull List<String> ids, @NonNull Callback<List<Timetable>> callback) {
        final List<Task<DocumentSnapshot>> reads = new ArrayList<>();
        for (String id : ids) {
            reads.add(firestore.collection(TIMETABLES).document(id).get());
        }
        Tasks.whenAllSuccess(reads)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        fail(callback, task.getException());
                        return;
                    }
                    final List<Timetable> timetables = new ArrayList<>();
                    for (Object document : task.getResult()) {
                        final Timetable timetable =
                                DepartmentMappers.timetable((DocumentSnapshot) document);
                        if (timetable != null) {
                            timetables.add(timetable);
                        }
                    }
                    callback.onResult(Outcome.success(timetables));
                });
    }

    @Override
    public void saveTimetable(
            @NonNull User hod,
            @NonNull Timetable timetable,
            @NonNull Callback<Timetable> callback) {
        firestore
                .collection(TIMETABLES)
                .document(timetable.getId())
                .set(DepartmentMappers.newTimetable(timetable, hod.getUid()))
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onResult(Outcome.success(timetable));
                    } else {
                        fail(callback, task.getException());
                    }
                });
    }

    private CollectionReference batches() {
        return firestore.collection(BATCHES);
    }
}
