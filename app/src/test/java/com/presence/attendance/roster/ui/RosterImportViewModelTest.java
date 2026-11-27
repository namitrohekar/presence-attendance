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
import com.presence.attendance.roster.domain.StudentEmoji;
import com.presence.attendance.roster.ui.RosterImportViewModel.Phase;
import com.presence.attendance.roster.ui.RosterImportViewModel.Result;
import com.presence.attendance.roster.ui.RosterImportViewModel.State;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeRosterRepository;
import com.presence.attendance.testutil.Fixtures;
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
    private static final List<RosterEntry> ROSTER =
            Arrays.asList(Fixtures.ENTRY, Fixtures.OTHER_ENTRY);

    /** Three good rows, one already there, and four that must be rejected. */
    private static final String FILE =
            "email,roll no,name,cohort,batch,emoji\n"
                    + A
                    + ",262011005,Aditi Pawar,MC26,1,fox\n"
                    + B
                    + ",262010006,Bhavesh Kale,MC26,,\n"
                    + C
                    + ",262010007,Chirag More,MC26,2,\n"
                    + Fixtures.ENTRY.getEmail()
                    + ",262011001,Meera Joshi,MC26,,\n"
                    + "someone@gmail.com,262010008,Some One,MC26,,\n"
                    + A
                    + ",262010009,Aditi Pawar,MC26,,\n"
                    + "dnaik_mc26@mc.vjti.ac.in,262010002,Dev Naik,MC26,,\n"
                    + "not,enough";

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeRosterRepository roster = new FakeRosterRepository();
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);
    private RosterImportViewModel viewModel;

    @Before
    public void setUp() {
        auth.signedInAs(Fixtures.TEACHER);
        viewModel = new RosterImportViewModel(auth, roster, online);
        viewModel.getState().observeForever(state -> {});
    }

    private State state() {
        return viewModel.getState().getValue();
    }

    @Test
    public void theWholeFileIsValidatedBeforeAnythingIsWritten() {
        viewModel.onFile(FILE, ROSTER);
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
    public void onlyReadyRowsAreWrittenOneAtATimeInFileOrder() {
        viewModel.onFile(FILE, ROSTER);
        viewModel.startImport();
        assertEquals(
                Arrays.asList(
                        "add:" + A + ":262011005:MC26:Aditi Pawar/1/FOX",
                        "add:" + B + ":262010006:MC26:Bhavesh Kale/-/DOLPHIN",
                        "add:" + C + ":262010007:MC26:Chirag More/2/TURTLE"),
                roster.calls);
        assertEquals(Phase.DONE, state().phase);
        assertEquals(3, state().count(Result.IMPORTED));
    }

    @Test
    public void eachRowGetsItsOwnResultAndOneFailureDoesNotStopTheRest() {
        roster.addOutcomeByEmail.put(A, Outcome.success(AddOutcome.ROLL_TAKEN));
        roster.failAdd(B, AppError.OFFLINE);
        viewModel.onFile(FILE, ROSTER);
        viewModel.startImport();
        assertEquals(3, roster.calls.size());
        assertEquals(Result.ROLL_TAKEN, state().results.get(1));
        assertEquals(Result.FAILED, state().results.get(2));
        assertEquals(Result.IMPORTED, state().results.get(3));
        assertEquals(1, state().count(Result.IMPORTED));
    }

    @Test
    public void aRowAddedBySomeoneElseSincethePreviewIsReportedNotOverwritten() {
        roster.addOutcomeByEmail.put(C, Outcome.success(AddOutcome.EMAIL_EXISTS));
        viewModel.onFile(FILE, ROSTER);
        viewModel.startImport();
        assertEquals(Result.EMAIL_EXISTS, state().results.get(3));
    }

    @Test
    public void anUnreadableOrOversizedFileIsRefused() {
        viewModel.onFile(null, ROSTER);
        assertEquals(Phase.IDLE, state().phase);
        assertEquals(AppError.FILE_UNREADABLE, state().fileError);
        assertFalse(state().openPreview);
    }

    @Test
    public void anEmptyFileIsRefused() {
        viewModel.onFile("email,roll no,name,cohort\n\n", ROSTER);
        assertEquals(Phase.IDLE, state().phase);
        assertEquals(RosterCsv.FileProblem.EMPTY, state().fileProblem);
    }

    @Test
    public void aFileWithoutTheColumnNamesIsRefused() {
        viewModel.onFile("email,rollNo,cohort\n" + A + ",262011005,MC26", ROSTER);
        assertEquals(Phase.IDLE, state().phase);
        assertEquals(RosterCsv.FileProblem.NO_HEADER, state().fileProblem);
    }

    @Test
    public void studentsAlreadyThereGetNewDetailsInsteadOfBeingAdded() {
        viewModel.onFile(
                "email,roll no,name,cohort,batch\n"
                        + Fixtures.ENTRY.getEmail()
                        + ",262011001,Meera S. Joshi,MC26,2\n"
                        + A
                        + ",262011005,Aditi Pawar,MC26,\n",
                ROSTER);
        assertEquals(2, state().total);
        assertEquals(1, state().count(RosterCsv.Check.DETAILS_CHANGED));
        viewModel.startImport();
        assertEquals(
                Arrays.asList(
                        "details:" + Fixtures.ENTRY.getEmail() + ":Meera S. Joshi/2/OWL",
                        "add:" + A + ":262011005:MC26:Aditi Pawar/-/OWL"),
                roster.calls);
        assertEquals(Result.UPDATED, state().results.get(1));
        assertEquals(Result.IMPORTED, state().results.get(2));
    }

    @Test
    public void aStudentRemovedSinceThePreviewIsNotRecreatedByAnUpdate() {
        roster.detailsOutcome = Outcome.success(CorrectOutcome.NOT_FOUND);
        viewModel.onFile(
                "email,roll no,name,cohort\n"
                        + Fixtures.ENTRY.getEmail()
                        + ",262011001,Meera S. Joshi,MC26\n",
                ROSTER);
        viewModel.startImport();
        assertEquals(Result.FAILED, state().results.get(1));
    }

    @Test
    public void aFileWithNothingToImportCannotBeStarted() {
        viewModel.onFile(
                "email,roll no,name,cohort\nsomeone@gmail.com,262010008,Some One,MC26", ROSTER);
        assertEquals(0, state().total);
        viewModel.startImport();
        assertEquals(Phase.PREVIEW, state().phase);
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void offlineBlocksTheImport() {
        viewModel.onFile(FILE, ROSTER);
        online.setValue(false);
        viewModel.startImport();
        assertEquals(Phase.PREVIEW, state().phase);
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void aLostSessionWritesNothingAndReportsEveryRow() {
        viewModel.onFile(FILE, ROSTER);
        auth.userOutcome = Outcome.failure(AppError.UNAUTHENTICATED);
        viewModel.startImport();
        assertEquals(Phase.DONE, state().phase);
        assertEquals(3, state().count(Result.FAILED));
        assertTrue(roster.calls.isEmpty());
    }

    @Test
    public void thePreviewIsOpenedOnceAndTheFileIsForgottenOnReset() {
        viewModel.onFile(FILE, ROSTER);
        viewModel.onPreviewOpened();
        assertFalse(state().openPreview);
        assertEquals(Phase.PREVIEW, state().phase);
        viewModel.reset();
        assertEquals(Phase.IDLE, state().phase);
        assertTrue(state().rows.isEmpty());
        assertNull(state().fileError);
    }

    @Test
    public void importingTheSameFileAgainSkipsWhatIsAlreadyThere() {
        final List<RosterEntry> afterFirstImport =
                Arrays.asList(
                        Fixtures.ENTRY,
                        Fixtures.OTHER_ENTRY,
                        new RosterEntry(
                                A,
                                "262011005",
                                "MC26",
                                "teacherA",
                                0,
                                null,
                                "Aditi Pawar",
                                1,
                                StudentEmoji.FOX),
                        new RosterEntry(
                                B,
                                "262010006",
                                "MC26",
                                "teacherA",
                                0,
                                null,
                                "Bhavesh Kale",
                                null,
                                StudentEmoji.DOLPHIN),
                        new RosterEntry(
                                C,
                                "262010007",
                                "MC26",
                                "teacherA",
                                0,
                                null,
                                "Chirag More",
                                2,
                                StudentEmoji.TURTLE));
        viewModel.onFile(FILE, afterFirstImport);
        assertEquals(0, state().total);
        assertEquals(4, state().count(RosterCsv.Check.ALREADY_ON_ROSTER));
    }
}
