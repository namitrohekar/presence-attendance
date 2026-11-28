package com.presence.attendance.course.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.ui.HomeViewModel.State;
import com.presence.attendance.course.ui.HomeViewModel.Status;
import com.presence.attendance.department.domain.Batch;
import com.presence.attendance.department.domain.Timetable;
import com.presence.attendance.department.domain.TimetableSlot;
import com.presence.attendance.session.domain.Geofence;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeBatchRepository;
import com.presence.attendance.testutil.FakeCourseRepository;
import com.presence.attendance.testutil.FakeSessionRepository;
import com.presence.attendance.testutil.Fixtures;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;

public final class HomeViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeCourseRepository courses = new FakeCourseRepository();
    private final FakeSessionRepository sessions = new FakeSessionRepository();
    private final FakeBatchRepository batches = new FakeBatchRepository();
    private static final long NOW = 1_000_000L;

    private State load() {
        return new HomeViewModel(auth, courses, sessions, batches, () -> NOW, ZoneOffset.UTC)
                .getState()
                .getValue();
    }

    @Test
    public void facultyLoadTheCoursesTheyTeach() {
        auth.signedInAs(Fixtures.TEACHER);
        courses.teacherCourses = Outcome.success(Arrays.asList(Fixtures.COURSE));
        final State state = load();
        assertEquals(Status.CONTENT, state.status);
        assertEquals(Fixtures.TEACHER, state.user);
        assertEquals(1, state.courses.size());
        assertEquals(
                Arrays.asList("syncSubjects:teacherA", "loadTeacherCourses:teacherA"),
                courses.calls);
    }

    @Test
    public void studentsLoadTheCoursesTheyJoined() {
        auth.signedInAs(Fixtures.STUDENT);
        courses.studentCourses = Outcome.success(Arrays.asList(Fixtures.COURSE));
        final State state = load();
        assertEquals(Status.CONTENT, state.status);
        assertEquals(
                Arrays.asList("syncSubjects:studentA", "loadStudentCourses:studentA"),
                courses.calls);
    }

    @Test
    public void noCoursesIsTheEmptyStateNotAnError() {
        auth.signedInAs(Fixtures.STUDENT);
        final State state = load();
        assertEquals(Status.EMPTY, state.status);
        assertEquals(
                "the role is still known, for the empty-state action",
                Fixtures.STUDENT,
                state.user);
        assertNull(state.error);
    }

    @Test
    public void aFailedCourseLoadIsAnErrorWithItsCause() {
        auth.signedInAs(Fixtures.TEACHER);
        courses.teacherCourses = Outcome.failure(AppError.OFFLINE);
        final State state = load();
        assertEquals(Status.ERROR, state.status);
        assertEquals(AppError.OFFLINE, state.error);
    }

    @Test
    public void aFailedProfileLoadIsAnError() {
        auth.userOutcome = Outcome.failure(AppError.OFFLINE);
        final State state = load();
        assertEquals(Status.ERROR, state.status);
        assertTrue("no courses were requested without a user", courses.calls.isEmpty());
    }

    @Test
    public void aMissingSessionSendsTheUserToSignIn() {
        auth.userOutcome = Outcome.failure(AppError.UNAUTHENTICATED);
        assertEquals(Status.SIGNED_OUT, load().status);
    }

    @Test
    public void anUnregisteredStudentIsSentToRegistration() {
        auth.userOutcome = Outcome.failure(AppError.REGISTRATION_REQUIRED);
        assertEquals(Status.REGISTRATION_REQUIRED, load().status);
        assertTrue(courses.calls.isEmpty());
    }

    @Test
    public void anAccountThatIsNoLongerEligibleIsSignedOut() {
        auth.userOutcome = Outcome.failure(AppError.NOT_VJTI_ACCOUNT);
        assertEquals(Status.SIGNED_OUT, load().status);
    }

    @Test
    public void facultySeeWhichCourseHasASessionRunning() {
        auth.signedInAs(Fixtures.TEACHER);
        courses.teacherCourses = Outcome.success(Arrays.asList(Fixtures.COURSE));
        final Geofence area = Geofence.around(19.076, 72.8777, 100);
        sessions.activeOutcome =
                Outcome.success(
                        Arrays.asList(
                                new Session(
                                        "sessLive",
                                        "courseA",
                                        "teacherA",
                                        Session.Status.ACTIVE,
                                        NOW - 60_000,
                                        NOW + 60_000,
                                        area),
                                // Still ACTIVE on the server, but its end time has passed.
                                new Session(
                                        "sessStale",
                                        "courseB",
                                        "teacherA",
                                        Session.Status.ACTIVE,
                                        NOW - 120_000,
                                        NOW - 1,
                                        area)));
        final State state = load();
        assertEquals("sessLive", state.liveSessionByCourse.get("courseA"));
        assertEquals(
                "an expired session is not shown as live", 1, state.liveSessionByCourse.size());
    }

    @Test
    public void studentsNeverQueryActiveSessions() {
        auth.signedInAs(Fixtures.STUDENT);
        courses.studentCourses = Outcome.success(Arrays.asList(Fixtures.COURSE));
        load();
        assertTrue(sessions.calls.isEmpty());
    }

    @Test
    public void refreshPicksUpANewCourse() {
        auth.signedInAs(Fixtures.TEACHER);
        final HomeViewModel viewModel =
                new HomeViewModel(auth, courses, sessions, batches, () -> NOW, ZoneOffset.UTC);
        assertEquals(Status.EMPTY, viewModel.getState().getValue().status);

        courses.teacherCourses = Outcome.success(Arrays.asList(Fixtures.COURSE));
        viewModel.refresh();
        assertEquals(Status.CONTENT, viewModel.getState().getValue().status);
    }

    @Test
    public void signOutClearsTheSessionAndTheScreen() {
        auth.signedInAs(Fixtures.TEACHER);
        final HomeViewModel viewModel =
                new HomeViewModel(auth, courses, sessions, batches, () -> NOW, ZoneOffset.UTC);
        viewModel.signOut();
        assertTrue(auth.signedOut);
        assertEquals(Status.SIGNED_OUT, viewModel.getState().getValue().status);
        assertNull(viewModel.getState().getValue().user);
    }

    @Test
    public void theHodSeesTheDepartmentsBatchesEvenWithNoCourses() {
        auth.signedInAs(Fixtures.HOD);
        batches.batches.add(Fixtures.BATCH);
        final State state = load();
        assertEquals(Status.CONTENT, state.status);
        assertEquals(Arrays.asList(Fixtures.BATCH), state.batches);
    }

    @Test
    public void ateacherWhoIsNotTheHodSeesNoBatches() {
        auth.signedInAs(Fixtures.TEACHER);
        batches.batches.add(Fixtures.BATCH);
        final State state = load();
        assertEquals(Status.EMPTY, state.status);
        assertTrue(state.batches.isEmpty());
    }

    @Test
    public void theHodSeesAnErrorWhenTheBatchesCannotBeRead() {
        auth.signedInAs(Fixtures.HOD);
        batches.failure = AppError.OFFLINE;
        assertEquals(Status.ERROR, load().status);
    }

    /** Monday 30 November 2026, 10:45 UTC. */
    private static final long MONDAY = Instant.parse("2026-11-30T10:45:00Z").toEpochMilli();

    private static TimetableSlot slot(int day, String start, String courseId) {
        return new TimetableSlot(
                day, start, "12:25", courseId, TimetableSlot.Type.LECTURE, "AL-301", "SJ");
    }

    @Test
    public void aFailedSubjectSyncStillListsTheCourses() {
        auth.signedInAs(Fixtures.STUDENT);
        courses.syncOutcome = Outcome.failure(AppError.OFFLINE);
        courses.studentCourses = Outcome.success(Arrays.asList(Fixtures.COURSE));
        assertEquals(Status.CONTENT, load().status);
    }

    @Test
    public void todayShowsTheDaysClassesOfTheUsersOwnSubjectsInOrder() {
        auth.signedInAs(Fixtures.STUDENT);
        courses.studentCourses =
                Outcome.success(Arrays.asList(Fixtures.LECTURE, Fixtures.LAB, Fixtures.COURSE));
        batches.batches.add(Fixtures.BATCH);
        batches.timetables.put(
                "MC26S1",
                new Timetable(
                        "MC26",
                        1,
                        Arrays.asList(
                                slot(1, "13:30", Fixtures.LECTURE.getId()),
                                slot(2, "09:30", Fixtures.LECTURE.getId()),
                                slot(1, "10:30", Fixtures.LAB.getId()),
                                slot(1, "10:30", "MC26S1OSG2"))));
        final HomeViewModel viewModel =
                new HomeViewModel(auth, courses, sessions, batches, () -> MONDAY, ZoneOffset.UTC);
        final State state = viewModel.getState().getValue();
        assertEquals(
                Arrays.asList(
                        slot(1, "10:30", Fixtures.LAB.getId()),
                        slot(1, "13:30", Fixtures.LECTURE.getId())),
                state.today);
        assertEquals(Fixtures.LAB, state.courseOf(state.today.get(0)));
        assertEquals(10 * 60 + 45, viewModel.minutesNow());
        assertTrue("the HOD section stays hidden", state.batches.isEmpty());
    }

    @Test
    public void aBatchThatMovedOnShowsNoClassesFromItsLastSemester() {
        auth.signedInAs(Fixtures.STUDENT);
        courses.studentCourses = Outcome.success(Arrays.asList(Fixtures.LECTURE));
        batches.batches.add(new Batch("MC", 2026, 2, 2, 70, 4));
        final State state = load();
        assertTrue(state.today.isEmpty());
        assertTrue(batches.calls.isEmpty());
    }

    @Test
    public void coursesWithoutABatchNeedNoBatches() {
        auth.signedInAs(Fixtures.STUDENT);
        courses.studentCourses = Outcome.success(Arrays.asList(Fixtures.COURSE));
        batches.failure = AppError.OFFLINE;
        assertEquals(Status.CONTENT, load().status);
    }

    @Test
    public void unreadableBatchesStillShowAStudentsSubjects() {
        auth.signedInAs(Fixtures.STUDENT);
        courses.studentCourses = Outcome.success(Arrays.asList(Fixtures.LECTURE));
        batches.failure = AppError.OFFLINE;
        final State state = load();
        assertEquals(Status.CONTENT, state.status);
        assertTrue(state.today.isEmpty());
    }
}
