package com.presence.attendance.course.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.presence.attendance.testutil.Fixtures;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class SubjectEnrolmentTest {

    private static final Course LAB_2 =
            Course.subject("MC26S1OSG2", "teacherA", "a@b.c", "OS Lab", "OS", 75, "MC26", 1, 2);
    private static final Course UNCLAIMED =
            Course.subject("MC26S1WT", "", "a@b.c", "Web Technology", "WT", 75, "MC26", 1, 0);

    @Test
    public void aStudentJoinsTheBatchsSubjectsAndOnlyTheirOwnGroupsLabs() {
        final SubjectEnrolment plan =
                SubjectEnrolment.plan(
                        Arrays.asList(Fixtures.LECTURE, Fixtures.LAB, LAB_2, UNCLAIMED),
                        Collections.emptyMap(),
                        1);
        assertEquals(Arrays.asList(Fixtures.LECTURE, Fixtures.LAB), plan.toJoin);
        assertTrue(plan.toMove.isEmpty());
    }

    @Test
    public void withoutALabGroupOnlyTheBatchsSubjects() {
        assertEquals(
                Collections.singletonList(Fixtures.LECTURE),
                SubjectEnrolment.plan(
                                Arrays.asList(Fixtures.LECTURE, Fixtures.LAB),
                                Collections.emptyMap(),
                                0)
                        .toJoin);
    }

    @Test
    public void aMembershipFollowsItsSubjectToTheTeacherWhoClaimedIt() {
        final Map<String, String> mine = new HashMap<>();
        mine.put(Fixtures.LECTURE.getId(), "teacherB");
        mine.put(Fixtures.LAB.getId(), "teacherA");
        final SubjectEnrolment plan =
                SubjectEnrolment.plan(Arrays.asList(Fixtures.LECTURE, Fixtures.LAB), mine, 1);
        assertTrue(plan.toJoin.isEmpty());
        assertEquals(Collections.singletonList(Fixtures.LECTURE), plan.toMove);
    }

    @Test
    public void aTeachersOwnCourseIsNeverJoinedThisWay() {
        assertTrue(
                SubjectEnrolment.plan(
                                Collections.singletonList(Fixtures.COURSE),
                                Collections.emptyMap(),
                                1)
                        .isEmpty());
    }
}
