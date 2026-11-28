package com.presence.attendance.session.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Constants;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.location.LocationFix;
import com.presence.attendance.core.location.LocationSource;
import com.presence.attendance.session.domain.Geofence;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.session.domain.SessionRepository;

/**
 * Start-session sheet: how long the session runs and where students must be. "Campus" uses the
 * geofence from the server; "Here" builds one around the teacher's current position.
 */
public final class StartSessionViewModel extends ViewModel {

    static final int DEFAULT_DURATION_MIN = 60;
    static final int HERE_RADIUS_MIN_M = 50;
    static final int HERE_RADIUS_MAX_M = 250;
    static final int DEFAULT_RADIUS_M = 100;

    public enum Mode {
        CAMPUS,
        HERE,
        /** The geofence saved for the timetable's room. */
        ROOM
    }

    /** Where the "Here" location stands. */
    public enum Fix {
        NOT_NEEDED,
        NEEDS_PERMISSION,
        LOCATING,
        READY,
        FAILED
    }

    /** Immutable sheet state. {@code started} is set once and ends the sheet. */
    public static final class State {
        public final boolean online;
        public final int durationMin;
        @NonNull public final Mode mode;
        public final int radiusM;
        @NonNull public final Fix fix;
        public final int accuracyM;
        public final boolean busy;
        @Nullable public final AppError error;
        @Nullable public final Session started;

        /** The timetable's room for this class, "" when the session is not started from one. */
        @NonNull public final String room;

        /** Whether that room's location is saved, so ROOM can be chosen. */
        public final boolean roomSaved;

        State(
                boolean online,
                int durationMin,
                @NonNull Mode mode,
                int radiusM,
                @NonNull Fix fix,
                int accuracyM,
                boolean busy,
                @Nullable AppError error,
                @Nullable Session started,
                @NonNull String room,
                boolean roomSaved) {
            this.online = online;
            this.durationMin = durationMin;
            this.mode = mode;
            this.radiusM = radiusM;
            this.fix = fix;
            this.accuracyM = accuracyM;
            this.busy = busy;
            this.error = error;
            this.started = started;
            this.room = room;
            this.roomSaved = roomSaved;
        }

        /** Whether everything needed to start is in place. */
        public boolean canStart() {
            return online
                    && !busy
                    && started == null
                    && (mode == Mode.CAMPUS
                            || (mode == Mode.HERE && fix == Fix.READY)
                            || (mode == Mode.ROOM && roomSaved));
        }

        /** Whether "Save as this room" can be offered: standing in it, with a good fix. */
        public boolean canSaveRoom() {
            return online && !busy && !room.isEmpty() && mode == Mode.HERE && fix == Fix.READY;
        }

        @NonNull
        State with(
                @NonNull Fix newFix,
                int newAccuracyM,
                boolean newBusy,
                @Nullable AppError newError) {
            return new State(
                    online,
                    durationMin,
                    mode,
                    radiusM,
                    newFix,
                    newAccuracyM,
                    newBusy,
                    newError,
                    started,
                    room,
                    roomSaved);
        }
    }

    private final AuthRepository auth;
    private final SessionRepository sessionRepository;
    private final LocationSource location;
    private final String courseId;
    private final MediatorLiveData<State> state =
            new MediatorLiveData<>(
                    new State(
                            false,
                            DEFAULT_DURATION_MIN,
                            Mode.CAMPUS,
                            DEFAULT_RADIUS_M,
                            Fix.NOT_NEEDED,
                            0,
                            false,
                            null,
                            null,
                            "",
                            false));
    // Kept out of the state: coordinates are never rendered or logged.
    @Nullable private LocationFix here;
    @Nullable private Geofence roomFence;

    public StartSessionViewModel(
            @NonNull AuthRepository auth,
            @NonNull SessionRepository sessionRepository,
            @NonNull LocationSource location,
            @NonNull LiveData<Boolean> online,
            @NonNull String courseId) {
        this(auth, sessionRepository, location, online, courseId, "", DEFAULT_DURATION_MIN);
    }

    /**
     * Started from a class in the timetable: its room and the minutes left in it. A room whose
     * location is saved is chosen at once; one that is not starts at "Here", so it can be saved.
     */
    public StartSessionViewModel(
            @NonNull AuthRepository auth,
            @NonNull SessionRepository sessionRepository,
            @NonNull LocationSource location,
            @NonNull LiveData<Boolean> online,
            @NonNull String courseId,
            @NonNull String room,
            int durationMin) {
        this.auth = auth;
        this.sessionRepository = sessionRepository;
        this.location = location;
        this.courseId = courseId;
        state.addSource(
                online,
                isOnline -> {
                    final State s = current();
                    state.setValue(
                            new State(
                                    Boolean.TRUE.equals(isOnline),
                                    s.durationMin,
                                    s.mode,
                                    s.radiusM,
                                    s.fix,
                                    s.accuracyM,
                                    s.busy,
                                    s.error,
                                    s.started,
                                    s.room,
                                    s.roomSaved));
                });
        if (durationMin != DEFAULT_DURATION_MIN) {
            setDuration(durationMin);
        }
        if (!room.isEmpty()) {
            final State s = current();
            state.setValue(
                    new State(
                            s.online,
                            s.durationMin,
                            s.mode,
                            s.radiusM,
                            s.fix,
                            s.accuracyM,
                            s.busy,
                            s.error,
                            s.started,
                            room,
                            false));
            sessionRepository.loadRoom(room, this::onRoom);
        }
    }

