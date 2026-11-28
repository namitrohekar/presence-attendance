package com.presence.attendance.attendance.data;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.AggregateQuerySnapshot;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.AttendanceRepository;
import com.presence.attendance.attendance.domain.CourseCount;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** {@link AttendanceRepository} backed by Cloud Firestore. */
public final class FirestoreAttendanceRepository implements AttendanceRepository {

    private static final String ATTENDANCE = "attendance";
    private static final String SESSIONS = "sessions";
    private static final String MEMBERSHIPS = "memberships";
    private static final String STATUS = "status";
    private static final List<String> PRESENT_STATUSES =
            Arrays.asList(
                    AttendanceRecord.Status.PRESENT.name(),
                    AttendanceRecord.Status.LATE_SYNC.name(),
                    AttendanceRecord.Status.MANUAL_PRESENT.name());
    private static final String DEVICES = "devices";
    private static final String CHANGES = "attendanceChanges";

    /** How long a LIVE mark may take before the app stops waiting for the server. */
    private static final long MARK_BUDGET_MS = 8_000;

    /** A background sync has no one waiting on a screen, so it may take longer. */
    private static final long SYNC_BUDGET_MS = 20_000;

    private final FirebaseFirestore firestore;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final String deviceId;

    /**
     * @param deviceId this phone's id, see {@link com.presence.attendance.core.DeviceId}
     */
    public FirestoreAttendanceRepository(
            @NonNull FirebaseFirestore firestore, @NonNull String deviceId) {
        this.firestore = firestore;
        this.deviceId = deviceId;
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
    public void markLive(
            @NonNull User student,
            @NonNull Session session,
            @NonNull String token,
            @NonNull LocationFix fix,
            @NonNull Callback<MarkResult> callback) {
        write(
                session.getId(),
                student.getUid(),
                AttendanceMappers.liveMark(student, session, token, fix, deviceId),
                MARK_BUDGET_MS,
                callback);
    }

    @Override
    public void markQueued(
            @NonNull Session session,
            @NonNull PendingMark mark,
            @NonNull Callback<MarkResult> callback) {
        write(
                session.getId(),
                mark.getStudentId(),
                AttendanceMappers.queuedMark(session, mark, deviceId),
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
            @NonNull AttendanceRecord.Status status,
            @NonNull String note,
            @NonNull Callback<Boolean> callback) {
        if (!hasRecord && status != AttendanceRecord.Status.MANUAL_PRESENT) {
            // No record already means absent; there is nothing to revoke.
            callback.onResult(Outcome.success(false));
            return;
        }
        final String recordId = sessionId + "_" + studentId;
        final DocumentReference ref = attendance().document(recordId);
        // A transaction, so offline it fails instead of being queued. An existing record is
        // read for its status and revision; a missing one cannot be read by a teacher, so the
        // roster's "no record" decides the create, and the rules refuse it if that was stale.
        firestore
                .runTransaction(transaction -> {
                    String from = AttendanceMappers.NO_RECORD;
                    long revision = 1;
                    if (hasRecord) {
                        final DocumentSnapshot before = transaction.get(ref);
                        from = AttendanceMappers.status(before);
                        revision = AttendanceMappers.revision(before) + 1;
                        transaction.update(
                                ref,
                                AttendanceMappers.correction(teacherId, status, note, revision));
                    } else {
                        transaction.set(
                                ref,
                                AttendanceMappers.manualPresent(
                                        teacherId, courseId, sessionId, studentId, note));
                    }
                    transaction.set(
                            firestore.collection(CHANGES).document(recordId + "_" + revision),
                            AttendanceMappers.change(
                                    recordId, revision, teacherId, studentId, from, status, note));
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

    @Override
    public void loadStudentRecordsForTeacher(
            @NonNull String teacherId,
            @NonNull String studentId,
            @NonNull String courseId,
            @NonNull Callback<List<AttendanceRecord>> callback) {
        // The teacher's own id is in the query because the rules list only records carrying it.
        // Equality only, so no composite index is needed.
        load(
                attendance()
                        .whereEqualTo(AttendanceMappers.TEACHER_ID, teacherId)
                        .whereEqualTo(AttendanceMappers.STUDENT_ID, studentId)
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

    /**
     * Creates the record and claims this phone for the session, in one transaction, and answers
     * within the budget either way. Nothing is written if the student already has a record, or if
     * another student has claimed this phone.
     */
    private void write(
            @NonNull String sessionId,
            @NonNull String studentId,
            @NonNull Map<String, Object> document,
            long budgetMs,
            @NonNull Callback<MarkResult> callback) {
        final DocumentReference ref = record(sessionId, studentId);
        final DocumentReference claim =
                firestore
                        .collection(SESSIONS)
                        .document(sessionId)
                        .collection(DEVICES)
                        .document(deviceId);
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
                    final DocumentSnapshot claimed = transaction.get(claim);
                    if (claimed.exists()
                            && !studentId.equals(AttendanceMappers.claimedBy(claimed))) {
                        // Read only to say why. The rules refuse it regardless.
                        return MarkResult.DEVICE_USED;
                    }
                    transaction.set(ref, document);
                    if (!claimed.exists()) {
                        transaction.set(claim, AttendanceMappers.deviceClaim(studentId));
                    }
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

    @Override
    public void countCourse(@NonNull String courseId, @NonNull Callback<CourseCount> callback) {
        final Task<AggregateQuerySnapshot> held =
                firestore
                        .collection(SESSIONS)
                        .whereEqualTo(AttendanceMappers.COURSE_ID, courseId)
                        .whereEqualTo(STATUS, "ENDED")
                        .count()
                        .get(AggregateSource.SERVER);
        final Task<AggregateQuerySnapshot> students =
                firestore
                        .collection(MEMBERSHIPS)
                        .whereEqualTo(AttendanceMappers.COURSE_ID, courseId)
                        .count()
                        .get(AggregateSource.SERVER);
        final Task<AggregateQuerySnapshot> present =
                attendance()
                        .whereEqualTo(AttendanceMappers.COURSE_ID, courseId)
                        .whereIn(STATUS, PRESENT_STATUSES)
                        .count()
                        .get(AggregateSource.SERVER);
        Tasks.whenAllSuccess(held, students, present)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        callback.onResult(
                                Outcome.failure(FirebaseErrors.toAppError(task.getException())));
                        return;
                    }
                    callback.onResult(
                            Outcome.success(
                                    new CourseCount(
                                            (int) held.getResult().getCount(),
                                            (int) students.getResult().getCount(),
                                            (int) present.getResult().getCount())));
                });
    }

    private CollectionReference attendance() {
        return firestore.collection(ATTENDANCE);
    }
}
