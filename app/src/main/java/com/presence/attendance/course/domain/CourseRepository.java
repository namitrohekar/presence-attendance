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

    /** The students enrolled in one of the teacher's courses, sorted by roll number. */
    @MainThread
    void loadMembers(
            @NonNull String teacherId,
            @NonNull String courseId,
            @NonNull Callback<List<Member>> callback);

    /** What saving one subject from a timetable import did. */
    enum SaveOutcome {
        CREATED,
        /** A new name or teacher; a new teacher starts unclaimed. */
        UPDATED,
        UNCHANGED
    }

    /**
     * Brings the user's subjects up to date before their courses are listed: a teacher claims the
     * subjects the HOD assigned to their address; a student joins their batch's subjects for the
     * current semester (their lab group's labs only) and follows a reassigned subject to its new
     * teacher. Resolves to how many changes were written.
     */
    @MainThread
    void syncSubjects(@NonNull User user, @NonNull Callback<Integer> callback);

    /** HOD: a batch's subjects for one semester, sorted by name and then lab group. */
    @MainThread
    void loadSubjects(
            @NonNull String batchId, int semester, @NonNull Callback<List<Course>> callback);

    /**
     * HOD: creates the subject, or gives an existing one the new name and teacher. Never changes
     * its batch, semester or lab group.
     */
    @MainThread
    void saveSubject(
            @NonNull User hod, @NonNull Course subject, @NonNull Callback<SaveOutcome> callback);
}
