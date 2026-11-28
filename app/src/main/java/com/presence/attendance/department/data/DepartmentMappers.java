package com.presence.attendance.department.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.presence.attendance.core.data.Snapshots;
import com.presence.attendance.department.domain.Batch;
import com.presence.attendance.department.domain.FacultyMember;
import com.presence.attendance.department.domain.Timetable;
import com.presence.attendance.department.domain.TimetableSlot;
import com.presence.attendance.roster.domain.StudentIdentity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit mapping for {@code batches} and {@code faculty} documents. Field lists are held to
 * {@code contracts/documents.json}.
 */
public final class DepartmentMappers {

    static final String PROGRAMME = "programme";
    static final String START_YEAR = "startYear";
    static final String YEARS = "years";
    static final String SEMESTER = "semester";
    static final String INTAKE = "intake";
    static final String LAB_GROUPS = "labGroups";
    static final String CREATED_BY = "createdBy";
    static final String CREATED_AT = "createdAt";
    static final String EMAIL = "email";
    static final String NAME = "name";
    static final String INITIALS = "initials";
    static final String ADDED_BY = "addedBy";
    static final String ADDED_AT = "addedAt";
    static final String BATCH_ID = "batchId";
    static final String SLOTS = "slots";
    static final String UPDATED_BY = "updatedBy";
    static final String UPDATED_AT = "updatedAt";
    static final String DAY = "day";
    static final String START = "start";
    static final String END = "end";
    static final String COURSE_ID = "courseId";
    static final String TYPE = "type";
    static final String ROOM = "room";
    static final String FACULTY = "faculty";

    private DepartmentMappers() {}

    /** The batch in the snapshot, or null if the document is missing or malformed. */
    @Nullable
    public static Batch batch(@NonNull DocumentSnapshot snapshot) {
        final String programme = Snapshots.string(snapshot, PROGRAMME);
        final Long startYear = Snapshots.integer(snapshot, START_YEAR);
        final Long years = Snapshots.integer(snapshot, YEARS);
        final Long semester = Snapshots.integer(snapshot, SEMESTER);
        final Long intake = Snapshots.integer(snapshot, INTAKE);
        final Long labGroups = Snapshots.integer(snapshot, LAB_GROUPS);
        if (programme == null
                || startYear == null
                || years == null
                || semester == null
                || intake == null
                || labGroups == null
                || !Batch.isProgramme(programme)
                || !Batch.isStartYear(startYear.intValue())) {
            return null;
        }
        final Batch batch =
                new Batch(
                        programme,
                        startYear.intValue(),
                        years.intValue(),
                        semester.intValue(),
                        intake.intValue(),
                        labGroups.intValue());
        // A document stored under another id is not this batch.
        return batch.getId().equals(snapshot.getId()) ? batch : null;
    }

    @NonNull
    static Map<String, Object> newBatch(
            @NonNull String programme,
            int startYear,
            int years,
            int intake,
            int labGroups,
            @NonNull String hodUid) {
        final Map<String, Object> document = new HashMap<>();
        document.put(PROGRAMME, programme);
        document.put(START_YEAR, startYear);
        document.put(YEARS, years);
        document.put(SEMESTER, 1);
        document.put(INTAKE, intake);
        document.put(LAB_GROUPS, labGroups);
        document.put(CREATED_BY, hodUid);
        document.put(CREATED_AT, FieldValue.serverTimestamp());
        return document;
    }

    /** The four fields the HOD may change after creating a batch. */
    @NonNull
    static Map<String, Object> batchUpdate(int years, int semester, int intake, int labGroups) {
        final Map<String, Object> fields = new HashMap<>();
        fields.put(YEARS, years);
        fields.put(SEMESTER, semester);
        fields.put(INTAKE, intake);
        fields.put(LAB_GROUPS, labGroups);
        return fields;
    }

