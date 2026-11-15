package com.presence.attendance.roster.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.roster.domain.RosterRepository.AddOutcome;
import com.presence.attendance.roster.ui.AddStudentViewModel.State;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeRosterRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class AddStudentViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private static final String EMAIL = "apawar_mc26@mc.vjti.ac.in";

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeRosterRepository roster = new FakeRosterRepository();
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);
    private AddStudentViewModel viewModel;

    @Before
    public void setUp() {
        auth.signedInAs(Fixtures.TEACHER);
        viewModel = new AddStudentViewModel(auth, roster, online);
        viewModel.getState().observeForever(state -> {});
    }

    private State state() {
        return viewModel.getState().getValue();
    }

    @Test
    public void aValidStudentIsAddedWithNormalisedValues() {
        viewModel.submit("  APawar_MC26@mc.vjti.ac.in ", " 262011005 ", " mc26 ");
        assertEquals(Collections.singletonList("add:" + EMAIL + ":262011005:MC26"), roster.calls);
        assertTrue(state().added);
    }

    @Test
    public void everyInvalidFieldIsFlaggedAndNothingIsWritten() {
        viewModel.submit("apawar@gmail.com", "12345", "m");
        assertTrue(state().emailInvalid);
        assertTrue(state().rollNoInvalid);
        assertTrue(state().cohortInvalid);
        assertTrue(roster.calls.isEmpty());

        viewModel.submit(EMAIL, "262011005", "MC-26");
        assertFalse(state().emailInvalid);
        assertFalse(state().rollNoInvalid);
        assertTrue(state().cohortInvalid);
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void lookAlikeDomainsAreRefused() {
        for (String email :
                new String[] {
                    "a@x.mc.vjti.ac.in",
                    "a@mc.vjti.ac.in.evil.com",
                    "a@vjti.ac.in",
                    "a@mc.vjti.ac.in@gmail.com"
                }) {
            viewModel.submit(email, "262011005", "MC26");
            assertTrue(email, state().emailInvalid);
        }
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void conflictsAreReportedAgainstTheirField() {
        roster.addOutcome = Outcome.success(AddOutcome.EMAIL_EXISTS);
        viewModel.submit(EMAIL, "262011005", "MC26");
        assertEquals(AddOutcome.EMAIL_EXISTS, state().conflict);
        assertFalse(state().added);

        roster.addOutcome = Outcome.success(AddOutcome.ROLL_TAKEN);
        viewModel.submit(EMAIL, "262011005", "MC26");
        assertEquals(AddOutcome.ROLL_TAKEN, state().conflict);
    }

    @Test
    public void offlineBlocksTheWrite() {
        online.setValue(false);
        viewModel.submit(EMAIL, "262011005", "MC26");
        assertEquals(AppError.OFFLINE, state().error);
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void aFailureIsShownAndCanBeRetried() {
        roster.addOutcome = Outcome.failure(AppError.PERMISSION_DENIED);
        viewModel.submit(EMAIL, "262011005", "MC26");
        assertEquals(AppError.PERMISSION_DENIED, state().error);
        assertFalse(state().busy);

        roster.addOutcome = Outcome.success(AddOutcome.ADDED);
        viewModel.submit(EMAIL, "262011005", "MC26");
        assertNull(state().error);
        assertTrue(state().added);
    }
}
