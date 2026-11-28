package com.presence.attendance.session.domain;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.Callback;
import java.util.List;

/** Sessions and their one-time tokens. Every write needs a connection and never queues. */
public interface SessionRepository {

    /** The campus geofence from {@code appConfig/campus}. Fails with CAMPUS_NOT_CONFIGURED. */
    @MainThread
    void loadCampus(@NonNull Callback<Geofence> callback);

    /**
     * The geofence saved for a room, by its name as the timetable writes it ("AL-301"). Fails with
     * ROOM_NOT_SAVED if nobody has saved it yet.
     */
    @MainThread
    void loadRoom(@NonNull String name, @NonNull Callback<Geofence> callback);

    /** Saves the geofence of the room the teacher is standing in, replacing any earlier one. */
    @MainThread
    void saveRoom(
            @NonNull User teacher,
            @NonNull String name,
            @NonNull Geofence geofence,
            @NonNull Callback<Geofence> callback);

    /** Starts a session that ends {@code durationMin} minutes from now. */
    @MainThread
    void startSession(
            @NonNull User teacher,
            @NonNull String courseId,
            int durationMin,
            @NonNull Geofence geofence,
            @NonNull Callback<Session> callback);

    /**
     * Ends a session. Ending early stamps the server time; closing one that has already run out
     * keeps its original end, because the rules never let an end time move later.
     */
    @MainThread
    void endSession(@NonNull String sessionId, @NonNull Callback<Session> callback);

    /**
     * Records how many people the teacher counted in the room, once the session is over. Never
     * queued: fails offline. Succeeds with the stored count.
     */
    @MainThread
    void setHeadcount(
            @NonNull String sessionId, int headcount, @NonNull Callback<Integer> callback);

    /** Fails with SESSION_NOT_FOUND. */
    @MainThread
    void loadSession(@NonNull String sessionId, @NonNull Callback<Session> callback);

    /** The teacher's sessions that are still ACTIVE on the server, any course. */
    @MainThread
    void loadActiveSessions(@NonNull String teacherId, @NonNull Callback<List<Session>> callback);

    /** The sessions of a course the signed-in student is enrolled in, newest first. */
    @MainThread
    void loadEnrolledSessions(@NonNull String courseId, @NonNull Callback<List<Session>> callback);

    /** A course's sessions, newest first. */
    @MainThread
    void loadCourseSessions(
            @NonNull String teacherId,
            @NonNull String courseId,
            @NonNull Callback<List<Session>> callback);

    /**
     * Writes one token. Succeeds only once the server has acknowledged it; offline it fails rather
     * than waiting.
     */
    @MainThread
    void writeToken(
            @NonNull String sessionId, @NonNull String token, @NonNull Callback<String> callback);
}
