package com.presence.attendance.department.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.domain.Course;
import com.presence.attendance.course.domain.CourseRepository.SaveOutcome;
import com.presence.attendance.department.domain.Batch;
import com.presence.attendance.department.domain.FacultyMember;
import com.presence.attendance.department.ui.SubjectsViewModel.Change;
import com.presence.attendance.department.ui.SubjectsViewModel.Phase;
import com.presence.attendance.department.ui.SubjectsViewModel.State;
import com.presence.attendance.department.ui.SubjectsViewModel.Status;
import com.presence.attendance.roster.domain.RosterCsv;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeBatchRepository;
import com.presence.attendance.testutil.FakeCourseRepository;
import com.presence.attendance.testutil.FakeFacultyRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class SubjectsViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private static final FacultyMember GS =
            new FacultyMember("gs@vjti.ac.in", "Gajanan Sidam", "GS");
    private static final FacultyMember SJ =
            new FacultyMember("sj@vjti.ac.in", "Sanjay Joshi", "SJ");

    /** Two OS lab groups taken by GS, a lecture by SJ, a lab by someone not on the list. */
    private static final String FILE =
            "day,start,end,code,subject,type,batch,faculty,room\n"
                    + "MON,10:30,12:25,OS,Operating System Lab,Lab,1,GS,\n"
                    + "TUE,10:30,12:25,OS,Operating System Lab,Lab,2,GS,\n"
                    + "WED,09:30,10:25,MIS,Management Information System,Lecture,,SJ,AL-301\n"
                    + "THU,10:30,12:25,MP,Mini Project,Lab,1,RM,\n"
                    + "XYZ,10:30,12:25,MP,Mini Project,Lab,2,RM,\n";

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeCourseRepository courses = new FakeCourseRepository();
    private final FakeBatchRepository batches = new FakeBatchRepository();
    private final FakeFacultyRepository faculty = new FakeFacultyRepository();
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);
    private SubjectsViewModel viewModel;

    @Before
    public void setUp() {
        auth.signedInAs(Fixtures.HOD);
        batches.batches.add(Fixtures.BATCH);
        faculty.members.add(GS);
        faculty.members.add(SJ);
        viewModel = newViewModel();
    }

    private SubjectsViewModel newViewModel() {
        final SubjectsViewModel model =
                new SubjectsViewModel(auth, courses, batches, faculty, online, "MC26");
        model.getState().observeForever(state -> {});
        return model;
    }

    private State state() {
        return viewModel.getState().getValue();
    }

    private static Course subject(String id, String email, int group) {
        return Course.subject(id, "", email, "Operating System Lab", "OS", 75, "MC26", 1, group);
    }

    @Test
    public void theBatchsSemesterSubjectsAndTimetableAreLoaded() {
        courses.subjects.add(subject("MC26S1OSG1", GS.getEmail(), 1));
        viewModel = newViewModel();
        assertEquals(Status.CONTENT, state().status);
        assertEquals(1, state().subjects.size());
        assertNull("nothing imported yet", state().timetable);
        assertTrue(courses.calls.contains("loadSubjects:MC26:1"));
        assertEquals(GS, state().teacher(GS.getEmail()));
    }

    @Test
    public void aFileIsPreviewedWithMatchedTeachersAndNothingIsWritten() {
        viewModel.onFile(FILE);
        assertEquals(Phase.PREVIEW, state().phase);
        assertTrue(state().openPreview);
        assertNotNull(state().plan);
        assertEquals(4, state().plan.subjects.size());
        assertEquals(4, state().plan.timetable.getSlots().size());
        assertEquals(1, state().rejected());
        assertEquals(4, state().count(Change.NEW));
        assertEquals(GS, state().plan.subjects.get(0).teacher);
        assertNull("RM is not on the list", state().plan.subjects.get(3).teacher);
        assertTrue(courses.calls.stream().noneMatch(call -> call.startsWith("saveSubject")));
    }

    @Test
    public void savingWritesEachSubjectThenTheTimetable() {
        viewModel.onFile(FILE);
        viewModel.save();
        assertEquals(Phase.DONE, state().phase);
        assertEquals(4, state().saved);
        final List<String> saves = new ArrayList<>();
        for (String call : courses.calls) {
            if (call.startsWith("saveSubject")) {
                saves.add(call);
            }
        }
        assertEquals(
                List.of(
                        "saveSubject:MC26S1OSG1:gs@vjti.ac.in",
                        "saveSubject:MC26S1OSG2:gs@vjti.ac.in",
                        "saveSubject:MC26S1MIS:sj@vjti.ac.in",
                        "saveSubject:MC26S1MPG1:"),
                saves);
        assertEquals("saveTimetable:MC26S1:4", batches.calls.get(batches.calls.size() - 1));
        assertEquals(4, state().subjects.size());
        assertNotNull(state().timetable);
    }

    @Test
    public void importingAgainChangesOnlyWhatTheFileChanged() {
        viewModel.onFile(FILE);
        viewModel.save();
        viewModel.reset();
        viewModel.onFile(
                FILE.replace(
                        "MIS,Management Information System,Lecture,,SJ",
                        "MIS,Management Information System,Lecture,,GS"));
        assertEquals(3, state().count(Change.SAME));
        assertEquals(1, state().count(Change.UPDATE));
    }

    @Test
    public void aTeacherTheHodPickedIsKeptWhenTheFileMatchesNobody() {
        courses.subjects.add(
                Course.subject(
                        "MC26S1MPG1", "", SJ.getEmail(), "Mini Project", "MP", 75, "MC26", 1, 1));
        viewModel = newViewModel();
        viewModel.onFile(FILE);
        assertEquals(Change.SAME, state().changes.get(3));
        viewModel.save();
        assertEquals(SJ.getEmail(), courses.subjects.get(0).getTeacherEmail());
    }

    @Test
    public void aSubjectThatFailsIsReportedAndTheRestAreStillSaved() {
        viewModel.onFile(FILE);
        courses.subjectFailure = AppError.OFFLINE;
        viewModel.save();
        assertEquals(4, state().count(Change.FAILED));
        assertEquals(Phase.DONE, state().phase);
    }

    @Test
    public void offlineBlocksSaving() {
        viewModel.onFile(FILE);
        online.setValue(false);
        viewModel.save();
        assertEquals(Phase.PREVIEW, state().phase);
    }

    @Test
    public void unusableFilesAreRefusedWithTheReason() {
        viewModel.onFile(null);
        assertEquals(AppError.FILE_UNREADABLE, state().fileError);
        viewModel.onFile("email,roll no,name\n");
        assertEquals(RosterCsv.FileProblem.NO_HEADER, state().fileProblem);
        assertEquals(Phase.IDLE, state().phase);
    }

    @Test
    public void theHodGivesASubjectToAnotherTeacherWhoMustClaimIt() {
        courses.subjects.add(
                Course.subject(
                        "MC26S1OSG1", "teacherB", GS.getEmail(), "OS Lab", "OS", 75, "MC26", 1, 1));
        viewModel = newViewModel();
        final List<Outcome<SaveOutcome>> results = new ArrayList<>();
        viewModel.assign(state().subjects.get(0), SJ.getEmail(), results::add);
        assertEquals(SaveOutcome.UPDATED, results.get(0).getValue());
        assertEquals(SJ.getEmail(), state().subjects.get(0).getTeacherEmail());
        assertEquals("", state().subjects.get(0).getTeacherId());
    }

    @Test
    public void aBatchThatCannotBeReadIsAnError() {
        batches.failure = AppError.OFFLINE;
        viewModel = newViewModel();
        assertEquals(Status.ERROR, state().status);
        assertEquals(AppError.OFFLINE, state().error);
    }

    @Test
    public void theCurrentSemesterIsTheOneShown() {
        batches.batches.clear();
        batches.batches.add(new Batch("MC", 2026, 2, 2, 70, 4));
        viewModel = newViewModel();
        assertTrue(courses.calls.contains("loadSubjects:MC26:2"));
        viewModel.onFile(FILE);
        assertEquals("MC26S2", state().plan.timetable.getId());
    }

    @Test
    public void theHodCanTeachWithoutBeingOnTheFacultyList() {
        assertEquals(Fixtures.HOD.getEmail(), state().faculty.get(0).getEmail());
        assertEquals("AK", state().faculty.get(0).getInitials());
        viewModel.onFile("day,start,end,subject,faculty\nMON,09:30,10:25,Maths,Prof. Asha\n");
        assertEquals(Fixtures.HOD.getEmail(), state().plan.subjects.get(0).teacherEmail());
    }

    @Test
    public void anHodOnTheFacultyListIsListedOnce() {
        faculty.members.add(
                new FacultyMember(Fixtures.HOD.getEmail(), Fixtures.HOD.getName(), "AK"));
        viewModel = newViewModel();
        assertEquals(3, state().faculty.size());
    }
}
