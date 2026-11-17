package com.presence.attendance.scan.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.attendance.domain.AttendanceRepository;
import com.presence.attendance.attendance.domain.AttendanceRepository.MarkResult;
import com.presence.attendance.attendance.domain.PendingMark;
import com.presence.attendance.attendance.domain.PendingMarkStore;
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

    /**
     * Immutable screen state. In DONE: {@code queued} means saved for later, otherwise {@code
     * rejection} is null exactly when marked.
     */
    public static final class State {
        @NonNull public final Stage stage;
        public final boolean locationReady;
        @Nullable public final AppError rejection;
        public final boolean queued;

        State(@NonNull Stage stage, boolean locationReady, @Nullable AppError rejection) {
            this(stage, locationReady, rejection, false);
        }

        State(
                @NonNull Stage stage,
                boolean locationReady,
                @Nullable AppError rejection,
                boolean queued) {
            this.stage = stage;
            this.locationReady = locationReady;
            this.rejection = rejection;
            this.queued = queued;
        }
    }

    /** Continues a scan once a usable fix is available. */
    private interface FixReceiver {
        void accept(@NonNull LocationFix fix);
    }

    private final AuthRepository auth;
    private final SessionRepository sessions;
    private final AttendanceRepository attendance;
    private final PendingMarkStore queue;
    private final Runnable requestSync;
    private final TokenRotation.Scheduler scheduler;
    private final LongSupplier elapsedNanos;
    private final LongSupplier wallClockMs;
    private final MutableLiveData<State> state =
            new MutableLiveData<>(new State(Stage.SCANNING, false, null));

    // Kept out of the state: coordinates are never rendered or logged.
    @Nullable private LocationFix latestFix;
    @Nullable private FixReceiver waitingForFix;
    @Nullable private Runnable cancelFixTimeout;
    private boolean online = true;
    // The device clock when the code was read, which is what a saved scan must carry.
    private long scannedAtMs;

    public ScanViewModel(
            @NonNull AuthRepository auth,
            @NonNull SessionRepository sessions,
            @NonNull AttendanceRepository attendance,
            @NonNull PendingMarkStore queue,
            @NonNull Runnable requestSync,
            @NonNull TokenRotation.Scheduler scheduler,
            @NonNull LongSupplier elapsedNanos,
            @NonNull LongSupplier wallClockMs) {
        this.auth = auth;
        this.sessions = sessions;
        this.attendance = attendance;
        this.queue = queue;
        this.requestSync = requestSync;
        this.scheduler = scheduler;
        this.elapsedNanos = elapsedNanos;
        this.wallClockMs = wallClockMs;
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** Whether the device has a connection. Without one a scan is saved instead of sent. */
    public void onOnline(boolean nowOnline) {
        online = nowOnline;
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
        scannedAtMs = wallClockMs.getAsLong();
        state.setValue(new State(Stage.CHECKING, current().locationReady, null));
        auth.loadUser(user -> {
            if (!user.isSuccess()) {
                reject(user.getError());
            } else if (online) {
                checkSession(user.getValue(), payload);
            } else {
                save(user.getValue(), payload);
            }
        });
    }

    /**
     * The server cannot be reached, so the scan is kept on the device and sent later. Nothing is
     * known about the session here; the checks happen when it is sent, and the rules decide.
     */
    private void save(@NonNull User student, @NonNull QrPayload payload) {
        withFix(fix -> {
            if (fix.isMock()) {
                reject(AppError.MOCK_LOCATION);
                return;
            }
            queue.add(
                    scan(student, payload, fix),
                    () -> {
                        requestSync.run();
                        state.setValue(new State(Stage.DONE, current().locationReady, null, true));
                    });
        });
    }

    /** This scan as it is kept or sent later: the code, the place, and when the code was read. */
    @NonNull
    private PendingMark scan(
            @NonNull User student, @NonNull QrPayload payload, @NonNull LocationFix fix) {
        return new PendingMark(
                payload.getSessionId(),
                student.getUid(),
                payload.getToken(),
                fix.getLat(),
                fix.getLng(),
                fix.getAccuracyM(),
                scannedAtMs,
                PendingMark.State.PENDING,
                null);
    }

    /** True for failures that say nothing about the scan itself, only about the connection. */
    private static boolean isUnreachable(@Nullable AppError error) {
        return error == AppError.OFFLINE || error == AppError.TIMEOUT;
    }

    private void checkSession(@NonNull User student, @NonNull QrPayload payload) {
        sessions.loadSession(
                payload.getSessionId(),
                loaded -> {
                    if (isUnreachable(loaded.getError())) {
                        save(student, payload);
                    } else if (!loaded.isSuccess()) {
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
                    student,
                    session,
                    payload.getToken(),
                    fix,
                    marked -> {
                        if (isUnreachable(marked.getError())) {
                            // Whether it arrived is unknown. Sending it again later is safe:
                            // a record that exists is reported, not written twice.
                            save(student, payload);
                        } else if (marked.getError() == AppError.PERMISSION_DENIED) {
                            sendLate(student, session, payload, fix);
                        } else {
                            onMarked(marked);
                        }
                    });
        }
    }

    /**
     * Every check the app can make has passed and the mark was still refused, so the code stopped
     * being current while the mark was on its way. The same scan is sent once more with the time
     * the code was read. The rules accept that only if the code was current at that moment, so a
     * picture of an old code is refused here as well.
     */
    private void sendLate(
            @NonNull User student,
            @NonNull Session session,
            @NonNull QrPayload payload,
            @NonNull LocationFix fix) {
        attendance.markQueued(
                session,
                scan(student, payload, fix),
                marked -> {
                    if (isUnreachable(marked.getError())) {
                        save(student, payload);
                    } else {
                        onMarked(marked);
                    }
                });
    }

    private void onMarked(@NonNull Outcome<MarkResult> marked) {
        if (marked.isSuccess()) {
            switch (marked.getValue()) {
                case MARKED:
                    state.setValue(new State(Stage.DONE, current().locationReady, null));
                    break;
                case DEVICE_USED:
                    reject(AppError.DEVICE_USED);
                    break;
                default:
                    reject(AppError.ALREADY_MARKED);
                    break;
            }
        } else if (marked.getError() == AppError.PERMISSION_DENIED) {
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
                            // Fixes that arrive but are too vague need a different remedy
                            // from no fix at all.
                            reject(
                                    latestFix != null && isRecent(latestFix)
                                            ? AppError.LOCATION_INACCURATE
                                            : AppError.LOCATION_UNAVAILABLE);
                        },
                        FIX_WAIT_MS);
    }

    /** Recent and accurate enough to be compared with the geofence. */
    private boolean isUsable(@NonNull LocationFix fix) {
        return isRecent(fix)
                && fix.getAccuracyM() > 0
                && fix.getAccuracyM() <= Constants.MAX_ACCURACY_M;
    }

    private boolean isRecent(@NonNull LocationFix fix) {
        final long ageMs =
                TimeUnit.NANOSECONDS.toMillis(
                        elapsedNanos.getAsLong() - fix.getElapsedRealtimeNanos());
        return ageMs <= FIX_MAX_AGE_MS;
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
