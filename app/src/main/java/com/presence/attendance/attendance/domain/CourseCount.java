package com.presence.attendance.attendance.domain;

/**
 * A course counted rather than read: sessions closed, students in it, and present marks. Enough for
 * a department view, where reading every record of every subject would exceed the free plan. Proxy
 * penalties are left out, and a session nobody closed is not counted. Immutable.
 */
public final class CourseCount {

    public final int sessions;
    public final int students;
    public final int presentMarks;

    public CourseCount(int sessions, int students, int presentMarks) {
        this.sessions = sessions;
        this.students = students;
        this.presentMarks = presentMarks;
    }

    /** False until a session has been held for a course with students. */
    public boolean hasSessions() {
        return sessions > 0 && students > 0;
    }

    /** Present marks over all the marks there could have been, whole percent, at most 100. */
    public int percent() {
        final long possible = (long) sessions * students;
        return possible == 0
                ? 0
                : (int) Math.min(100, (200L * presentMarks + possible) / (2 * possible));
    }
}
