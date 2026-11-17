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

    /**
     * Sends a scan that was saved while offline. The server records it as a late sync if the code
     * was current at the saved scan time and the session's grace period has not run out.
     */
    @MainThread
    void markQueued(
            @NonNull Session session,
            @NonNull PendingMark mark,
            @NonNull Callback<MarkResult> callback);

    /**
     * A teacher's correction for one student in one of their sessions, with the reason.
     *
     * <p>{@code hasRecord} comes from the live roster: a teacher cannot read a record that does not
     * exist, so the write is chosen without reading. If the roster was stale the rules refuse the
     * write and nothing changes. Marking absent a student with no record writes nothing, because no
     * record already means absent; the result is then false.
     */
    @MainThread
    void correct(
            @NonNull String teacherId,
            @NonNull String courseId,
            @NonNull String sessionId,
            @NonNull String studentId,
            boolean hasRecord,
            boolean present,
            @NonNull String note,
            @NonNull Callback<Boolean> callback);

    /** The student's own records in one course. */
    @MainThread
    void loadStudentRecords(
            @NonNull String studentId,
            @NonNull String courseId,
            @NonNull Callback<List<AttendanceRecord>> callback);

    /** Every record in one of the teacher's courses. */
    @MainThread
    void loadCourseRecords(
            @NonNull String teacherId,
            @NonNull String courseId,
            @NonNull Callback<List<AttendanceRecord>> callback);
}
