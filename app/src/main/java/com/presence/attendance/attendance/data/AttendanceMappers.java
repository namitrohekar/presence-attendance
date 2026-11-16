package com.presence.attendance.attendance.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.PendingMark;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.core.data.Snapshots;
import com.presence.attendance.core.location.LocationFix;
import com.presence.attendance.session.domain.Session;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/** Explicit mapping for {@code attendance} documents. */
final class AttendanceMappers {

    static final String SESSION_ID = "sessionId";
    static final String TEACHER_ID = "teacherId";
    static final String MARKED_AT = "markedAt";
    static final String COURSE_ID = "courseId";
    static final String STUDENT_ID = "studentId";
    private static final String STATUS = "status";
    private static final String SOURCE = "source";
    private static final String TOKEN = "token";
    private static final String LAT = "lat";
    private static final String LNG = "lng";
    private static final String ACCURACY_M = "accuracyM";
    private static final String SOURCE_LIVE = "LIVE";
    private static final String SOURCE_QUEUED = "QUEUED";
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String NOTE = "note";
    private static final String UPDATED_BY = "updatedBy";
    private static final String UPDATED_AT = "updatedAt";
    private static final String SCANNED_AT = "scannedAt";

    private AttendanceMappers() {}

    /** The record in the snapshot, or null if the document is malformed. */
    @Nullable
    static AttendanceRecord record(@NonNull DocumentSnapshot snapshot) {
        final String sessionId = Snapshots.string(snapshot, SESSION_ID);
        final String studentId = Snapshots.string(snapshot, STUDENT_ID);
        final AttendanceRecord.Status status =
                AttendanceRecord.Status.fromWire(Snapshots.string(snapshot, STATUS));
        final Timestamp markedAt =
                snapshot.getTimestamp(MARKED_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        if (sessionId == null || studentId == null || status == null || markedAt == null) {
            return null;
        }
        return new AttendanceRecord(sessionId, studentId, status, markedAt.toDate().getTime());
    }

    /**
     * A LIVE mark. Course and teacher are the session's, as the rules require; the time is the
     * server's. Coordinates travel only in this document, readable by the student and the course's
     * teacher.
     */
    @NonNull
    static Map<String, Object> liveMark(
            @NonNull User student,
            @NonNull Session session,
            @NonNull String token,
            @NonNull LocationFix fix) {
        final Map<String, Object> document = new HashMap<>();
        document.put(SESSION_ID, session.getId());
        document.put(COURSE_ID, session.getCourseId());
        document.put(TEACHER_ID, session.getTeacherId());
        document.put(STUDENT_ID, student.getUid());
        document.put(STATUS, AttendanceRecord.Status.PRESENT.name());
        document.put(SOURCE, SOURCE_LIVE);
        document.put(TOKEN, token);
        document.put(MARKED_AT, FieldValue.serverTimestamp());
        document.put(LAT, fix.getLat());
        document.put(LNG, fix.getLng());
        document.put(ACCURACY_M, (double) fix.getAccuracyM());
        return document;
    }

    /**
     * A scan saved offline, sent late. {@code scannedAt} is the device clock at the scan; the rules
     * accept it only inside the window in which the scanned code was current.
     */
    @NonNull
    static Map<String, Object> queuedMark(@NonNull Session session, @NonNull PendingMark mark) {
        final Map<String, Object> document = new HashMap<>();
        document.put(SESSION_ID, session.getId());
        document.put(COURSE_ID, session.getCourseId());
        document.put(TEACHER_ID, session.getTeacherId());
        document.put(STUDENT_ID, mark.getStudentId());
        document.put(STATUS, AttendanceRecord.Status.LATE_SYNC.name());
        document.put(SOURCE, SOURCE_QUEUED);
        document.put(TOKEN, mark.getToken());
        document.put(MARKED_AT, FieldValue.serverTimestamp());
        document.put(SCANNED_AT, new Timestamp(new Date(mark.getScannedAtMillis())));
        document.put(LAT, mark.getLat());
        document.put(LNG, mark.getLng());
        document.put(ACCURACY_M, (double) mark.getAccuracyM());
        return document;
    }

    /** A teacher marking present a student who has no record for the session. */
    @NonNull
    static Map<String, Object> manualPresent(
            @NonNull String teacherId,
            @NonNull String courseId,
            @NonNull String sessionId,
            @NonNull String studentId,
            @NonNull String note) {
        final Map<String, Object> document = correction(teacherId, true, note);
        document.put(SESSION_ID, sessionId);
        document.put(COURSE_ID, courseId);
        document.put(TEACHER_ID, teacherId);
        document.put(STUDENT_ID, studentId);
        document.put(SOURCE, SOURCE_MANUAL);
        document.put(MARKED_AT, FieldValue.serverTimestamp());
        return document;
    }

    /** The only fields a teacher may change on an existing record. */
    @NonNull
    static Map<String, Object> correction(
            @NonNull String teacherId, boolean present, @NonNull String note) {
        final Map<String, Object> changes = new HashMap<>();
        changes.put(
                STATUS,
                (present
                                ? AttendanceRecord.Status.MANUAL_PRESENT
                                : AttendanceRecord.Status.MANUAL_ABSENT)
                        .name());
        changes.put(NOTE, note);
        changes.put(UPDATED_BY, teacherId);
        changes.put(UPDATED_AT, FieldValue.serverTimestamp());
        return changes;
    }
}
