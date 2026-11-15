package com.presence.attendance.core.location;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationCompat;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;

/** {@link LocationSource} backed by Google Play services' fused location provider. */
public final class FusedLocationSource implements LocationSource {

    private static final long MAX_AGE_MS = 15_000;
    private static final long TIMEOUT_MS = 10_000;

    private final Context context;
    private final FusedLocationProviderClient client;

    public FusedLocationSource(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.client = LocationServices.getFusedLocationProviderClient(this.context);
    }

    @Override
    public boolean hasPrecisePermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // The permission is checked on the line above the call; lint cannot see through the helper.
    @SuppressLint("MissingPermission")
    @Override
    public void current(@NonNull Callback<LocationFix> callback) {
        if (!hasPrecisePermission()) {
            callback.onResult(Outcome.failure(AppError.LOCATION_PERMISSION_REQUIRED));
            return;
        }
        final CurrentLocationRequest request =
                new CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setMaxUpdateAgeMillis(MAX_AGE_MS)
                        .setDurationMillis(TIMEOUT_MS)
                        .build();
        client.getCurrentLocation(request, null)
                .addOnCompleteListener(task -> {
                    final Location location = task.isSuccessful() ? task.getResult() : null;
                    if (location == null || !location.hasAccuracy()) {
                        callback.onResult(Outcome.failure(AppError.LOCATION_UNAVAILABLE));
                        return;
                    }
                    callback.onResult(
                            Outcome.success(
                                    new LocationFix(
                                            location.getLatitude(),
                                            location.getLongitude(),
                                            location.getAccuracy(),
                                            LocationCompat.isMock(location),
                                            location.getElapsedRealtimeNanos())));
                });
    }
}
