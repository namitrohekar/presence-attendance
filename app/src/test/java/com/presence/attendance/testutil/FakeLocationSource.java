package com.presence.attendance.testutil;

import androidx.annotation.NonNull;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.location.LocationFix;
import com.presence.attendance.core.location.LocationSource;

/** In-memory {@link LocationSource}. Answers synchronously with whatever the test set. */
public final class FakeLocationSource implements LocationSource {

    public boolean permission = true;
    public Outcome<LocationFix> fixOutcome = Outcome.failure(AppError.LOCATION_UNAVAILABLE);
    public int requests;

    @Override
    public boolean hasPrecisePermission() {
        return permission;
    }

    @Override
    public void current(@NonNull Callback<LocationFix> callback) {
        requests++;
        callback.onResult(fixOutcome);
    }
}
