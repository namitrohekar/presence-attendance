package com.presence.attendance.session.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.domain.Member;
import com.presence.attendance.session.domain.Geofence;
import com.presence.attendance.session.domain.QrPayload;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.session.ui.LiveSessionViewModel.Phase;
import com.presence.attendance.session.ui.LiveSessionViewModel.State;
import com.presence.attendance.testutil.FakeAttendanceRepository;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeCourseRepository;
import com.presence.attendance.testutil.FakeScheduler;
import com.presence.attendance.testutil.FakeSessionRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class LiveSessionViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private static final String SESSION_ID = "kYp3Qv9sT2mLx7Zb1cDe";
    private static final long HOUR = 3_600_000L;
    private static final Geofence AREA = Geofence.around(19.076, 72.8777, 100);

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeSessionRepository sessions = new FakeSessionRepository();
    private final FakeAttendanceRepository attendance = new FakeAttendanceRepository();
    private final FakeCourseRepository courses = new FakeCourseRepository();
    private final FakeScheduler time = new FakeScheduler();
    private int tokenCounter;
    private LiveSessionViewModel viewModel;

    private static Session session(Session.Status status, long endsAt) {
        return new Session(SESSION_ID, "courseA", "teacherA", status, 0, endsAt, AREA);
    }

    /** 22 base64url characters, different each time. */
    private String nextToken() {
        tokenCounter++;
        return String.format("token%017d", tokenCounter);
    }

    @Before
    public void setUp() {
        auth.signedInAs(Fixtures.TEACHER);
        sessions.sessionOutcome = Outcome.success(session(Session.Status.ACTIVE, HOUR));
        sessions.endOutcome = Outcome.success(session(Session.Status.ENDED, 1000));
    }

    private void open() {
        viewModel =
                new LiveSessionViewModel(
                        auth,
                        sessions,
                        attendance,
                        courses,
                        SESSION_ID,
                        time,
                        () -> time.now,
                        () -> time.now,
                        this::nextToken);
        viewModel.getState().observeForever(state -> {});
    }

    private State state() {
        return viewModel.getState().getValue();
    }

    @Test
    public void aRunningSessionOpensLiveAndListensToItsRoster() {
        open();
        assertEquals(Phase.LIVE, state().phase);
        assertEquals(Collections.singletonList(SESSION_ID + ":teacherA"), attendance.observed);
        assertTrue(courses.calls.contains("loadMembers:teacherA:courseA"));
    }

    @Test
    public void noTokenIsWrittenUntilTheScreenIsVisible() {
        open();
        assertTrue(sessions.tokens.isEmpty());
        viewModel.onVisible();
        assertEquals(1, sessions.tokens.size());
    }

    @Test
    public void theQrAppearsOnlyAfterTheServerAcknowledgesTheToken() {
        open();
        viewModel.onVisible();
        assertNull(state().qrText);
        sessions.answerToken(true);
        assertEquals(QrPayload.build(SESSION_ID, sessions.tokens.get(0)), state().qrText);
        assertEquals(12_000, state().rotatesAtElapsedMs);
    }

    @Test
    public void theQrRotatesEveryTwelveSeconds() {
        open();
        viewModel.onVisible();
        sessions.answerToken(true);
        time.advance(12_000);
        assertEquals(2, sessions.tokens.size());
        sessions.answerToken(true);
        assertEquals(QrPayload.build(SESSION_ID, sessions.tokens.get(1)), state().qrText);
    }

    @Test
    public void aFailedWriteShowsReconnectingAndTheOldCodeGoesBlankWhenItExpires() {
        open();
        viewModel.onVisible();
        sessions.answerToken(true);
        time.advance(12_000);
        sessions.answerToken(false);
        assertTrue(state().reconnecting);
        assertEquals(
                "the old code is still valid",
                QrPayload.build(SESSION_ID, sessions.tokens.get(0)),
                state().qrText);
        while (time.now < 20_000) {
            time.advance(2_000);
            if (!sessions.pendingTokens.isEmpty()) {
                sessions.answerToken(false);
            }
        }
        assertNull("blank once the token is 20 s old", state().qrText);
        assertTrue(state().reconnecting);
    }

    @Test
    public void leavingTheScreenStopsTheWriterAndBlanksTheCode() {
        open();
        viewModel.onVisible();
        sessions.answerToken(true);
        viewModel.onHidden();
        assertNull(state().qrText);
        time.advance(60_000);
        assertEquals(
                "no tokens are written for a screen nobody can see", 1, sessions.tokens.size());

        viewModel.onVisible();
        assertEquals(2, sessions.tokens.size());
    }

    @Test
    public void marksArriveLiveAndOnlyPresentOnesAreCounted() {
        courses.membersOutcome =
                Outcome.success(
                        Collections.singletonList(
                                new Member(
                                        "studentA",
                                        "Meera Joshi",
                                        "mjoshi_mc26@mc.vjti.ac.in",
                                        "262011001")));
        open();
        attendance.feed.setValue(
                Outcome.success(
                        Arrays.asList(
                                new AttendanceRecord(
                                        "sessA", "studentA", AttendanceRecord.Status.PRESENT, 3000),
                                new AttendanceRecord(
                                        "sessA",
                                        "studentB",
                                        AttendanceRecord.Status.LATE_SYNC,
                                        2000),
                                new AttendanceRecord(
                                        "sessA",
                                        "studentC",
                                        AttendanceRecord.Status.MANUAL_ABSENT,
                                        1000))));
        assertEquals(3, state().records.size());
        assertEquals(2, state().presentCount());
        assertEquals("Meera Joshi", state().members.get("studentA").getName());
    }

    @Test
    public void aRosterFailureDoesNotTakeDownTheQr() {
        open();
        viewModel.onVisible();
        sessions.answerToken(true);
        attendance.feed.setValue(Outcome.failure(AppError.OFFLINE));
        assertEquals(Phase.LIVE, state().phase);
        assertEquals(QrPayload.build(SESSION_ID, sessions.tokens.get(0)), state().qrText);
    }

    @Test
    public void endingStopsTheWriterAndFinishesTheSession() {
        open();
        viewModel.onVisible();
        sessions.answerToken(true);
        viewModel.end();
        assertTrue(sessions.calls.contains("endSession:" + SESSION_ID));
        assertEquals(Phase.ENDED, state().phase);
        assertNull(state().qrText);
        time.advance(120_000);
        assertEquals(1, sessions.tokens.size());
    }

    @Test
    public void aFailedEndKeepsTheSessionRunningAndSaysWhy() {
        sessions.endOutcome = Outcome.failure(AppError.OFFLINE);
        open();
        viewModel.onVisible();
        sessions.answerToken(true);
        viewModel.end();
        assertEquals(Phase.LIVE, state().phase);
        assertEquals(AppError.OFFLINE, state().error);
        assertFalse(state().ending);
        assertEquals("codes resume", 2, sessions.tokens.size());
    }

    @Test
    public void theSessionEndsItselfWhenItsTimeRunsOut() {
        sessions.sessionOutcome = Outcome.success(session(Session.Status.ACTIVE, 30_000));
        open();
        viewModel.onVisible();
        sessions.answerToken(true);
        time.advance(30_000);
        assertEquals(Phase.ENDED, state().phase);
        assertTrue(sessions.calls.contains("endSession:" + SESSION_ID));
    }

    @Test
    public void reopeningAnEndedOrExpiredSessionShowsItEnded() {
        sessions.sessionOutcome = Outcome.success(session(Session.Status.ENDED, 500));
        open();
        assertEquals(Phase.ENDED, state().phase);
        assertFalse("nothing to close", sessions.calls.contains("endSession:" + SESSION_ID));
        assertTrue(attendance.observed.isEmpty());

        time.now = 2 * HOUR;
        sessions.sessionOutcome = Outcome.success(session(Session.Status.ACTIVE, HOUR));
        sessions.calls.clear();
        open();
        assertEquals(Phase.ENDED, state().phase);
        assertTrue(
                "a stale ACTIVE session is closed",
                sessions.calls.contains("endSession:" + SESSION_ID));
    }

    @Test
    public void aRefusedTokenMeansTheSessionWasEndedElsewhere() {
        open();
        viewModel.onVisible();
        sessions.sessionOutcome = Outcome.success(session(Session.Status.ENDED, 100));
        sessions.answerToken(AppError.PERMISSION_DENIED);
        assertEquals(Phase.ENDED, state().phase);
    }

    @Test
    public void aMissingSessionIsAnErrorThatCanBeRetried() {
        sessions.sessionOutcome = Outcome.failure(AppError.SESSION_NOT_FOUND);
        open();
        assertEquals(Phase.ERROR, state().phase);
        assertEquals(AppError.SESSION_NOT_FOUND, state().error);

        sessions.sessionOutcome = Outcome.success(session(Session.Status.ACTIVE, HOUR));
        viewModel.retry();
        assertEquals(Phase.LIVE, state().phase);
    }
}
