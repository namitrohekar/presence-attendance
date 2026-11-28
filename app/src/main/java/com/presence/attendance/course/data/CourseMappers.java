package com.presence.attendance.course.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.data.Snapshots;
import com.presence.attendance.course.domain.Course;
import com.presence.attendance.course.domain.JoinCodeInfo;
import com.presence.attendance.course.domain.Member;
import java.util.HashMap;
import java.util.Map;

/** Explicit mapping for {@code courses}, {@code joinCodes} and {@code memberships} documents. */
final class CourseMappers {

    static final String TEACHER_ID = "teacherId";
    static final String STUDENT_ID = "studentId";
    static final String COURSE_ID = "courseId";
    static final String NAME = "name";
    private static final String CODE = "code";
    private static final String THRESHOLD = "threshold";
    private static final String JOIN_CODE = "joinCode";
    private static final String CREATED_AT = "createdAt";
    private static final String COURSE_NAME = "courseName";
    private static final String TEACHER_NAME = "teacherName";
    private static final String EMAIL = "email";
    private static final String ROLL_NO = "rollNo";
    private static final String COHORT = "cohort";
    private static final String JOINED_AT = "joinedAt";
    static final String TEACHER_EMAIL = "teacherEmail";
    static final String BATCH_ID = "batchId";
    static final String SEMESTER = "semester";
    static final String LAB_GROUP = "labGroup";
    private static final String CREATED_BY = "createdBy";

    private CourseMappers() {}

    /** The course in the snapshot, or null if the document is missing or malformed. */
    @Nullable
    static Course course(@NonNull DocumentSnapshot snapshot) {
        final String teacherId = Snapshots.string(snapshot, TEACHER_ID);
        final String name = Snapshots.string(snapshot, NAME);
        final String code = Snapshots.string(snapshot, CODE);
        final Long threshold = Snapshots.integer(snapshot, THRESHOLD);
        final String joinCode = Snapshots.string(snapshot, JOIN_CODE);
        final String batchId = Snapshots.string(snapshot, BATCH_ID);
        if (teacherId == null
                || name == null
                || code == null
                || threshold == null
                || (joinCode == null && batchId == null)) {
            return null;
        }
        if (batchId == null) {
            return new Course(
                    snapshot.getId(), teacherId, name, code, threshold.intValue(), joinCode);
        }
        final Long semester = Snapshots.integer(snapshot, SEMESTER);
        final Long labGroup = Snapshots.integer(snapshot, LAB_GROUP);
        final String teacherEmail = Snapshots.string(snapshot, TEACHER_EMAIL);
        if (semester == null || labGroup == null || teacherEmail == null) {
            return null;
        }
        return Course.subject(
                snapshot.getId(),
                teacherId,
                teacherEmail,
                name,
                code,
                threshold.intValue(),
                batchId,
                semester.intValue(),
                labGroup.intValue());
    }

    /** A new subject as the HOD writes it: unclaimed, under the HOD's name and server time. */
    @NonNull
    static Map<String, Object> newSubject(@NonNull Course subject, @NonNull String hodUid) {
        final Map<String, Object> document = new HashMap<>();
        document.put(TEACHER_ID, "");
        document.put(TEACHER_EMAIL, subject.getTeacherEmail());
        document.put(NAME, subject.getName());
        document.put(CODE, subject.getCode());
        document.put(THRESHOLD, subject.getThreshold());
        document.put(BATCH_ID, subject.getBatchId());
        document.put(SEMESTER, subject.getSemester());
        document.put(LAB_GROUP, subject.getLabGroup());
        document.put(CREATED_BY, hodUid);
        document.put(CREATED_AT, FieldValue.serverTimestamp());
        return document;
    }

    /** A registered student's membership of a subject of their batch: no join code. */
    @NonNull
    static Map<String, Object> newSubjectMembership(
            @NonNull User student, @NonNull Course subject) {
        final String rollNo = student.getRollNo();
        final String cohort = student.getCohort();
        if (rollNo == null || cohort == null) {
            throw new IllegalStateException("Only a registered student can be in a subject");
        }
        final Map<String, Object> document = new HashMap<>();
        document.put(COURSE_ID, subject.getId());
        document.put(TEACHER_ID, subject.getTeacherId());
        document.put(STUDENT_ID, student.getUid());
        document.put(NAME, student.getName());
        document.put(EMAIL, student.getEmail());
        document.put(ROLL_NO, rollNo);
        document.put(COHORT, cohort);
        document.put(JOINED_AT, FieldValue.serverTimestamp());
        return document;
    }

    /** The member in a membership snapshot, or null if the document is malformed. */
    @Nullable
    static Member member(@NonNull DocumentSnapshot snapshot) {
        final String studentId = Snapshots.string(snapshot, STUDENT_ID);
        final String name = Snapshots.string(snapshot, NAME);
        final String email = Snapshots.string(snapshot, EMAIL);
        if (studentId == null || name == null || email == null) {
            return null;
        }
        final String rollNo = Snapshots.string(snapshot, ROLL_NO);
        return new Member(studentId, name, email, rollNo != null ? rollNo : "");
    }

    @NonNull
    static Map<String, Object> newCourse(@NonNull Course course) {
        final Map<String, Object> document = new HashMap<>();
        document.put(TEACHER_ID, course.getTeacherId());
        document.put(NAME, course.getName());
        document.put(CODE, course.getCode());
        document.put(THRESHOLD, course.getThreshold());
        document.put(JOIN_CODE, course.getJoinCode());
        document.put(CREATED_AT, FieldValue.serverTimestamp());
        return document;
    }

    /** What the join code resolves to, or null if the document is missing or malformed. */
    @Nullable
    static JoinCodeInfo joinCode(@NonNull DocumentSnapshot snapshot) {
        final String courseId = Snapshots.string(snapshot, COURSE_ID);
        final String courseName = Snapshots.string(snapshot, COURSE_NAME);
        final String teacherName = Snapshots.string(snapshot, TEACHER_NAME);
        final String teacherId = Snapshots.string(snapshot, TEACHER_ID);
        if (courseId == null || courseName == null || teacherName == null || teacherId == null) {
            return null;
        }
        return new JoinCodeInfo(snapshot.getId(), courseId, courseName, teacherName, teacherId);
    }

    @NonNull
    static Map<String, Object> newJoinCode(@NonNull Course course, @NonNull User teacher) {
        final Map<String, Object> document = new HashMap<>();
        document.put(COURSE_ID, course.getId());
        document.put(COURSE_NAME, course.getName());
        document.put(TEACHER_NAME, teacher.getName());
        document.put(TEACHER_ID, teacher.getUid());
        return document;
    }

    /**
     * @throws IllegalStateException if the student is not registered; the UI never offers joining
     *     to an unregistered account
     */
    @NonNull
    static Map<String, Object> newMembership(
            @NonNull User student, @NonNull JoinCodeInfo joinCode) {
        final String rollNo = student.getRollNo();
        final String cohort = student.getCohort();
        if (rollNo == null || cohort == null) {
            throw new IllegalStateException("Only a registered student can join a course");
        }
        final Map<String, Object> document = new HashMap<>();
        document.put(COURSE_ID, joinCode.getCourseId());
        document.put(TEACHER_ID, joinCode.getTeacherId());
        document.put(STUDENT_ID, student.getUid());
        document.put(NAME, student.getName());
        document.put(EMAIL, student.getEmail());
        document.put(ROLL_NO, rollNo);
        document.put(COHORT, cohort);
        document.put(JOIN_CODE, joinCode.getCode());
        document.put(JOINED_AT, FieldValue.serverTimestamp());
        return document;
    }
}
