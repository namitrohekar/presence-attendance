package com.presence.attendance.department.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.presence.attendance.core.AppError;
import com.presence.attendance.department.domain.FacultyMember;
import com.presence.attendance.department.ui.FacultySheetViewModel.Problem;
import com.presence.attendance.department.ui.FacultySheetViewModel.State;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeFacultyRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class FacultySheetViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private static final FacultyMember SIDAM =
            new FacultyMember("gsidam@vjti.ac.in", "Gajanan Sidam", "GS");

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeFacultyRepository faculty = new FakeFacultyRepository();
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);

    @Before
    public void setUp() {
        auth.signedInAs(Fixtures.HOD);
    }

    private FacultySheetViewModel sheet(FacultyMember editing) {
        final FacultySheetViewModel viewModel =
                new FacultySheetViewModel(auth, faculty, online, editing);
        viewModel.getState().observeForever(state -> {});
        return viewModel;
    }

    private static State state(FacultySheetViewModel viewModel) {
        return viewModel.getState().getValue();
    }

    @Test
    public void aTeacherIsAddedWithInitialsTakenFromTheNameWhenLeftEmpty() {
        final FacultySheetViewModel viewModel = sheet(null);
        viewModel.save(" Prof.  Gajanan Sidam ", "", " GSidam@VJTI.ac.in ");
        assertEquals(
                Collections.singletonList("add:gsidam@vjti.ac.in:Prof. Gajanan Sidam:GS"),
                faculty.calls);
        assertTrue(state(viewModel).done);
    }

    @Test
    public void typedInitialsAreNormalised() {
        sheet(null).save("Siddharth Jambhavdekar", " s. j. ", "sj@vjti.ac.in");
        assertEquals(
                Collections.singletonList("add:sj@vjti.ac.in:Siddharth Jambhavdekar:SJ"),
                faculty.calls);
    }

    @Test
    public void invalidFieldsAndAnExistingAddressAreReported() {
        final FacultySheetViewModel viewModel = sheet(null);
        viewModel.save(" ", "", "a@b.co");
        assertEquals(Problem.NAME_INVALID, state(viewModel).problem);
        viewModel.save("A B", "ABCDE", "a@b.co");
        assertEquals(Problem.INITIALS_INVALID, state(viewModel).problem);
        viewModel.save("A B", "", "not an address");
        assertEquals(Problem.EMAIL_INVALID, state(viewModel).problem);
        assertTrue(faculty.calls.isEmpty());

        faculty.members.add(SIDAM);
        viewModel.save("Gajanan Sidam", "GS", "gsidam@vjti.ac.in");
        assertEquals(Problem.EXISTS, state(viewModel).problem);
        assertFalse(state(viewModel).done);
    }

    @Test
    public void editingChangesTheNameAndInitialsOnly() {
        faculty.members.add(SIDAM);
        final FacultySheetViewModel viewModel = sheet(SIDAM);
        viewModel.save("Gajanan R. Sidam", "GRS", "ignored@example.com");
        assertEquals(
                Collections.singletonList("update:gsidam@vjti.ac.in:Gajanan R. Sidam:GRS"),
                faculty.calls);
        assertTrue(state(viewModel).done);
    }

    @Test
    public void removingTakesTwoTaps() {
        faculty.members.add(SIDAM);
        final FacultySheetViewModel viewModel = sheet(SIDAM);
        viewModel.remove();
        assertEquals(Problem.CONFIRM_REMOVE, state(viewModel).problem);
        assertTrue(faculty.calls.isEmpty());
        viewModel.remove();
        assertEquals(Collections.singletonList("remove:gsidam@vjti.ac.in"), faculty.calls);
        assertTrue(state(viewModel).done);
    }

    @Test
    public void someoneRemovedMeanwhileIsReported() {
        final FacultySheetViewModel viewModel = sheet(SIDAM);
        viewModel.save("Gajanan Sidam", "GS", "");
        assertEquals(Problem.GONE, state(viewModel).problem);
    }

    @Test
    public void offlineBlocksEveryWrite() {
        online.setValue(false);
        final FacultySheetViewModel viewModel = sheet(null);
        viewModel.save("A B", "", "a@b.co");
        assertEquals(AppError.OFFLINE, state(viewModel).error);
        assertTrue(faculty.calls.isEmpty());
    }
}
