package com.presence.attendance.session.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.location.LocationFix;
import com.presence.attendance.session.domain.Geofence;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.session.ui.StartSessionViewModel.Fix;
import com.presence.attendance.session.ui.StartSessionViewModel.Mode;
import com.presence.attendance.session.ui.StartSessionViewModel.State;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeLocationSource;
import com.presence.attendance.testutil.FakeSessionRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class StartSessionViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private static final Geofence CAMPUS = Geofence.around(19.0222, 72.8561, 300);
    private static final Session STARTED =
            new Session(
                    "sessNew", "courseA", "teacherA", Session.Status.ACTIVE, 0, 3_600_000, CAMPUS);

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeSessionRepository sessions = new FakeSessionRepository();
    private final FakeLocationSource location = new FakeLocationSource();
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);
    private StartSessionViewModel viewModel;

    @Before
    public void setUp() {
        auth.signedInAs(Fixtures.TEACHER);
        sessions.campusOutcome = Outcome.success(CAMPUS);
        sessions.startOutcome = Outcome.success(STARTED);
        location.fixOutcome = Outcome.success(new LocationFix(19.076, 72.8777, 12f, false, 0));
        viewModel = new StartSessionViewModel(auth, sessions, location, online, "courseA");
        viewModel.getState().observeForever(state -> {});
    }

    private State state() {
        return viewModel.getState().getValue();
    }

    @Test
    public void defaultsAreSixtyMinutesOnCampus() {
        assertEquals(60, state().durationMin);
        assertEquals(Mode.CAMPUS, state().mode);
        assertTrue(state().canStart());
    }

    @Test
    public void aCampusSessionUsesTheServersGeofence() {
        viewModel.setDuration(45);
        viewModel.start();
        assertEquals(Arrays.asList("loadCampus", "startSession:courseA:45"), sessions.calls);
        assertEquals(300, sessions.lastGeofence.getRadiusM(), 0);
        assertEquals(STARTED, state().started);
        assertEquals("the device never asked for a location", 0, location.requests);
    }

    @Test
    public void aMissingCampusConfigIsExplained() {
        sessions.campusOutcome = Outcome.failure(AppError.CAMPUS_NOT_CONFIGURED);
        viewModel.start();
        assertEquals(AppError.CAMPUS_NOT_CONFIGURED, state().error);
        assertNull(state().started);
        assertFalse(state().busy);
    }

    @Test
    public void hereBuildsAGeofenceAroundTheTeacherWithTheChosenRadius() {
        viewModel.setMode(Mode.HERE);
        assertEquals(Fix.READY, state().fix);
        assertEquals(12, state().accuracyM);
        viewModel.setRadius(150);
        viewModel.start();
        assertEquals("startSession:courseA:60", sessions.calls.get(0));
        assertEquals(19.076, sessions.lastGeofence.getLat(), 0);
        assertEquals(150, sessions.lastGeofence.getRadiusM(), 0);
        assertEquals(Math.cos(Math.toRadians(19.076)), sessions.lastGeofence.getCosLat(), 1e-12);
        assertNotNull(state().started);
    }

    @Test
    public void hereNeedsPrecisePermissionFirst() {
        location.permission = false;
        viewModel.setMode(Mode.HERE);
        assertEquals(Fix.NEEDS_PERMISSION, state().fix);
        assertFalse(state().canStart());
        assertEquals(0, location.requests);

        location.permission = true;
        viewModel.onPermissionResult();
        assertEquals(Fix.READY, state().fix);
        assertTrue(state().canStart());
    }

    @Test
    public void aVagueOrMissingFixCannotStartASession() {
        location.fixOutcome = Outcome.success(new LocationFix(19.076, 72.8777, 180f, false, 0));
        viewModel.setMode(Mode.HERE);
        assertEquals(Fix.FAILED, state().fix);
        assertEquals(AppError.LOCATION_INACCURATE, state().error);
        viewModel.start();
        assertTrue(sessions.calls.isEmpty());

        location.fixOutcome = Outcome.failure(AppError.LOCATION_UNAVAILABLE);
        viewModel.setMode(Mode.CAMPUS);
        viewModel.setMode(Mode.HERE);
        assertEquals(Fix.FAILED, state().fix);
    }

    @Test
    public void durationAndRadiusAreKeptWithinTheirBounds() {
        viewModel.setDuration(1);
        assertEquals(5, state().durationMin);
        viewModel.setDuration(999);
        assertEquals(180, state().durationMin);
        viewModel.setRadius(10);
        assertEquals(50, state().radiusM);
        viewModel.setRadius(900);
        assertEquals(250, state().radiusM);
    }

    @Test
    public void offlineBlocksTheStart() {
        online.setValue(false);
        assertFalse(state().canStart());
        viewModel.start();
        assertEquals(AppError.OFFLINE, state().error);
        assertTrue(sessions.calls.isEmpty());
    }

    @Test
    public void aFailureIsShownAndCanBeRetried() {
        sessions.startOutcome = Outcome.failure(AppError.PERMISSION_DENIED);
        viewModel.start();
        assertEquals(AppError.PERMISSION_DENIED, state().error);
        assertNull(state().started);

        sessions.startOutcome = Outcome.success(STARTED);
        viewModel.start();
        assertEquals(STARTED, state().started);
    }

    @Test
    public void aStartedSessionCannotBeStartedTwice() {
        viewModel.start();
        viewModel.start();
        assertEquals(2, sessions.calls.size());
    }

    private static final Geofence ROOM = Geofence.around(19.0223, 72.8562, 40);

    private StartSessionViewModel fromTimetable(String room, int minutes) {
        final StartSessionViewModel model =
                new StartSessionViewModel(
                        auth, sessions, location, online, "courseA", room, minutes);
        model.getState().observeForever(state -> {});
        return model;
    }

    @Test
    public void aSavedRoomIsChosenAndTheSessionRunsUntilTheClassEnds() {
        sessions.rooms.put("AL-301", ROOM);
        viewModel = fromTimetable("AL-301", 42);
        assertEquals(Mode.ROOM, state().mode);
        assertTrue(state().roomSaved);
        assertEquals(42, state().durationMin);
        assertEquals("no fix is needed", 0, location.requests);
        viewModel.start();
        assertEquals(ROOM, sessions.lastGeofence);
        assertEquals(42, sessions.lastDurationMin);
    }

    @Test
    public void anUnsavedRoomStartsHereAndCanBeSavedForNextTime() {
        viewModel = fromTimetable("AL-301", 60);
        assertEquals(Mode.HERE, state().mode);
        assertFalse(state().roomSaved);
        assertTrue(state().canSaveRoom());
        viewModel.setRadius(60);
        viewModel.saveRoom();
        assertTrue(sessions.calls.contains("saveRoom:AL-301:60"));
        assertEquals(Mode.ROOM, state().mode);
        assertTrue(state().roomSaved);
        assertFalse(state().busy);
    }

    @Test
    public void aRoomThatCannotBeSavedSaysWhyAndStaysHere() {
        sessions.roomFailure = AppError.PERMISSION_DENIED;
        viewModel = fromTimetable("AL-301", 60);
        viewModel.saveRoom();
        assertEquals(AppError.PERMISSION_DENIED, state().error);
        assertEquals(Mode.HERE, state().mode);
        assertFalse(state().busy);
    }

    @Test
    public void withoutARoomNothingCanBeSaved() {
        viewModel.setMode(Mode.HERE);
        assertFalse(state().canSaveRoom());
        viewModel.saveRoom();
        assertTrue(sessions.calls.stream().noneMatch(call -> call.startsWith("saveRoom")));
    }
}
