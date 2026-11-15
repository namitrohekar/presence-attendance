package com.presence.attendance.session.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.data.FirebaseErrors;
import com.presence.attendance.session.domain.Geofence;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.session.domain.SessionRepository;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** {@link SessionRepository} backed by Cloud Firestore. */
public final class FirestoreSessionRepository implements SessionRepository {

    private static final String SESSIONS = "sessions";
    private static final String TOKENS = "tokens";
    private static final String CAMPUS = "appConfig/campus";
    private static final int HISTORY_PAGE = 50;

    private final FirebaseFirestore firestore;

    public FirestoreSessionRepository(@NonNull FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void loadCampus(@NonNull Callback<Geofence> callback) {
        firestore
                .document(CAMPUS)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        fail(callback, task.getException());
                        return;
                    }
                    final Geofence campus = SessionMappers.campus(task.getResult());
                    callback.onResult(
                            campus != null
                                    ? Outcome.success(campus)
                                    : Outcome.failure(AppError.CAMPUS_NOT_CONFIGURED));
                });
    }

    @Override
    public void startSession(
            @NonNull User teacher,
            @NonNull String courseId,
            int durationMin,
            @NonNull Geofence geofence,
            @NonNull Callback<Session> callback) {
        final DocumentReference ref = sessions().document();
        final long now = System.currentTimeMillis();
        // The end is chosen on the device; the rules accept it within a minute of the server's
        // own clock and stamp the start themselves.
        final long endsAt = now + TimeUnit.MINUTES.toMillis(durationMin);
        final Task<Void> write =
                firestore.runTransaction(transaction -> {
                    transaction.set(
                            ref,
                            SessionMappers.newSession(
                                    courseId, teacher.getUid(), new Date(endsAt), geofence));
                    return null;
                });
        write.addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onResult(
                        Outcome.success(
                                new Session(
                                        ref.getId(),
                                        courseId,
                                        teacher.getUid(),
                                        Session.Status.ACTIVE,
                                        now,
                                        endsAt,
                                        geofence)));
            } else {
                fail(callback, task.getException());
            }
        });
    }

    @Override
    public void endSession(@NonNull String sessionId, @NonNull Callback<Session> callback) {
        // Which form the rules accept depends on the server's clock, not ours. Try the one our
        // clock suggests, and the other one if that is refused.
        endSession(sessionId, true, callback);
    }

    private void endSession(
            @NonNull String sessionId, boolean firstAttempt, @NonNull Callback<Session> callback) {
        final DocumentReference ref = sessions().document(sessionId);
        firestore
                .runTransaction(transaction -> {
                    final Session session = SessionMappers.session(transaction.get(ref));
                    if (session == null || session.getStatus() == Session.Status.ENDED) {
                        return session;
                    }
                    final boolean stillRunning = session.isLive(System.currentTimeMillis());
                    // First attempt follows our clock; the second assumes it was wrong.
                    if (stillRunning == firstAttempt) {
                        transaction.update(
                                ref,
                                SessionMappers.STATUS,
                                Session.Status.ENDED.name(),
                                SessionMappers.ENDS_AT,
                                FieldValue.serverTimestamp());
                    } else {
                        transaction.update(ref, SessionMappers.STATUS, Session.Status.ENDED.name());
                    }
                    return new Session(
                            session.getId(),
                            session.getCourseId(),
                            session.getTeacherId(),
                            Session.Status.ENDED,
                            session.getStartedAtMillis(),
                            Math.min(session.getEndsAtMillis(), System.currentTimeMillis()),
                            session.getGeofence());
                })
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        final Session ended = task.getResult();
                        callback.onResult(
                                ended != null
                                        ? Outcome.success(ended)
                                        : Outcome.failure(AppError.SESSION_NOT_FOUND));
                        return;
                    }
                    final AppError error = FirebaseErrors.toAppError(task.getException());
                    if (error == AppError.PERMISSION_DENIED && firstAttempt) {
                        endSession(sessionId, false, callback);
                    } else {
                        callback.onResult(Outcome.failure(error));
                    }
                });
    }

    @Override
    public void loadSession(@NonNull String sessionId, @NonNull Callback<Session> callback) {
        sessions()
                .document(sessionId)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        fail(callback, task.getException());
                        return;
                    }
                    final Session session = SessionMappers.session(task.getResult());
                    callback.onResult(
                            session != null
                                    ? Outcome.success(session)
                                    : Outcome.failure(AppError.SESSION_NOT_FOUND));
                });
    }

    @Override
    public void loadActiveSessions(
            @NonNull String teacherId, @NonNull Callback<List<Session>> callback) {
        deliver(
                sessions()
                        .whereEqualTo(SessionMappers.TEACHER_ID, teacherId)
                        .whereEqualTo(SessionMappers.STATUS, Session.Status.ACTIVE.name()),
                callback);
    }

    @Override
    public void loadCourseSessions(
            @NonNull String teacherId,
            @NonNull String courseId,
            @NonNull Callback<List<Session>> callback) {
        // The rules only allow a teacher to list sessions that carry their own id.
        deliver(
                sessions()
                        .whereEqualTo(SessionMappers.TEACHER_ID, teacherId)
                        .whereEqualTo(SessionMappers.COURSE_ID, courseId)
                        .orderBy(SessionMappers.STARTED_AT, Query.Direction.DESCENDING)
                        .limit(HISTORY_PAGE),
                callback);
    }

    @Override
    public void writeToken(
            @NonNull String sessionId, @NonNull String token, @NonNull Callback<String> callback) {
        final DocumentReference ref =
                sessions().document(sessionId).collection(TOKENS).document(token);
        // A transaction, not a plain set: offline it fails instead of being queued, so a token
        // is never written late, long after anyone could have seen it.
        final Task<Void> write =
                firestore.runTransaction(transaction -> {
                    transaction.set(ref, SessionMappers.newToken());
                    return null;
                });
        write.addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onResult(Outcome.success(token));
            } else {
                fail(callback, task.getException());
            }
        });
    }

    private static void deliver(@NonNull Query query, @NonNull Callback<List<Session>> callback) {
        // Server first: a cached list could hide a session ended from another device.
        query.get(Source.DEFAULT)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        fail(callback, task.getException());
                        return;
                    }
                    final List<Session> sessions = new ArrayList<>();
                    for (DocumentSnapshot document : task.getResult().getDocuments()) {
                        final Session session = SessionMappers.session(document);
                        if (session != null) {
                            sessions.add(session);
                        }
                    }
                    callback.onResult(Outcome.success(sessions));
                });
    }

    private static <T> void fail(@NonNull Callback<T> callback, @Nullable Exception error) {
        callback.onResult(Outcome.failure(FirebaseErrors.toAppError(error)));
    }

    private CollectionReference sessions() {
        return firestore.collection(SESSIONS);
    }
}
