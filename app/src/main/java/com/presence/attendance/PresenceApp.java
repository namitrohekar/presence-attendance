package com.presence.attendance;

import android.app.Application;
import android.os.StrictMode;
import androidx.annotation.NonNull;
import com.presence.attendance.core.AppContainer;

public final class PresenceApp extends Application {

    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            enableStrictMode();
        }
        container = new AppContainer(this);
    }

    @NonNull
    public AppContainer getContainer() {
        return container;
    }

    private static void enableStrictMode() {
        StrictMode.setThreadPolicy(
                new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
    }
}
