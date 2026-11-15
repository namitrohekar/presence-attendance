package com.presence.attendance.attendance.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.AttendanceRepository;
import com.presence.attendance.core.Outcome;
import com.presence.attendance.core.data.QueryLiveData;
import com.presence.attendance.core.data.Snapshots;
import java.util.List;

/** {@link AttendanceRepository} backed by Cloud Firestore. */
public final class FirestoreAttendanceRepository implements AttendanceRepository {

    private static final String ATTENDANCE = "attendance";
    private static final String SESSION_ID = "sessionId";
    private static final String TEACHER_ID = "teacherId";
    private static final String STUDENT_ID = "studentId";
    private static final String STATUS = "status";
    private static final String MARKED_AT = "markedAt";

    private final FirebaseFirestore firestore;

    public FirestoreAttendanceRepository(@NonNull FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @NonNull
    @Override
    public LiveData<Outcome<List<AttendanceRecord>>> observeSession(
            @NonNull String sessionId, @NonNull String teacherId) {
        // Both equality constraints are required by the rules; the order needs the composite
        // index in firestore.indexes.json.
        return new QueryLiveData<>(
                firestore
                        .collection(ATTENDANCE)
                        .whereEqualTo(SESSION_ID, sessionId)
                        .whereEqualTo(TEACHER_ID, teacherId)
                        .orderBy(MARKED_AT, Query.Direction.DESCENDING),
                FirestoreAttendanceRepository::record);
    }

    /** The record in the snapshot, or null if the document is malformed. */
    @Nullable
    private static AttendanceRecord record(@NonNull DocumentSnapshot snapshot) {
        final String studentId = Snapshots.string(snapshot, STUDENT_ID);
        final AttendanceRecord.Status status =
                AttendanceRecord.Status.fromWire(Snapshots.string(snapshot, STATUS));
        final Timestamp markedAt =
                snapshot.getTimestamp(MARKED_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        if (studentId == null || status == null || markedAt == null) {
            return null;
        }
        return new AttendanceRecord(studentId, status, markedAt.toDate().getTime());
    }
}
