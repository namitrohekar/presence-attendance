package com.presence.attendance.attendance.data;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.AttendanceRepository;
import com.presence.attendance.attendance.domain.PendingMark;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.data.FirebaseErrors;
import com.presence.attendance.core.data.QueryLiveData;
import com.presence.attendance.core.location.LocationFix;
import com.presence.attendance.session.domain.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** {@link AttendanceRepository} backed by Cloud Firestore. */
public final class FirestoreAttendanceRepository implements AttendanceRepository {

    private static final String ATTENDANCE = "attendance";

    /** How long a LIVE mark may take before the app stops waiting for the server. */
    private static final long MARK_BUDGET_MS = 8_000;

    /** A background sync has no one waiting on a screen, so it may take longer. */
    private static final long SYNC_BUDGET_MS = 20_000;

    private final FirebaseFirestore firestore;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public FirestoreAttendanceRepository(@NonNull FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @NonNull
    @Override
    public LiveData<Outcome<List<AttendanceRecord>>> observeSession(
            @NonNull String sessionId, @NonNull String teacherId) {
        // Both equality constraints are required by the rules; the order needs the composite
        // index in firestore.indexes.json.
        return new QueryLiveData<>(
                attendance()
                        .whereEqualTo(AttendanceMappers.SESSION_ID, sessionId)
                        .whereEqualTo(AttendanceMappers.TEACHER_ID, teacherId)
                        .orderBy(AttendanceMappers.MARKED_AT, Query.Direction.DESCENDING),
                AttendanceMappers::record);
    }

    @Override
    public void hasMarked(
            @NonNull String sessionId,
            @NonNull String studentId,
            @NonNull Callback<Boolean> callback) {
        // Server only: a cached "no" must not let a second scan go ahead.
        record(sessionId, studentId)
                .get(Source.SERVER)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onResult(Outcome.success(task.getResult().exists()));
                    } else {
                        callback.onResult(
                                Outcome.failure(FirebaseErrors.toAppError(task.getException())));
                    }
                });
    }

    @Override
    public void markLive(
            @NonNull User student,
            @NonNull Session session,
            @NonNull String token,
            @NonNull LocationFix fix,
            @NonNull Callback<MarkResult> callback) {
        write(
                record(session.getId(), student.getUid()),
                AttendanceMappers.liveMark(student, session, token, fix),
                MARK_BUDGET_MS,
                callback);
    }

    @Override
    public void markQueued(
            @NonNull Session session,
            @NonNull PendingMark mark,
            @NonNull Callback<MarkResult> callback) {
        write(
                record(session.getId(), mark.getStudentId()),
                AttendanceMappers.queuedMark(session, mark),
                SYNC_BUDGET_MS,
                callback);
    }

    @Override
    public void correct(
            @NonNull String teacherId,
            @NonNull String courseId,
            @NonNull String sessionId,
            @NonNull String studentId,
            boolean hasRecord,
            boolean present,
            @NonNull String note,
            @NonNull Callback<Boolean> callback) {
        if (!hasRecord && !present) {
            callback.onResult(Outcome.success(false));
            return;
        }
        final DocumentReference ref = record(sessionId, studentId);
        // A transaction with no read: offline it fails instead of being queued. Updating a
        // record that is gone fails, and creating one that exists is refused by the rules,
        // because a full overwrite touches fields a teacher may not change.
        firestore
                .runTransaction(transaction -> {
                    if (hasRecord) {
                        transaction.update(
                                ref, AttendanceMappers.correction(teacherId, present, note));
                    } else {
                        transaction.set(
                                ref,
                                AttendanceMappers.manualPresent(
                                        teacherId, courseId, sessionId, studentId, note));
                    }
                    return true;
                })
                .addOnCompleteListener(task ->
                        callback.onResult(
                                task.isSuccessful()
                                        ? Outcome.success(task.getResult())
                                        : Outcome.failure(
                                                FirebaseErrors.toAppError(task.getException()))));
    }

    @Override
    public void loadStudentRecords(
            @NonNull String studentId,
            @NonNull String courseId,
            @NonNull Callback<List<AttendanceRecord>> callback) {
        // The rules allow a student to list only records that carry their own id.
        load(
                attendance()
                        .whereEqualTo(AttendanceMappers.STUDENT_ID, studentId)
                        .whereEqualTo(AttendanceMappers.COURSE_ID, courseId),
                callback);
    }

    @Override
    public void loadCourseRecords(
            @NonNull String teacherId,
            @NonNull String courseId,
            @NonNull Callback<List<AttendanceRecord>> callback) {
        // The rules allow a teacher to list only records that carry their own id.
        load(
                attendance()
                        .whereEqualTo(AttendanceMappers.TEACHER_ID, teacherId)
                        .whereEqualTo(AttendanceMappers.COURSE_ID, courseId),
                callback);
    }

    private static void load(
            @NonNull Query query, @NonNull Callback<List<AttendanceRecord>> callback) {
        query.get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        callback.onResult(
                                Outcome.failure(FirebaseErrors.toAppError(task.getException())));
                        return;
                    }
                    final List<AttendanceRecord> records = new ArrayList<>();
                    for (DocumentSnapshot document : task.getResult().getDocuments()) {
                        final AttendanceRecord record = AttendanceMappers.record(document);
                        if (record != null) {
                            records.add(record);
                        }
                    }
                    callback.onResult(Outcome.success(records));
                });
    }

    /** Creates the record unless one exists, and answers within the budget either way. */
    private void write(
            @NonNull DocumentReference ref,
            @NonNull Map<String, Object> document,
            long budgetMs,
            @NonNull Callback<MarkResult> callback) {
        // Whichever comes first, the server's answer or the end of the budget, is delivered;
        // the other is ignored.
        final boolean[] answered = {false};
        final Runnable timeout =
                () -> {
                    if (!answered[0]) {
                        answered[0] = true;
                        callback.onResult(Outcome.failure(AppError.TIMEOUT));
                    }
                };
        mainHandler.postDelayed(timeout, budgetMs);
        // A transaction, not a plain set: offline it fails instead of being silently queued.
        firestore
                .runTransaction(transaction -> {
                    if (transaction.get(ref).exists()) {
                        return MarkResult.ALREADY_MARKED;
                    }
                    transaction.set(ref, document);
                    return MarkResult.MARKED;
                })
                .addOnCompleteListener(task -> {
                    if (answered[0]) {
                        return;
                    }
                    answered[0] = true;
                    mainHandler.removeCallbacks(timeout);
                    callback.onResult(
                            task.isSuccessful()
                                    ? Outcome.success(task.getResult())
                                    : Outcome.failure(
                                            FirebaseErrors.toAppError(task.getException())));
                });
    }

    private DocumentReference record(@NonNull String sessionId, @NonNull String studentId) {
        return attendance().document(sessionId + "_" + studentId);
    }

    private CollectionReference attendance() {
        return firestore.collection(ATTENDANCE);
    }
}
