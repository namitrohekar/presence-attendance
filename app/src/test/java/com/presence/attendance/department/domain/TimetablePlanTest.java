package com.presence.attendance.department.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.presence.attendance.testutil.TimetableFixture;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class TimetablePlanTest {

    private static Map<String, TimetablePlan.Subject> byId(TimetablePlan plan) {
        final Map<String, TimetablePlan.Subject> map = new HashMap<>();
        for (TimetablePlan.Subject subject : plan.subjects) {
            map.put(subject.courseId, subject);
        }
        return map;
    }

    private static TimetablePlan fymca() {
        return TimetablePlan.of(
                TimetableCsv.parse(TimetableFixture.FYMCA, 4),
                "MCA26",
                1,
                FacultyMatchTest.FACULTY);
    }

    @Test
    public void sixSubjectsForTheBatchAndFiveLabsForEachOfFourGroups() {
        final TimetablePlan plan = fymca();
        assertEquals(26, plan.subjects.size());
        assertEquals(38, plan.timetable.getSlots().size());
        assertEquals("MCA26S1", plan.timetable.getId());
        int labs = 0;
        for (TimetablePlan.Subject subject : plan.subjects) {
            labs += subject.labGroup > 0 ? 1 : 0;
        }
        assertEquals(20, labs);
    }

    @Test
    public void aLectureAndItsLabAreSeparateSubjectsThoughTheyShareACode() {
        final Map<String, TimetablePlan.Subject> subjects = byId(fymca());
        assertEquals("Business English", subjects.get("MCA26S1R5MC5016L").name);
        assertEquals(NC(), subjects.get("MCA26S1R5MC5016L").teacher);
        assertEquals("Business English Lab", subjects.get("MCA26S1R5MC5016LG3").name);
        assertEquals(3, subjects.get("MCA26S1R5MC5016LG3").labGroup);
    }

    @Test
    public void aTutorialCountsWithItsLectureWhichNamesTheTeacher() {
        final TimetablePlan plan = fymca();
        final TimetablePlan.Subject maths = byId(plan).get("MCA26S1R5MC5014S");
        assertEquals("Mathematical and Statistical Foundations 1", maths.name);
        assertEquals("Prof. Archana", maths.facultyText);
        assertEquals(FacultyMatchTest.AB, maths.teacher);
        int tutorials = 0;
        for (TimetableSlot slot : plan.timetable.getSlots()) {
            if (slot.getType() == TimetableSlot.Type.TUTORIAL) {
                tutorials++;
                assertEquals(maths.courseId, slot.getCourseId());
            }
        }
        assertEquals(2, tutorials);
    }

    @Test
    public void aTeacherNobodyMatchesIsLeftForTheHod() {
        final TimetablePlan.Subject mini = byId(fymca()).get("MCA26S1R5MC5019DG1");
        assertEquals("RM", mini.facultyText);
        assertNull(mini.teacher);
        assertEquals("", mini.teacherEmail());
    }

    @Test
    public void rejectedRowsMakeNothing() {
        final TimetablePlan plan =
                TimetablePlan.of(
                        TimetableCsv.parse(
                                "day,start,end,subject\nMON,10:30,11:25,Operating System\n"
                                        + "TUE,10:30,11:25,Organisational Studies\n"
                                        + "XYZ,10:30,11:25,AI\n",
                                4),
                        "MCA26",
                        1,
                        List.of());
        assertEquals("two names with the same initials stay apart", 2, plan.subjects.size());
        assertEquals(2, plan.timetable.getSlots().size());
        assertEquals("OS", plan.subjects.get(0).code);
        assertEquals("MCA26S1OPERATINGSYSTEM", plan.subjects.get(0).courseId);
    }

    private static FacultyMember NC() {
        return FacultyMatchTest.NC;
    }
}