    /** The faculty member in the snapshot, or null if the document is missing or malformed. */
    @Nullable
    static FacultyMember faculty(@NonNull DocumentSnapshot snapshot) {
        final String email = Snapshots.string(snapshot, EMAIL);
        final String name = Snapshots.string(snapshot, NAME);
        final String initials = Snapshots.string(snapshot, INITIALS);
        if (email == null
                || name == null
                || initials == null
                || !StudentIdentity.isName(name)
                || !FacultyMember.isInitials(initials)) {
            return null;
        }
        return new FacultyMember(email, name, initials);
    }

    @NonNull
    static Map<String, Object> newFaculty(
            @NonNull String email,
            @NonNull String name,
            @NonNull String initials,
            @NonNull String hodUid) {
        final Map<String, Object> document = new HashMap<>();
        document.put(EMAIL, email);
        document.put(NAME, name);
        document.put(INITIALS, initials);
        document.put(ADDED_BY, hodUid);
        document.put(ADDED_AT, FieldValue.serverTimestamp());
        return document;
    }

    @NonNull
    static Map<String, Object> facultyUpdate(@NonNull String name, @NonNull String initials) {
        final Map<String, Object> fields = new HashMap<>();
        fields.put(NAME, name);
        fields.put(INITIALS, initials);
        return fields;
    }

    /**
     * The timetable in the snapshot, or null if it is missing or malformed. A bad slot is skipped.
     */
    @Nullable
    static Timetable timetable(@NonNull DocumentSnapshot snapshot) {
        final String batchId = Snapshots.string(snapshot, BATCH_ID);
        final Long semester = Snapshots.integer(snapshot, SEMESTER);
        final Object stored = snapshot.get(SLOTS);
        if (batchId == null || semester == null || !(stored instanceof List)) {
            return null;
        }
        final List<TimetableSlot> slots = new ArrayList<>();
        for (Object item : (List<?>) stored) {
            final TimetableSlot slot = item instanceof Map ? slot((Map<?, ?>) item) : null;
            if (slot != null) {
                slots.add(slot);
            }
        }
        return new Timetable(batchId, semester.intValue(), slots);
    }

    @Nullable
    static TimetableSlot slot(@NonNull Map<?, ?> map) {
        final Object day = map.get(DAY);
        final Object start = map.get(START);
        final Object end = map.get(END);
        final Object courseId = map.get(COURSE_ID);
        final Object type = map.get(TYPE);
        final Object room = map.get(ROOM);
        final Object faculty = map.get(FACULTY);
        if (!(day instanceof Number)
                || !(start instanceof String)
                || !(end instanceof String)
                || !(courseId instanceof String)
                || !(type instanceof String)) {
            return null;
        }
        final TimetableSlot.Type kind;
        try {
            kind = TimetableSlot.Type.valueOf((String) type);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
        return new TimetableSlot(
                ((Number) day).intValue(),
                (String) start,
                (String) end,
                (String) courseId,
                kind,
                room instanceof String ? (String) room : "",
                faculty instanceof String ? (String) faculty : "");
    }

    @NonNull
    static Map<String, Object> newTimetable(@NonNull Timetable timetable, @NonNull String hodUid) {
        final List<Map<String, Object>> slots = new ArrayList<>();
        for (TimetableSlot slot : timetable.getSlots()) {
            slots.add(slot(slot));
        }
        final Map<String, Object> document = new HashMap<>();
        document.put(BATCH_ID, timetable.getBatchId());
        document.put(SEMESTER, timetable.getSemester());
        document.put(SLOTS, slots);
        document.put(UPDATED_BY, hodUid);
        document.put(UPDATED_AT, FieldValue.serverTimestamp());
        return document;
    }

    @NonNull
    static Map<String, Object> slot(@NonNull TimetableSlot slot) {
        final Map<String, Object> map = new HashMap<>();
        map.put(DAY, slot.getDay());
        map.put(START, slot.getStart());
        map.put(END, slot.getEnd());
        map.put(COURSE_ID, slot.getCourseId());
        map.put(TYPE, slot.getType().name());
        map.put(ROOM, slot.getRoom());
        map.put(FACULTY, slot.getFaculty());
        return map;
    }
}
