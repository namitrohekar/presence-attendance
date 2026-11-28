package com.presence.attendance.department.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.presence.attendance.core.AppError;
import com.presence.attendance.department.ui.BatchSheetViewModel.State;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeBatchRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class BatchSheetViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeBatchRepository batches = new FakeBatchRepository();
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);

    @Before
    public void setUp() {
        auth.signedInAs(Fixtures.HOD);
    }

    private BatchSheetViewModel sheet(boolean editing) {
        final BatchSheetViewModel viewModel =
                new BatchSheetViewModel(auth, batches, online, editing ? Fixtures.BATCH : null);
        viewModel.getState().observeForever(state -> {});
        return viewModel;
    }

    private static State state(BatchSheetViewModel viewModel) {
        return viewModel.getState().getValue();
    }

    @Test
    public void aNewBatchIsCreatedFromNormalisedValues() {
        final BatchSheetViewModel viewModel = sheet(false);
        viewModel.submit(" mca ", "2025", "2", "", " 70 ", "4");
        assertEquals(Collections.singletonList("create:MCA25:2:70:4"), batches.calls);
        assertTrue(state(viewModel).done);
    }

    @Test
    public void anExistingBatchIsReportedNotOverwritten() {
        batches.batches.add(Fixtures.BATCH);
        final BatchSheetViewModel viewModel = sheet(false);
        viewModel.submit("MC", "2026", "2", "", "60", "3");
        assertTrue(state(viewModel).exists);
        assertFalse(state(viewModel).done);
        assertEquals(70, batches.batches.get(0).getIntake());
    }

    @Test
    public void everyInvalidFieldIsFlaggedOnCreate() {
        final BatchSheetViewModel viewModel = sheet(false);
        viewModel.submit("M", "2009", "6", "", "0", "11");
        final BatchSheetViewModel.Invalid invalid = state(viewModel).invalid;
        assertTrue(invalid.programme);
        assertTrue(invalid.startYear);
        assertTrue(invalid.years);
        assertFalse("the semester is not asked when creating", invalid.semester);
        assertTrue(invalid.intake);
        assertTrue(invalid.labGroups);
        assertTrue(batches.calls.isEmpty());
    }

    @Test
    public void editingChangesTheSemesterIntakeAndGroupsButNotTheId() {
        batches.batches.add(Fixtures.BATCH);
        final BatchSheetViewModel viewModel = sheet(true);
        viewModel.submit("ignored", "1999", "2", "3", "68", "3");
        assertEquals(Collections.singletonList("update:MC26:2:3:68:3"), batches.calls);
        assertTrue(state(viewModel).done);
    }

    @Test
    public void aSemesterBeyondTheProgrammeIsRefused() {
        final BatchSheetViewModel viewModel = sheet(true);
        viewModel.submit("", "", "2", "5", "70", "4");
        assertTrue(state(viewModel).invalid.semester);
        viewModel.submit("", "", "2", "0", "70", "4");
        assertTrue(state(viewModel).invalid.semester);
        assertTrue(batches.calls.isEmpty());
    }

    @Test
    public void offlineAndFailuresAreShown() {
        online.setValue(false);
        final BatchSheetViewModel viewModel = sheet(false);
        viewModel.submit("MCA", "2025", "2", "", "70", "4");
        assertEquals(AppError.OFFLINE, state(viewModel).error);
        online.setValue(true);
        batches.failure = AppError.PERMISSION_DENIED;
        viewModel.submit("MCA", "2025", "2", "", "70", "4");
        assertEquals(AppError.PERMISSION_DENIED, state(viewModel).error);
    }
}
