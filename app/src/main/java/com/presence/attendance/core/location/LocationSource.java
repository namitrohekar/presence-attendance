package com.presence.attendance.core.location;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import com.presence.attendance.core.Callback;
import java.util.function.Consumer;

/** The device's position. */
public interface LocationSource {

    /** Whether precise (fine) location has been granted. Approximate-only is not enough. */
    boolean hasPrecisePermission();

    /** Whether location is switched on in system settings. */
    boolean isLocationEnabled();

    /**
     * One fresh high-accuracy reading. Fails with {@code LOCATION_PERMISSION_REQUIRED} or {@code
     * LOCATION_UNAVAILABLE}.
     */
    @MainThread
    void current(@NonNull Callback<LocationFix> callback);

    /**
     * Starts high-accuracy updates, delivered on the main thread. Does nothing without precise
     * permission.
     *
     * @return a handle that stops the updates; the caller must run it
     */
    @MainThread
    @NonNull
    Runnable observe(@NonNull Consumer<LocationFix> listener);
}
