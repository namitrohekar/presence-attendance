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

/** The student's history and the teacher's per-student report. */
public final class ReportViewModelsTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private static final long NOW = 10_000;

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeCourseRepository courses = new FakeCourseRepository();
    private final FakeSessionRepository sessions = new FakeSessionRepository();
    private final FakeAttendanceRepository attendance = new FakeAttendanceRepository();

    private static Session ended(String id) {
        return new Session(
                id,
                "courseA",
                "teacherA",
                Session.Status.ENDED,
                0,
                NOW - 1,
                Geofence.around(19.076, 72.8777, 100));
    }

    private static AttendanceRecord record(String sessionId, String studentId, Status status) {
        return new AttendanceRecord(sessionId, studentId, status, 1);
    }

    @Before
    public void setUp() {
        sessions.courseSessionsOutcome =
                Outcome.success(Arrays.asList(ended("s1"), ended("s2"), ended("s3"), ended("s4")));
    }

    // ---- Student history --------------------------------------------------------------------

    private HistoryViewModel.State history() {
        auth.signedInAs(Fixtures.STUDENT);
        final HistoryViewModel viewModel =
                new HistoryViewModel(auth, sessions, attendance, "courseA", () -> NOW);
        viewModel.getState().observeForever(state -> {});
        return viewModel.getState().getValue();
    }

    @Test
    public void aStudentSeesEachSessionAndTheirOverallFigure() {
        final String me = Fixtures.STUDENT.getUid();
        attendance.recordsOutcome =
                Outcome.success(
                        Arrays.asList(
                                record("s1", me, Status.PRESENT),
                                record("s2", me, Status.MANUAL_ABSENT),
                                record("s3", me, Status.LATE_SYNC)));
        final HistoryViewModel.State state = history();
        assertEquals(HistoryViewModel.Status.CONTENT, state.status);
        assertEquals(4, state.sessions.size());
        assertEquals(50, state.stats.percent());
        assertNull("no record for the missed session", state.recordBySession.get("s4"));
        assertEquals(
                Collections.singletonList("student:" + me + ":courseA"), attendance.recordLoads);
        assertTrue(sessions.calls.contains("loadEnrolledSessions:courseA"));
    }

    @Test
    public void noSessionsYetIsItsOwnStateAndAFailureKeepsItsReason() {
        sessions.courseSessionsOutcome = Outcome.success(Collections.emptyList());
        assertEquals(HistoryViewModel.Status.EMPTY, history().status);
        assertTrue("records are not fetched for nothing", attendance.recordLoads.isEmpty());

        sessions.courseSessionsOutcome = Outcome.failure(AppError.PERMISSION_DENIED);
        final HistoryViewModel.State failed = history();
        assertEquals(HistoryViewModel.Status.ERROR, failed.status);
        assertEquals(AppError.PERMISSION_DENIED, failed.error);
    }

    // ---- Faculty report ---------------------------------------------------------------------

    private CourseStudentsViewModel.State report(int threshold) {
        auth.signedInAs(Fixtures.TEACHER);
        final CourseStudentsViewModel viewModel =
                new CourseStudentsViewModel(
                        auth, courses, sessions, attendance, "courseA", threshold, () -> NOW);
        viewModel.getState().observeForever(state -> {});
        return viewModel.getState().getValue();
    }

    private static Member member(String id, String rollNo) {
        return new Member(id, "Name " + id, id + "@mc.vjti.ac.in", rollNo);
    }

    @Test
    public void exactlyTheStudentsBelowTheThresholdAreAtRisk() {
        courses.membersOutcome =
                Outcome.success(
                        Arrays.asList(
                                member("all", "262010001"),
                                member("three", "262010002"),
                                member("two", "262010003"),
                                member("none", "262010004")));
        attendance.recordsOutcome =
                Outcome.success(
                        Arrays.asList(
                                record("s1", "all", Status.PRESENT),
                                record("s2", "all", Status.PRESENT),
                                record("s3", "all", Status.PRESENT),
                                record("s4", "all", Status.PRESENT),
                                record("s1", "three", Status.PRESENT),
                                record("s2", "three", Status.LATE_SYNC),
                                record("s3", "three", Status.MANUAL_PRESENT),
                                record("s1", "two", Status.PRESENT),
                                record("s2", "two", Status.PRESENT),
                                // Someone who left the course is not in the report.
                                record("s1", "gone", Status.PRESENT)));
        final CourseStudentsViewModel.State state = report(75);
        assertEquals(4, state.rows.size());
        assertEquals("roll-number order is kept", "all", state.rows.get(0).member.getStudentId());
        assertEquals(100, state.rows.get(0).stats.percent());
        assertFalse("exactly 75% meets a 75% requirement", state.rows.get(1).atRisk);
        assertTrue(state.rows.get(2).atRisk);
        assertTrue(state.rows.get(3).atRisk);
        assertEquals(2, state.atRiskCount);
        assertEquals(
                Collections.singletonList("teacher:" + Fixtures.TEACHER.getUid() + ":courseA"),
                attendance.recordLoads);

        assertEquals("a stricter requirement catches one more", 3, report(80).atRiskCount);
    }

    @Test
    public void beforeAnySessionNobodyIsAtRisk() {
        sessions.courseSessionsOutcome = Outcome.success(Collections.emptyList());
        courses.membersOutcome =
                Outcome.success(Collections.singletonList(member("a", "262010001")));
        final CourseStudentsViewModel.State state = report(75);
        assertEquals(CourseStudentsViewModel.Status.CONTENT, state.status);
        assertFalse(state.rows.get(0).stats.hasSessions());
        assertEquals(0, state.atRiskCount);
    }

    @Test
    public void noStudentsIsEmptyAndAFailedLoadIsAnError() {
        assertEquals(CourseStudentsViewModel.Status.EMPTY, report(75).status);
        courses.membersOutcome =
                Outcome.success(Collections.singletonList(member("a", "262010001")));
        attendance.recordsOutcome = Outcome.failure(AppError.OFFLINE);
        final CourseStudentsViewModel.State failed = report(75);
        assertEquals(CourseStudentsViewModel.Status.ERROR, failed.status);
        assertEquals(AppError.OFFLINE, failed.error);
    }
}
