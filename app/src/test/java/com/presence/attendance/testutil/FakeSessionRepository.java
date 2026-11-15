package com.presence.attendance.testutil;

import androidx.annotation.NonNull;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.session.domain.Geofence;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.session.domain.SessionRepository;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link SessionRepository}. Most calls answer synchronously with what the test set;
 * token writes are held in {@link #pendingTokens} so the test decides when the server answers.
 */
public final class FakeSessionRepository implements SessionRepository {

    public Outcome<Geofence> campusOutcome = Outcome.failure(AppError.CAMPUS_NOT_CONFIGURED);
    public Outcome<Session> startOutcome = Outcome.failure(AppError.UNKNOWN);
    public Outcome<Session> endOutcome = Outcome.failure(AppError.UNKNOWN);
    public Outcome<Session> sessionOutcome = Outcome.failure(AppError.SESSION_NOT_FOUND);
    public Outcome<List<Session>> activeOutcome = Outcome.success(new ArrayList<>());
    public Outcome<List<Session>> courseSessionsOutcome = Outcome.success(new ArrayList<>());

    public final List<String> calls = new ArrayList<>();
    public final List<String> tokens = new ArrayList<>();
    public final List<Callback<String>> pendingTokens = new ArrayList<>();
    public Geofence lastGeofence;
    public int lastDurationMin;

    /** The server answers the oldest outstanding token write. */
    public void answerToken(boolean acknowledged) {
        answerToken(acknowledged ? null : AppError.OFFLINE);
    }

    public void answerToken(AppError error) {
        final String token = tokens.get(tokens.size() - pendingTokens.size());
        final Callback<String> callback = pendingTokens.remove(0);
        callback.onResult(error == null ? Outcome.success(token) : Outcome.failure(error));
    }

    @Override
    public void loadCampus(@NonNull Callback<Geofence> callback) {
        calls.add("loadCampus");
        callback.onResult(campusOutcome);
    }

    @Override
    public void startSession(
            @NonNull User teacher,
            @NonNull String courseId,
            int durationMin,
            @NonNull Geofence geofence,
            @NonNull Callback<Session> callback) {
        calls.add("startSession:" + courseId + ":" + durationMin);
        lastGeofence = geofence;
        lastDurationMin = durationMin;
        callback.onResult(startOutcome);
    }

    @Override
    public void endSession(@NonNull String sessionId, @NonNull Callback<Session> callback) {
        calls.add("endSession:" + sessionId);
        callback.onResult(endOutcome);
    }

    @Override
    public void loadSession(@NonNull String sessionId, @NonNull Callback<Session> callback) {
        calls.add("loadSession:" + sessionId);
        callback.onResult(sessionOutcome);
    }

    @Override
    public void loadActiveSessions(
            @NonNull String teacherId, @NonNull Callback<List<Session>> callback) {
        calls.add("loadActiveSessions:" + teacherId);
        callback.onResult(activeOutcome);
    }

    @Override
    public void loadCourseSessions(
            @NonNull String teacherId,
            @NonNull String courseId,
            @NonNull Callback<List<Session>> callback) {
        calls.add("loadCourseSessions:" + teacherId + ":" + courseId);
        callback.onResult(courseSessionsOutcome);
    }

    @Override
    public void writeToken(
            @NonNull String sessionId, @NonNull String token, @NonNull Callback<String> callback) {
        tokens.add(token);
        pendingTokens.add(callback);
    }
}
