package com.presence.attendance.attendance.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.presence.attendance.attendance.domain.AttendanceRecord.Status;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Constants;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.testutil.FakeAttendanceRepository;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class CorrectionViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeAttendanceRepository attendance = new FakeAttendanceRepository();
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(true);
    private CorrectionViewModel viewModel;

    @Before
    public void setUp() {
        auth.signedInAs(Fixtures.TEACHER);
        viewModel =
                new CorrectionViewModel(
                        auth, attendance, online, "courseA", "sessA", "studentA", true);
        viewModel.getState().observeForever(state -> {});
    }

    private CorrectionViewModel.State state() {
        return viewModel.getState().getValue();
    }

    @Test
    public void aCorrectionIsSentAsTheSignedInTeacherWithATrimmedNote() {
        viewModel.save(Status.MANUAL_PRESENT, "  Phone battery died  ");
        assertEquals(
                Collections.singletonList(
                        Fixtures.TEACHER.getUid()
                                + ":courseA:sessA:studentA:update:MANUAL_PRESENT:Phone battery died"),
                attendance.corrections);
        assertTrue(state().done);
    }

    @Test
    public void aBlankOrOverlongNoteIsRefusedBeforeAnythingIsSent() {
        viewModel.save(Status.MANUAL_ABSENT, "   ");
        assertTrue(state().noteMissing);
        final String tooLong = "x".repeat(Constants.NOTE_MAX_LEN + 1);
        viewModel.save(Status.MANUAL_ABSENT, tooLong);
        assertTrue(state().noteMissing);
        assertTrue(attendance.corrections.isEmpty());
        viewModel.save(Status.MANUAL_ABSENT, "x".repeat(Constants.NOTE_MAX_LEN));
        assertEquals("the longest allowed note is sent", 1, attendance.corrections.size());
    }

    @Test
    public void aRefusalKeepsTheSheetOpenWithTheReason() {
        attendance.correctOutcome = Outcome.failure(AppError.PERMISSION_DENIED);
        viewModel.save(Status.MANUAL_PRESENT, "why");
        assertFalse(state().done);
        assertEquals(AppError.PERMISSION_DENIED, state().error);
        assertTrue("it can be tried again", state().canSave());
    }

    @Test
    public void offlineNothingIsSent() {
        online.setValue(false);
        viewModel.save(Status.MANUAL_PRESENT, "why");
        assertTrue(attendance.corrections.isEmpty());
        assertFalse(state().canSave());
    }

    @Test
    public void aRevokeIsSentWithItsReason() {
        viewModel.save(Status.REVOKED, "Friend scanned for them");
        assertEquals(
                Collections.singletonList(
                        Fixtures.TEACHER.getUid()
                                + ":courseA:sessA:studentA:update:REVOKED:Friend scanned for them"),
                attendance.corrections);
        assertTrue(state().done);
    }
}
