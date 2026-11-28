package com.presence.attendance.department.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.department.domain.TimetableSlot.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an imported timetable makes: one subject per code for the whole batch (its lectures and
 * tutorials), one per code and lab group for labs, and the week's slots pointing at them. Rows that
 * did not pass their check are left out. Immutable.
 */
public final class TimetablePlan {

    /** A subject the import makes or updates. Immutable. */
    public static final class Subject {
        @NonNull public final String courseId;
        @NonNull public final String name;
        @NonNull public final String code;

        /** 0 for the whole batch. */
        public final int labGroup;

        /** The timetable's words for the teacher. */
        @NonNull public final String facultyText;

        /** Who those words name on the faculty list, or null if nobody or several. */
        @Nullable public final FacultyMember teacher;

        Subject(
                @NonNull String courseId,
                @NonNull String name,
                @NonNull String code,
                int labGroup,
                @NonNull String facultyText,
                @Nullable FacultyMember teacher) {
            this.courseId = courseId;
            this.name = name;
            this.code = code;
            this.labGroup = labGroup;
            this.facultyText = facultyText;
            this.teacher = teacher;
        }

        /** The address the subject is assigned to, "" for nobody. */
        @NonNull
        public String teacherEmail() {
            return teacher != null ? teacher.getEmail() : "";
        }
    }

    @NonNull public final List<Subject> subjects;
    @NonNull public final Timetable timetable;

    private TimetablePlan(@NonNull List<Subject> subjects, @NonNull Timetable timetable) {
        this.subjects = Collections.unmodifiableList(subjects);
        this.timetable = timetable;
    }

    @NonNull
    public static TimetablePlan of(
            @NonNull List<TimetableCsv.Row> rows,
            @NonNull String batchId,
            int semester,
            @NonNull List<FacultyMember> faculty) {
        // A subject is named after, and taught by, its first lecture; a tutorial or a lab only
        // when it has none.
        final Map<String, TimetableCsv.Row> firstOf = new LinkedHashMap<>();
        final List<TimetableSlot> slots = new ArrayList<>();
        for (TimetableCsv.Row row : rows) {
            if (row.check != TimetableCsv.Check.READY || row.type == null) {
                continue;
            }
            final int group = row.type == Type.LAB ? row.labGroup : 0;
            final String id = Timetable.subjectId(batchId, semester, row.key, group);
            final TimetableCsv.Row first = firstOf.get(id);
            if (first == null || (first.type != Type.LECTURE && row.type == Type.LECTURE)) {
                firstOf.put(id, row);
            }
            slots.add(
                    new TimetableSlot(
                            row.day, row.start, row.end, id, row.type, row.room, row.faculty));
        }
        final List<Subject> subjects = new ArrayList<>();
        for (Map.Entry<String, TimetableCsv.Row> entry : firstOf.entrySet()) {
            final TimetableCsv.Row row = entry.getValue();
            subjects.add(
                    new Subject(
                            entry.getKey(),
                            row.subject,
                            row.code,
                            row.type == Type.LAB ? row.labGroup : 0,
                            row.faculty,
                            FacultyMatch.find(row.faculty, faculty)));
        }
        return new TimetablePlan(subjects, new Timetable(batchId, semester, slots));
    }
}
