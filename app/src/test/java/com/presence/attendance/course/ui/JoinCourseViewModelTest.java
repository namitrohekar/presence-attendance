package com.presence.attendance.course.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.ui.JoinCourseViewModel.State;
import com.presence.attendance.course.ui.JoinCourseViewModel.Step;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeCourseRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class JoinCourseViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeCourseRepository courses = new FakeCourseRepository();
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);
    private JoinCourseViewModel viewModel;

    @Before
    public void setUp() {
        auth.signedInAs(Fixtures.STUDENT);
        courses.findOutcome = Outcome.success(Fixtures.JOIN_CODE);
        courses.joinOutcome = Outcome.success(Fixtures.JOIN_CODE);
        viewModel = new JoinCourseViewModel(auth, courses, online);
        // The connectivity source only feeds the state while something observes it.
        viewModel.getState().observeForever(state -> {});
    }

    private State state() {
        return viewModel.getState().getValue();
    }

    @Test
    public void aValidCodeShowsTheCourseAndTeacherBeforeAnythingIsWritten() {
        viewModel.lookUp(" abc234 ");
        assertEquals(Collections.singletonList("findJoinCode:ABC234"), courses.calls);
        assertEquals(Step.CONFIRM, state().step);
        assertEquals("Data Structures", state().course.getCourseName());
        assertEquals("Asha Kulkarni", state().course.getTeacherName());
    }

    @Test
    public void aMalformedCodeIsRejectedWithoutALookup() {
        for (String typed : new String[] {"", "ABC", "ABCDEFG", "ABC DE", "ABCDE0", "ABCDEO"}) {
            viewModel.lookUp(typed);
            assertTrue(typed, state().codeInvalid);
            assertEquals(Step.ENTER_CODE, state().step);
        }
        assertTrue(courses.calls.isEmpty());
    }

    @Test
    public void anUnknownCodeGetsItsOwnMessage() {
        courses.findOutcome = Outcome.failure(AppError.JOIN_CODE_NOT_FOUND);
        viewModel.lookUp("ABC234");
        assertEquals(Step.ENTER_CODE, state().step);
        assertEquals(AppError.JOIN_CODE_NOT_FOUND, state().error);
        assertFalse(state().codeInvalid);
    }

    @Test
    public void aTeacherCannotJoinTheirOwnCourse() {
        auth.signedInAs(Fixtures.TEACHER);
        viewModel.lookUp("ABC234");
        assertEquals(Step.ENTER_CODE, state().step);
        assertEquals(AppError.OWN_COURSE, state().error);
    }

    @Test
    public void joiningWithARollNumber() {
        viewModel.lookUp("ABC234");
        viewModel.join(" 21CS045 ");
        assertEquals(Arrays.asList("findJoinCode:ABC234", "joinCourse:ABC234"), courses.calls);
        assertEquals("21CS045", courses.lastRollNo);
        assertEquals(Step.JOINED, state().step);
    }

    @Test
    public void aBlankRollNumberIsLeftOut() {
        viewModel.lookUp("ABC234");
        viewModel.join("   ");
        assertNull(courses.lastRollNo);
        assertEquals(Step.JOINED, state().step);
    }

    @Test
    public void joiningTwiceSaysSoAndStaysOnTheConfirmStep() {
        courses.joinOutcome = Outcome.failure(AppError.ALREADY_JOINED);
        viewModel.lookUp("ABC234");
        viewModel.join("");
        assertEquals(Step.CONFIRM, state().step);
        assertEquals(AppError.ALREADY_JOINED, state().error);
        assertEquals("the course is still shown", Fixtures.JOIN_CODE, state().course);
    }

    @Test
    public void joinDoesNothingBeforeACourseIsConfirmed() {
        viewModel.join("21CS045");
        assertTrue(courses.calls.isEmpty());
        assertEquals(Step.ENTER_CODE, state().step);
    }

    @Test
    public void offlineBlocksBothSteps() {
        online.setValue(false);
        viewModel.lookUp("ABC234");
        assertEquals(AppError.OFFLINE, state().error);
        assertTrue(courses.calls.isEmpty());

        online.setValue(true);
        viewModel.lookUp("ABC234");
        online.setValue(false);
        viewModel.join("");
        assertEquals(Step.CONFIRM, state().step);
        assertEquals(AppError.OFFLINE, state().error);
        assertEquals("no membership was written", 1, courses.calls.size());
    }

    @Test
    public void usingADifferentCodeReturnsToTheFirstStep() {
        viewModel.lookUp("ABC234");
        viewModel.useDifferentCode();
        assertEquals(Step.ENTER_CODE, state().step);
        assertNull(state().course);
        assertNull(state().error);
    }
}
