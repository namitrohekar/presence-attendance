package com.presence.attendance.course.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.ui.CreateCourseViewModel.State;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeCourseRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class CreateCourseViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeCourseRepository courses = new FakeCourseRepository();
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);
    private CreateCourseViewModel viewModel;

    @Before
    public void setUp() {
        auth.signedInAs(Fixtures.TEACHER);
        viewModel = new CreateCourseViewModel(auth, courses, online);
        // The connectivity source only feeds the state while something observes it.
        viewModel.getState().observeForever(state -> {});
    }

    private State state() {
        return viewModel.getState().getValue();
    }

    @Test
    public void aValidFormCreatesTheCourseAndShowsItsJoinCode() {
        courses.createOutcome = Outcome.success(Fixtures.COURSE);
        viewModel.submit("  Data Structures ", " CS201 ");
        assertEquals(
                "input is trimmed",
                Collections.singletonList("createCourse:Data Structures:CS201"),
                courses.calls);
        assertEquals("ABC234", state().created.getJoinCode());
        assertFalse(state().submitting);
    }

    @Test
    public void missingFieldsAreFlaggedAndNothingIsWritten() {
        viewModel.submit("   ", "");
        assertTrue(state().nameMissing);
        assertTrue(state().codeMissing);
        assertTrue(courses.calls.isEmpty());

        viewModel.submit("Data Structures", "");
        assertFalse(state().nameMissing);
        assertTrue(state().codeMissing);
        assertTrue(courses.calls.isEmpty());
    }

    @Test
    public void offlineBlocksTheWriteWithAnExplanation() {
        online.setValue(false);
        assertFalse(state().online);
        viewModel.submit("Data Structures", "CS201");
        assertEquals(AppError.OFFLINE, state().error);
        assertTrue("nothing is queued for later", courses.calls.isEmpty());
    }

    @Test
    public void comingBackOnlineEnablesTheAction() {
        online.setValue(false);
        online.setValue(true);
        assertTrue(state().online);
    }

    @Test
    public void aFailureIsShownAndTheFormCanBeSubmittedAgain() {
        courses.createOutcome = Outcome.failure(AppError.PERMISSION_DENIED);
        viewModel.submit("Data Structures", "CS201");
        assertEquals(AppError.PERMISSION_DENIED, state().error);
        assertNull(state().created);
        assertFalse(state().submitting);

        courses.createOutcome = Outcome.success(Fixtures.COURSE);
        viewModel.submit("Data Structures", "CS201");
        assertNull(state().error);
        assertEquals(Fixtures.COURSE, state().created);
    }

    @Test
    public void aCreatedCourseCannotBeSubmittedTwice() {
        courses.createOutcome = Outcome.success(Fixtures.COURSE);
        viewModel.submit("Data Structures", "CS201");
        viewModel.submit("Data Structures", "CS201");
        assertEquals(1, courses.calls.size());
    }

    @Test
    public void aLostSessionIsReported() {
        auth.userOutcome = Outcome.failure(AppError.UNAUTHENTICATED);
        viewModel.submit("Data Structures", "CS201");
        assertEquals(AppError.UNAUTHENTICATED, state().error);
        assertTrue(courses.calls.isEmpty());
    }
}
