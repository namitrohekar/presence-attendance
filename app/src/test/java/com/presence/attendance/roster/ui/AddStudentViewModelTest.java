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
    private static final String NAME = "Aditi Pawar";

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeRosterRepository roster = new FakeRosterRepository();
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);
    private AddStudentViewModel viewModel;

    @Before
    public void setUp() {
        auth.signedInAs(Fixtures.HOD);
        viewModel = new AddStudentViewModel(auth, roster, online, "MC26", 4);
        viewModel.getState().observeForever(state -> {});
    }

    private State state() {
        return viewModel.getState().getValue();
    }

    private void submit(String email, String rollNo) {
        viewModel.submit(email, rollNo, NAME, "");
    }

    @Test
    public void aValidStudentIsAddedToTheBatchWithNormalisedValues() {
        viewModel.submit(" APawar_MC26@mc.vjti.ac.in ", " 262011005 ", "  Aditi   Pawar ", " B3 ");
        assertEquals(
                Collections.singletonList("add:" + EMAIL + ":262011005:MC26:Aditi Pawar/3"),
                roster.calls);
        assertTrue(state().added);
    }

    @Test
    public void noLabGroupYetIsFine() {
        submit(EMAIL, "262011005");
        assertEquals(
                Collections.singletonList("add:" + EMAIL + ":262011005:MC26:Aditi Pawar/-"),
                roster.calls);
    }

    @Test
    public void everyInvalidFieldIsFlaggedAndNothingIsWritten() {
        viewModel.submit("apawar@gmail.com", "12345", " ", "5");
        assertTrue(state().invalid.email);
        assertTrue(state().invalid.rollNo);
        assertTrue(state().invalid.name);
        assertTrue("the batch has four groups", state().invalid.labGroup);
        assertTrue(roster.calls.isEmpty());

        viewModel.submit(EMAIL, "262011005", "a".repeat(81), "2");
        assertFalse(state().invalid.email);
        assertTrue(state().invalid.name);
        assertFalse(state().invalid.labGroup);
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void lookAlikeDomainsAreRefused() {
        for (String email :
                new String[] {"a@x.mc.vjti.ac.in", "a@mc.vjti.ac.in.evil.com", "a@vjti.ac.in"}) {
            submit(email, "262011005");
            assertTrue(email, state().invalid.email);
        }
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void conflictsAreReportedAgainstTheirField() {
        roster.addOutcome = Outcome.success(AddOutcome.EMAIL_EXISTS);
        submit(EMAIL, "262011005");
        assertEquals(AddOutcome.EMAIL_EXISTS, state().conflict);
        assertFalse(state().added);

        roster.addOutcome = Outcome.success(AddOutcome.ROLL_TAKEN);
        submit(EMAIL, "262011005");
        assertEquals(AddOutcome.ROLL_TAKEN, state().conflict);
    }

    @Test
    public void offlineBlocksTheWrite() {
        online.setValue(false);
        submit(EMAIL, "262011005");
        assertEquals(AppError.OFFLINE, state().error);
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void aFailureIsShownAndCanBeRetried() {
        roster.addOutcome = Outcome.failure(AppError.PERMISSION_DENIED);
        submit(EMAIL, "262011005");
        assertEquals(AppError.PERMISSION_DENIED, state().error);
        assertFalse(state().busy);

        roster.addOutcome = Outcome.success(AddOutcome.ADDED);
        submit(EMAIL, "262011005");
        assertNull(state().error);
        assertTrue(state().added);
    }
}
