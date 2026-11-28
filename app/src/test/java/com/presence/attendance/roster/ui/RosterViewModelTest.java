package com.presence.attendance.roster.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.department.domain.Batch;
import com.presence.attendance.department.domain.LabGroups;
import com.presence.attendance.roster.domain.RosterEntry;
import com.presence.attendance.roster.ui.RosterViewModel.State;
import com.presence.attendance.roster.ui.RosterViewModel.Status;
import com.presence.attendance.roster.ui.RosterViewModel.Task;
import com.presence.attendance.testutil.FakeBatchRepository;
import com.presence.attendance.testutil.FakeRosterRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class RosterViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private final FakeRosterRepository roster = new FakeRosterRepository();
    private final FakeBatchRepository batches = new FakeBatchRepository();
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);
    private final RosterEntry inAnotherBatch =
            new RosterEntry("x@mc.vjti.ac.in", "252010001", "MCA25", "hod", 0, null, "X Y", null);

    @Before
    public void setUp() {
        batches.batches.add(Fixtures.BATCH);
        roster.rosterOutcome =
                Outcome.success(
                        new ArrayList<>(
                                Arrays.asList(
                                        Fixtures.ENTRY, Fixtures.OTHER_ENTRY, inAnotherBatch)));
    }

    private RosterViewModel load(String batchId) {
        final RosterViewModel viewModel = new RosterViewModel(roster, batches, online, batchId);
        viewModel.getState().observeForever(state -> {});
        return viewModel;
    }

    @Test
    public void theBatchAndOnlyItsStudentsAreLoaded() {
        final State state = load("MC26").getState().getValue();
        assertEquals(Status.CONTENT, state.status);
        assertEquals(Fixtures.BATCH, state.batch);
        assertEquals(Arrays.asList(Fixtures.ENTRY, Fixtures.OTHER_ENTRY), state.entries);
    }

    @Test
    public void aMissingBatchIsAnError() {
        final State state = load("MCA40").getState().getValue();
        assertEquals(Status.ERROR, state.status);
        assertEquals(AppError.BATCH_NOT_FOUND, state.error);
    }

    @Test
    public void theSemesterMovesOnOnceAndTheBatchIsReloaded() {
        final RosterViewModel viewModel = load("MC26");
        viewModel.moveSemester();
        assertEquals(Arrays.asList("update:MC26:2:2:70:4"), batches.calls);
        final State state = viewModel.getState().getValue();
        assertEquals(2, state.batch.getSemester());
        assertEquals(Task.SEMESTER, state.work.task);
        assertTrue(state.work.finished);
        assertNull(state.work.error);
        viewModel.onWorkSeen();
        assertNull(viewModel.getState().getValue().work);
    }

    @Test
    public void theLastSemesterCannotMoveOn() {
        batches.batches.set(0, new Batch("MC", 2026, 2, 4, 70, 4));
        load("MC26").moveSemester();
        assertTrue(batches.calls.isEmpty());
    }

    @Test
    public void offlineTheSemesterStays() {
        online.setValue(false);
        final RosterViewModel viewModel = load("MC26");
        viewModel.moveSemester();
        assertEquals(AppError.OFFLINE, viewModel.getState().getValue().work.error);
        assertTrue(batches.calls.isEmpty());
    }

    @Test
    public void labGroupsAreWrittenOnlyForStudentsWhoseGroupChanges() {
        final RosterViewModel viewModel = load("MC26");
        // In roll-number order Rohan (262010002) takes group 1 and Meera (262011001), now in
        // group 1, moves to group 2: both are written.
        final LabGroups.Plan plan = LabGroups.plan(viewModel.entries(), 2);
        viewModel.applyLabGroups(plan);
        assertEquals(
                Arrays.asList(
                        "details:" + Fixtures.OTHER_ENTRY.getEmail() + ":Rohan Patil/1",
                        "details:" + Fixtures.ENTRY.getEmail() + ":Meera Joshi/2"),
                roster.calls);
        final State state = viewModel.getState().getValue();
        assertEquals(Task.LAB_GROUPS, state.work.task);
        assertEquals(2, state.work.done);
        assertTrue(state.work.finished);
    }

    @Test
    public void aFailureStopsTheGroupingAndSaysWhere() {
        roster.detailsOutcome = Outcome.failure(AppError.PERMISSION_DENIED);
        final RosterViewModel viewModel = load("MC26");
        viewModel.applyLabGroups(LabGroups.plan(viewModel.entries(), 2));
        assertEquals(1, roster.calls.size());
        final State state = viewModel.getState().getValue();
        assertEquals(AppError.PERMISSION_DENIED, state.work.error);
        assertEquals(0, state.work.done);
    }
}
