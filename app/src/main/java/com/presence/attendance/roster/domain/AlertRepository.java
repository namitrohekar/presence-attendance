package com.presence.attendance.roster.domain;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.presence.attendance.core.Outcome;
import java.util.List;

/** Identity alerts: written by the account that was rejected, read by faculty. */
public interface AlertRepository {

    /**
     * Records the signed-in account's own rejected attempt. Best effort: the rejection has already
     * happened, so a failure here is logged and otherwise ignored.
     *
     * @param audience a faculty uid, or {@link IdentityAlert#AUDIENCE_ALL}
     * @param whenDone run on the main thread once the write has succeeded or failed; it never waits
     *     for a connection
     */
    @MainThread
    void report(
            @NonNull String uid,
            @NonNull String email,
            @NonNull IdentityAlert.Type type,
            @Nullable String enteredRollNo,
            @NonNull String audience,
            @NonNull Runnable whenDone);

    /**
     * Alerts addressed to this teacher or to all faculty, newest first. Listens only while
     * observed, so the screen updates live and costs nothing when closed.
     */
    @NonNull
    LiveData<Outcome<List<IdentityAlert>>> observe(@NonNull String teacherUid);

    /** Deletes alerts older than the retention period. Best effort. */
    @MainThread
    void purgeExpired(@NonNull String teacherUid);
}
