package com.presence.attendance.attendance.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.AttendanceRecord.Status;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.domain.Member;
import com.presence.attendance.testutil.FakeAttendanceRepository;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeCourseRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;

public final class SessionRosterViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeCourseRepository courses = new FakeCourseRepository();
    private final FakeAttendanceRepository attendance = new FakeAttendanceRepository();

    private static Member member(String id) {
        return new Member(id, "Name " + id, id + "@mc.vjti.ac.in", "26201000" + id.length());
    }

    private SessionRosterViewModel start() {
        auth.signedInAs(Fixtures.TEACHER);
        final SessionRosterViewModel viewModel =
                new SessionRosterViewModel(auth, courses, attendance, "courseA", "sessA");
        viewModel.getState().observeForever(state -> {});
        return viewModel;
    }

    @Test
    public void membersWithoutARecordAreAbsentAndOnlyPresentStatusesCount() {
        courses.membersOutcome =
                Outcome.success(Arrays.asList(member("a"), member("bb"), member("ccc")));
        final SessionRosterViewModel viewModel = start();
        attendance.feed.setValue(
                Outcome.success(
                        Arrays.asList(
                                new AttendanceRecord("a", Status.LATE_SYNC, 1),
                                new AttendanceRecord("bb", Status.MANUAL_ABSENT, 2),
                                // Someone who has since left the course is not counted.
                                new AttendanceRecord("gone", Status.PRESENT, 3))));
        final SessionRosterViewModel.State state = viewModel.getState().getValue();
        assertEquals(SessionRosterViewModel.Status.CONTENT, state.status);
        assertEquals(3, state.members.size());
        assertEquals(1, state.presentCount());
        assertEquals(
                Collections.singletonList("sessA:" + Fixtures.TEACHER.getUid()),
                attendance.observed);
    }

    @Test
    public void aCourseWithNoStudentsIsEmptyAndAFailureIsAnError() {
        courses.membersOutcome = Outcome.success(Collections.emptyList());
        assertEquals(SessionRosterViewModel.Status.EMPTY, start().getState().getValue().status);
        assertTrue(attendance.observed.isEmpty());

        courses.membersOutcome = Outcome.failure(AppError.OFFLINE);
        final SessionRosterViewModel.State failed = start().getState().getValue();
        assertEquals(SessionRosterViewModel.Status.ERROR, failed.status);
        assertEquals(AppError.OFFLINE, failed.error);
    }
}
