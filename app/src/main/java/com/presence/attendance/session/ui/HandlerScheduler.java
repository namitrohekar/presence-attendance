package com.presence.attendance.session.ui;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.presence.attendance.session.domain.TokenRotation;

/** Runs a ViewModel's timers on the main thread. */
public final class HandlerScheduler implements TokenRotation.Scheduler {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @NonNull
    @Override
    public Runnable schedule(@NonNull Runnable task, long delayMs) {
        handler.postDelayed(task, delayMs);
        return () -> handler.removeCallbacks(task);
    }
}
