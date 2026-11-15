package com.presence.attendance.session.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.AttendanceRepository;
import com.presence.attendance.auth.domain.AuthRepository;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.domain.CourseRepository;
import com.presence.attendance.course.domain.Member;
import com.presence.attendance.session.domain.QrPayload;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.session.domain.SessionRepository;
import com.presence.attendance.session.domain.TokenRotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The live session screen. State machine: LOADING -> LIVE -> ENDED, or ERROR.
 *
 * <p>Tokens are written only while the screen is visible: a QR nobody can see needs no tokens,
 * which also bounds the writes. The session itself keeps running if the teacher leaves, and the
 * screen picks it up again when reopened, including after the process was killed.
 */
public final class LiveSessionViewModel extends ViewModel {

    public enum Phase {
        LOADING,
        LIVE,
        ENDED,
        ERROR
    }

    /** Immutable screen state. */
    public static final class State {
        @NonNull public final Phase phase;
        @Nullable public final Session session;

        /** The text to render as a QR, or null to show a blank. */
        @Nullable public final String qrText;

        public final boolean reconnecting;

        /** When the code changes next, on the elapsed-time clock; 0 if none is showing. */
        public final long rotatesAtElapsedMs;

        @NonNull public final List<AttendanceRecord> records;
        @NonNull public final Map<String, Member> members;
        public final boolean ending;
        @Nullable public final AppError error;

        State(
                @NonNull Phase phase,
                @Nullable Session session,
                @Nullable String qrText,
                boolean reconnecting,
                long rotatesAtElapsedMs,
                @NonNull List<AttendanceRecord> records,
                @NonNull Map<String, Member> members,
                boolean ending,
                @Nullable AppError error) {
            this.phase = phase;
            this.session = session;
            this.qrText = qrText;
            this.reconnecting = reconnecting;
            this.rotatesAtElapsedMs = rotatesAtElapsedMs;
            this.records = Collections.unmodifiableList(records);
            this.members = Collections.unmodifiableMap(members);
            this.ending = ending;
            this.error = error;
        }

        /** Records that count towards attendance. */
        public int presentCount() {
            int count = 0;
            for (AttendanceRecord record : records) {
                if (record.getStatus().countsAsPresent()) {
                    count++;
                }
            }
            return count;
        }
    }

    private final AuthRepository auth;
    private final SessionRepository sessionRepository;
    private final AttendanceRepository attendance;
    private final CourseRepository courses;
    private final String sessionId;
    private final TokenRotation.Scheduler scheduler;
    private final LongSupplier wallClockMs;
    private final TokenRotation rotation;
    private final MediatorLiveData<State> state =
            new MediatorLiveData<>(
                    new State(
                            Phase.LOADING,
                            null,
                            null,
                            false,
                            0,
                            Collections.emptyList(),
                            new HashMap<>(),
                            false,
                            null));

    private boolean visible;
    @Nullable private LiveData<Outcome<List<AttendanceRecord>>> roster;
    @Nullable private Runnable cancelAutoEnd;

