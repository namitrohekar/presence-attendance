package com.presence.attendance.attendance.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import com.presence.attendance.attendance.domain.AttendanceRepository.MarkResult;
import com.presence.attendance.attendance.domain.PendingMark.State;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Constants;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.session.domain.Geofence;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.testutil.FakePendingMarkStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Every saved scan ends in exactly one state, and sending twice never writes twice. */
public final class PendingMarkSyncTest {

    private static final String STUDENT = "studentA";
    private static final String TOKEN = "AbCdEfGhIjKlMnOpQrSt_-";
    private static final long MINUTE = 60_000L;
    private static final long ENDS_AT = 60 * MINUTE;
    private static final long SCANNED_AT = 10 * MINUTE;
    private static final long GRACE = Constants.QUEUE_GRACE_MIN * MINUTE;
    private static final double INSIDE_LAT = 19.07618;

    private final FakePendingMarkStore store = new FakePendingMarkStore();
    private final Map<String, Outcome<Session>> sessions = new HashMap<>();
    private final List<String> sent = new ArrayList<>();
    private Outcome<MarkResult> markOutcome = Outcome.success(MarkResult.MARKED);

    private final PendingMarkSync sync =
            new PendingMarkSync(
                    store,
                    new PendingMarkSync.Remote() {
                        @NonNull
                        @Override
                        public Outcome<Session> loadSession(@NonNull String sessionId) {
                            return sessions.getOrDefault(
                                    sessionId, Outcome.failure(AppError.SESSION_NOT_FOUND));
                        }

                        @NonNull
                        @Override
                        public Outcome<MarkResult> markQueued(
                                @NonNull Session session, @NonNull PendingMark mark) {
                            sent.add(session.getId() + ":" + mark.getToken());
                            return markOutcome;
                        }
                    });

    private void session(String id) {
        sessions.put(
                id,
                Outcome.success(
                        new Session(
                                id,
                                "courseA",
                                "teacherA",
                                Session.Status.ACTIVE,
                                0,
                                ENDS_AT,
                                Geofence.around(19.076, 72.8777, 100))));
    }

    private void saved(String sessionId, String studentId, double lat, long scannedAt) {
        store.add(
                new PendingMark(
                        sessionId,
                        studentId,
                        TOKEN,
                        lat,
                        72.8777,
                        10f,
                        scannedAt,
                        State.PENDING,
                        null),
                () -> {});
    }

    private void saved(String sessionId) {
        saved(sessionId, STUDENT, INSIDE_LAT, SCANNED_AT);
    }

    @Test
    public void aSavedScanIsSentAndMarkedSynced() {
        session("s1");
        saved("s1");
        assertFalse("nothing left to retry", sync.run(STUDENT, 20 * MINUTE));
        assertEquals(List.of("s1:" + TOKEN), sent);
        assertEquals(State.SYNCED, store.only("s1").getState());
        assertNull(store.only("s1").getReason());
    }

    @Test
    public void aSettledScanForgetsItsCodeAndCoordinates() {
        session("s1");
        saved("s1");
        sync.run(STUDENT, 20 * MINUTE);
        final PendingMark settled = store.only("s1");
        assertEquals("", settled.getToken());
        assertEquals(0, settled.getLat(), 0);
        assertEquals(0, settled.getLng(), 0);
    }

    @Test
    public void runningAgainSendsNothingTwice() {
        session("s1");
        saved("s1");
        sync.run(STUDENT, 20 * MINUTE);
        sync.run(STUDENT, 21 * MINUTE);
        assertEquals(1, sent.size());
    }

    @Test
    public void aRecordThatAlreadyExistsCountsAsSynced() {
        // The first attempt reached the server but its answer was lost.
        session("s1");
        saved("s1");
        markOutcome = Outcome.success(MarkResult.ALREADY_MARKED);
        assertFalse(sync.run(STUDENT, 20 * MINUTE));
        assertEquals(State.SYNCED, store.only("s1").getState());
    }

