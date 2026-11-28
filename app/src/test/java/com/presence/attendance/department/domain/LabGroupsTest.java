package com.presence.attendance.department.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.presence.attendance.roster.domain.RosterEntry;
import com.presence.attendance.roster.domain.StudentDetails;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class LabGroupsTest {

    /** Students with roll numbers 262011001 upwards, given in reverse to prove the sort. */
    private static List<RosterEntry> students(int count) {
        final List<RosterEntry> students = new ArrayList<>();
        for (int i = count; i >= 1; i--) {
            students.add(
                    new RosterEntry(
                            "s" + i + "@mc.vjti.ac.in",
                            String.format("2620110%02d", i),
                            "MC26",
                            "hod",
                            0,
                            null,
                            "Student " + i,
                            null));
        }
        return students;
    }

    private static List<Integer> sizes(LabGroups.Plan plan) {
        final List<Integer> sizes = new ArrayList<>();
        for (LabGroups.Range range : plan.ranges) {
            sizes.add(range.count);
        }
        return sizes;
    }

    @Test
    public void seventyIntoFourIsEighteenEighteenSeventeenSeventeen() {
        final LabGroups.Plan plan = LabGroups.plan(students(70), 4);
        assertEquals(List.of(18, 18, 17, 17), sizes(plan));
        assertEquals("262011001", plan.ranges.get(0).firstRollNo);
        assertEquals("262011018", plan.ranges.get(0).lastRollNo);
        assertEquals("262011019", plan.ranges.get(1).firstRollNo);
        assertEquals("262011070", plan.ranges.get(3).lastRollNo);
        assertEquals(Integer.valueOf(1), plan.groupByEmail.get("s18@mc.vjti.ac.in"));
        assertEquals(Integer.valueOf(2), plan.groupByEmail.get("s19@mc.vjti.ac.in"));
        assertEquals("everyone changes from no group", 70, plan.changes.size());
    }

    @Test
    public void studentsAlreadyInTheirPlannedGroupAreNotChanged() {
        final List<RosterEntry> grouped = new ArrayList<>();
        for (RosterEntry student : students(8)) {
            final int group = Integer.parseInt(student.getRollNo().substring(7)) <= 4 ? 1 : 2;
            grouped.add(student.withDetails(new StudentDetails(student.getName(), group)));
        }
        assertTrue(LabGroups.plan(grouped, 2).changes.isEmpty());
        // Into four groups of two, only the first two students stay where they are.
        assertEquals(6, LabGroups.plan(grouped, 4).changes.size());
    }

    @Test
    public void fewerStudentsThanGroupsLeavesEmptyGroups() {
        final LabGroups.Plan plan = LabGroups.plan(students(2), 4);
        assertEquals(List.of(1, 1, 0, 0), sizes(plan));
        assertNull(plan.ranges.get(3).firstRollNo);
        assertEquals(List.of(0, 0, 0), sizes(LabGroups.plan(Collections.emptyList(), 3)));
    }

    @Test
    public void atLeastOneGroup() {
        assertThrows(IllegalArgumentException.class, () -> LabGroups.plan(students(3), 0));
    }
}
