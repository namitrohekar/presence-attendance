package com.presence.attendance.attendance.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.AttendanceRecord.Status;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.domain.Member;
import com.presence.attendance.session.domain.Geofence;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.testutil.FakeAttendanceRepository;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeCourseRepository;
import com.presence.attendance.testutil.FakeSessionRepository;
import com.presence.attendance.testutil.Fixtures;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class SessionRosterViewModelTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeCourseRepository courses = new FakeCourseRepository();
    private final FakeAttendanceRepository attendance = new FakeAttendanceRepository();
    private final FakeSessionRepository sessions = new FakeSessionRepository();

    private static Session ended(Integer headcount) {
        return new Session(
                "sessA",
                "courseA",
                Fixtures.TEACHER.getUid(),
                Session.Status.ENDED,
                0,
                1,
                Geofence.around(19.076, 72.8777, 100),
                headcount);
    }

    @Before
    public void endedSession() {
        sessions.sessionOutcome = Outcome.success(ended(null));
    }

    private static Member member(String id) {
        return new Member(id, "Name " + id, id + "@mc.vjti.ac.in", "26201000" + id.length());
    }

    private SessionRosterViewModel start() {
        auth.signedInAs(Fixtures.TEACHER);
        final SessionRosterViewModel viewModel =
                new SessionRosterViewModel(auth, courses, sessions, attendance, "courseA", "sessA");
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
                                new AttendanceRecord("sessA", "a", Status.LATE_SYNC, 1),
                                new AttendanceRecord("sessA", "bb", Status.MANUAL_ABSENT, 2),
                                // Someone who has since left the course is not counted.
                                new AttendanceRecord("sessA", "gone", Status.PRESENT, 3))));
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

    private static AttendanceRecord scan(String student, Status status, double lat) {
        return new AttendanceRecord("sessA", student, status, 1, true, null, lat, 72.8777, 0);
    }

    @Test
    public void theHeadcountIsComparedWithTheMarks() {
        sessions.sessionOutcome = Outcome.success(ended(1));
        courses.membersOutcome =
                Outcome.success(Arrays.asList(member("a"), member("bb"), member("ccc")));
        final SessionRosterViewModel viewModel = start();
        attendance.feed.setValue(
                Outcome.success(
                        Arrays.asList(
                                scan("a", Status.PRESENT, 19.07611),
                                scan("bb", Status.PRESENT, 19.07612))));
        assertEquals(Integer.valueOf(1), viewModel.getState().getValue().headcount);
        assertEquals(
                "one more mark than people",
                Integer.valueOf(1),
                viewModel.getState().getValue().difference());

        viewModel.saveHeadcount(3);
        assertEquals(
                Collections.singletonList("setHeadcount:sessA:3"), sessions.calls.subList(1, 2));
        assertEquals(Integer.valueOf(-1), viewModel.getState().getValue().difference());
        assertFalse(viewModel.getState().getValue().headcountSaving);

        // A record arriving later keeps the headcount the teacher entered.
        attendance.feed.setValue(
                Outcome.success(
                        Arrays.asList(
                                scan("a", Status.PRESENT, 19.07611),
                                scan("bb", Status.PRESENT, 19.07612),
                                scan("ccc", Status.LATE_SYNC, 19.07613))));
        assertEquals(Integer.valueOf(0), viewModel.getState().getValue().difference());
    }

    @Test
    public void aFailedSaveKeepsTheOldHeadcountAndSaysWhy() {
        sessions.sessionOutcome = Outcome.success(ended(40));
        sessions.headcountOutcome = Outcome.failure(AppError.OFFLINE);
        courses.membersOutcome = Outcome.success(Collections.singletonList(member("a")));
        final SessionRosterViewModel viewModel = start();
        attendance.feed.setValue(Outcome.success(Collections.emptyList()));
        viewModel.saveHeadcount(41);
        final SessionRosterViewModel.State state = viewModel.getState().getValue();
        assertEquals(Integer.valueOf(40), state.headcount);
        assertEquals(AppError.OFFLINE, state.headcountError);
    }

    @Test
    public void marksAtTheSamePlaceAreFlaggedForReview() {
        courses.membersOutcome =
                Outcome.success(Arrays.asList(member("a"), member("bb"), member("ccc")));
        final SessionRosterViewModel viewModel = start();
        attendance.feed.setValue(
                Outcome.success(
                        Arrays.asList(
                                scan("a", Status.PRESENT, 19.07611),
                                scan("bb", Status.PRESENT, 19.07611),
                                scan("ccc", Status.PRESENT, 19.07615),
                                // Not enrolled any more: never flagged, never a reason to flag.
                                scan("gone", Status.PRESENT, 19.07615))));
        final SessionRosterViewModel.State state = viewModel.getState().getValue();
        assertEquals(2, state.flags.size());
        assertEquals("bb", state.flags.get("a").otherStudentId);
        assertFalse(state.flags.containsKey("ccc"));
    }

    @Test
    public void theListStartsCollapsedAndToggles() {
        courses.membersOutcome = Outcome.success(Collections.singletonList(member("a")));
        final SessionRosterViewModel viewModel = start();
        attendance.feed.setValue(Outcome.success(Collections.emptyList()));
        assertFalse(viewModel.getState().getValue().expanded);
        viewModel.toggleExpanded();
        assertTrue(viewModel.getState().getValue().expanded);
        attendance.feed.setValue(Outcome.success(Collections.emptyList()));
        assertTrue("a live update keeps it open", viewModel.getState().getValue().expanded);
    }

    @Test
    public void aSessionThatCannotBeReadIsAnError() {
        sessions.sessionOutcome = Outcome.failure(AppError.PERMISSION_DENIED);
        courses.membersOutcome = Outcome.success(Collections.singletonList(member("a")));
        final SessionRosterViewModel.State state = start().getState().getValue();
        assertEquals(SessionRosterViewModel.Status.ERROR, state.status);
        assertEquals(AppError.PERMISSION_DENIED, state.error);
    }

    @Test
    public void aHeadcountIsAWholeNumberFromZeroToTheMaximum() {
        assertEquals(Integer.valueOf(0), SessionRosterViewModel.parseHeadcount("0"));
        assertEquals(Integer.valueOf(56), SessionRosterViewModel.parseHeadcount(" 56 "));
        assertEquals(Integer.valueOf(1000), SessionRosterViewModel.parseHeadcount("1000"));
        assertEquals(
                "digits from another script",
                Integer.valueOf(12),
                SessionRosterViewModel.parseHeadcount("١٢"));
        for (String bad : new String[] {"", " ", "-1", "1001", "4.5", "12a", "99999"}) {
            assertNull(bad, SessionRosterViewModel.parseHeadcount(bad));
        }
    }
}
