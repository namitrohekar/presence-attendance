package com.presence.attendance.testutil;

import com.presence.attendance.auth.domain.Role;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.course.domain.Course;
import com.presence.attendance.course.domain.JoinCodeInfo;
import com.presence.attendance.roster.domain.RosterEntry;
import com.presence.attendance.roster.domain.StudentEmoji;

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

    public static final Course COURSE =
            new Course("courseA", TEACHER.getUid(), "Data Structures", "CS201", 75, "ABC234");
    public static final JoinCodeInfo JOIN_CODE =
            new JoinCodeInfo(
                    "ABC234", "courseA", "Data Structures", TEACHER.getName(), TEACHER.getUid());

    /** STUDENT's roster entry, registered, in batch 1. */
    public static final RosterEntry ENTRY =
            new RosterEntry(
                    STUDENT.getEmail(),
                    "262011001",
                    "MC26",
                    TEACHER.getUid(),
                    0,
                    STUDENT.getUid(),
                    "Meera Joshi",
                    1,
                    StudentEmoji.OWL);

    /** A classmate on the roster who has not registered, in no batch. */
    public static final RosterEntry OTHER_ENTRY =
            new RosterEntry(
                    "rpatil_mc26@mc.vjti.ac.in",
                    "262010002",
                    "MC26",
                    TEACHER.getUid(),
                    0,
                    null,
                    "Rohan Patil",
                    null,
                    StudentEmoji.TIGER);

    /** A classmate added before names were kept: no name, batch or emoji. */
    public static final RosterEntry LEGACY_ENTRY =
            new RosterEntry(
                    "kdesai_mc26@mc.vjti.ac.in", "262010004", "MC26", TEACHER.getUid(), 0, null);

    private Fixtures() {}
}
