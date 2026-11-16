package com.presence.attendance.scan.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.presence.attendance.attendance.domain.AttendanceRepository.MarkResult;
import com.presence.attendance.attendance.domain.PendingMark;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.location.LocationFix;
import com.presence.attendance.scan.ui.ScanViewModel.Stage;
import com.presence.attendance.scan.ui.ScanViewModel.State;
import com.presence.attendance.session.domain.Geofence;
import com.presence.attendance.session.domain.QrPayload;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.testutil.FakeAttendanceRepository;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakePendingMarkStore;
import com.presence.attendance.testutil.FakeScheduler;
import com.presence.attendance.testutil.FakeSessionRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class ScanViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private static final String SESSION_ID = "kYp3Qv9sT2mLx7Zb1cDe";
    private static final String TOKEN = "AbCdEfGhIjKlMnOpQrSt_-";
    private static final String QR = QrPayload.build(SESSION_ID, TOKEN);
    private static final long HOUR = 3_600_000L;
    private static final long NANOS_PER_MS = 1_000_000L;
    private static final Geofence AREA = Geofence.around(19.076, 72.8777, 100);

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeSessionRepository sessions = new FakeSessionRepository();
    private final FakeAttendanceRepository attendance = new FakeAttendanceRepository();
    private final FakeScheduler time = new FakeScheduler();
    private final FakePendingMarkStore queue = new FakePendingMarkStore();
    private final int[] syncRequests = {0};
    private ScanViewModel viewModel;

    /** A fix taken now: 20 m north of the centre unless told otherwise. */
    private LocationFix fix(double lat, float accuracyM, boolean mock) {
        return new LocationFix(lat, 72.8777, accuracyM, mock, time.now * NANOS_PER_MS);
    }

    private LocationFix inside() {
        return fix(19.07618, 10f, false);
    }

    @Before
    public void setUp() {
        auth.signedInAs(Fixtures.STUDENT);
        sessions.sessionOutcome =
                Outcome.success(
                        new Session(
                                SESSION_ID,
                                "courseA",
                                "teacherA",
                                Session.Status.ACTIVE,
                                0,
                                HOUR,
                                AREA));
        viewModel =
                new ScanViewModel(
                        auth,
                        sessions,
                        attendance,
                        queue,
                        () -> syncRequests[0]++,
                        time,
                        () -> time.now * NANOS_PER_MS,
                        () -> time.now);
        viewModel.getState().observeForever(state -> {});
    }

    private State state() {
        return viewModel.getState().getValue();
    }

    @Test
    public void theStatusLineFollowsTheLocation() {
        assertFalse(state().locationReady);
        viewModel.onFix(inside());
        assertTrue(state().locationReady);
        viewModel.onFix(fix(19.07618, 250f, false));
        assertFalse("too inaccurate to use", state().locationReady);
    }

    @Test
    public void aValidCodeInsideTheAreaMarksAttendance() {
        viewModel.onFix(inside());
        viewModel.onQrText(QR);
        assertEquals(Collections.singletonList(SESSION_ID + ":" + TOKEN), attendance.marks);
        assertEquals(Stage.DONE, state().stage);
        assertNull(state().rejection);
    }

    @Test
    public void anythingThatIsNotAnAttendanceCodeIsIgnoredAndScanningContinues() {
        viewModel.onFix(inside());
        for (String text :
                new String[] {
                    null, "", "https://example.com", "prs1:bad", QR + "x", "WIFI:S:campus;;"
                }) {
            viewModel.onQrText(text);
            assertEquals(String.valueOf(text), Stage.SCANNING, state().stage);
        }
        assertTrue(attendance.marks.isEmpty());
        viewModel.onQrText(QR);
        assertEquals(Stage.DONE, state().stage);
    }

    @Test
    public void onlyTheFirstValidCodeIsActedOn() {
        viewModel.onFix(inside());
        viewModel.onQrText(QR);
        viewModel.onQrText(QR);
        assertEquals(1, attendance.marks.size());
    }

    @Test
    public void alreadyMarkedIsCaughtBeforeAnythingIsSent() {
        attendance.hasMarkedOutcome = Outcome.success(true);
        viewModel.onFix(inside());
        viewModel.onQrText(QR);
        assertEquals(AppError.ALREADY_MARKED, state().rejection);
        assertTrue(attendance.marks.isEmpty());
        assertTrue("the session was not even read", sessions.calls.isEmpty());
    }

    @Test
    public void aStudentWhoIsNotEnrolledIsToldSo() {
        sessions.sessionOutcome = Outcome.failure(AppError.PERMISSION_DENIED);
        viewModel.onFix(inside());
        viewModel.onQrText(QR);
        assertEquals(AppError.NOT_ENROLLED, state().rejection);
        assertTrue(attendance.marks.isEmpty());
    }

    @Test
    public void anEndedOrExpiredSessionIsRefused() {
        viewModel.onFix(inside());
        sessions.sessionOutcome =
                Outcome.success(
                        new Session(
                                SESSION_ID,
                                "courseA",
                                "teacherA",
                                Session.Status.ENDED,
                                0,
                                HOUR,
                                AREA));
        viewModel.onQrText(QR);
        assertEquals(AppError.SESSION_ENDED, state().rejection);

        setUp();
        time.now = 2 * HOUR;
        viewModel.onFix(inside());
        viewModel.onQrText(QR);
        assertEquals("ACTIVE but past its end", AppError.SESSION_ENDED, state().rejection);
        assertTrue(attendance.marks.isEmpty());
    }

    @Test
    public void aMockLocationIsRejected() {
        viewModel.onFix(fix(19.07618, 10f, true));
        viewModel.onQrText(QR);
        assertEquals(AppError.MOCK_LOCATION, state().rejection);
        assertTrue(attendance.marks.isEmpty());
    }

    @Test
    public void outsideTheAreaIsRejectedWithTheSameMathAsTheRules() {
        // 222 m north with 10 m accuracy, against a 100 m radius.
        viewModel.onFix(fix(19.078, 10f, false));
        viewModel.onQrText(QR);
        assertEquals(AppError.OUTSIDE_AREA, state().rejection);
        assertTrue(attendance.marks.isEmpty());
    }

    @Test
    public void accuracyExtendsTheReachExactlyAsTheRulesAllow() {
        // 105 m north: outside with 4 m accuracy (reach 104), inside with 6 m (reach 106).
        final double lat = 19.076 + 105.0 / 111320.0;
        viewModel.onFix(fix(lat, 4f, false));
        viewModel.onQrText(QR);
        assertEquals(AppError.OUTSIDE_AREA, state().rejection);

        setUp();
        viewModel.onFix(fix(lat, 6f, false));
        viewModel.onQrText(QR);
        assertNull(state().rejection);
        assertEquals(1, attendance.marks.size());
    }

    @Test
    public void aFixThatIsTooOldIsNotUsed() {
        viewModel.onFix(inside());
        time.advance(ScanViewModel.FIX_MAX_AGE_MS + 1);
        viewModel.onQrText(QR);
        assertEquals("still waiting for a fresh fix", Stage.CHECKING, state().stage);
        assertTrue(attendance.marks.isEmpty());
        viewModel.onFix(inside());
        assertEquals(Stage.DONE, state().stage);
        assertNull(state().rejection);
    }

    @Test
    public void withNoFixItWaitsSixSecondsThenGivesAClearReason() {
        viewModel.onQrText(QR);
        assertEquals(Stage.CHECKING, state().stage);
        time.advance(ScanViewModel.FIX_WAIT_MS - 1);
        assertEquals(Stage.CHECKING, state().stage);
        time.advance(1);
        assertEquals(AppError.LOCATION_UNAVAILABLE, state().rejection);
        viewModel.onFix(inside());
        assertTrue("a late fix does not mark after the rejection", attendance.marks.isEmpty());
    }

    @Test
    public void aFixArrivingDuringTheWaitCompletesTheMark() {
        viewModel.onQrText(QR);
        time.advance(3_000);
        viewModel.onFix(inside());
        assertNull(state().rejection);
        assertEquals(1, attendance.marks.size());
        time.advance(10_000);
        assertNull("the timeout was cancelled", state().rejection);
    }

    @Test
    public void aDenialAfterPassingEveryCheckMeansTheCodeExpired() {
        attendance.markOutcome = Outcome.failure(AppError.PERMISSION_DENIED);
        viewModel.onFix(inside());
        viewModel.onQrText(QR);
        assertEquals(AppError.QR_EXPIRED, state().rejection);
    }

    @Test
    public void aRaceWithAnotherDeviceIsReportedAsAlreadyMarked() {
        attendance.markOutcome = Outcome.success(MarkResult.ALREADY_MARKED);
        viewModel.onFix(inside());
        viewModel.onQrText(QR);
        assertEquals(AppError.ALREADY_MARKED, state().rejection);
    }

    @Test
    public void otherFailuresKeepTheirOwnReasonAndAreNotSaved() {
        attendance.markOutcome = Outcome.failure(AppError.UNAUTHENTICATED);
        viewModel.onFix(inside());
        viewModel.onQrText(QR);
        assertEquals(AppError.UNAUTHENTICATED, state().rejection);

        setUp();
        attendance.hasMarkedOutcome = Outcome.failure(AppError.UNKNOWN);
        viewModel.onFix(inside());
        viewModel.onQrText(QR);
        assertEquals(AppError.UNKNOWN, state().rejection);
        assertTrue(queue.marks.isEmpty());
    }

    @Test
    public void theCoordinatesSentAreTheOnesThatWereChecked() {
        final LocationFix checked = inside();
        viewModel.onFix(checked);
        viewModel.onQrText(QR);
        assertEquals(checked, attendance.lastFix);
    }

    // ---- Offline: the scan is saved instead of sent -----------------------------------------

    private void assertSaved() {
        assertEquals(Stage.DONE, state().stage);
        assertTrue(state().queued);
        assertNull(state().rejection);
        final PendingMark saved = queue.only(SESSION_ID);
        assertEquals(Fixtures.STUDENT.getUid(), saved.getStudentId());
        assertEquals(TOKEN, saved.getToken());
        assertEquals(PendingMark.State.PENDING, saved.getState());
        assertEquals(1, syncRequests[0]);
    }

    @Test
    public void withNoConnectionTheScanIsSavedWithoutAskingTheServer() {
        viewModel.onOnline(false);
        viewModel.onFix(inside());
        viewModel.onQrText(QR);
        assertSaved();
        assertTrue(sessions.calls.isEmpty());
        assertTrue(attendance.marks.isEmpty());
    }

    @Test
    public void theSavedScanCarriesTheTimeTheCodeWasRead() {
        // The connection drops mid-check; the fix arrives five seconds after the scan.
        time.now = 1_000_000;
        attendance.hasMarkedOutcome = Outcome.failure(AppError.OFFLINE);
        viewModel.onQrText(QR);
        time.advance(5_000);
        viewModel.onFix(inside());
        assertSaved();
        assertEquals(1_000_000, queue.only(SESSION_ID).getScannedAtMillis());
    }

    @Test
    public void anUnreachableServerAtAnyStepSavesTheScan() {
        viewModel.onFix(inside());
        sessions.sessionOutcome = Outcome.failure(AppError.TIMEOUT);
        viewModel.onQrText(QR);
        assertSaved();
    }

    @Test
    public void aMarkWhoseAnswerNeverCameIsSavedToBeSentAgain() {
        attendance.markOutcome = Outcome.failure(AppError.TIMEOUT);
        viewModel.onFix(inside());
        viewModel.onQrText(QR);
        assertSaved();
        assertEquals("it was attempted once", 1, attendance.marks.size());
    }

    @Test
    public void aRefusalIsNeverSaved() {
        attendance.markOutcome = Outcome.failure(AppError.PERMISSION_DENIED);
        viewModel.onFix(inside());
        viewModel.onQrText(QR);
        assertEquals(AppError.QR_EXPIRED, state().rejection);
        assertFalse(state().queued);
        assertTrue(queue.marks.isEmpty());
    }

    @Test
    public void offlineAMockLocationIsStillRejected() {
        viewModel.onOnline(false);
        viewModel.onFix(fix(19.07618, 10f, true));
        viewModel.onQrText(QR);
        assertEquals(AppError.MOCK_LOCATION, state().rejection);
        assertTrue(queue.marks.isEmpty());
    }

    @Test
    public void offlineWithNoLocationNothingIsSaved() {
        viewModel.onOnline(false);
        viewModel.onQrText(QR);
        time.advance(ScanViewModel.FIX_WAIT_MS);
        assertEquals(AppError.LOCATION_UNAVAILABLE, state().rejection);
        assertTrue(queue.marks.isEmpty());
    }
}
