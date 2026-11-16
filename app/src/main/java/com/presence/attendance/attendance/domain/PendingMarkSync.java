package com.presence.attendance.attendance.domain;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import com.presence.attendance.attendance.domain.AttendanceRepository.MarkResult;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Constants;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.session.domain.Session;
import java.util.concurrent.TimeUnit;

/**
 * Sends a student's saved scans. Each one ends in exactly one state: synced, expired, rejected, or
 * still pending when the server could not be reached.
 *
 * <p>Running it twice is harmless: the record's id is fixed by student and session, so a scan that
 * already reached the server is reported as marked, not written again. The rules have the final say
 * on every scan; the checks here only name the reason.
 */
public final class PendingMarkSync {

    /** The server, as blocking calls. */
    public interface Remote {
        @NonNull
        Outcome<Session> loadSession(@NonNull String sessionId);

        @NonNull
        Outcome<MarkResult> markQueued(@NonNull Session session, @NonNull PendingMark mark);
    }

    private static final long GRACE_MS = TimeUnit.MINUTES.toMillis(Constants.QUEUE_GRACE_MIN);

    private final PendingMarkStore store;
    private final Remote remote;

    public PendingMarkSync(@NonNull PendingMarkStore store, @NonNull Remote remote) {
        this.store = store;
        this.remote = remote;
    }

    /** Returns true if at least one scan is still pending and worth another attempt later. */
    @WorkerThread
    public boolean run(@NonNull String studentId, long nowMillis) {
        boolean retry = false;
        for (PendingMark mark : store.pending(studentId)) {
            retry |= !send(mark, nowMillis);
        }
        return retry;
    }

    /** Returns false if the scan is still pending. */
    private boolean send(@NonNull PendingMark mark, long nowMillis) {
        final Outcome<Session> loaded = remote.loadSession(mark.getSessionId());
        if (!loaded.isSuccess()) {
            // Only members can read a session, so a denial means not enrolled.
            return loaded.getError() == AppError.PERMISSION_DENIED
                    ? reject(mark, AppError.NOT_ENROLLED)
                    : settleFailure(mark, loaded.getError());
        }
        final Session session = loaded.getValue();
        if (mark.getScannedAtMillis() >= session.getEndsAtMillis()) {
            return reject(mark, AppError.SESSION_ENDED);
        }
        if (nowMillis >= session.getEndsAtMillis() + GRACE_MS) {
            store.resolve(mark, PendingMark.State.EXPIRED, null);
            return true;
        }
        if (!session.getGeofence().contains(mark.getLat(), mark.getLng(), mark.getAccuracyM())) {
            return reject(mark, AppError.OUTSIDE_AREA);
        }
        final Outcome<MarkResult> sent = remote.markQueued(session, mark);
        if (sent.isSuccess()) {
            store.resolve(mark, PendingMark.State.SYNCED, null);
            return true;
        }
        // Every check the app can make has passed, so the code was not current when scanned.
        return sent.getError() == AppError.PERMISSION_DENIED
                ? reject(mark, AppError.QR_EXPIRED)
                : settleFailure(mark, sent.getError());
    }

    /** A failure that is not a refusal: gone for good, or simply not reachable yet. */
    private boolean settleFailure(@NonNull PendingMark mark, @NonNull AppError error) {
        if (error == AppError.SESSION_NOT_FOUND) {
            return reject(mark, error);
        }
        return false;
    }

    private boolean reject(@NonNull PendingMark mark, @NonNull AppError reason) {
        store.resolve(mark, PendingMark.State.REJECTED, reason);
        return true;
    }
}
