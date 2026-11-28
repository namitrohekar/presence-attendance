package com.presence.attendance.course.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A student enrolled in a course, as their teacher sees them: the name from the roster when it has
 * one, else the name they joined with. Immutable.
 */
public final class Member {

    @NonNull private final String studentId;
    @NonNull private final String name;
    @NonNull private final String email;
    @NonNull private final String rollNo;
    @Nullable private final Integer labGroup;

    public Member(
            @NonNull String studentId,
            @NonNull String name,
            @NonNull String email,
            @NonNull String rollNo) {
        this(studentId, name, email, rollNo, null);
    }

    public Member(
            @NonNull String studentId,
            @NonNull String name,
            @NonNull String email,
            @NonNull String rollNo,
            @Nullable Integer labGroup) {
        this.studentId = studentId;
        this.name = name;
        this.email = email;
        this.rollNo = rollNo;
        this.labGroup = labGroup;
    }

    /**
     * This member with what the roster says about them now. A name the roster lacks keeps the one
     * they joined with, for an entry added before names were kept.
     */
    @NonNull
    public Member withRoster(@Nullable String rosterName, @Nullable Integer rosterLabGroup) {
        return new Member(
                studentId, rosterName != null ? rosterName : name, email, rollNo, rosterLabGroup);
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

    /** The lab group on the roster, or null. */
    @Nullable
    public Integer getLabGroup() {
        return labGroup;
    }
}
