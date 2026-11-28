package com.presence.attendance.department.domain;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** A batch's week for one semester, as the HOD imported it. Immutable. */
public final class Timetable {

    @NonNull private final String batchId;
    private final int semester;
    @NonNull private final List<TimetableSlot> slots;

    public Timetable(@NonNull String batchId, int semester, @NonNull List<TimetableSlot> slots) {
        this.batchId = batchId;
        this.semester = semester;
        this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
    }

    /** The document id: the batch and the semester, "MCA26S1". */
    @NonNull
    public static String idOf(@NonNull String batchId, int semester) {
        return batchId + "S" + semester;
    }

    /**
     * The id of a subject's course: batch, semester, its code and, for one lab group, "G" and the
     * group. The same subject imported again gets the same id, so nothing is made twice.
     */
    @NonNull
    public static String subjectId(
            @NonNull String batchId, int semester, @NonNull String code, int labGroup) {
        final String bare = code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return idOf(batchId, semester) + bare + (labGroup > 0 ? "G" + labGroup : "");
    }

    @NonNull
    public String getId() {
        return idOf(batchId, semester);
    }

    @NonNull
    public String getBatchId() {
        return batchId;
    }

    public int getSemester() {
        return semester;
    }

    @NonNull
    public List<TimetableSlot> getSlots() {
        return slots;
    }

    /** The slots on a day (1 is Monday) for any of the given courses, earliest first. */
    @NonNull
    public static List<TimetableSlot> on(
            @NonNull Collection<Timetable> timetables,
            int day,
            @NonNull Collection<String> courseIds) {
        final List<TimetableSlot> found = new ArrayList<>();
        for (Timetable timetable : timetables) {
            for (TimetableSlot slot : timetable.slots) {
                if (slot.getDay() == day && courseIds.contains(slot.getCourseId())) {
                    found.add(slot);
                }
            }
        }
        found.sort(
                Comparator.comparingInt(TimetableSlot::startMinutes)
                        .thenComparing(TimetableSlot::getCourseId));
        return found;
    }
}
