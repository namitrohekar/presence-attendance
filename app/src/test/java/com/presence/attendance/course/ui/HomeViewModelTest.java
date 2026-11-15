package com.presence.attendance.course.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.ui.HomeViewModel.State;
import com.presence.attendance.course.ui.HomeViewModel.Status;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeCourseRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;

public final class HomeViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeCourseRepository courses = new FakeCourseRepository();

    private State load() {
        return new HomeViewModel(auth, courses).getState().getValue();
    }

    @Test
    public void facultyLoadTheCoursesTheyTeach() {
        auth.signedInAs(Fixtures.TEACHER);
        courses.teacherCourses = Outcome.success(Arrays.asList(Fixtures.COURSE));
        final State state = load();
        assertEquals(Status.CONTENT, state.status);
        assertEquals(Fixtures.TEACHER, state.user);
        assertEquals(1, state.courses.size());
        assertEquals(Collections.singletonList("loadTeacherCourses:teacherA"), courses.calls);
    }

    @Test
    public void studentsLoadTheCoursesTheyJoined() {
        auth.signedInAs(Fixtures.STUDENT);
        courses.studentCourses = Outcome.success(Arrays.asList(Fixtures.COURSE));
        final State state = load();
        assertEquals(Status.CONTENT, state.status);
        assertEquals(Collections.singletonList("loadStudentCourses:studentA"), courses.calls);
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
    public void refreshPicksUpANewCourse() {
        auth.signedInAs(Fixtures.TEACHER);
        final HomeViewModel viewModel = new HomeViewModel(auth, courses);
        assertEquals(Status.EMPTY, viewModel.getState().getValue().status);

        courses.teacherCourses = Outcome.success(Arrays.asList(Fixtures.COURSE));
        viewModel.refresh();
        assertEquals(Status.CONTENT, viewModel.getState().getValue().status);
    }

    @Test
    public void signOutClearsTheSessionAndTheScreen() {
        auth.signedInAs(Fixtures.TEACHER);
        final HomeViewModel viewModel = new HomeViewModel(auth, courses);
        viewModel.signOut();
        assertTrue(auth.signedOut);
        assertEquals(Status.SIGNED_OUT, viewModel.getState().getValue().status);
        assertNull(viewModel.getState().getValue().user);
    }
}
