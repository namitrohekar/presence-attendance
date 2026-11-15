package com.presence.attendance.attendance.domain;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.location.LocationFix;
import com.presence.attendance.session.domain.Session;
import java.util.List;

/** Attendance records. */
public interface AttendanceRepository {

    /** What happened to a mark the server answered. */
    enum MarkResult {
        MARKED,
        /** A record for this student and session already existed; nothing was written. */
        ALREADY_MARKED
    }

    /**
     * A session's records for its teacher, newest first. Listens only while observed, so the live
     * roster updates without polling and costs nothing when the screen is closed.
     */
    @NonNull
    LiveData<Outcome<List<AttendanceRecord>>> observeSession(
            @NonNull String sessionId, @NonNull String teacherId);

    /** Whether the student already has a record for the session. */
    @MainThread
    void hasMarked(
            @NonNull String sessionId,
            @NonNull String studentId,
            @NonNull Callback<Boolean> callback);

    /**
     * Marks the student present now, with the token they scanned and where they are.
     *
     * <p>Never queued by the SDK: it runs as a transaction with a time budget. Fails with {@code
     * PERMISSION_DENIED} when the rules refuse it, and with {@code OFFLINE} or {@code TIMEOUT} when
     * the server could not be reached in time, in which case the outcome is unknown.
     */
    @MainThread
    void markLive(
            @NonNull User student,
            @NonNull Session session,
            @NonNull String token,
            @NonNull LocationFix fix,
            @NonNull Callback<MarkResult> callback);
}
