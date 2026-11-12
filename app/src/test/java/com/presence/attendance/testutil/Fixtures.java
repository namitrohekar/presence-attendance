package com.presence.attendance.testutil;

import com.presence.attendance.auth.domain.Role;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.course.domain.Course;
import com.presence.attendance.course.domain.JoinCodeInfo;

/** Shared test data. The same people as the rules suite's fixtures. */
public final class Fixtures {

    public static final User TEACHER =
            new User("teacherA", "Asha Kulkarni", "asha.kulkarni@example.edu", Role.FACULTY);
    public static final User STUDENT =
            new User("studentA", "Meera Joshi", "meera.joshi@example.edu", Role.STUDENT);

    public static final Course COURSE =
            new Course("courseA", TEACHER.getUid(), "Data Structures", "CS201", 75, "ABC234");
    public static final JoinCodeInfo JOIN_CODE =
            new JoinCodeInfo(
                    "ABC234", "courseA", "Data Structures", TEACHER.getName(), TEACHER.getUid());

    private Fixtures() {}
}
