package com.presence.attendance.core.location;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import com.presence.attendance.core.Callback;

/** The device's position. */
public interface LocationSource {

    /** Whether precise (fine) location has been granted. Approximate-only is not enough. */
    boolean hasPrecisePermission();

    /**
     * One fresh high-accuracy reading. Fails with {@code LOCATION_PERMISSION_REQUIRED} or {@code
     * LOCATION_UNAVAILABLE}.
     */
    @MainThread
    void current(@NonNull Callback<LocationFix> callback);
}
