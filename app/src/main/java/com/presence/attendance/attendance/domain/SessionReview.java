package com.presence.attendance.attendance.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Marks worth a second look before the teacher signs off a session. These are hints, never
 * verdicts: the teacher decides.
 */
public final class SessionReview {

    public enum Reason {
        /**
         * Exactly the same coordinates as another student's mark. Two real phones essentially never
         * report identical positions; a shared fake location does.
         */
        SAME_PLACE,
        /** Saved without a connection and sent later, so the scan time came from the phone. */
        SENT_LATE
    }

    /** Why one student's mark is worth a look. */
    public static final class Flag {
        @NonNull public final Reason reason;

        /** For {@link Reason#SAME_PLACE}: another student at the same place. */
        @Nullable public final String otherStudentId;

        Flag(@NonNull Reason reason, @Nullable String otherStudentId) {
            this.reason = reason;
            this.otherStudentId = otherStudentId;
        }
    }

    private SessionReview() {}

    /**
     * At most one flag per student, keyed by student id, for the records that count as present. The
     * same place outranks a late send.
     */
    @NonNull
    public static Map<String, Flag> flags(@NonNull Collection<AttendanceRecord> records) {
        final Map<String, Flag> flags = new HashMap<>();
        // Sorted so the "other student" named is the same on every run.
        final Map<String, List<String>> byPlace = new TreeMap<>();
        for (AttendanceRecord record : records) {
            if (!record.getStatus().countsAsPresent()) {
                continue;
            }
            final Double lat = record.getLat();
            final Double lng = record.getLng();
            if (lat != null && lng != null) {
                byPlace.computeIfAbsent(lat + "," + lng, place -> new ArrayList<>())
                        .add(record.getStudentId());
            }
        }
        for (List<String> students : byPlace.values()) {
            if (students.size() < 2) {
                continue;
            }
            students.sort(null);
            for (String student : students) {
                final String other = students.get(student.equals(students.get(0)) ? 1 : 0);
                flags.put(student, new Flag(Reason.SAME_PLACE, other));
            }
        }
        for (AttendanceRecord record : records) {
            if (record.getStatus() == AttendanceRecord.Status.LATE_SYNC) {
                flags.putIfAbsent(record.getStudentId(), new Flag(Reason.SENT_LATE, null));
            }
        }
        return flags;
    }
}
