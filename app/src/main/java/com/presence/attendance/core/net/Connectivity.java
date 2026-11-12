package com.presence.attendance.core.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

/**
 * Whether the device has validated internet access (not merely a connected network). Listens only
 * while observed.
 */
public final class Connectivity extends LiveData<Boolean> {

    private final ConnectivityManager manager;

    private final ConnectivityManager.NetworkCallback callback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(
                        @NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                    postValue(isValidated(capabilities));
                }

                @Override
                public void onLost(@NonNull Network network) {
                    postValue(false);
                }
            };

    public Connectivity(@NonNull Context context) {
        manager = context.getSystemService(ConnectivityManager.class);
    }

    @Override
    protected void onActive() {
        // The callback only reports changes, so start from the current state.
        setValue(isValidated(manager.getNetworkCapabilities(manager.getActiveNetwork())));
        manager.registerDefaultNetworkCallback(callback);
    }

    @Override
    protected void onInactive() {
        manager.unregisterNetworkCallback(callback);
    }

    private static boolean isValidated(NetworkCapabilities capabilities) {
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
