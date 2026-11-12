package com.presence.attendance.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.BuildConfig;

/**
 * The only logging entry point. A no-op in release builds, so nothing the app handles (tokens,
 * coordinates, emails) can reach logcat on a user's device.
 */
public final class Log {

    private Log() {}

    public static void w(@NonNull String tag, @NonNull String message, @Nullable Throwable error) {
        if (BuildConfig.DEBUG) {
            android.util.Log.w(tag, message, error);
        }
    }
}
