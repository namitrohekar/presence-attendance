package com.presence.attendance.course.domain;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Which of a batch's subjects a student must join, and which memberships must follow a subject to
 * its new teacher. A student is in every subject for the whole batch and in the labs of their own
 * lab group. A subject nobody has claimed yet waits: its membership would name no teacher.
 * Immutable.
 */
public final class SubjectEnrolment {

    @NonNull public final List<Course> toJoin;

    /** Subjects the student is in whose teacher has changed since they joined. */
    @NonNull public final List<Course> toMove;

    private SubjectEnrolment(@NonNull List<Course> toJoin, @NonNull List<Course> toMove) {
        this.toJoin = Collections.unmodifiableList(toJoin);
        this.toMove = Collections.unmodifiableList(toMove);
    }

    /**
     * @param subjects the batch's subjects for its current semester
     * @param teacherByCourse the teacher each of the student's memberships names, by course id
     * @param labGroup the student's lab group, 0 for none
     */
    @NonNull
    public static SubjectEnrolment plan(
            @NonNull List<Course> subjects,
            @NonNull Map<String, String> teacherByCourse,
            int labGroup) {
        final List<Course> toJoin = new ArrayList<>();
        final List<Course> toMove = new ArrayList<>();
        for (Course subject : subjects) {
            final boolean forStudent =
                    subject.getLabGroup() == 0 || subject.getLabGroup() == labGroup;
            if (!subject.isSubject() || !forStudent || subject.getTeacherId().isEmpty()) {
                continue;
            }
            final String teacher = teacherByCourse.get(subject.getId());
            if (teacher == null) {
                toJoin.add(subject);
            } else if (!teacher.equals(subject.getTeacherId())) {
                toMove.add(subject);
            }
        }
        return new SubjectEnrolment(toJoin, toMove);
    }

    public boolean isEmpty() {
        return toJoin.isEmpty() && toMove.isEmpty();
    }
}
