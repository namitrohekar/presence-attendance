package com.presence.attendance.core;

import android.content.Context;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.presence.attendance.BuildConfig;
import com.presence.attendance.attendance.data.FirestoreAttendanceRepository;
import com.presence.attendance.attendance.domain.AttendanceRepository;
import com.presence.attendance.auth.data.FirebaseAuthRepository;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.core.location.FusedLocationSource;
import com.presence.attendance.core.location.LocationSource;
import com.presence.attendance.core.net.Connectivity;
import com.presence.attendance.course.data.FirestoreCourseRepository;
import com.presence.attendance.course.domain.CourseRepository;
import com.presence.attendance.roster.data.FirestoreAlertRepository;
import com.presence.attendance.roster.data.FirestoreRosterRepository;
import com.presence.attendance.roster.domain.AlertRepository;
import com.presence.attendance.roster.domain.RosterRepository;
import com.presence.attendance.session.data.FirestoreSessionRepository;
import com.presence.attendance.session.domain.SessionRepository;
import java.security.SecureRandom;

/**
 * Manual dependency injection: the single place where implementations are chosen. Built once by the
 * Application; everything else depends on the interfaces it hands out.
 *
 * <p>Repositories are created on first use because Firebase cannot initialise in a build without
 * {@code google-services.json}. Such a build never gets past the sign-in screen.
 */
public final class AppContainer {

    private static final int AUTH_EMULATOR_PORT = 9099;
    private static final int FIRESTORE_EMULATOR_PORT = 8080;

    private final Connectivity connectivity;
    private final Context appContext;
    @Nullable private AuthRepository auth;
    @Nullable private CourseRepository courses;
    @Nullable private RosterRepository roster;
    @Nullable private AlertRepository alerts;
    @Nullable private SessionRepository sessions;
    @Nullable private AttendanceRepository attendance;
    @Nullable private LocationSource location;
    private final AppExecutors executors = new AppExecutors();

    public AppContainer(@NonNull Context context) {
        appContext = context.getApplicationContext();
        connectivity = new Connectivity(context.getApplicationContext());
        if (BuildConfig.FIREBASE_CONFIGURED && !BuildConfig.FIREBASE_EMULATOR_HOST.isEmpty()) {
            // Must happen before the first use of either instance.
            final String host = BuildConfig.FIREBASE_EMULATOR_HOST;
            FirebaseAuth.getInstance().useEmulator(host, AUTH_EMULATOR_PORT);
            FirebaseFirestore.getInstance().useEmulator(host, FIRESTORE_EMULATOR_PORT);
        }
    }

    @MainThread
    @NonNull
    public AuthRepository auth() {
        if (auth == null) {
            auth =
                    new FirebaseAuthRepository(
                            FirebaseAuth.getInstance(), FirebaseFirestore.getInstance(), alerts());
        }
        return auth;
    }

    @MainThread
    @NonNull
    public CourseRepository courses() {
        if (courses == null) {
            courses =
                    new FirestoreCourseRepository(
                            FirebaseFirestore.getInstance(), new SecureRandom());
        }
        return courses;
    }

    @MainThread
    @NonNull
    public RosterRepository roster() {
        if (roster == null) {
            roster = new FirestoreRosterRepository(FirebaseFirestore.getInstance());
        }
        return roster;
    }

    @MainThread
    @NonNull
    public AlertRepository alerts() {
        if (alerts == null) {
            alerts = new FirestoreAlertRepository(FirebaseFirestore.getInstance());
        }
        return alerts;
    }

    @MainThread
    @NonNull
    public SessionRepository sessions() {
        if (sessions == null) {
            sessions = new FirestoreSessionRepository(FirebaseFirestore.getInstance());
        }
        return sessions;
    }

    @MainThread
    @NonNull
    public AttendanceRepository attendance() {
        if (attendance == null) {
            attendance = new FirestoreAttendanceRepository(FirebaseFirestore.getInstance());
        }
        return attendance;
    }

    @MainThread
    @NonNull
    public LocationSource location() {
        if (location == null) {
            location = new FusedLocationSource(appContext);
        }
        return location;
    }

    @NonNull
    public AppExecutors executors() {
        return executors;
    }

    /** True while the device has validated internet access. */
    @NonNull
    public LiveData<Boolean> online() {
        return connectivity;
    }
}
