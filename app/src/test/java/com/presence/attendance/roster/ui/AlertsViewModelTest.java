package com.presence.attendance.roster.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.roster.domain.IdentityAlert;
import com.presence.attendance.roster.ui.AlertsViewModel.State;
import com.presence.attendance.roster.ui.AlertsViewModel.Status;
import com.presence.attendance.testutil.FakeAlertRepository;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeRosterRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class AlertsViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private static final IdentityAlert STRANGER =
            new IdentityAlert(
                    "a1", IdentityAlert.Type.NON_VJTI_EMAIL, "some.one@gmail.com", null, 1000L);
    private static final IdentityAlert MISMATCH =
            new IdentityAlert(
                    "a2",
                    IdentityAlert.Type.ROLL_MISMATCH,
                    "kdesai_mc26@mc.vjti.ac.in",
                    "262011001",
                    2000L);

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeAlertRepository alerts = new FakeAlertRepository();
    private final FakeRosterRepository roster = new FakeRosterRepository();
    private AlertsViewModel viewModel;

    @Before
    public void setUp() {
        auth.signedInAs(Fixtures.TEACHER);
        roster.rosterOutcome = Outcome.success(Arrays.asList(Fixtures.ENTRY, Fixtures.OTHER_ENTRY));
        viewModel = new AlertsViewModel(auth, alerts, roster);
        viewModel.getState().observeForever(state -> {});
    }

    private State state() {
        return viewModel.getState().getValue();
    }

    @Test
    public void listensForThisTeachersAlertsAndPurgesExpiredOnes() {
        assertEquals(Collections.singletonList("teacherA"), alerts.observed);
        assertEquals(1, alerts.purges);
        assertEquals(Status.LOADING, state().status);
    }

    @Test
    public void alertsArriveLive() {
        alerts.feed.setValue(Outcome.success(Collections.emptyList()));
        assertEquals(Status.EMPTY, state().status);
        alerts.feed.setValue(Outcome.success(Collections.singletonList(STRANGER)));
        assertEquals(Status.CONTENT, state().status);
        assertEquals(1, state().alerts.size());
    }

    @Test
    public void theRosterIsReadOnlyWhenAMismatchNeedsItAndOnlyOnce() {
        alerts.feed.setValue(Outcome.success(Collections.singletonList(STRANGER)));
        assertEquals("no mismatch, no roster read", 0, roster.loads);

        alerts.feed.setValue(Outcome.success(Arrays.asList(MISMATCH, STRANGER)));
        alerts.feed.setValue(Outcome.success(Arrays.asList(MISMATCH, STRANGER)));
        assertEquals(1, roster.loads);
        assertEquals(
                "the typed number is looked up to show whose it is",
                Fixtures.ENTRY.getEmail(),
                state().emailByRollNo.get("262011001"));
    }

    @Test
    public void aRosterFailureStillShowsTheAlerts() {
        roster.rosterOutcome = Outcome.failure(AppError.OFFLINE);
        alerts.feed.setValue(Outcome.success(Collections.singletonList(MISMATCH)));
        assertEquals(Status.CONTENT, state().status);
        assertTrue(state().emailByRollNo.isEmpty());
    }

    @Test
    public void aListenerFailureIsAnErrorAndRetryListensAgain() {
        alerts.feed.setValue(Outcome.failure(AppError.PERMISSION_DENIED));
        assertEquals(Status.ERROR, state().status);
        assertEquals(AppError.PERMISSION_DENIED, state().error);
        viewModel.retry();
        assertEquals(2, alerts.observed.size());
    }

    @Test
    public void withoutASessionNothingIsListenedTo() {
        final FakeAuthRepository nobody = new FakeAuthRepository();
        final FakeAlertRepository untouched = new FakeAlertRepository();
        final AlertsViewModel orphan = new AlertsViewModel(nobody, untouched, roster);
        assertEquals(Status.ERROR, orphan.getState().getValue().status);
        assertTrue(untouched.observed.isEmpty());
    }
}
