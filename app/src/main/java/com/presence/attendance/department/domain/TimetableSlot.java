package com.presence.attendance.department.domain;

import androidx.annotation.NonNull;
import java.util.Objects;

/**
 * One class in a batch's week: Monday 10:30 to 12:25, Operating System Lab for lab group 1, in
 * AL-301, taken by GS. Times are "HH:MM" on a 24-hour clock. Immutable.
 */
public final class TimetableSlot {

    /** What kind of class the timetable says it is. A tutorial counts with its lecture. */
    public enum Type {
        LECTURE,
        LAB,
        TUTORIAL
    }

    /** 1 is Monday and 7 is Sunday, as {@link java.time.DayOfWeek#getValue()}. */
    private final int day;

    @NonNull private final String start;
    @NonNull private final String end;
    @NonNull private final String courseId;
    @NonNull private final Type type;
    @NonNull private final String room;

    /** The timetable's own words for who teaches it, such as "AP+GS". Shown, never trusted. */
    @NonNull private final String faculty;

    public TimetableSlot(
            int day,
            @NonNull String start,
            @NonNull String end,
            @NonNull String courseId,
            @NonNull Type type,
            @NonNull String room,
            @NonNull String faculty) {
        this.day = day;
        this.start = start;
        this.end = end;
        this.courseId = courseId;
        this.type = type;
        this.room = room;
        this.faculty = faculty;
    }

    /** Minutes since midnight of "HH:MM", or -1 if it is not that shape. */
    public static int minutesOf(@NonNull String time) {
        if (!time.matches("^[0-9]{2}:[0-9]{2}$")) {
            return -1;
        }
        final int hours = Integer.parseInt(time.substring(0, 2));
        final int minutes = Integer.parseInt(time.substring(3));
        return hours < 24 && minutes < 60 ? hours * 60 + minutes : -1;
    }

    public int getDay() {
        return day;
    }

    @NonNull
    public String getStart() {
        return start;
    }

    @NonNull
    public String getEnd() {
        return end;
    }

    public int startMinutes() {
        return minutesOf(start);
    }

    public int endMinutes() {
        return minutesOf(end);
    }

    @NonNull
    public String getCourseId() {
        return courseId;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    @NonNull
    public String getRoom() {
        return room;
    }

    @NonNull
    public String getFaculty() {
        return faculty;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TimetableSlot)) {
            return false;
        }
        final TimetableSlot that = (TimetableSlot) other;
        return day == that.day
                && start.equals(that.start)
                && end.equals(that.end)
                && courseId.equals(that.courseId)
                && type == that.type
                && room.equals(that.room)
                && faculty.equals(that.faculty);
    }

    @Override
    public int hashCode() {
        return Objects.hash(day, start, end, courseId, type, room, faculty);
    }

    @NonNull
    @Override
    public String toString() {
        return day + " " + start + "-" + end + " " + courseId + " " + type;
    }
}
