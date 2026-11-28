package com.presence.attendance.course.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A course taught by one teacher: either the teacher's own, joined by students with its join code,
 * or a subject of a batch that the HOD made from the timetable. Immutable.
 */
public final class Course {

    @NonNull private final String id;
    @NonNull private final String teacherId;
    @NonNull private final String name;
    @NonNull private final String code;
    private final int threshold;
    @NonNull private final String joinCode;

    /** For a subject the HOD made from a timetable: its batch; null for a teacher's own course. */
    @Nullable private final String batchId;

    private final int semester;

    /** 0 for the whole batch. */
    private final int labGroup;

    /** The address the HOD assigned the subject to, "" for nobody or a teacher's own course. */
    @NonNull private final String teacherEmail;

    /** A teacher's own course, joined with its code. */
    public Course(
            @NonNull String id,
            @NonNull String teacherId,
            @NonNull String name,
            @NonNull String code,
            int threshold,
            @NonNull String joinCode) {
        this(id, teacherId, name, code, threshold, joinCode, null, 0, 0, "");
    }

    private Course(
            @NonNull String id,
            @NonNull String teacherId,
            @NonNull String name,
            @NonNull String code,
            int threshold,
            @NonNull String joinCode,
            @Nullable String batchId,
            int semester,
            int labGroup,
            @NonNull String teacherEmail) {
        this.id = id;
        this.teacherId = teacherId;
        this.name = name;
        this.code = code;
        this.threshold = threshold;
        this.joinCode = joinCode;
        this.batchId = batchId;
        this.semester = semester;
        this.labGroup = labGroup;
        this.teacherEmail = teacherEmail;
    }

    /**
     * A subject of a batch, made by the HOD. {@code teacherId} is "" until the assigned teacher
     * claims it. Students are in it by their batch and lab group, with no join code.
     */
    @NonNull
    public static Course subject(
            @NonNull String id,
            @NonNull String teacherId,
            @NonNull String teacherEmail,
            @NonNull String name,
            @NonNull String code,
            int threshold,
            @NonNull String batchId,
            int semester,
            int labGroup) {
        return new Course(
                id,
                teacherId,
                name,
                code,
                threshold,
                "",
                batchId,
                semester,
                labGroup,
                teacherEmail);
    }

    public boolean isSubject() {
        return batchId != null;
    }

    @Nullable
    public String getBatchId() {
        return batchId;
    }

    public int getSemester() {
        return semester;
    }

    public int getLabGroup() {
        return labGroup;
    }

    @NonNull
    public String getTeacherEmail() {
        return teacherEmail;
    }

    /** The same subject assigned to another teacher, unclaimed until they sign in. */
    @NonNull
    public Course withTeacher(@NonNull String email) {
        return new Course(
                id, "", name, code, threshold, joinCode, batchId, semester, labGroup, email);
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
