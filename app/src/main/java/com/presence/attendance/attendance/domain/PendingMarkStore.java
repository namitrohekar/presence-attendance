package com.presence.attendance.attendance.domain;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import com.presence.attendance.core.AppError;
import java.util.List;

/** The on-device queue of scans waiting to be sent. Survives the app being closed. */
public interface PendingMarkStore {

    /**
     * Saves a scan, replacing an earlier one for the same student and session. {@code saved} runs
     * on the main thread once it is on disk.
     */
    @MainThread
    void add(@NonNull PendingMark mark, @NonNull Runnable saved);

    /** Removes a scan that is no longer waiting. A pending one stays. */
    @MainThread
    void dismiss(@NonNull PendingMark mark);

    /** The student's saved scans, newest first. */
    @NonNull
    LiveData<List<PendingMark>> observe(@NonNull String studentId);

    /** The student's scans still waiting to be sent. */
    @WorkerThread
    @NonNull
    List<PendingMark> pending(@NonNull String studentId);

    /**
     * Records what became of a pending scan, and forgets its code and coordinates. Does nothing if
     * the student has scanned again since.
     */
    @WorkerThread
    void resolve(
            @NonNull PendingMark mark, @NonNull PendingMark.State state, @Nullable AppError reason);
}
