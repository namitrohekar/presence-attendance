package com.presence.attendance.core;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

/** Receives the outcome of an asynchronous repository call, always on the main thread. */
@FunctionalInterface
public interface Callback<T> {

    @MainThread
    void onResult(@NonNull Outcome<T> outcome);
}
