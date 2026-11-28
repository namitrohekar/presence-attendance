package com.presence.attendance.roster.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.roster.domain.RosterCsv;
import com.presence.attendance.roster.domain.RosterEntry;
import com.presence.attendance.roster.domain.RosterRepository.AddOutcome;
import com.presence.attendance.roster.domain.RosterRepository.CorrectOutcome;
import com.presence.attendance.roster.ui.RosterImportViewModel.Phase;
import com.presence.attendance.roster.ui.RosterImportViewModel.Result;
import com.presence.attendance.roster.ui.RosterImportViewModel.State;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeRosterRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class RosterImportViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private static final String A = "apawar_mc26@mc.vjti.ac.in";
    private static final String B = "bkale_mc26@mc.vjti.ac.in";
    private static final String C = "cmore_mc26@mc.vjti.ac.in";

    /** Three good rows, one already there, and four that must be rejected. */
    private static final String FILE =
            "email,roll no,name,lab group\n"
                    + A
                    + ",262011005,Aditi Pawar,1\n"
                    + B
                    + ",262010006,Bhavesh Kale,\n"
                    + C
                    + ",262010007,Chirag More,2\n"
                    + Fixtures.ENTRY.getEmail()
                    + ",262011001,Meera Joshi,\n"
                    + "someone@gmail.com,262010008,Some One,\n"
                    + A
                    + ",262010009,Aditi Pawar,\n"
                    + "dnaik_mc26@mc.vjti.ac.in,262010002,Dev Naik,\n"
                    + "not,enough";

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeRosterRepository roster = new FakeRosterRepository();
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);
    private RosterImportViewModel viewModel;

    @Before
    public void setUp() {
        auth.signedInAs(Fixtures.HOD);
        roster.rosterOutcome =
                Outcome.success(
                        new ArrayList<>(Arrays.asList(Fixtures.ENTRY, Fixtures.OTHER_ENTRY)));
        viewModel = new RosterImportViewModel(auth, roster, online);
        viewModel.getState().observeForever(state -> {});
    }

    private State state() {
        return viewModel.getState().getValue();
    }

    @Test
    public void theWholeFileIsValidatedBeforeAnythingIsWritten() {
        viewModel.onFile(FILE, Fixtures.BATCH);
        assertEquals(Phase.PREVIEW, state().phase);
        assertTrue(state().openPreview);
        assertEquals(8, state().rows.size());
        assertEquals(3, state().total);
        assertEquals(1, state().count(RosterCsv.Check.ALREADY_ON_ROSTER));
        assertEquals(1, state().count(RosterCsv.Check.INVALID_EMAIL));
        assertEquals(1, state().count(RosterCsv.Check.DUPLICATE_EMAIL));
        assertEquals(1, state().count(RosterCsv.Check.ROLL_TAKEN));
        assertEquals(1, state().count(RosterCsv.Check.MALFORMED));
        assertTrue("the preview writes nothing", roster.calls.isEmpty());
    }

    @Test
    public void readyRowsAreAddedToTheBatchOneAtATimeInFileOrder() {
        viewModel.onFile(FILE, Fixtures.BATCH);
        viewModel.startImport();
        assertEquals(
                Arrays.asList(
                        "add:" + A + ":262011005:MC26:Aditi Pawar/1",
                        "add:" + B + ":262010006:MC26:Bhavesh Kale/-",
                        "add:" + C + ":262010007:MC26:Chirag More/2"),
                roster.calls);
        assertEquals(Phase.DONE, state().phase);
        assertEquals(3, state().count(Result.IMPORTED));
    }

    @Test
    public void eachRowGetsItsOwnResultAndOneFailureDoesNotStopTheRest() {
        roster.addOutcomeByEmail.put(A, Outcome.success(AddOutcome.ROLL_TAKEN));
        roster.failAdd(B, AppError.OFFLINE);
        viewModel.onFile(FILE, Fixtures.BATCH);
        viewModel.startImport();
        assertEquals(3, roster.calls.size());
        assertEquals(Result.ROLL_TAKEN, state().results.get(1));
        assertEquals(Result.FAILED, state().results.get(2));
        assertEquals(Result.IMPORTED, state().results.get(3));
    }

    @Test
    public void aStudentAlreadyInAnotherBatchIsRejected() {
        roster.rosterOutcome =
                Outcome.success(
                        Arrays.asList(
                                new RosterEntry(
                                        A,
                                        "252010009",
                                        "MCA25",
                                        "teacherA",
                                        0,
                                        null,
                                        "A P",
                                        null)));
        viewModel.onFile("email,roll no,name\n" + A + ",262011005,Aditi Pawar\n", Fixtures.BATCH);
        assertEquals(1, state().count(RosterCsv.Check.EMAIL_ON_ROSTER));
        assertEquals(0, state().total);
    }

    @Test
    public void studentsAlreadyThereGetNewDetailsInsteadOfBeingAdded() {
        viewModel.onFile(
                "email,roll no,name,lab group\n"
                        + Fixtures.ENTRY.getEmail()
                        + ",262011001,Meera S. Joshi,2\n"
                        + A
                        + ",262011005,Aditi Pawar,\n",
                Fixtures.BATCH);
        assertEquals(2, state().total);
        viewModel.startImport();
        assertEquals(
                Arrays.asList(
                        "details:" + Fixtures.ENTRY.getEmail() + ":Meera S. Joshi/2",
                        "add:" + A + ":262011005:MC26:Aditi Pawar/-"),
                roster.calls);
        assertEquals(Result.UPDATED, state().results.get(1));
        assertEquals(Result.IMPORTED, state().results.get(2));
    }

    @Test
    public void aStudentRemovedSinceThePreviewIsNotRecreatedByAnUpdate() {
        roster.detailsOutcome = Outcome.success(CorrectOutcome.NOT_FOUND);
        viewModel.onFile(
                "email,roll no,name\n" + Fixtures.ENTRY.getEmail() + ",262011001,Meera S. Joshi\n",
                Fixtures.BATCH);
        viewModel.startImport();
        assertEquals(Result.FAILED, state().results.get(1));
    }

    @Test
    public void unreadableEmptyAndHeaderlessFilesAreRefused() {
        viewModel.onFile(null, Fixtures.BATCH);
        assertEquals(AppError.FILE_UNREADABLE, state().fileError);
        viewModel.onFile("email,roll no,name\n\n", Fixtures.BATCH);
        assertEquals(RosterCsv.FileProblem.EMPTY, state().fileProblem);
        viewModel.onFile("email,rollNo,cohort\n" + A + ",262011005,MC26", Fixtures.BATCH);
        assertEquals(RosterCsv.FileProblem.NO_HEADER, state().fileProblem);
        assertEquals(Phase.IDLE, state().phase);
        assertFalse(state().openPreview);
    }

    @Test
    public void aRosterThatCannotBeReadStopsThePreview() {
        roster.rosterOutcome = Outcome.failure(AppError.OFFLINE);
        viewModel.onFile(FILE, Fixtures.BATCH);
        assertEquals(Phase.IDLE, state().phase);
        assertEquals(AppError.OFFLINE, state().fileError);
    }

    @Test
    public void offlineBlocksTheImport() {
        viewModel.onFile(FILE, Fixtures.BATCH);
        online.setValue(false);
        viewModel.startImport();
        assertEquals(Phase.PREVIEW, state().phase);
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void aLostSessionWritesNothingAndReportsEveryRow() {
        viewModel.onFile(FILE, Fixtures.BATCH);
        auth.userOutcome = Outcome.failure(AppError.UNAUTHENTICATED);
        viewModel.startImport();
        assertEquals(Phase.DONE, state().phase);
        assertEquals(3, state().count(Result.FAILED));
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void thePreviewIsOpenedOnceAndTheFileIsForgottenOnReset() {
        viewModel.onFile(FILE, Fixtures.BATCH);
        viewModel.onPreviewOpened();
        assertFalse(state().openPreview);
        viewModel.reset();
        assertEquals(Phase.IDLE, state().phase);
        assertTrue(state().rows.isEmpty());
        assertNull(state().fileError);
    }

    @Test
    public void importingTheSameFileAgainSkipsWhatIsAlreadyThere() {
        final List<RosterEntry> afterFirstImport =
                new ArrayList<>(Arrays.asList(Fixtures.ENTRY, Fixtures.OTHER_ENTRY));
        afterFirstImport.add(
                new RosterEntry(A, "262011005", "MC26", "t", 0, null, "Aditi Pawar", 1));
        afterFirstImport.add(
                new RosterEntry(B, "262010006", "MC26", "t", 0, null, "Bhavesh Kale", null));
        afterFirstImport.add(
                new RosterEntry(C, "262010007", "MC26", "t", 0, null, "Chirag More", 2));
        roster.rosterOutcome = Outcome.success(afterFirstImport);
        viewModel.onFile(FILE, Fixtures.BATCH);
        assertEquals(0, state().total);
        assertEquals(4, state().count(RosterCsv.Check.ALREADY_ON_ROSTER));
    }
}
