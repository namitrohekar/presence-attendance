package com.presence.attendance.scan.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.attendance.domain.AttendanceRepository;
import com.presence.attendance.attendance.domain.AttendanceRepository.MarkResult;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Constants;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.location.LocationFix;
import com.presence.attendance.session.domain.QrPayload;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.session.domain.SessionRepository;
import com.presence.attendance.session.domain.TokenRotation;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * What happens after the camera sees a code. State machine: SCANNING -> CHECKING -> DONE.
 *
 * <p>The security rules answer a bad mark with a bare denial, so every check the app can make is
 * made here first, in order, to tell the student exactly what is wrong: already marked, not
 * enrolled, session over, no usable location, mock location, outside the area. Only a mark that
 * passes all of them is sent, and a denial then can only mean the code has expired.
 */
public final class ScanViewModel extends ViewModel {

    /** A fix older than this is not trusted to be where the student is now. */
    static final long FIX_MAX_AGE_MS = 15_000;

    /** How long to wait for a usable fix once a code has been scanned. */
    static final long FIX_WAIT_MS = 6_000;

    public enum Stage {
        SCANNING,
        CHECKING,
        DONE
    }

    /** Immutable screen state. In DONE, {@code rejection} is null exactly when marked. */
    public static final class State {
        @NonNull public final Stage stage;
        public final boolean locationReady;
        @Nullable public final AppError rejection;

        State(@NonNull Stage stage, boolean locationReady, @Nullable AppError rejection) {
            this.stage = stage;
            this.locationReady = locationReady;
            this.rejection = rejection;
        }
    }

    /** Continues a scan once a usable fix is available. */
    private interface FixReceiver {
        void accept(@NonNull LocationFix fix);
    }

    private final AuthRepository auth;
    private final SessionRepository sessions;
    private final AttendanceRepository attendance;
    private final TokenRotation.Scheduler scheduler;
    private final LongSupplier elapsedNanos;
    private final LongSupplier wallClockMs;
    private final MutableLiveData<State> state =
            new MutableLiveData<>(new State(Stage.SCANNING, false, null));

    // Kept out of the state: coordinates are never rendered or logged.
    @Nullable private LocationFix latestFix;
    @Nullable private FixReceiver waitingForFix;
    @Nullable private Runnable cancelFixTimeout;

