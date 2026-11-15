package com.presence.attendance.course.domain;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.Callback;
import java.util.List;

/** Courses, join codes and memberships. Every write needs a connection and is atomic. */
public interface CourseRepository {

    /** Courses the user teaches, sorted by name. */
    @MainThread
    void loadTeacherCourses(@NonNull String teacherId, @NonNull Callback<List<Course>> callback);

    /** Courses the user has joined, sorted by name. */
    @MainThread
    void loadStudentCourses(@NonNull String studentId, @NonNull Callback<List<Course>> callback);

    /** Creates a course with a new unique join code and the default attendance threshold. */
    @MainThread
    void createCourse(
            @NonNull User teacher,
            @NonNull String name,
            @NonNull String code,
            @NonNull Callback<Course> callback);

    /** Resolves a valid join code. Fails with {@code JOIN_CODE_NOT_FOUND} if no course has it. */
    @MainThread
    void findJoinCode(@NonNull String joinCode, @NonNull Callback<JoinCodeInfo> callback);

    /**
     * Enrols a registered student in the course the join code resolves to, with the roll number and
     * cohort from their registration. Fails with {@code ALREADY_JOINED} if they are already a
     * member.
     */
    @MainThread
    void joinCourse(
            @NonNull User student,
            @NonNull JoinCodeInfo joinCode,
            @NonNull Callback<JoinCodeInfo> callback);
}
