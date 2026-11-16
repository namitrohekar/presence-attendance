package com.presence.attendance.attendance.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.presence.attendance.attendance.domain.PendingMark;
import com.presence.attendance.attendance.domain.PendingMark.State;
import com.presence.attendance.testutil.FakePendingMarkStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public final class SavedScansViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private final FakePendingMarkStore store = new FakePendingMarkStore();
    private final int[] syncRequests = {0};
    private final SavedScansViewModel viewModel =
            new SavedScansViewModel(store, () -> syncRequests[0]++);

    private static PendingMark mark(String sessionId, State state) {
        return new PendingMark(sessionId, "studentA", "t", 0, 0, 0, 0, state, null);
    }

    private void show(PendingMark... marks) {
        store.marks.clear();
        store.marks.addAll(List.of(marks));
        store.feed.setValue(new ArrayList<>(store.marks));
    }

    @Test
    public void onlyTheSignedInStudentsScansAreObserved() {
        viewModel.getMarks().observeForever(marks -> {});
        viewModel.setStudent("studentA");
        assertEquals("studentA", store.observedStudent);
    }

    @Test
    public void aWaitingScanAsksForASyncAndASettledListDoesNot() {
        viewModel.getMarks().observeForever(marks -> {});
        viewModel.setStudent("studentA");
        show(mark("s1", State.SYNCED), mark("s2", State.EXPIRED));
        assertEquals(0, syncRequests[0]);
        show(mark("s1", State.SYNCED), mark("s3", State.PENDING));
        assertEquals(1, syncRequests[0]);
    }

    @Test
    public void aSettledScanCanBeDismissedButAWaitingOneCannot() {
        viewModel.getMarks().observeForever(marks -> {});
        viewModel.setStudent("studentA");
        show(mark("s1", State.REJECTED), mark("s2", State.PENDING));
        viewModel.dismiss("s1");
        viewModel.dismiss("s2");
        assertEquals(1, store.marks.size());
        assertTrue(store.marks.get(0).getSessionId().equals("s2"));
    }
}