    public LiveSessionViewModel(
            @NonNull AuthRepository auth,
            @NonNull SessionRepository sessionRepository,
            @NonNull AttendanceRepository attendance,
            @NonNull CourseRepository courses,
            @NonNull String sessionId,
            @NonNull TokenRotation.Scheduler scheduler,
            @NonNull LongSupplier elapsedMs,
            @NonNull LongSupplier wallClockMs,
            @NonNull Supplier<String> tokenGenerator) {
        this.auth = auth;
        this.sessionRepository = sessionRepository;
        this.attendance = attendance;
        this.courses = courses;
        this.sessionId = sessionId;
        this.scheduler = scheduler;
        this.wallClockMs = wallClockMs;
        this.rotation =
                new TokenRotation(
                        tokenGenerator, this::writeToken, scheduler, elapsedMs, this::onRotation);
        load();
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** The screen came into view: start showing codes. */
    public void onVisible() {
        visible = true;
        if (current().phase == Phase.LIVE && !current().ending) {
            rotation.start();
        }
    }

    /** The screen left view: stop writing tokens and blank the code. */
    public void onHidden() {
        visible = false;
        rotation.stop();
    }

    public void retry() {
        if (current().phase == Phase.ERROR) {
            load();
        }
    }

    /** The teacher confirmed ending the session. */
    public void end() {
        final State s = current();
        if (s.phase != Phase.LIVE || s.ending) {
            return;
        }
        rotation.stop();
        set(Phase.LIVE, s.session, true, null);
        sessionRepository.endSession(
                sessionId,
                outcome -> {
                    if (outcome.isSuccess()) {
                        finish(outcome.getValue());
                    } else {
                        // Still running: say why, and keep showing codes.
                        set(Phase.LIVE, current().session, false, outcome.getError());
                        if (visible) {
                            rotation.start();
                        }
                    }
                });
    }

    private void load() {
        set(Phase.LOADING, null, false, null);
        auth.loadUser(user -> {
            if (!user.isSuccess()) {
                set(Phase.ERROR, null, false, user.getError());
                return;
            }
            final String uid = user.getValue().getUid();
            sessionRepository.loadSession(
                    sessionId,
                    outcome -> {
                        if (outcome.isSuccess()) {
                            onSession(uid, outcome.getValue());
                        } else {
                            set(Phase.ERROR, null, false, outcome.getError());
                        }
                    });
        });
    }

    private void onSession(@NonNull String uid, @NonNull Session session) {
        final long now = wallClockMs.getAsLong();
        if (!session.isLive(now)) {
            if (session.getStatus() == Session.Status.ACTIVE) {
                // Ran out while nobody was looking. Close it; the screen is ended either way.
                sessionRepository.endSession(sessionId, ignored -> {});
            }
            finish(session);
            return;
        }
        set(Phase.LIVE, session, false, null);
        if (roster == null) {
            roster = attendance.observeSession(sessionId, uid);
            state.addSource(roster, this::onRoster);
        }
        courses.loadMembers(uid, session.getCourseId(), this::onMembers);
        cancel(cancelAutoEnd);
        cancelAutoEnd = scheduler.schedule(this::onEndTimeReached, session.getEndsAtMillis() - now);
        if (visible) {
            rotation.start();
        }
    }

    private void onEndTimeReached() {
        if (current().phase == Phase.LIVE) {
            rotation.stop();
            sessionRepository.endSession(sessionId, ignored -> {});
            finish(current().session);
        }
    }

    private void finish(@Nullable Session session) {
        rotation.stop();
        cancel(cancelAutoEnd);
        cancelAutoEnd = null;
        set(Phase.ENDED, session, false, null);
    }

    private void writeToken(
            @NonNull String token, @NonNull java.util.function.Consumer<Boolean> onResult) {
        sessionRepository.writeToken(
                sessionId,
                token,
                outcome -> {
                    if (!outcome.isSuccess() && outcome.getError() == AppError.PERMISSION_DENIED) {
                        // The rules only refuse a token when the session is no longer live.
                        onTokenRefused();
                    }
                    onResult.accept(outcome.isSuccess());
                });
    }

    private void onTokenRefused() {
        sessionRepository.loadSession(
                sessionId,
                outcome -> {
                    final boolean over =
                            outcome.isSuccess()
                                    && !outcome.getValue().isLive(wallClockMs.getAsLong());
                    if (over && current().phase == Phase.LIVE) {
                        finish(outcome.getValue());
                    }
                });
    }

    private void onRotation(@NonNull TokenRotation.State rotationState) {
        final State s = current();
        if (s.phase != Phase.LIVE) {
            return;
        }
        final String qrText =
                rotationState.token != null
                        ? QrPayload.build(sessionId, rotationState.token)
                        : null;
        state.setValue(
                new State(
                        s.phase,
                        s.session,
                        qrText,
                        rotationState.reconnecting,
                        rotationState.rotatesAtMs,
                        s.records,
                        s.members,
                        s.ending,
                        s.error));
    }

    private void onRoster(@NonNull Outcome<List<AttendanceRecord>> outcome) {
        if (!outcome.isSuccess()) {
            // The QR matters more than the list: keep showing codes and the last known roster.
            return;
        }
        final State s = current();
        state.setValue(
                new State(
                        s.phase,
                        s.session,
                        s.qrText,
                        s.reconnecting,
                        s.rotatesAtElapsedMs,
                        outcome.getValue(),
                        s.members,
                        s.ending,
                        s.error));
    }

    private void onMembers(@NonNull Outcome<List<Member>> outcome) {
        if (!outcome.isSuccess()) {
            return;
        }
        final Map<String, Member> members = new HashMap<>();
        for (Member member : outcome.getValue()) {
            members.put(member.getStudentId(), member);
        }
        final State s = current();
        state.setValue(
                new State(
                        s.phase,
                        s.session,
                        s.qrText,
                        s.reconnecting,
                        s.rotatesAtElapsedMs,
                        s.records,
                        members,
                        s.ending,
                        s.error));
    }

    private void set(
            @NonNull Phase phase,
            @Nullable Session session,
            boolean ending,
            @Nullable AppError error) {
        final State s = current();
        final boolean live = phase == Phase.LIVE;
        state.setValue(
                new State(
                        phase,
                        session,
                        live ? s.qrText : null,
                        live && s.reconnecting,
                        live ? s.rotatesAtElapsedMs : 0,
                        s.records,
                        s.members,
                        ending,
                        error));
    }

    @Override
    protected void onCleared() {
        rotation.stop();
        cancel(cancelAutoEnd);
    }

    private static void cancel(@Nullable Runnable handle) {
        if (handle != null) {
            handle.run();
        }
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
