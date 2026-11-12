package com.presence.attendance.course.domain;

import androidx.annotation.NonNull;

/**
 * What a join code resolves to. This is all a student can see of a course before joining it.
 * Immutable.
 */
public final class JoinCodeInfo {

    @NonNull private final String code;
    @NonNull private final String courseId;
    @NonNull private final String courseName;
    @NonNull private final String teacherName;
    @NonNull private final String teacherId;

    public JoinCodeInfo(
            @NonNull String code,
            @NonNull String courseId,
            @NonNull String courseName,
            @NonNull String teacherName,
            @NonNull String teacherId) {
        this.code = code;
        this.courseId = courseId;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.teacherId = teacherId;
    }

    @NonNull
    public String getCode() {
        return code;
    }

    @NonNull
    public String getCourseId() {
        return courseId;
    }

    @NonNull
    public String getCourseName() {
        return courseName;
    }

    @NonNull
    public String getTeacherName() {
        return teacherName;
    }

    @NonNull
    public String getTeacherId() {
        return teacherId;
    }
}