    /** A saved room is chosen; an unsaved one needs the teacher's position to be saved. */
    private void onRoom(@NonNull Outcome<Geofence> outcome) {
        if (!outcome.isSuccess()) {
            setMode(Mode.HERE);
            return;
        }
        roomFence = outcome.getValue();
        final State s = current();
        state.setValue(
                new State(
                        s.online,
                        s.durationMin,
                        Mode.ROOM,
                        s.radiusM,
                        Fix.NOT_NEEDED,
                        0,
                        false,
                        null,
                        s.started,
                        s.room,
                        true));
    }

    /**
     * Saves the teacher's position and the chosen radius as the room's location, then uses it.
     * Every later session in this room can start without waiting for a fix.
     */
    public void saveRoom() {
        final State s = current();
        final LocationFix fix = here;
        if (!s.canSaveRoom() || fix == null) {
            return;
        }
        state.setValue(s.with(s.fix, s.accuracyM, true, null));
        final Geofence geofence = Geofence.around(fix.getLat(), fix.getLng(), s.radiusM);
        auth.loadUser(user -> {
            if (!user.isSuccess()) {
                fail(user.getError());
                return;
            }
            sessionRepository.saveRoom(
                    user.getValue(),
                    s.room,
                    geofence,
                    saved -> {
                        if (saved.isSuccess()) {
                            onRoom(saved);
                        } else {
                            fail(saved.getError());
                        }
                    });
        });
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    public void setDuration(int minutes) {
        final State s = current();
        final int clamped =
                Math.max(Constants.SESSION_MIN_MIN, Math.min(Constants.SESSION_MAX_MIN, minutes));
        state.setValue(
                new State(
                        s.online,
                        clamped,
                        s.mode,
                        s.radiusM,
                        s.fix,
                        s.accuracyM,
                        s.busy,
                        null,
                        s.started,
                        s.room,
                        s.roomSaved));
    }

    public void setRadius(int metres) {
        final State s = current();
        final int clamped = Math.max(HERE_RADIUS_MIN_M, Math.min(HERE_RADIUS_MAX_M, metres));
        state.setValue(
                new State(
                        s.online,
                        s.durationMin,
                        s.mode,
                        clamped,
                        s.fix,
                        s.accuracyM,
                        s.busy,
                        null,
                        s.started,
                        s.room,
                        s.roomSaved));
    }

    public void setMode(@NonNull Mode mode) {
        final State s = current();
        if (s.busy || s.mode == mode) {
            return;
        }
        state.setValue(
                new State(
                        s.online,
                        s.durationMin,
                        mode,
                        s.radiusM,
                        Fix.NOT_NEEDED,
                        0,
                        false,
                        null,
                        s.started,
                        s.room,
                        s.roomSaved));
        if (mode == Mode.HERE) {
            locate();
        }
    }

    /** The user answered the location permission request. */
    public void onPermissionResult() {
        if (current().mode == Mode.HERE) {
            locate();
        }
    }

    private void locate() {
        here = null;
        if (!location.hasPrecisePermission()) {
            state.setValue(current().with(Fix.NEEDS_PERMISSION, 0, false, null));
            return;
        }
        state.setValue(current().with(Fix.LOCATING, 0, false, null));
        location.current(outcome -> {
            if (current().mode != Mode.HERE) {
                return;
            }
            if (!outcome.isSuccess()) {
                state.setValue(current().with(Fix.FAILED, 0, false, outcome.getError()));
                return;
            }
            final LocationFix fix = outcome.getValue();
            if (fix.getAccuracyM() > Constants.MAX_ACCURACY_M) {
                // Too vague to centre a geofence on.
                state.setValue(current().with(Fix.FAILED, 0, false, AppError.LOCATION_INACCURATE));
                return;
            }
            here = fix;
            state.setValue(current().with(Fix.READY, Math.round(fix.getAccuracyM()), false, null));
        });
    }

    public void start() {
        final State s = current();
        if (s.busy || s.started != null) {
            return;
        }
        // Starting a session never queues: it must reach the server now or not happen.
        if (!s.online) {
            state.setValue(s.with(s.fix, s.accuracyM, false, AppError.OFFLINE));
            return;
        }
        final LocationFix fix = here;
        if (s.mode == Mode.HERE && (s.fix != Fix.READY || fix == null)) {
            return;
        }
        if (s.mode == Mode.ROOM && roomFence == null) {
            return;
        }
        state.setValue(s.with(s.fix, s.accuracyM, true, null));
        auth.loadUser(user -> {
            if (!user.isSuccess()) {
                fail(user.getError());
            } else if (s.mode == Mode.ROOM && roomFence != null) {
                create(user.getValue(), roomFence);
            } else if (s.mode == Mode.CAMPUS) {
                sessionRepository.loadCampus(campus -> {
                    if (campus.isSuccess()) {
                        create(user.getValue(), campus.getValue());
                    } else {
                        fail(campus.getError());
                    }
                });
            } else if (fix != null) {
                create(user.getValue(), Geofence.around(fix.getLat(), fix.getLng(), s.radiusM));
            }
        });
    }

    private void create(@NonNull User teacher, @NonNull Geofence geofence) {
        sessionRepository.startSession(
                teacher, courseId, current().durationMin, geofence, this::onStarted);
    }

    private void onStarted(@NonNull Outcome<Session> outcome) {
        if (!outcome.isSuccess()) {
            fail(outcome.getError());
            return;
        }
        final State s = current();
        state.setValue(
                new State(
                        s.online,
                        s.durationMin,
                        s.mode,
                        s.radiusM,
                        s.fix,
                        s.accuracyM,
                        false,
                        null,
                        outcome.getValue(),
                        s.room,
                        s.roomSaved));
    }

    private void fail(@Nullable AppError error) {
        final State s = current();
        state.setValue(s.with(s.fix, s.accuracyM, false, error));
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