    @Test
    public void anUnreachableServerLeavesTheScanPendingAndAsksForARetry() {
        saved("s1");
        for (AppError error : new AppError[] {AppError.OFFLINE, AppError.TIMEOUT}) {
            sessions.put("s1", Outcome.failure(error));
            assertTrue(error.name(), sync.run(STUDENT, 20 * MINUTE));
            assertEquals(State.PENDING, store.only("s1").getState());
        }
        session("s1");
        markOutcome = Outcome.failure(AppError.TIMEOUT);
        assertTrue("the write itself timed out", sync.run(STUDENT, 20 * MINUTE));
        assertEquals(State.PENDING, store.only("s1").getState());
        assertEquals(TOKEN, store.only("s1").getToken());
    }

    @Test
    public void theGracePeriodIsExclusiveAtItsEnd() {
        session("s1");
        session("s2");
        saved("s1");
        sync.run(STUDENT, ENDS_AT + GRACE - 1);
        assertEquals("one millisecond inside", State.SYNCED, store.only("s1").getState());
        saved("s2");
        assertFalse(sync.run(STUDENT, ENDS_AT + GRACE));
        assertEquals("exactly at the end", State.EXPIRED, store.only("s2").getState());
        assertEquals("an expired scan is never sent", 1, sent.size());
    }

    @Test
    public void aScanMadeAfterTheSessionEndedIsRejected() {
        session("s1");
        saved("s1", STUDENT, INSIDE_LAT, ENDS_AT);
        assertFalse(sync.run(STUDENT, ENDS_AT + MINUTE));
        assertEquals(State.REJECTED, store.only("s1").getState());
        assertEquals(AppError.SESSION_ENDED, store.only("s1").getReason());
        assertTrue(sent.isEmpty());
    }

    @Test
    public void aScanFromOutsideTheAreaIsRejectedWithoutBeingSent() {
        session("s1");
        saved("s1", STUDENT, 19.09, SCANNED_AT);
        sync.run(STUDENT, 20 * MINUTE);
        assertEquals(AppError.OUTSIDE_AREA, store.only("s1").getReason());
        assertTrue(sent.isEmpty());
    }

    @Test
    public void eachRefusalIsNamed() {
        saved("gone");
        sessions.put("denied", Outcome.failure(AppError.PERMISSION_DENIED));
        saved("denied");
        session("stale");
        saved("stale");
        markOutcome = Outcome.failure(AppError.PERMISSION_DENIED);
        assertFalse(sync.run(STUDENT, 20 * MINUTE));
        assertEquals(AppError.SESSION_NOT_FOUND, store.only("gone").getReason());
        assertEquals(AppError.NOT_ENROLLED, store.only("denied").getReason());
        assertEquals(AppError.QR_EXPIRED, store.only("stale").getReason());
    }

    @Test
    public void aSavedScanFromAPhoneAnotherStudentUsedIsRejectedNotSynced() {
        session("s1");
        saved("s1");
        markOutcome = Outcome.success(MarkResult.DEVICE_USED);
        assertFalse(sync.run(STUDENT, 20 * MINUTE));
        assertEquals(State.REJECTED, store.only("s1").getState());
        assertEquals(AppError.DEVICE_USED, store.only("s1").getReason());
    }

    @Test
    public void oneUnreachableScanDoesNotHoldBackTheOthers() {
        sessions.put("s1", Outcome.failure(AppError.OFFLINE));
        saved("s1");
        session("s2");
        saved("s2");
        assertTrue(sync.run(STUDENT, 20 * MINUTE));
        assertEquals(State.PENDING, store.only("s1").getState());
        assertEquals(State.SYNCED, store.only("s2").getState());
    }

    @Test
    public void anotherAccountsScansAreLeftAlone() {
        session("s1");
        saved("s1", "studentB", INSIDE_LAT, SCANNED_AT);
        assertFalse(sync.run(STUDENT, 20 * MINUTE));
        assertTrue(sent.isEmpty());
        assertEquals(State.PENDING, store.only("s1").getState());
    }

    @Test
    public void aNewerScanOfTheSameSessionIsNotOverwrittenByAnOlderResult() {
        session("s1");
        saved("s1");
        final PendingMark old = store.only("s1");
        store.add(
                new PendingMark(
                        "s1",
                        STUDENT,
                        "newToken_0000000000001",
                        INSIDE_LAT,
                        72.8777,
                        10f,
                        SCANNED_AT + MINUTE,
                        State.PENDING,
                        null),
                () -> {});
        store.resolve(old, State.REJECTED, AppError.QR_EXPIRED);
        assertEquals(State.PENDING, store.only("s1").getState());
    }
}
