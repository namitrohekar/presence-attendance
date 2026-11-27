package com.presence.attendance.roster.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.roster.domain.RosterRepository.CorrectOutcome;
import com.presence.attendance.roster.domain.StudentDetails;
import com.presence.attendance.roster.domain.StudentEmoji;
import com.presence.attendance.roster.ui.RosterEntryViewModel.Problem;
import com.presence.attendance.roster.ui.RosterEntryViewModel.State;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeRosterRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class RosterEntryViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private static final String EMAIL = Fixtures.ENTRY.getEmail();

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeRosterRepository roster = new FakeRosterRepository();
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);
    private RosterEntryViewModel viewModel;

    @Before
    public void setUp() {
        auth.signedInAs(Fixtures.TEACHER);
        viewModel =
                new RosterEntryViewModel(
                        auth,
                        roster,
                        online,
                        EMAIL,
                        "262011001",
                        "MC26",
                        new StudentDetails("Meera Joshi", null, StudentEmoji.OWL));
        viewModel.getState().observeForever(state -> {});
    }

    private State state() {
        return viewModel.getState().getValue();
    }

    @Test
    public void detailsAreSavedWithoutAReasonAndNormalised() {
        viewModel.saveDetails("  Meera   S. Joshi ", " 2 ", StudentEmoji.PEACOCK);
        assertEquals(
                Collections.singletonList("details:" + EMAIL + ":Meera S. Joshi/2/PEACOCK"),
                roster.calls);
        assertTrue(state().done);
    }

    @Test
    public void detailsWithoutAPickTakeTheRollNumbersEmoji() {
        viewModel.saveDetails("Meera Joshi", "", null);
        // 262011001 ends in 1: the second emoji.
        assertEquals(
                Collections.singletonList("details:" + EMAIL + ":Meera Joshi/-/TIGER"),
                roster.calls);
    }

    @Test
    public void unchangedDetailsCloseTheSheetWithoutAWrite() {
        viewModel.saveDetails(" Meera Joshi ", "", StudentEmoji.OWL);
        assertTrue(state().done);
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void invalidDetailsAreFlagged() {
        viewModel.saveDetails("   ", "", null);
        assertEquals(Problem.NAME_INVALID, state().problem);
        viewModel.saveDetails("Meera Joshi", "0", null);
        assertEquals(Problem.BATCH_INVALID, state().problem);
        viewModel.saveDetails("Meera Joshi", "21", null);
        assertEquals(Problem.BATCH_INVALID, state().problem);
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void detailsOfAStudentRemovedMeanwhileAreReported() {
        roster.detailsOutcome = Outcome.success(CorrectOutcome.NOT_FOUND);
        viewModel.saveDetails("Meera S. Joshi", "", null);
        assertEquals(Problem.GONE, state().problem);
    }

    @Test
    public void detailsNeedAConnection() {
        online.setValue(false);
        viewModel.saveDetails("Meera S. Joshi", "", null);
        assertEquals(AppError.OFFLINE, state().error);
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void aCorrectionSendsTheNewValuesAndTheReason() {
        viewModel.save(" 262011099 ", " mc25 ", "  Mistyped in the list ");
        assertEquals(
                Collections.singletonList(
                        "UPDATE:" + EMAIL + ":262011099:MC25:Mistyped in the list"),
                roster.calls);
        assertTrue(state().done);
    }

    @Test
    public void everyActionNeedsAReason() {
        viewModel.save("262011099", "MC26", "   ");
        assertEquals(Problem.REASON_MISSING, state().problem);
        viewModel.release("");
        assertEquals(Problem.REASON_MISSING, state().problem);
        viewModel.remove(" ");
        assertEquals(Problem.REASON_MISSING, state().problem);
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void aCorrectionThatChangesNothingIsRefused() {
        viewModel.save("262011001", "mc26", "No change");
        assertEquals(Problem.UNCHANGED, state().problem);
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void invalidValuesAreFlagged() {
        viewModel.save("123", "MC26", "Reason");
        assertEquals(Problem.ROLL_NO_INVALID, state().problem);
        viewModel.save("262011099", "m", "Reason");
        assertEquals(Problem.COHORT_INVALID, state().problem);
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void releaseAndRemoveSendNoNewValues() {
        viewModel.release("New Google account");
        assertEquals("RELEASE:" + EMAIL + ":null:null:New Google account", roster.calls.get(0));
    }

    @Test
    public void removeIsItsOwnAction() {
        viewModel.remove("Left the programme");
        assertEquals("REMOVE:" + EMAIL + ":null:null:Left the programme", roster.calls.get(0));
        assertTrue(state().done);
    }

    @Test
    public void aTakenRollNumberAndAVanishedEntryAreReported() {
        roster.correctOutcome = Outcome.success(CorrectOutcome.ROLL_TAKEN);
        viewModel.save("262010002", "MC26", "Swap");
        assertEquals(Problem.ROLL_TAKEN, state().problem);
        assertFalse(state().done);

        roster.correctOutcome = Outcome.success(CorrectOutcome.NOT_FOUND);
        viewModel.remove("Duplicate");
        assertEquals(Problem.GONE, state().problem);
    }

    @Test
    public void offlineBlocksEveryAction() {
        online.setValue(false);
        viewModel.remove("Left");
        assertEquals(AppError.OFFLINE, state().error);
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void aFailureIsShownAndNothingIsMarkedDone() {
        roster.correctOutcome = Outcome.failure(AppError.PERMISSION_DENIED);
        viewModel.release("Reason");
        assertEquals(AppError.PERMISSION_DENIED, state().error);
        assertFalse(state().done);
        assertNull(state().problem);
    }
}
