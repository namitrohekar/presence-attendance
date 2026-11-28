package com.presence.attendance.testutil;

import com.presence.attendance.auth.domain.Role;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.course.domain.Course;
import com.presence.attendance.course.domain.JoinCodeInfo;
import com.presence.attendance.department.domain.Batch;
import com.presence.attendance.roster.domain.RosterEntry;

/** Shared test data. The same people as the rules suite's fixtures. */
public final class Fixtures {

    public static final User TEACHER =
            new User("teacherA", "Asha Kulkarni", "asha.kulkarni@example.edu", Role.FACULTY);
    public static final User STUDENT =
            new User(
                    "studentA",
                    "Meera Joshi",
                    "mjoshi_mc26@mc.vjti.ac.in",
                    Role.STUDENT,
                    "262011001",
                    "MC26");

    /** TEACHER as the HOD. */
    public static final User HOD = TEACHER.asHod();

    /** MC26: a two-year programme from 2026, in its first semester, 70 seats, four lab groups. */
    public static final Batch BATCH = new Batch("MC", 2026, 2, 1, 70, 4);

    /** MC26's Operating System lectures, claimed by TEACHER. */
    public static final Course LECTURE =
            Course.subject(
                    "MC26S1R5MC5013T",
                    "teacherA",
                    "asha.kulkarni@example.edu",
                    "Operating System",
                    "R5MC5013T",
                    75,
                    "MC26",
                    1,
                    0);

    /** MC26's Operating System Lab for lab group 1, claimed by TEACHER. */
    public static final Course LAB =
            Course.subject(
                    "MC26S1R5MC5013PG1",
                    "teacherA",
                    "asha.kulkarni@example.edu",
                    "Operating System Lab",
                    "R5MC5013P",
                    75,
                    "MC26",
                    1,
                    1);

    public static final Course COURSE =
            new Course("courseA", TEACHER.getUid(), "Data Structures", "CS201", 75, "ABC234");
    public static final JoinCodeInfo JOIN_CODE =
            new JoinCodeInfo(
                    "ABC234", "courseA", "Data Structures", TEACHER.getName(), TEACHER.getUid());

    /** STUDENT's roster entry in batch MC26, registered, in lab group 1. */
    public static final RosterEntry ENTRY =
            new RosterEntry(
                    STUDENT.getEmail(),
                    "262011001",
                    "MC26",
                    TEACHER.getUid(),
                    0,
                    STUDENT.getUid(),
                    "Meera Joshi",
                    1);

    /** A classmate in MC26 who has not registered and has no lab group yet. */
    public static final RosterEntry OTHER_ENTRY =
            new RosterEntry(
                    "rpatil_mc26@mc.vjti.ac.in",
                    "262010002",
                    "MC26",
                    TEACHER.getUid(),
                    0,
                    null,
                    "Rohan Patil",
                    null);

    /** A classmate added before names were kept: no name or lab group. */
    public static final RosterEntry LEGACY_ENTRY =
            new RosterEntry(
                    "kdesai_mc26@mc.vjti.ac.in", "262010004", "MC26", TEACHER.getUid(), 0, null);

    private Fixtures() {}
}
