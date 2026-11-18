package com.presence.attendance.attendance.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.AttendanceRecord.Status;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.domain.Member;
import com.presence.attendance.session.domain.Geofence;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.testutil.FakeAttendanceRepository;
import com.presence.attendance.testutil.FakeAuthRepository;
import com.presence.attendance.testutil.FakeCourseRepository;
import com.presence.attendance.testutil.FakeSessionRepository;
import com.presence.attendance.testutil.Fixtures;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/** The calendar on a student's course screen, and the month switch on the teacher's report. */
public final class MonthViewModelsTest {

    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private static final ZoneId MUMBAI = ZoneId.of("Asia/Kolkata");
    private static final long NOW = millis(2026, 11, 18, 12);

    private final FakeAuthRepository auth = new FakeAuthRepository();
    private final FakeCourseRepository courses = new FakeCourseRepository();
    private final FakeSessionRepository sessions = new FakeSessionRepository();
    private final FakeAttendanceRepository attendance = new FakeAttendanceRepository();
    private final List<Session> held = new ArrayList<>();
    private final List<AttendanceRecord> records = new ArrayList<>();

    private static long millis(int y, int m, int d, int h) {
        return LocalDateTime.of(y, m, d, h, 0).atZone(MUMBAI).toInstant().toEpochMilli();
    }

    /** A class on a date in 2026, newest first as the repository returns them. */
    private void held(String id, int month, int day, String student, Status status) {
        final long start = millis(2026, month, day, 9);
        held.add(
                0,
                new Session(
                        id,
                        "courseA",
                        "teacherA",
                        Session.Status.ENDED,
                        start,
                        start + 3_600_000,
                        Geofence.around(19.0, 72.8, 100)));
        if (status != null) {
            records.add(new AttendanceRecord(id, student, status, start));
        }
    }

    private HistoryViewModel history(String studentId) {
        sessions.courseSessionsOutcome = Outcome.success(held);
        attendance.recordsOutcome = Outcome.success(records);
        final HistoryViewModel viewModel =
                new HistoryViewModel(
                        auth,
                        sessions,
                        attendance,
                        "courseA",
                        studentId,
                        () -> NOW,
                        MUMBAI,
                        DayOfWeek.MONDAY);
        viewModel.getState().observeForever(state -> {});
        return viewModel;
    }

    private void aSemester(String student) {
        held("sep", 9, 15, student, Status.PRESENT);
        held("oct1", 10, 6, student, Status.PRESENT);
        held("oct2", 10, 7, student, null);
        held("nov1", 11, 2, student, Status.PRESENT);
        held("nov2", 11, 3, student, Status.PRESENT);
        held("nov3", 11, 4, student, Status.REVOKED);
    }

    @Test
    public void theCalendarOpensOnTheMonthOfTheLatestClassWithItsOwnFigure() {
        auth.signedInAs(Fixtures.STUDENT);
        aSemester(Fixtures.STUDENT.getUid());
        final HistoryViewModel.State state = history(null).getState().getValue();
        assertEquals(YearMonth.of(2026, 11), state.month);
        assertEquals(3, state.monthStats.getTotal());
        assertEquals("two attended, one off for the strike", 1, state.monthStats.getPresent());
        assertEquals("the overall figure covers every month", 6, state.stats.getTotal());
        assertTrue(state.hasEarlierMonth);
        assertFalse("November is the current month", state.hasLaterMonth);
    }

    @Test
    public void monthsStepBackToTheFirstClassAndForwardToTheCurrentMonth() {
        auth.signedInAs(Fixtures.STUDENT);
        aSemester(Fixtures.STUDENT.getUid());
        final HistoryViewModel viewModel = history(null);
        viewModel.previousMonth();
        assertEquals(YearMonth.of(2026, 10), viewModel.getState().getValue().month);
        assertEquals(50, viewModel.getState().getValue().monthStats.percent());
        viewModel.previousMonth();
        viewModel.previousMonth();
        assertEquals(
                "not before the first class",
                YearMonth.of(2026, 9),
                viewModel.getState().getValue().month);
        assertFalse(viewModel.getState().getValue().hasEarlierMonth);
        viewModel.nextMonth();
        viewModel.nextMonth();
        viewModel.nextMonth();
        assertEquals(YearMonth.of(2026, 11), viewModel.getState().getValue().month);
    }

    @Test
    public void pickingADayFiltersAndPickingItAgainOrChangingMonthClearsIt() {
        auth.signedInAs(Fixtures.STUDENT);
        aSemester(Fixtures.STUDENT.getUid());
        final HistoryViewModel viewModel = history(null);
        final LocalDate third = LocalDate.of(2026, 11, 3);
        viewModel.selectDay(third);
        assertEquals(third, viewModel.getState().getValue().day);
        viewModel.selectDay(third);
        assertNull(viewModel.getState().getValue().day);
        viewModel.selectDay(LocalDate.of(2026, 10, 6));
        assertNull(
                "a day outside the month on view is ignored", viewModel.getState().getValue().day);
        viewModel.selectDay(third);
        viewModel.previousMonth();
        assertNull(viewModel.getState().getValue().day);
    }

    @Test
    public void aTeacherOpensOneStudentWithTheirOwnQueries() {
        auth.signedInAs(Fixtures.TEACHER);
        aSemester("s7");
        final HistoryViewModel.State state = history("s7").getState().getValue();
        assertEquals(HistoryViewModel.Status.CONTENT, state.status);
        assertTrue(
                sessions.calls.contains(
                        "loadCourseSessions:" + Fixtures.TEACHER.getUid() + ":courseA"));
        assertEquals(
                Collections.singletonList(
                        "teacher:" + Fixtures.TEACHER.getUid() + ":student:s7:courseA"),
                attendance.recordLoads);
    }

    @Test
    public void theReportSwitchesToThisMonthWithoutLoadingAgain() {
        auth.signedInAs(Fixtures.TEACHER);
        held("oct", 10, 6, "a", Status.PRESENT);
        held("nov1", 11, 2, "a", null);
        held("nov2", 11, 3, "a", null);
        sessions.courseSessionsOutcome = Outcome.success(held);
        attendance.recordsOutcome = Outcome.success(records);
        courses.membersOutcome =
                Outcome.success(
                        Arrays.asList(new Member("a", "Name a", "a@mc.vjti.ac.in", "262010001")));
        final CourseStudentsViewModel viewModel =
                new CourseStudentsViewModel(
                        auth, courses, sessions, attendance, "courseA", 75, () -> NOW, MUMBAI);
        viewModel.getState().observeForever(state -> {});
        assertNull(viewModel.getState().getValue().month);
        assertEquals(3, viewModel.getState().getValue().rows.get(0).stats.getTotal());
        viewModel.toggleMonth();
        final CourseStudentsViewModel.State november = viewModel.getState().getValue();
        assertEquals(YearMonth.of(2026, 11), november.month);
        assertEquals(2, november.rows.get(0).stats.getTotal());
        assertEquals(0, november.rows.get(0).stats.getPresent());
        assertEquals(1, november.atRiskCount);
        assertEquals("no second load", 1, attendance.recordLoads.size());
        viewModel.toggleMonth();
        assertNull(viewModel.getState().getValue().month);
    }
}
