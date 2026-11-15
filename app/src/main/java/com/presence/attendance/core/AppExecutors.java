package com.presence.attendance.core;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** The app's only threads besides the ones inside the Firebase and camera libraries. */
public final class AppExecutors {

    // One thread: disk work is rare and small, and a single queue keeps it ordered.
    private final Executor io = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** For file and database access. Never touches views. */
    @NonNull
    public Executor io() {
        return io;
    }

    @NonNull
    public Executor main() {
        return mainHandler::post;
    }
}