    public ScanViewModel(
            @NonNull AuthRepository auth,
            @NonNull SessionRepository sessions,
            @NonNull AttendanceRepository attendance,
            @NonNull TokenRotation.Scheduler scheduler,
            @NonNull LongSupplier elapsedNanos,
            @NonNull LongSupplier wallClockMs) {
        this.auth = auth;
        this.sessions = sessions;
        this.attendance = attendance;
        this.scheduler = scheduler;
        this.elapsedNanos = elapsedNanos;
        this.wallClockMs = wallClockMs;
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** A location update arrived. Updates start as soon as the scanner opens. */
    public void onFix(@NonNull LocationFix fix) {
        latestFix = fix;
        final State s = current();
        final boolean ready = isUsable(fix);
        if (s.stage != Stage.DONE && s.locationReady != ready) {
            state.setValue(new State(s.stage, ready, null));
        }
        final FixReceiver receiver = waitingForFix;
        if (ready && receiver != null) {
            stopWaitingForFix();
            receiver.accept(fix);
        }
    }

    /**
     * The camera decoded some text. Anything that is not exactly an attendance code is ignored and
     * scanning continues; the first valid one stops it.
     */
    public void onQrText(@Nullable String text) {
        final QrPayload payload = QrPayload.parse(text);
        if (payload == null || current().stage != Stage.SCANNING) {
            return;
        }
        state.setValue(new State(Stage.CHECKING, current().locationReady, null));
        auth.loadUser(user -> {
            if (user.isSuccess()) {
                checkNotMarked(user.getValue(), payload);
            } else {
                reject(user.getError());
            }
        });
    }

    private void checkNotMarked(@NonNull User student, @NonNull QrPayload payload) {
        attendance.hasMarked(
                payload.getSessionId(),
                student.getUid(),
                marked -> {
                    if (!marked.isSuccess()) {
                        reject(marked.getError());
                    } else if (marked.getValue()) {
                        reject(AppError.ALREADY_MARKED);
                    } else {
                        checkSession(student, payload);
                    }
                });
    }

    private void checkSession(@NonNull User student, @NonNull QrPayload payload) {
        sessions.loadSession(
                payload.getSessionId(),
                loaded -> {
                    if (!loaded.isSuccess()) {
                        // Only members can read a session, so a denial means not enrolled.
                        final boolean denied = loaded.getError() == AppError.PERMISSION_DENIED;
                        reject(denied ? AppError.NOT_ENROLLED : loaded.getError());
                    } else if (!loaded.getValue().isLive(wallClockMs.getAsLong())) {
                        reject(AppError.SESSION_ENDED);
                    } else {
                        withFix(fix -> checkPlace(student, loaded.getValue(), payload, fix));
                    }
                });
    }

    private void checkPlace(
            @NonNull User student,
            @NonNull Session session,
            @NonNull QrPayload payload,
            @NonNull LocationFix fix) {
        if (fix.isMock()) {
            reject(AppError.MOCK_LOCATION);
        } else if (!session.getGeofence()
                .contains(fix.getLat(), fix.getLng(), fix.getAccuracyM())) {
            reject(AppError.OUTSIDE_AREA);
        } else {
            attendance.markLive(
                    student, session, payload.getToken(), fix, marked -> onMarked(marked));
        }
    }

    private void onMarked(@NonNull Outcome<MarkResult> marked) {
        if (marked.isSuccess()) {
            if (marked.getValue() == MarkResult.MARKED) {
                state.setValue(new State(Stage.DONE, current().locationReady, null));
            } else {
                reject(AppError.ALREADY_MARKED);
            }
        } else if (marked.getError() == AppError.PERMISSION_DENIED) {
            // Every check the app can make has passed, so the token must be stale.
            reject(AppError.QR_EXPIRED);
        } else {
            reject(marked.getError());
        }
    }

    /** Uses the newest fix if it is usable; otherwise waits a little for one. */
    private void withFix(@NonNull FixReceiver receiver) {
        final LocationFix fix = latestFix;
        if (fix != null && isUsable(fix)) {
            receiver.accept(fix);
            return;
        }
        waitingForFix = receiver;
        cancelFixTimeout =
                scheduler.schedule(
                        () -> {
                            stopWaitingForFix();
                            reject(AppError.LOCATION_UNAVAILABLE);
                        },
                        FIX_WAIT_MS);
    }

    /** Recent and accurate enough to be compared with the geofence. */
    private boolean isUsable(@NonNull LocationFix fix) {
        final long ageMs =
                TimeUnit.NANOSECONDS.toMillis(
                        elapsedNanos.getAsLong() - fix.getElapsedRealtimeNanos());
        return ageMs <= FIX_MAX_AGE_MS
                && fix.getAccuracyM() > 0
                && fix.getAccuracyM() <= Constants.MAX_ACCURACY_M;
    }

    private void stopWaitingForFix() {
        waitingForFix = null;
        if (cancelFixTimeout != null) {
            cancelFixTimeout.run();
            cancelFixTimeout = null;
        }
    }

    private void reject(@Nullable AppError reason) {
        state.setValue(
                new State(
                        Stage.DONE,
                        current().locationReady,
                        reason != null ? reason : AppError.UNKNOWN));
    }

    @Override
    protected void onCleared() {
        stopWaitingForFix();
    }

    @NonNull
    private State current() {
        final State value = state.getValue();
        if (value == null) {
            throw new IllegalStateException("State is always set");
        }
        return value;
    }
}
