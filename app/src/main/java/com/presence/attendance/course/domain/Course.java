package com.presence.attendance.course.domain;

import androidx.annotation.NonNull;

/** A course taught by one teacher and joined by students with its join code. Immutable. */
public final class Course {

    @NonNull private final String id;
    @NonNull private final String teacherId;
    @NonNull private final String name;
    @NonNull private final String code;
    private final int threshold;
    @NonNull private final String joinCode;

    public Course(
            @NonNull String id,
            @NonNull String teacherId,
            @NonNull String name,
            @NonNull String code,
            int threshold,
            @NonNull String joinCode) {
        this.id = id;
        this.teacherId = teacherId;
        this.name = name;
        this.code = code;
        this.threshold = threshold;
        this.joinCode = joinCode;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getTeacherId() {
        return teacherId;
    }

    @NonNull
    public String getName() {
        return name;
    }

    /** The institute's short code for the course, for example "CS201". */
    @NonNull
    public String getCode() {
        return code;
    }

    /** Attendance percentage below which a student is at risk. */
    public int getThreshold() {
        return threshold;
    }

    @NonNull
    public String getJoinCode() {
        return joinCode;
    }
}
