package com.presence.attendance.course.domain;

import androidx.annotation.NonNull;

/** A student enrolled in a course, as their teacher sees them. Immutable. */
public final class Member {

    @NonNull private final String studentId;
    @NonNull private final String name;
    @NonNull private final String email;
    @NonNull private final String rollNo;

    public Member(
            @NonNull String studentId,
            @NonNull String name,
            @NonNull String email,
            @NonNull String rollNo) {
        this.studentId = studentId;
        this.name = name;
        this.email = email;
        this.rollNo = rollNo;
    }

    @NonNull
    public String getStudentId() {
        return studentId;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getEmail() {
        return email;
    }

    /** The roll number recorded when the student joined; empty for a pre-roster membership. */
    @NonNull
    public String getRollNo() {
        return rollNo;
    }
}
