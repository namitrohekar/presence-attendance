package com.presence.attendance.testutil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Callback;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.course.domain.Course;
import com.presence.attendance.course.domain.CourseRepository;
import com.presence.attendance.course.domain.JoinCodeInfo;
import com.presence.attendance.course.domain.Member;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link CourseRepository}. Answers synchronously with whatever the test set and records
 * every call, so tests can assert that a write did or did not happen.
 */
public final class FakeCourseRepository implements CourseRepository {

    public Outcome<List<Course>> teacherCourses = Outcome.success(new ArrayList<>());
    public Outcome<List<Course>> studentCourses = Outcome.success(new ArrayList<>());
    public Outcome<Course> createOutcome = Outcome.failure(AppError.UNKNOWN);
    public Outcome<JoinCodeInfo> findOutcome = Outcome.failure(AppError.JOIN_CODE_NOT_FOUND);
    public Outcome<JoinCodeInfo> joinOutcome = Outcome.failure(AppError.UNKNOWN);

    public final List<String> calls = new ArrayList<>();
    @Nullable public User lastJoiner;

    @Override
    public void loadTeacherCourses(
            @NonNull String teacherId, @NonNull Callback<List<Course>> callback) {
        calls.add("loadTeacherCourses:" + teacherId);
        callback.onResult(teacherCourses);
    }

    @Override
    public void loadStudentCourses(
            @NonNull String studentId, @NonNull Callback<List<Course>> callback) {
        calls.add("loadStudentCourses:" + studentId);
        callback.onResult(studentCourses);
    }

    @Override
    public void createCourse(
            @NonNull User teacher,
            @NonNull String name,
            @NonNull String code,
            @NonNull Callback<Course> callback) {
        calls.add("createCourse:" + name + ":" + code);
        callback.onResult(createOutcome);
    }

    @Override
    public void findJoinCode(@NonNull String joinCode, @NonNull Callback<JoinCodeInfo> callback) {
        calls.add("findJoinCode:" + joinCode);
        callback.onResult(findOutcome);
    }

    @Override
    public void joinCourse(
            @NonNull User student,
            @NonNull JoinCodeInfo joinCode,
            @NonNull Callback<JoinCodeInfo> callback) {
        calls.add("joinCourse:" + joinCode.getCode());
        lastJoiner = student;
        callback.onResult(joinOutcome);
    }

    public Outcome<List<Member>> membersOutcome = Outcome.success(new ArrayList<>());

    @Override
    public void loadMembers(
            @NonNull String teacherId,
            @NonNull String courseId,
            @NonNull Callback<List<Member>> callback) {
        calls.add("loadMembers:" + teacherId + ":" + courseId);
        callback.onResult(membersOutcome);
    }
}
